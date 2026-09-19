package com.leafyezi233.minecrafttofish.game;

import com.leafyezi233.minecrafttofish.item.ModItems;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * 两个小游戏（渔轮转盘 / 高尔顿板）共用的纯工具。
 *
 * <h2>为什么「毫秒 → 刻」必须只有一份实现</h2>
 * 服务端按「毫秒配置」算出结算时刻，客户端按「毫秒配置」算出动画进度。
 * 两边若各写一份取整规则，配一个非 50 倍数的时长（比如 1234 ms）就会出现
 * 「服务端已经结算、客户端动画还差一点点没演完」的错位 ——
 * 表现为动画停在中间，玩家看到的结果和实际结果对不上。
 * <p>这个坑原先只在转盘里踩过（当时叫 {@code WheelTiming}）；
 * 高尔顿板同样要按毫秒配时长、同样要两端一致，所以取整规则上提到这里，
 * <b>不允许任何一个游戏自己再写一份</b>。
 */
public final class GameSupport {

	/** 一毫秒对应的游戏刻数（50 ms = 1 tick） */
	public static final long MS_PER_TICK = 50L;

	private GameSupport() {
	}

	/**
	 * 是否为本模组的鱼（攻击性 / 凶猛 / 微缩）。
	 *
	 * <p>刻意用 {@code ==} 引用比较，与 {@code EconomyHud.isModFish} 保持一致 ——
	 * 模组物品是单例，引用比较既准确又便宜。
	 */
	public static boolean isModFish(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		Item item = stack.getItem();
		return item == ModItems.AGGRESSIVE_FISH
				|| item == ModItems.BRUTAL_FISH
				|| item == ModItems.TIMID_FISH;
	}

	/**
	 * 毫秒换算成游戏刻，<b>向上取整</b>。
	 * <p>向上取整而不是四舍五入：配 1 ms 时若取整成 0 刻，
	 * 动画会在同一刻内开始并结束，玩家完全看不到过程。
	 *
	 * @param millis 毫秒数；非正数返回 0
	 */
	public static long ticksOf(int millis) {
		if (millis <= 0) {
			return 0L;
		}
		return (millis + MS_PER_TICK - 1) / MS_PER_TICK;
	}
}
