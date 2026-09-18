package com.leafyezi233.minecrafttofish.wheel.client;

import com.leafyezi233.minecrafttofish.economy.client.ClientValueCache;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.wheel.FishWheelScreenHandler;
import com.leafyezi233.minecrafttofish.wheel.WheelConstants;
import com.leafyezi233.minecrafttofish.wheel.WheelSector;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.math.RotationAxis;

/**
 * 渔轮转盘的客户端界面（<b>仅客户端</b>）。
 *
 * <h2>坐标陷阱（实测确认，不是猜的）</h2>
 * {@code HandledScreen.render()} 的顺序是：
 * <pre>
 *   drawBackground(ctx, ...)      ← 此时<b>还没有</b> translate(x, y)
 *   Screen.render(...)
 *   matrices.push(); translate(x, y)
 *   ...画槽位...
 *   drawForeground(ctx, ...)      ← 此时<b>已经</b> translate(x, y)
 *   matrices.pop()
 * </pre>
 * 所以 {@link #drawBackground} 里画面板必须自己加 {@code x}/{@code y} 偏移，
 * 而 {@link #drawForeground} 里用面板相对坐标。转盘放 foreground：
 * 既画在槽位之上，坐标也省事。
 *
 * <h2>为什么不用贴图</h2>
 * 面板与转盘全部用 {@code DrawContext.fill} 画。已确认 {@code fill} 内部取
 * {@code matrices.peek().getPositionMatrix()}，<b>受矩阵影响</b>，
 * 所以旋转动画可以直接靠 {@code MatrixStack} 变换实现，不需要任何贴图资源。
 */
public class FishWheelScreen extends HandledScreen<FishWheelScreenHandler> {

	/** 面板配色 */
	private static final int PANEL_BG = 0xFFC6C6C6;
	private static final int PANEL_BORDER_LIGHT = 0xFFFFFFFF;
	private static final int PANEL_BORDER_DARK = 0xFF555555;
	private static final int SLOT_BG = 0xFF8B8B8B;

	/** 转盘配色 */
	private static final int WHEEL_RIM = 0xFF3A3A3A;
	private static final int WHEEL_HUB = 0xFFF0F0F0;
	private static final int POINTER_COLOR = 0xFFFF3B30;

	/** 文字颜色 */
	private static final int TITLE_COLOR = 0xFF404040;
	private static final int RESULT_WIN_COLOR = 0xFF1B7F1B;
	private static final int RESULT_LOSE_COLOR = 0xFF8B1A1A;

	/** 每根辐条的角度宽度（度）。360 / 5 = 72 根，视觉上足够圆滑 */
	private static final float SPOKE_STEP_DEGREES = 5.0f;

	/** 按钮 */
	private ButtonWidget spinButton;

	/** 转盘动画状态 */
	private final WheelAnimation animation = new WheelAnimation();

	/** 上次见到的服务端抽奖序号，用于检测"有新结果" */
	private int lastSeenSequence = 0;

	/** 服务端同步来的奖励金额；{@link WheelConstants#NO_RESULT_VALUE} 表示尚未结算 */
	private long lastRewardValue = WheelConstants.NO_RESULT_VALUE;

	public FishWheelScreen(FishWheelScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		this.backgroundWidth = WheelConstants.SCREEN_WIDTH;
		this.backgroundHeight = WheelConstants.SCREEN_HEIGHT;
		this.playerInventoryTitleY = WheelConstants.INVENTORY_TITLE_Y;
	}

	@Override
	protected void init() {
		super.init();

		// 标题在面板内的位置
		this.titleX = 8;
		this.titleY = 6;

		this.spinButton = ButtonWidget.builder(
						Text.translatable("wheel.minecraft_to_fish.button.spin"),
						button -> startSpin())
				.dimensions(this.x + WheelConstants.BUTTON_X, this.y + WheelConstants.BUTTON_Y,
						WheelConstants.BUTTON_WIDTH, WheelConstants.BUTTON_HEIGHT)
				.build();

		this.addDrawableChild(spinButton);

		// 进界面时对齐一次序号，避免把"上一次的结果"当成新结果播一遍动画
		this.lastSeenSequence = handler.spinSequence();
	}

