package com.leafyezi233.minecrafttofish.wheel.client;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.wheel.ModBlocks;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;

/**
 * 渔轮转盘的客户端界面注册（<b>仅客户端</b>）。
 *
 * <p>服务端用 {@code ScreenHandlerRegistry.registerExtended} 注册界面类型，
 * 客户端必须在这里用 {@link ScreenRegistry#register} 把它绑定到具体的
 * {@code Screen} 实现 —— 否则原版会用一个兜底渲染，看不到转盘。
 */
@Environment(EnvType.CLIENT)
public final class WheelClient {

	private static boolean initialized = false;

	private WheelClient() {
	}

	/** 注册客户端界面（幂等） */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		ScreenRegistry.register(ModBlocks.FISH_WHEEL_SCREEN_HANDLER, FishWheelScreen::new);

		MyMod.LOGGER.info("[wheel] 客户端界面已注册：{}", com.leafyezi233.minecrafttofish.wheel.WheelConstants.PATH);
	}
}
