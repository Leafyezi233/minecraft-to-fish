package com.leafyezi233.minecrafttofish.economy.client;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.item.ModItems;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

/**
 * 价值悬浮窗（<b>仅客户端</b>）。
 *
 * <p>玩家主手或副手拿着模组里的鱼时，在屏幕<b>右侧正中</b>显示这条鱼的名字和它值多少钱，
 * 让"钓到 → 卖钱"这条循环随时可见，不用再去敲 {@code /fv query}。
 *
 * <h2>显示规则</h2>
 * <ul>
 *   <li>只认模组自己的三条鱼（{@code aggressive_fish} / {@code brutal_fish} / {@code timid_fish}）；
 *       原版物品与其他模组物品不显示，避免刷屏</li>
 *   <li>主手优先：主手有鱼就显示主手的，否则看副手</li>
 *   <li>价值来自服务端同步（见 {@link ClientValueCache}），联机同样可用</li>
 *   <li>按 F1 隐藏 HUD 时一并隐藏；可在配置里用 {@code showValueHud} 整体关闭</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class EconomyHud {

	/** 距屏幕右边缘的间距 */
	private static final int MARGIN_RIGHT = 6;

	/** 背景框内边距 */
	private static final int PADDING = 4;

	/** 两行文字之间的额外行距 */
	private static final int LINE_GAP = 2;

	/** 背景色：半透明黑 */
	private static final int BACKGROUND = 0x90000000;

	/** 鱼名颜色：金色 */
	private static final int NAME_COLOR = 0xFFAA00;

	/** 价值颜色：绿色 */
	private static final int VALUE_COLOR = 0x55FF55;

	/** 未登记价值时的颜色：灰色 */
	private static final int NO_VALUE_COLOR = 0xAAAAAA;

	private static boolean initialized = false;

	/** 调试日志用：上次记录过的物品与价值，避免每帧刷屏 */
	private static Item lastLoggedItem;
	private static long lastLoggedValue = Long.MIN_VALUE;

	private EconomyHud() {
	}

	/** 注册 HUD 渲染回调（幂等） */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ClientValueCache.register();
		HudRenderCallback.EVENT.register(EconomyHud::onHudRender);
	}

	private static void onHudRender(DrawContext context, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.player == null) {
			return;
		}
		// F1 隐藏 HUD 时跟着一起藏起来
		if (client.options != null && client.options.hudHidden) {
			return;
		}
		// 配置里可整体关闭
		if (!EconomyConfig.get().showValueHud) {
			return;
		}

		ItemStack stack = fishInHands(client);
		if (stack == null) {
			return;
		}

		TextRenderer textRenderer = client.textRenderer;
		if (textRenderer == null) {
			return;
		}

		Text nameText = stack.getName();
		long value = ClientValueCache.resolve(stack);
		Text valueText = value > 0L
				? Text.literal(ClientValueCache.format(value))
				: Text.translatable("hud.minecraft_to_fish.no_value");

		logIfChanged(stack, value);

		// 两行宽度取大者，保证背景框包住最长的一行
		int textWidth = Math.max(textRenderer.getWidth(nameText), textRenderer.getWidth(valueText));
		int lineHeight = textRenderer.fontHeight;
		int boxWidth = textWidth + PADDING * 2;
		int boxHeight = lineHeight * 2 + LINE_GAP + PADDING * 2;

		int right = context.getScaledWindowWidth() - MARGIN_RIGHT;
		int left = right - boxWidth;
		// 竖直居中：以屏幕中线为基准把整个框居中
		int top = (context.getScaledWindowHeight() - boxHeight) / 2;
		int bottom = top + boxHeight;

		context.fill(left, top, right, bottom, BACKGROUND);

		// 文字左对齐，两行都从同一个 x 开始，视觉上更整齐
		int textX = left + PADDING;
		context.drawTextWithShadow(textRenderer, nameText, textX, top + PADDING, NAME_COLOR);
		context.drawTextWithShadow(textRenderer, valueText, textX, top + PADDING + lineHeight + LINE_GAP,
				value > 0L ? VALUE_COLOR : NO_VALUE_COLOR);
	}

	/**
	 * 取玩家手上（主手优先，其次副手）的模组鱼。
	 *
	 * @return 命中的物品栈；两只手都没有模组鱼时返回 {@code null}
	 */
	private static ItemStack fishInHands(MinecraftClient client) {
		ItemStack main = client.player.getMainHandStack();
		if (isModFish(main)) {
			return main;
		}
		ItemStack off = client.player.getOffHandStack();
		if (isModFish(off)) {
			return off;
		}
		return null;
	}

	/** 是否是模组自己的三条鱼之一 */
	private static boolean isModFish(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item == ModItems.AGGRESSIVE_FISH
				|| item == ModItems.BRUTAL_FISH
				|| item == ModItems.TIMID_FISH;
	}

	/** 调试日志：只在"手上的鱼或它的价格"发生变化时打一行，不刷屏 */
	private static void logIfChanged(ItemStack stack, long value) {
		if (!EconomyConfig.get().debugLogging) {
			return;
		}
		Item item = stack.getItem();
		if (item == lastLoggedItem && value == lastLoggedValue) {
			return;
		}
		lastLoggedItem = item;
		lastLoggedValue = value;
		MyMod.LOGGER.info("[economy] HUD：{} -> {}（已同步={}）",
				item.toString(),
				value > 0L ? ClientValueCache.format(value) : "未登记价值",
				ClientValueCache.isSynced());
	}
}
