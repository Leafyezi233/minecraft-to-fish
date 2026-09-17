package com.leafyezi233.minecrafttofish;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.leafyezi233.minecrafttofish.entity.AggressiveFishEntity;
import com.leafyezi233.minecrafttofish.entity.BrutalFishEntity;
import com.leafyezi233.minecrafttofish.entity.ModEntities;
import com.leafyezi233.minecrafttofish.entity.TimidFishEntity;
import com.leafyezi233.minecrafttofish.item.ModItems;

/**
 * 模组主入口（通用端：客户端和服务端都会执行）。
 * 物品、生物等注册逻辑之后加在这里，或拆成单独的注册类。
 */
public class MyMod implements ModInitializer {
	/** 模组 ID，必须和 fabric.mod.json 里的 id 一致 */
	public static final String MOD_ID = "minecraft_to_fish";

	/** 日志对象，用来在控制台/日志里打印信息 */
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Hello from {}! The mod is loading.", MOD_ID);

		// 注册自定义生物/物品
		ModEntities.register();
		ModItems.register();

		// 注册生物的默认属性（血量、攻击力）
		FabricDefaultAttributeRegistry.register(
				ModEntities.AGGRESSIVE_FISH,
				AggressiveFishEntity.createAggressiveFishAttributes());

		FabricDefaultAttributeRegistry.register(
				ModEntities.BRUTAL_FISH,
				BrutalFishEntity.createBrutalFishAttributes());

		FabricDefaultAttributeRegistry.register(
				ModEntities.TIMID_FISH,
				TimidFishEntity.createTimidFishAttributes());
	}
}