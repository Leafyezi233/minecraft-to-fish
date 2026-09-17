package com.leafyezi233.minecrafttofish.economy.value;

import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.minecraft.item.ItemStack;

/**
 * 兜底估值：给"没在数据包里登记过"的物品一个保底价格。
 * <p>默认<b>关闭</b>，避免经济被稀释 —— 只有登记过的物品才有价值，经济边界清晰。
 * 整合包想要"万物有价"时，把配置里的 {@code fallbackValueEnabled} 打开即可。
 */
public final class ItemValueFallback {

	private ItemValueFallback() {
	}

	/**
	 * 计算兜底价值。
	 *
	 * @param stack 待估值的物品
	 * @param cfg   当前配置
	 * @return 兜底价值；未开启或计算结果为 0 时返回 0
	 */
	public static long valueFor(ItemStack stack, EconomyConfig cfg) {
		if (!cfg.fallbackValueEnabled || stack.isEmpty()) {
			return 0L;
		}
		// 配置里写了具体数值就直接用
		if (cfg.fallbackValue > 0L) {
			return cfg.fallbackValue;
		}
		// 否则按堆叠数推导：不可堆叠的东西（工具/装备）比可堆叠的杂物值钱
		return stack.getMaxCount() == 1 ? 16L : 4L;
	}
}
