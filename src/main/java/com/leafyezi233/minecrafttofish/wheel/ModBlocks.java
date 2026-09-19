package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

/**
 * 渔轮转盘的方块与方块实体注册。
 *
 * <h2>为什么不再有界面类型</h2>
 * 改造前这里还要注册一个 {@code ScreenHandlerType}（且必须走 Fabric 的注册器，
 * 因为原版 {@code ScreenHandlerType} 没有公开构造函数）。
 * 转盘改成无界面交互后，界面连同它的类型注册一并删除 ——
 * 状态已经全在 {@link FishWheelBlockEntity} 里，不再需要「打开一个界面」这条路径。
 */
public final class ModBlocks {

	/**
	 * 方块本体。strength(2.0f) 对齐木板手感；不加 requiresTool()，空手也能挖。
	 *
	 * <p><b>为什么必须加 {@code nonOpaque()}</b>：本方块的模型只是一块 1/8 格厚的
	 * 底板（盘面由方块实体渲染器画），不是实心立方体。
	 * 默认设置下方块是「不透明」的，原版会据此认为它把六个面都挡严实了，
	 * 于是把<b>相邻方块朝着它的那一面整面剔除</b>——结果就是贴着墙放时，
	 * 墙面上会出现一个方块大小的洞。声明非不透明后原版才会照常绘制相邻面。
	 *
	 * <p>碰撞箱同步改成同一块底板：否则会出现「看着是薄板，撞上去是整格」的错位感，
	 * 而且盘面下方那半格会变成一堵看不见的墙。
	 */
	public static final Block FISH_WHEEL = new FishWheelBlock(
			AbstractBlock.Settings.create()
					.strength(2.0f)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque());

	/** 方块对应的物品（进背包/创造栏用的那个） */
	public static final Item FISH_WHEEL_ITEM = new BlockItem(FISH_WHEEL, new Item.Settings());

	/** 方块实体类型；在 {@link #register()} 里赋值 */
	public static BlockEntityType<FishWheelBlockEntity> FISH_WHEEL_BLOCK_ENTITY;

	private ModBlocks() {
	}

	/** 注册方块、物品、方块实体与创造栏条目（幂等由 Fabric 注册表保证） */
	public static void register() {
		Registry.register(Registries.BLOCK, WheelConstants.BLOCK_ID, FISH_WHEEL);
		Registry.register(Registries.ITEM, WheelConstants.BLOCK_ID, FISH_WHEEL_ITEM);

		// 方块实体类型：用 Fabric 的 builder 省掉 DataFixer 类型参数（原版 Builder.build 需要传 Type）
		FISH_WHEEL_BLOCK_ENTITY = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				WheelConstants.BLOCK_ID,
				FabricBlockEntityTypeBuilder.create(FishWheelBlockEntity::new, FISH_WHEEL).build());

		// 放进创造模式「功能方块」标签页
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
				entries.add(FISH_WHEEL_ITEM));

		MyMod.LOGGER.info("[wheel] 渔轮转盘已注册：方块={}，方块实体={}",
				WheelConstants.BLOCK_ID, WheelConstants.BLOCK_ID);
	}
}
