package com.leafyezi233.minecrafttofish.galton;

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
 * 高尔顿板的方块与方块实体注册。
 *
 * <h2>与转盘注册的区别：多了「加高」这一条</h2>
 * 转盘的盘面完全在单方块内，所以 {@code nonOpaque()} 就够了。
 * 高尔顿板要竖着排 6 排钉子 + 7 个槽位，纵向放不下，
 * 盘面渲染会<b>向上超出 1 格</b>（约到 y≈1.95）。这带来两个连带要求：
 * <ol>
 *   <li>放置时必须校验<b>上方是空气</b>，否则盘面会插进上方的方块里
 *       （见 {@link GaltonBlock#canPlaceAt}）</li>
 *   <li>渲染器必须声明 {@code rendersOutsideBoundingBox = true}，
 *       否则超出方块包围盒的部分会被原版裁掉 —— 表现为盘面上半截凭空消失</li>
 * </ol>
 * 碰撞箱仍然只有下面那块 1/8 格底板，<b>不是</b>两格高的墙：
 * 盘面是「看得见但不挡路」的表现层，玩家应该能站在板前面。
 */
public final class GaltonBlocks {

	/**
	 * 方块本体。strength 与手感对齐转盘（木板质感、空手可挖）。
	 *
	 * <p><b>为什么必须加 {@code nonOpaque()}</b>：本方块的模型只是一块薄底板
	 * （盘面由方块实体渲染器画），不是实心立方体。
	 * 默认设置下方块是「不透明」的，原版会据此认为它把六个面都挡严实了，
	 * 于是把<b>相邻方块朝着它的那一面整面剔除</b>——结果就是贴着墙放时，
	 * 墙面上会出现一个方块大小的洞。
	 */
	public static final Block FISH_GALTON = new GaltonBlock(
			AbstractBlock.Settings.create()
					.strength(2.0f)
					.sounds(BlockSoundGroup.WOOD)
					.nonOpaque());

	/** 方块对应的物品（进背包/创造栏用的那个） */
	public static final Item FISH_GALTON_ITEM = new GaltonBlockItem(FISH_GALTON, new Item.Settings());

	/** 方块实体类型；在 {@link #register()} 里赋值 */
	public static BlockEntityType<FishGaltonBlockEntity> FISH_GALTON_BLOCK_ENTITY;

	private GaltonBlocks() {
	}

	/** 注册方块、物品、方块实体与创造栏条目（幂等由 Fabric 注册表保证） */
	public static void register() {
		Registry.register(Registries.BLOCK, GaltonConstants.BLOCK_ID, FISH_GALTON);
		Registry.register(Registries.ITEM, GaltonConstants.BLOCK_ID, FISH_GALTON_ITEM);

		// 方块实体类型：用 Fabric 的 builder 省掉 DataFixer 类型参数（原版 Builder.build 需要传 Type）
		FISH_GALTON_BLOCK_ENTITY = Registry.register(
				Registries.BLOCK_ENTITY_TYPE,
				GaltonConstants.BLOCK_ID,
				FabricBlockEntityTypeBuilder.create(FishGaltonBlockEntity::new, FISH_GALTON).build());

		// 放进创造模式「功能方块」标签页
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
				entries.add(FISH_GALTON_ITEM));

		MyMod.LOGGER.info("[galton] 高尔顿板已注册：方块={}，方块实体={}",
				GaltonConstants.BLOCK_ID, GaltonConstants.BLOCK_ID);
	}
}
