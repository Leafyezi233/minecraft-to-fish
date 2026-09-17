package com.leafyezi233.minecrafttofish.item;

import com.leafyezi233.minecrafttofish.MyMod;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

/**
 * 模组物品注册：三条自定义鱼，作为对应生物的掉落物。
 * 贴图直接复用原版鳕鱼/鲑鱼/热带鱼（见 assets/minecraft_to_fish/models/item/*.json）。
 */
public class ModItems {

	/** 攻击性鱼（掉落物，外观沿用鳕鱼贴图） */
	public static final Item AGGRESSIVE_FISH = new Item(new Item.Settings());

	/** 凶猛的鱼（掉落物，外观沿用鲑鱼贴图） */
	public static final Item BRUTAL_FISH = new Item(new Item.Settings());

	/** 微缩鱼（原"胆小的鱼"，掉落物，外观沿用热带鱼贴图） */
	public static final Item TIMID_FISH = new Item(new Item.Settings());

	public static void register() {
		Registry.register(Registries.ITEM, new Identifier(MyMod.MOD_ID, "aggressive_fish"), AGGRESSIVE_FISH);
		Registry.register(Registries.ITEM, new Identifier(MyMod.MOD_ID, "brutal_fish"), BRUTAL_FISH);
		Registry.register(Registries.ITEM, new Identifier(MyMod.MOD_ID, "timid_fish"), TIMID_FISH);

		// 放进创造模式“食物与饮品”标签页，方便测试拿取
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FOOD_AND_DRINK).register(entries -> {
			entries.add(AGGRESSIVE_FISH);
			entries.add(BRUTAL_FISH);
			entries.add(TIMID_FISH);
		});
	}
}
