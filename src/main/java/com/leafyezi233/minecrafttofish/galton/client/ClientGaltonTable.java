package com.leafyezi233.minecrafttofish.galton.client;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.galton.GaltonBoard;
import com.leafyezi233.minecrafttofish.galton.net.GaltonTableSyncPayload;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端接收服务端下发的高尔顿板槽位表（<b>仅客户端</b>）。
 *
 * <p>收到后交给 {@link GaltonBoard#applyRemote}，之后 {@link GaltonBoard#current()}
 * 一律返回服务端那张表 —— 渲染与落点计算因此自动和服务端一致，
 * 不需要在渲染代码里到处判「该用哪张表」。
 *
 * <h2>与本地配置的关系</h2>
 * 本地配置仍然会被读取（单机时它<b>就是</b>权威，因为单机的服务端就是自己）。
 * 联机时以服务端下发的表为准；断线后清空，避免把上一个服务器的盘面留在界面上。
 *
 * <h2>⚠️ 阶段 5 渲染前必须确认这里生效</h2>
 * 渲染器画多少排钉子、槽位多宽、每格什么颜色，全部来自 {@link GaltonBoard#current()}。
 * 若本接收器没注册成功（或表没收到），渲染器会拿<b>本地配置</b>去画：
 * 单机下看不出区别，联机时服主一改配置就会出现
 * 「球按服务端路径弹，但钉子和槽位画的是客户端自己那套」——
 * 表现为球<b>从钉子旁边穿过去</b>。这类错位在单机测试里<b>永远复现不出来</b>，
 * 所以联机前务必确认日志里有「已同步服务端槽位表」。
 */
@Environment(EnvType.CLIENT)
public final class ClientGaltonTable {

	private static boolean registered = false;

	private ClientGaltonTable() {
	}

	/** 注册网络接收器与断线清理（幂等） */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;

		ClientPlayNetworking.registerGlobalReceiver(GaltonTableSyncPayload.TYPE,
				(payload, player, responseSender) -> {
					// 网络回调在渲染线程上执行（见 Fabric 的 PlayPacketHandler 约定），直接写入即可
					apply(payload);
				});

		// 断线后清空，避免残留上一个服务器的槽位表
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());

		MyMod.LOGGER.info("[galton] 客户端槽位表接收器已注册：{}", GaltonTableSyncPayload.CHANNEL);
	}

	/** 采用服务端发来的槽位表 */
	public static void apply(GaltonTableSyncPayload payload) {
		if (payload == null) {
			return;
		}
		GaltonBoard.applyRemote(payload.slots(), payload.pegRightChance());

		// 这条日志是联机排查的<b>唯一线索</b>（见类注释），所以不受 debugLogging 限制。
		// 转盘那边只在 debugLogging 下打，是因为它的表漏收只影响盘面美观；
		// 高尔顿板漏收会让球穿过钉子，值得在正常日志里留一条。
		GaltonBoard board = GaltonBoard.current();
		MyMod.LOGGER.info("[galton] 已同步服务端槽位表：{} 个槽位（{} 排钉子，p={}，期望值 {}，{}）",
				board.size(), board.rows(),
				String.format("%.4f", board.pegRightChance()),
				String.format("%.4f", board.expectedMultiplier()),
				GaltonBoard.usingRemote() ? "使用服务端的表" : "服务端的表非法，已退回本地配置");
	}

	/** 丢弃服务端下发的表，之后重新以本地配置为准 */
	public static void clear() {
		GaltonBoard.clearRemote();
	}
}
