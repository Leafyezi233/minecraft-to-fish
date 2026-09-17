package com.leafyezi233.minecrafttofish.entity;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import com.leafyezi233.minecrafttofish.MyMod;

/**
 * 本模组所有实体类型的注册入口。
 * 调用 ModEntities.register() 即可注册全部自定义实体。
 */
public final class ModEntities {
	// 攻击性鱼：一条有攻击性、会弹跳、离水不受伤、钓鱼时生成的鱼
	public static final EntityType<AggressiveFishEntity> AGGRESSIVE_FISH =
			Registry.register(
					Registries.ENTITY_TYPE,
					new Identifier(MyMod.MOD_ID, "aggressive_fish"),
					FabricEntityTypeBuilder.create(SpawnGroup.WATER_CREATURE, AggressiveFishEntity::new)
							.dimensions(EntityDimensions.fixed(0.5f, 0.5f)) // about cod size
							.build()
			);

	// 凶猛的鱼：和攻击性鱼逻辑几乎一样，但更大、血更多、攻击更高（外观用鲑鱼）
	public static final EntityType<BrutalFishEntity> BRUTAL_FISH =
			Registry.register(
					Registries.ENTITY_TYPE,
					new Identifier(MyMod.MOD_ID, "brutal_fish"),
					FabricEntityTypeBuilder.create(SpawnGroup.WATER_CREATURE, BrutalFishEntity::new)
							.dimensions(EntityDimensions.fixed(0.8f, 0.8f)) // bigger than cod
							.build()
			);

	// 胆小的鱼：非攻击性，钓上后落在玩家脚边，拼命逃回水里，入水即消失（外观用热带鱼）
	public static final EntityType<TimidFishEntity> TIMID_FISH =
			Registry.register(
					Registries.ENTITY_TYPE,
					new Identifier(MyMod.MOD_ID, "timid_fish"),
					FabricEntityTypeBuilder.create(SpawnGroup.WATER_CREATURE, TimidFishEntity::new)
							.dimensions(EntityDimensions.fixed(0.25f, 0.2f)) // tropical fish size (缩小一半)
							.build()
			);

	public static void register() {
		// 实体类型已在上方静态初始化时注册到 Registry。
		// 这里仅作占位，方便以后在此统一挂载实体相关的注册。
		// （渲染器在客户端入口注册，见 MyModClient）
	}
}