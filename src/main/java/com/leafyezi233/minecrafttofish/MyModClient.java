package com.leafyezi233.minecrafttofish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leafyezi233.minecrafttofish.economy.client.EconomyHud;
import com.leafyezi233.minecrafttofish.entity.ModEntities;
import com.leafyezi233.minecrafttofish.entity.client.AggressiveFishEntityRenderer;
import com.leafyezi233.minecrafttofish.entity.client.BrutalFishEntityRenderer;
import com.leafyezi233.minecrafttofish.entity.client.TimidFishEntityRenderer;
import com.leafyezi233.minecrafttofish.wheel.client.WheelClient;

/**
 * 客户端专用入口（只会在单机或服务器客户端上执行）。
 * 渲染、GUI、按键绑定、客户端物品栏等放这里。
 */
public class MyModClient implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(MyMod.MOD_ID + "-client");

	@Override
	public void onInitializeClient() {
		LOGGER.info("Client side of {} is ready.", MyMod.MOD_ID);

		// 注册实体的渲染器（客户端）
		EntityRendererRegistry.register(ModEntities.AGGRESSIVE_FISH, AggressiveFishEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.BRUTAL_FISH, BrutalFishEntityRenderer::new);
		EntityRendererRegistry.register(ModEntities.TIMID_FISH, TimidFishEntityRenderer::new);

		// 手持模组鱼时的价值悬浮窗（含服务端价值表同步的接收端）
		EconomyHud.init();

		// 渔轮转盘的客户端界面（面板 + 转盘绘制 + 旋转动画）
		WheelClient.init();
	}
}