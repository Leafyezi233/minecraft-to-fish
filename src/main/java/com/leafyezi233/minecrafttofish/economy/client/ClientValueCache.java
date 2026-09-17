package com.leafyezi233.minecrafttofish.economy.client;

import java.util.LinkedHashMap;
import java.util.Map;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.net.ValueTableSyncPayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * 客户端价值表镜像（<b>仅客户端</b>）。
 *
 * <p>服务端的价值表来自数据包 {@code fish_values/*.json}，客户端本地没有。
 * 本类接收 {@link ValueTableSyncPayload} 并保存一份只读副本，供 HUD 查询。
 *
 * <p><b>与 {@code ItemValueRegistry} 的关系</b>：两者刻意分离。
 * 服务端的 {@code ItemValueRegistry} 是权威数据，绝不会被网络数据覆盖；
 * 本类只服务于客户端显示，断线即清空。
 *
 * <h2>解析优先级</h2>
 * 与服务端保持一致：精确物品价值 &gt; 标签价值（多个命中取<b>最大</b>）&gt; 无价值。
 * 兜底公式不参与同步 —— 它由客户端自己的配置决定，不属于服务端价值表的一部分。
 */
@Environment(EnvType.CLIENT)
public final class ClientValueCache {

	/** 精确物品价值：物品 id -> 价值 */
	private static volatile Map<Identifier, Long> items = Map.of();

	/** 标签价值：标签 id -> 价值 */
	private static volatile Map<Identifier, Long> tags = Map.of();

	/** 服务端配置里的货币名，保证客户端显示的货币与服务端一致 */
	private static volatile String currencyName = "";

	/** 是否已收到过服务端的价值表 */
	private static volatile boolean synced = false;

	private ClientValueCache() {
	}

	/** 注册网络接收器与断线清理（幂等） */
	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(ValueTableSyncPayload.TYPE, (payload, player, responseSender) -> {
			// 网络回调在渲染线程上执行（见 Fabric 的 PlayPacketHandler 约定），直接写入即可
			apply(payload);
		});

		// 断线后清空，避免残留上一个服务器的价格表
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());

		MyMod.LOGGER.info("[economy] 客户端价值表接收器已注册：{}", ValueTableSyncPayload.CHANNEL);
	}

	/** 用服务端发来的快照整体替换本地镜像 */
	public static void apply(ValueTableSyncPayload payload) {
		if (payload == null) {
			return;
		}
		items = Map.copyOf(new LinkedHashMap<>(payload.items()));
		tags = Map.copyOf(new LinkedHashMap<>(payload.tags()));
		currencyName = payload.currencyName();
		synced = true;

		if (EconomyConfig.get().debugLogging) {
			MyMod.LOGGER.info("[economy] 已同步服务端价值表：{} 条物品 + {} 条标签，货币={}",
					items.size(), tags.size(), currencyName);
		}
	}

	/** 清空镜像（断线时调用） */
	public static void clear() {
		items = Map.of();
		tags = Map.of();
		currencyName = "";
		synced = false;
	}

	/** 是否已经拿到过服务端的价值表 */
	public static boolean isSynced() {
		return synced;
	}

	// ---------------------------------------------------------------- 查询

	/**
	 * 查询物品价值（客户端侧，只读）。
	 *
	 * @return 价值；未登记或镜像为空时返回 0
	 */
	public static long resolve(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return 0L;
		}

		// 1) 精确物品价值
		Long direct = items.get(Registries.ITEM.getId(stack.getItem()));
		if (direct != null) {
			return direct;
		}

		// 2) 标签价值：多个标签命中时取最大值，与服务端规则一致
		long best = 0L;
		for (Map.Entry<Identifier, Long> entry : tags.entrySet()) {
			long value = entry.getValue();
			if (value <= best) {
				continue;
			}
			if (stack.isIn(TagKey.of(RegistryKeys.ITEM, entry.getKey()))) {
				best = value;
			}
		}
		return best;
	}

	/**
	 * 金额格式化，例如 {@code "25 渔币"}。
	 * <p>优先使用服务端同步过来的货币名；还没同步到时退回本地配置。
	 */
	public static String format(long amount) {
		String currency = currencyName;
		if (currency == null || currency.isEmpty()) {
			currency = EconomyConfig.get().currencyName;
		}
		return amount + " " + currency;
	}
}
