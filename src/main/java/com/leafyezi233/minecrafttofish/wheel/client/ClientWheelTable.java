package com.leafyezi233.minecrafttofish.wheel.client;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;
import com.leafyezi233.minecrafttofish.wheel.net.WheelTableSyncPayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端接收服务端下发的转盘扇区表（<b>仅客户端</b>）。
 *
 * <p>收到后交给 {@link WheelTable#applyRemote}，之后
 * {@link WheelTable#current()} 一律返回服务端那张表 ——
 * 渲染与落点计算因此自动和服务端一致，不需要在渲染代码里到处判「该用哪张表」。
 *
 * <h2>与本地配置的关系</h2>
 * 本地配置仍然会被读取（单机时它<b>就是</b>权威，因为单机的服务端就是自己）。
 * 联机时它以服务端下发的表为准；断线后清空，避免把上一个服务器的盘面留在界面上。
 */
@Environment(EnvType.CLIENT)
public final class ClientWheelTable {

	private static boolean registered = false;

	private ClientWheelTable() {
	}

	/** 注册网络接收器与断线清理（幂等） */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		ClientPlayNetworking.registerGlobalReceiver(WheelTableSyncPayload.TYPE, (payload, player, responseSender) -> {
			// 网络回调在渲染线程上执行（见 Fabric 的 PlayPacketHandler 约定），直接写入即可
			apply(payload);
		});

		// 断线后清空，避免残留上一个服务器的扇区表
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());

		MyMod.LOGGER.info("[wheel] 客户端扇区表接收器已注册：{}", WheelTableSyncPayload.CHANNEL);
	}

	/** 采用服务端发来的扇区表 */
	public static void apply(WheelTableSyncPayload payload) {
		if (payload == null) {
			return;
		}
		WheelTable.applyRemote(payload.sectors());

		if (EconomyConfig.get().debugLogging) {
			MyMod.LOGGER.info("[wheel] 已同步服务端扇区表：{} 个扇区（期望值 {}）",
					WheelTable.current().size(),
					String.format("%.4f", WheelTable.current().expectedMultiplier()));
		}
	}

	/** 丢弃服务端下发的表，之后重新以本地配置为准 */
	public static void clear() {
		WheelTable.clearRemote();
	}
}
