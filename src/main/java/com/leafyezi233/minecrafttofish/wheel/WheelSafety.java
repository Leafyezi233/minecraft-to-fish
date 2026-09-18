package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 物品安全网：保证「玩家开着转盘界面时断线 / 服务器停止」这两条路径下鱼不会消失。
 *
 * <h2>为什么原版不管这件事</h2>
 * 读了字节码确认：{@code ServerPlayerEntity.onDisconnect()} 只做三件事
 * （置 {@code disconnected}、卸乘客、唤醒），<b>完全没有</b>
 * {@code closeHandledScreen()} / {@code onHandledScreenClosed()}；
 * {@code PlayerManager.remove()} 也没有。
 * 全服只有三处会调 {@code onHandledScreenClosed}：{@code PlayerEntity.tick()}
 * （{@code canUse} 失败时）、{@code ServerPlayNetworkHandler.onCloseHandledScreen}、以及重生路径。
 *
 * <p><b>结论：玩家开着界面直接断线，槽里的鱼会凭空消失。</b> 所以必须在这里补钩子。
 *
 * <h2>时序说明</h2>
 * Fabric 把 {@code DISCONNECT} 注入在 {@code onDisconnected} 的 <b>HEAD</b>，
 * 此时 {@code currentScreenHandler} 还是我们的实例，且玩家 {@code isAlive()} 为真、
 * {@code isDisconnected()} 为假，交还路径完全正常。
 */
public final class WheelSafety {

	private WheelSafety() {
	}

	/** 注册安全网（幂等由调用方保证，见 ModBlocks.register 之后的 MyMod 装配） */
	public static void register() {
		// 延迟结算驱动：让「鱼消失 / 价值变化」与客户端转盘动画结束对齐
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (player.currentScreenHandler instanceof FishWheelScreenHandler wheel && wheel.isSettling()) {
					try {
						wheel.tickSettlement();
					} catch (Throwable t) {
						// 结算失败不能让整个 tick 崩掉
						MyMod.LOGGER.error("[wheel] 转盘结算失败：{}", t.toString());
					}
				}
			}
		});

		// 坑 1：断线时原版不调 onClosed，鱼会丢
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			try {
				ServerPlayerEntity player = handler.getPlayer();
				if (player != null && player.currentScreenHandler instanceof FishWheelScreenHandler wheel) {
					wheel.returnFish(player);
				}
			} catch (Throwable t) {
				// 兜底逻辑绝不能因为异常而打断断线流程
				MyMod.LOGGER.error("[wheel] 断线交还物品失败：{}", t.toString());
			}
		});

		// 坑 1 的冗余保险：停服时对所有在线玩家兜一遍
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			try {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					if (player.currentScreenHandler instanceof FishWheelScreenHandler wheel) {
						wheel.returnFish(player);
					}
				}
			} catch (Throwable t) {
				MyMod.LOGGER.error("[wheel] 停服交还物品失败：{}", t.toString());
			}
		});

		MyMod.LOGGER.info("[wheel] 物品安全网已注册（断线 / 停服）");
	}
}
