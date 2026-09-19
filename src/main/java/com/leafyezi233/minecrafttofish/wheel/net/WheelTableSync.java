package com.leafyezi233.minecrafttofish.wheel.net;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 转盘扇区表的服务端同步。
 *
 * <p>与 {@code economy.net.ValueTableSync} 同一套模式（那个是给 HUD 同步价格表）。
 * 这里同步的是转盘几何，客户端没有它就没法把指针停到正确的扇区。
 *
 * <h2>为什么不在 JOIN 时发</h2>
 * Fabric 的 {@code ServerPlayConnectionEvents.JOIN} 触发在服务端下发
 * 「初始频道注册包」的<b>同时</b>，此刻服务端还不知道客户端能收哪些频道 ——
 * 此时发出去的包会被客户端当作未知频道丢弃。因此监听
 * {@link S2CPlayChannelEvents#REGISTER}：等客户端明确声明「我能收这个频道」之后再发。
 * 这条经验是价格表同步时踩出来的，这里直接沿用。
 *
 * <h2>下发时机</h2>
 * <ol>
 *   <li><b>客户端声明频道时</b> —— 覆盖玩家加入</li>
 *   <li><b>数据包重载成功时</b> —— {@code /fishvalue reload} 后无需重连</li>
 * </ol>
 * 配置改动需要重启或重载才会生效，与转盘本身的配置读取时机一致。
 *
 * <p><b>容错约定</b>：同步失败只记 WARN，绝不打断玩家加入或数据包重载。
 */
public final class WheelTableSync {

	private static boolean registered = false;

	private WheelTableSync() {
	}

	/** 注册同步钩子（幂等；客户端上这些事件不会触发） */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		// 客户端声明能收本频道 → 立刻推一份，这是玩家加入时的可靠投递点
		S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (channels.contains(WheelTableSyncPayload.CHANNEL)) {
				send(handler.getPlayer(), sender);
			}
		});

		// 数据包重载完成 → 广播给所有在线玩家（扇区表可能已变）
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				broadcast(server);
			}
		});
	}

	/** 给单个玩家发一份当前扇区表 */
	public static void send(ServerPlayerEntity player) {
		send(player, null);
	}

	private static void send(ServerPlayerEntity player, PacketSender sender) {
		if (player == null) {
			return;
		}
		try {
			WheelTableSyncPayload payload = WheelTableSyncPayload.of(WheelTable.current());
			if (sender != null) {
				sender.sendPacket(payload);
			} else {
				ServerPlayNetworking.send(player, payload);
			}
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[wheel] 向 {} 同步扇区表失败：{}", player.getName().getString(), t.toString());
		}
	}

	/** 给所有在线玩家发一份当前扇区表 */
	public static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		WheelTableSyncPayload payload;
		try {
			payload = WheelTableSyncPayload.of(WheelTable.current());
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[wheel] 扇区表快照构建失败，跳过同步：{}", t.toString());
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			try {
				ServerPlayNetworking.send(player, payload);
			} catch (Throwable t) {
				MyMod.LOGGER.warn("[wheel] 扇区表同步失败：{}", t.toString());
			}
		}
	}
}
