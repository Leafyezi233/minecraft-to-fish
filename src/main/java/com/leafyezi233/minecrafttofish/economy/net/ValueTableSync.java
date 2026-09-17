package com.leafyezi233.minecrafttofish.economy.net;

import java.util.LinkedHashMap;
import java.util.Map;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueRegistry;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * 价值表服务端同步。
 *
 * <p>客户端 HUD 需要知道"手上这条鱼值多少"，但价值表是服务端数据包的一部分，
 * 联机时客户端本地没有。本类负责把表推给客户端。
 *
 * <h2>为什么不在 JOIN 时发</h2>
 * Fabric 的 {@code ServerPlayConnectionEvents.JOIN} 触发在服务端下发
 * "初始频道注册包"的<b>同时</b>，此刻服务端还不知道客户端能收哪些频道 ——
 * 此时发出去的包会被客户端当作未知频道丢弃。因此改为监听
 * {@link S2CPlayChannelEvents#REGISTER}：等客户端明确声明"我能收这个频道"之后再发，
 * 这是唯一可靠的投递点。
 *
 * <h2>三个下发时机</h2>
 * <ol>
 *   <li><b>客户端声明频道时</b> —— 覆盖玩家加入（进服立刻生效）</li>
 *   <li><b>数据包重载成功时</b> —— {@code /fv reload} 后无需重连</li>
 *   <li><b>{@code /fv set} 改价后</b> —— 见 {@code FishValueCommand#set}，走 {@link #broadcast}</li>
 * </ol>
 *
 * <p>重复下发是安全的：客户端只是整体替换镜像（幂等）。
 * <p><b>容错约定</b>：同步失败只记 WARN，绝不打断玩家加入或数据包重载。
 */
public final class ValueTableSync {

	private static boolean registered = false;

	private ValueTableSync() {
	}

	/** 注册同步钩子（幂等；服务端与客户端都会调用，客户端上这些事件不会触发） */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		// 客户端声明能收本频道 → 立刻推一份，这是玩家加入时的可靠投递点
		S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (channels.contains(ValueTableSyncPayload.CHANNEL)) {
				send(handler.getPlayer(), sender);
			}
		});

		// 数据包重载完成（价值表已重新加载）→ 广播给所有在线玩家
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				broadcast(server);
			}
		});
	}

	/** 给单个玩家发一份当前价值表 */
	public static void send(ServerPlayerEntity player) {
		send(player, null);
	}

	private static void send(ServerPlayerEntity player, PacketSender sender) {
		if (player == null) {
			return;
		}
		try {
			ValueTableSyncPayload payload = buildPayload();
			if (sender != null) {
				sender.sendPacket(payload);
			} else {
				ServerPlayNetworking.send(player, payload);
			}
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 向 {} 同步价值表失败：{}", player.getName().getString(), t.toString());
		}
	}

	/** 给所有在线玩家发一份当前价值表 */
	public static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ValueTableSyncPayload payload;
		try {
			payload = buildPayload();
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 价值表快照构建失败，跳过同步：{}", t.toString());
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			try {
				ServerPlayNetworking.send(player, payload);
			} catch (Throwable t) {
				MyMod.LOGGER.warn("[economy] 价值表同步失败：{}", t.toString());
			}
		}
	}

	/**
	 * 构建当前价值表的网络快照。
	 *
	 * <p>精确物品价值会先合入运行时覆盖（{@code /fv set}），覆盖优先 ——
	 * 与 {@link ItemValueRegistry} 的解析优先级保持一致。
	 */
	public static ValueTableSyncPayload buildPayload() {
		Map<Identifier, Long> items = new LinkedHashMap<>(ItemValueRegistry.itemValues());
		items.putAll(ItemValueRegistry.runtimeOverrides());
		return new ValueTableSyncPayload(items, ItemValueRegistry.tagValues(), EconomyConfig.get().currencyName);
	}
}
