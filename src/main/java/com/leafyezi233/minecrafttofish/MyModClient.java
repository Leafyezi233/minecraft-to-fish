package com.leafyezi233.minecrafttofish;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

import com.leafyezi233.minecrafttofish.economy.client.EconomyHud;
import com.leafyezi233.minecrafttofish.entity.ModEntities;
import com.leafyezi233.minecrafttofish.entity.client.AggressiveFishEntityRenderer;
import com.leafyezi233.minecrafttofish.entity.client.BrutalFishEntityRenderer;
import com.leafyezi233.minecrafttofish.entity.client.TimidFishEntityRenderer;
import com.leafyezi233.minecrafttofish.galton.client.ClientGaltonTable;
import com.leafyezi233.minecrafttofish.galton.GaltonBlocks;
import com.leafyezi233.minecrafttofish.galton.client.FishGaltonBlockEntityRenderer;
import com.leafyezi233.minecrafttofish.wheel.ModBlocks;
import com.leafyezi233.minecrafttofish.wheel.client.ClientWheelTable;
import com.leafyezi233.minecrafttofish.wheel.client.FishWheelBlockEntityRenderer;

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

		// 转盘扇区表同步的接收端：客户端没有服务端的扇区表就画不出正确的盘面
		ClientWheelTable.register();

		// 高尔顿板槽位表同步的接收端：画多少排钉子、槽位多宽，全部来自服务端的表
		ClientGaltonTable.register();

		// 渔轮转盘在世界里的渲染（盘面 / 指针 / 盘上的鱼 / 结果文字）。
		// 用原版注册表：Fabric 的 BlockEntityRendererRegistry 已标记废弃。
		BlockEntityRendererFactories.register(ModBlocks.FISH_WHEEL_BLOCK_ENTITY,
				FishWheelBlockEntityRenderer::new);
		LOGGER.info("[wheel] 渔轮转盘渲染器已注册");

		// 高尔顿板在世界里的渲染（背板 / 钉子 / 槽位 / 球 / 鱼 / 结果文字）。
		// 钉子与槽位的数量由服务端下发的槽位表推导，所以必须等
		// ClientGaltonTable 收到表之后才画得对（单机下本地配置即权威）。
		BlockEntityRendererFactories.register(GaltonBlocks.FISH_GALTON_BLOCK_ENTITY,
				FishGaltonBlockEntityRenderer::new);
		LOGGER.info("[galton] 高尔顿板渲染器已注册");
	}
}