package com.leafyezi233.minecrafttofish.galton.net;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.galton.GaltonBoard;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.S2CPlayChannelEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 高尔顿板槽位表的服务端同步。
 *
 * <p>与 {@link com.leafyezi233.minecrafttofish.wheel.net.WheelTableSync} 同一套模式。
 *
 * <h2>为什么不在 JOIN 时发</h2>
 * Fabric 的 {@code ServerPlayConnectionEvents.JOIN} 触发在服务端下发
 * 「初始频道注册包」的<b>同时</b>，此刻服务端还不知道客户端能收哪些频道 ——
 * 此时发出去的包会被客户端当作未知频道丢弃。因此监听
 * {@link S2CPlayChannelEvents#REGISTER}：等客户端明确声明「我能收这个频道」之后再发。
 *
 * <h2>下发时机</h2>
 * <ol>
 *   <li><b>客户端声明频道时</b> —— 覆盖玩家加入</li>
 *   <li><b>数据包重载成功时</b> —— {@code /fishvalue reload} 后无需重连</li>
 * </ol>
 * 注意：<b>改配置文件不会触发重载</b>。槽位表在 {@code GaltonBoard.rebuild()} 时构建，
 * 而 {@code rebuild()} 由 {@code ModEconomy.init()} 调用 ——
 * 也就是说改完 {@code config/minecraft_to_fish.json} 需要重启服务器。
 * 这与转盘一致（转盘也是重启才重读配置），不是高尔顿板特有的限制。
 *
 * <p><b>容错约定</b>：同步失败只记 WARN，绝不打断玩家加入或数据包重载。
 * 客户端收不到表时会退回本地配置构建的表（见 {@code ClientGaltonTable}），
 * 单机下两者本就相同，联机下最坏情况是画错盘面 —— 这比让玩家掉线轻得多。
 */
public final class GaltonTableSync {

	private static boolean registered = false;

	private GaltonTableSync() {
	}

	/** 注册同步钩子（幂等；客户端上这些事件不会触发） */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		// 客户端声明能收本频道 → 立刻推一份，这是玩家加入时的可靠投递点
		S2CPlayChannelEvents.REGISTER.register((handler, sender, server, channels) -> {
			if (channels.contains(GaltonTableSyncPayload.CHANNEL)) {
				send(handler.getPlayer(), sender);
			}
		});

		// 数据包重载完成 → 广播给所有在线玩家（槽位表可能已变）
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> {
			if (success) {
				broadcast(server);
			}
		});
	}

	/** 给单个玩家发一份当前槽位表 */
	public static void send(ServerPlayerEntity player) {
		send(player, null);
	}

	private static void send(ServerPlayerEntity player, PacketSender sender) {
		if (player == null) {
			return;
		}
		try {
			GaltonTableSyncPayload payload = GaltonTableSyncPayload.of(GaltonBoard.current());
			if (sender != null) {
				sender.sendPacket(payload);
			} else {
				ServerPlayNetworking.send(player, payload);
			}
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[galton] 向 {} 同步槽位表失败：{}", player.getName().getString(), t.toString());
		}
	}

	/** 给所有在线玩家发一份当前槽位表 */
	public static void broadcast(MinecraftServer server) {
		if (server == null) {
			return;
		}
		GaltonTableSyncPayload payload;
		try {
			payload = GaltonTableSyncPayload.of(GaltonBoard.current());
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[galton] 槽位表快照构建失败，跳过同步：{}", t.toString());
			return;
		}
		for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
			try {
				ServerPlayNetworking.send(player, payload);
			} catch (Throwable t) {
				MyMod.LOGGER.warn("[galton] 槽位表同步失败：{}", t.toString());
			}
		}
	}
}
