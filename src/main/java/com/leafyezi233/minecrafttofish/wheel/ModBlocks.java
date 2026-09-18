package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.sound.BlockSoundGroup;

/**
 * 渔轮转盘的方块与界面类型注册。
 *
 * <h2>为什么界面类型必须走 Fabric 的注册器</h2>
 * 原版 {@code ScreenHandlerType} <b>没有公开构造函数</b>，其 {@code Factory} 也是包级私有，
 * 所以只能通过 Fabric 的 {@link ScreenHandlerRegistry#registerExtended} 注册。
 * 用 {@code registerExtended} 而不是 {@code registerSimple}，是因为需要把方块坐标
 * 随开屏数据下发给客户端（见 {@link FishWheelFactory}）。
 */
public final class ModBlocks {

	/** 方块本体。strength(2.0f) 对齐木板手感；不加 requiresTool()，空手也能挖 */
	public static final Block FISH_WHEEL = new FishWheelBlock(
			AbstractBlock.Settings.create()
					.strength(2.0f)
					.sounds(BlockSoundGroup.WOOD));

	/** 方块对应的物品（进背包/创造栏用的那个） */
	public static final Item FISH_WHEEL_ITEM = new BlockItem(FISH_WHEEL, new Item.Settings());

	/** 界面类型；在 {@link #register()} 里赋值 */
	public static ScreenHandlerType<FishWheelScreenHandler> FISH_WHEEL_SCREEN_HANDLER;

	private ModBlocks() {
	}

	/** 注册方块、物品、界面类型与创造栏条目（幂等由 Fabric 注册表保证） */
	public static void register() {
		Registry.register(Registries.BLOCK, WheelConstants.BLOCK_ID, FISH_WHEEL);
		Registry.register(Registries.ITEM, WheelConstants.BLOCK_ID, FISH_WHEEL_ITEM);

		FISH_WHEEL_SCREEN_HANDLER = ScreenHandlerRegistry.registerExtended(
				WheelConstants.SCREEN_HANDLER_ID,
				(syncId, playerInventory, buf) -> new FishWheelScreenHandler(syncId, playerInventory, buf));

		// 放进创造模式「功能方块」标签页
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries ->
				entries.add(FISH_WHEEL_ITEM));

		MyMod.LOGGER.info("[wheel] 渔轮转盘已注册：方块={}，界面={}",
				WheelConstants.BLOCK_ID, WheelConstants.SCREEN_HANDLER_ID);
	}
}
