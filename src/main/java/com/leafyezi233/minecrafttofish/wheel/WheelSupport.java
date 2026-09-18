package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.item.ModItems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 转盘的两端共用小工具。
 *
 * <p>「是否模组鱼」的判断刻意用 {@code ==} 引用比较，与 {@code EconomyHud.isModFish}
 * 保持一致 —— 模组物品是单例，引用比较既准确又便宜。
 */
public final class WheelSupport {

	private WheelSupport() {
	}

	/** 是否为本模组的鱼（攻击性 / 凶猛 / 微缩） */
	public static boolean isModFish(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item == ModItems.AGGRESSIVE_FISH
				|| item == ModItems.BRUTAL_FISH
				|| item == ModItems.TIMID_FISH;
	}
}