	/** 点「开始」：只发一个原版按钮信号，结果等服务端推回来 */
	private void startSpin() {
		if (this.client == null || this.client.player == null || this.client.interactionManager == null) {
			return;
		}
		// 只走原版按钮通道：客户端只发信号，服务端 onButtonClick 才是权威
		// （服务端会校验 syncId 与非旁观状态，并自动 sendContentUpdates）
		this.client.interactionManager.clickButton(handler.syncId, WheelConstants.BUTTON_SPIN);

		// 短暂禁用，避免同一次动画期间被连点；服务端另有冷却兜底
		spinButton.active = false;
	}

	/**
	 * 每 tick 推进动画。
	 * <p>注意<b>不能</b>覆写 {@code HandledScreen.tick()} —— 它是 {@code final}，
	 * 必须用这个钩子方法。
	 */
	@Override
	protected void handledScreenTick() {
		super.handledScreenTick();

		// 服务端推来新结果 → 启动动画。
		// 此刻服务端<b>还没</b>改动物品栏（延迟结算），所以槽里仍是原来的鱼，
		// 玩家在转动期间一直看得见它 —— 不会提前剧透结果。
		int seq = handler.spinSequence();
		if (seq > lastSeenSequence) {
			lastSeenSequence = seq;
			animation.sync(seq, handler.resultMultiplier(), handler.wheelTable(),
					EconomyConfig.get().wheelSpinDurationMs);
			// 本次奖励金额等服务端结算后随属性同步过来，这里先清空
			lastRewardValue = WheelConstants.NO_RESULT_VALUE;
		}

		animation.update();

		// 奖励金额取服务端同步的权威值。
		// 不再自己读槽位算 —— 动画结束是本地时钟决定的，与服务端的结算包存在竞态，
		// 包晚到一步就会读到加成前的旧值，显示错误金额。
		lastRewardValue = handler.resultValue();

		// 动画播完就恢复按钮
		if (!animation.isSpinning() && spinButton != null && !spinButton.active) {
			spinButton.active = true;
		}
	}

	// ---------------------------------------------------------------- 绘制

	/** 面板背景。注意：此方法在 translate(x, y) <b>之前</b>调用，必须自己加偏移 */
	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int left = this.x;
		int top = this.y;
		int right = left + this.backgroundWidth;
		int bottom = top + this.backgroundHeight;

		// 面板主体
		context.fill(left, top, right, bottom, PANEL_BG);

		// 立体边框：上/左亮，下/右暗，贴近原版容器观感
		context.fill(left, top, right - 1, top + 1, PANEL_BORDER_LIGHT);
		context.fill(left, top, left + 1, bottom - 1, PANEL_BORDER_LIGHT);
		context.fill(left + 1, bottom - 1, right, bottom, PANEL_BORDER_DARK);
		context.fill(right - 1, top + 1, right, bottom, PANEL_BORDER_DARK);

		// 鱼槽底
		int slotX = left + WheelConstants.SLOT_X;
		int slotY = top + WheelConstants.SLOT_Y;
		context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, PANEL_BORDER_DARK);
		context.fill(slotX, slotY, slotX + 16, slotY + 16, SLOT_BG);
	}

	/**
	 * 前景：转盘 + 结果文字。
	 * <p>此方法在 translate(x, y) <b>之后</b>调用，所以用面板相对坐标。
	 */
	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		WheelTable table = handler.wheelTable();

		drawWheel(context, table);
		drawPointer(context);
		drawResultText(context);
	}

	/** 画转盘：按扇区切成细辐条，整体受旋转角度影响 */
	private void drawWheel(DrawContext context, WheelTable table) {
		float rotation = animation.currentAngle();
		int cx = WheelConstants.WHEEL_CENTER_X;
		int cy = WheelConstants.WHEEL_CENTER_Y;
		int radius = WheelConstants.WHEEL_RADIUS;

		// 底盘（扇区画不到的边缘兜底）
		fillCircle(context, cx, cy, radius, WHEEL_RIM);

		// 逐扇区、逐辐条绘制
		for (int i = 0; i < table.size(); i++) {
			WheelSector sector = table.sector(i);
			float start = table.startAngle(i);
			float end = table.endAngle(i);

			for (float angle = start; angle < end; angle += SPOKE_STEP_DEGREES) {
				// 扇区末端那一根不要越过边界
				float span = Math.min(SPOKE_STEP_DEGREES, end - angle);
				drawSpoke(context, cx, cy, radius, angle + span / 2.0f, span, rotation, sector.color());
			}
		}

		// 中心轴
		fillCircle(context, cx, cy, 4, WHEEL_HUB);
	}

	/**
	 * 画一根辐条。
	 * <p>做法：把坐标系移到圆心 → 旋转 → 画一个从圆心射出的细长矩形。
	 * {@code fill} 受矩阵影响，所以这个矩形会跟着转。
	 */
	private void drawSpoke(DrawContext context, int cx, int cy, int radius,
			float angleDegrees, float spanDegrees, float extraRotation, int color) {

		var matrices = context.getMatrices();
		matrices.push();
		matrices.translate((float) cx, (float) cy, 0.0f);
		// 0° 指向正上方，所以要先回正 90°（矩阵默认 0° 指向右侧）
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angleDegrees + extraRotation - 90.0f));

		// 辐条宽度按角度换算成像素，保证相邻辐条刚好接上不留缝
		int halfWidth = Math.max(1, (int) Math.ceil(radius * Math.toRadians(spanDegrees) / 2.0) + 1);
		context.fill(0, -halfWidth, radius, halfWidth, color);

		matrices.pop();
	}

	/** 用一圈水平细条拼出一个实心圆（避免引入任何贴图） */
	private static void fillCircle(DrawContext context, int cx, int cy, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			int halfWidth = (int) Math.sqrt((double) radius * radius - (double) dy * dy);
			if (halfWidth <= 0) {
				continue;
			}
			context.fill(cx - halfWidth, cy + dy, cx + halfWidth, cy + dy + 1, color);
		}
	}

	/** 正上方的固定指针（不随盘面旋转） */
	private void drawPointer(DrawContext context) {
		int cx = WheelConstants.WHEEL_CENTER_X;
		int top = WheelConstants.WHEEL_CENTER_Y - WheelConstants.WHEEL_RADIUS - 4;

		// 一个朝下的三角形：逐行加宽
		for (int row = 0; row < 7; row++) {
			int halfWidth = row + 1;
			context.fill(cx - halfWidth, top + row, cx + halfWidth + 1, top + row + 1, POINTER_COLOR);
		}
	}

	/**
	 * 结果文字：动画结束<b>且服务端已结算</b>后才显示。
	 *
	 * <p>两个条件都要满足，否则会出现「先显示 ×2！、下一帧才补上金额」的闪烁 ——
	 * 动画结束由本地时钟决定，服务端的结算包可能还在路上。
	 */
	private void drawResultText(DrawContext context) {
		if (!animation.hasResult() || animation.isSpinning()) {
			return;
		}
		if (lastRewardValue == WheelConstants.NO_RESULT_VALUE) {
			// 服务端还没把结果同步过来，这一帧什么都不显示，等下一 tick
			return;
		}

		int multiplier = animation.resultMultiplier();
		int centerX = WheelConstants.WHEEL_CENTER_X;
		int textY = WheelConstants.RESULT_TEXT_Y;

		if (multiplier <= 0) {
			context.drawCenteredTextWithShadow(this.textRenderer,
					Text.translatable("wheel.minecraft_to_fish.result.lose"),
					centerX, textY, RESULT_LOSE_COLOR);
			return;
		}

		Text win = Text.translatable("wheel.minecraft_to_fish.result.win", multiplier);
		context.drawCenteredTextWithShadow(this.textRenderer, win, centerX, textY, RESULT_WIN_COLOR);

		// 奖励金额用服务端同步的权威值
		context.drawCenteredTextWithShadow(this.textRenderer,
				Text.literal(ClientValueCache.format(lastRewardValue)),
				centerX, textY + 11, RESULT_WIN_COLOR);
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		this.renderBackground(context);
		super.render(context, mouseX, mouseY, delta);
		this.drawMouseoverTooltip(context, mouseX, mouseY);
	}
}
