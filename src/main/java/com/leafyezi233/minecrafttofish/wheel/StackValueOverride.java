package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

/**
 * <b>物品栈级</b>价值覆盖：把「这一个物品实例」的价值直接写在它自己的 NBT 上。
 *
 * <h2>为什么需要它</h2>
 * 转盘中奖是「这条鱼的价值变成 ×N」。但现有的价值体系是按 <b>物品类型</b> 定价的：
 * 数据包价值表按物品 id、运行时覆盖按 {@code Item}、缓存也按 {@code Item} 建键。
 * 同一条鱼在不同玩家手里可能带着不同的加成，按类型存根本表达不了。
 * 所以加成必须落在物品实例上 —— 也就是 NBT。
 *
 * <h2>NBT 结构</h2>
 * <pre>
 * { ...原有 NBT...
 *   "minecraft_to_fish": { "value": 150L }
 * }
 * </pre>
 * 用模组 id 做子标签名，避免和原版或其它模组的键冲突。
 *
 * <h2>优先级</h2>
 * 这是<b>最高</b>优先级，高于 {@code /fishvalue set} 的运行时覆盖 ——
 * 运行时覆盖描述的是「这个物品类型值多少」，而本覆盖描述的是
 * 「这一个物品值多少」，后者更具体，理应更优先。
 *
 * <h2>清除时的注意事项</h2>
 * 清空覆盖后如果子标签空了，会顺手把它整个删掉，让物品栈恢复成「完全没有 NBT」的干净状态。
 * 否则一个空的 NBT 复合标签会让物品无法与同类无 NBT 物品堆叠，玩家会觉得很奇怪。
 */
public final class StackValueOverride {

	/** 子标签名，用模组 id 避免冲突 */
	private static final String SUB_TAG = MyMod.MOD_ID;

	/** 子标签里存价值的键名 */
	private static final String VALUE_KEY = "value";

	private StackValueOverride() {
	}

	/**
	 * 读取该物品栈的覆盖价值。
	 *
	 * @return 覆盖值；<b>没有覆盖时返回 -1</b>（注意不是 0 —— 0 是合法的「无价值」）
	 */
	public static long read(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
			return -1L;
		}
		NbtCompound sub = stack.getSubNbt(SUB_TAG);
		if (sub == null || !sub.contains(VALUE_KEY, NbtElement.LONG_TYPE)) {
			return -1L;
		}
		return Math.max(0L, sub.getLong(VALUE_KEY));
	}

	/** 是否带有覆盖值 */
	public static boolean has(ItemStack stack) {
		return read(stack) >= 0L;
	}

	/**
	 * 写入覆盖值。
	 * <p>负数会被夹到 0；0 表示「这条鱼现在一文不值」，与「没有覆盖」不同，
	 * 所以仍然会写入 NBT 而不是清除。
	 */
	public static void write(ItemStack stack, long value) {
		if (stack == null || stack.isEmpty()) {
			return;
		}
		stack.getOrCreateSubNbt(SUB_TAG).putLong(VALUE_KEY, Math.max(0L, value));
	}

	/**
	 * 清除覆盖值，让该物品栈回到「按类型定价」的普通状态。
	 * <p>子标签清空后会整个移除，避免留下空 NBT 影响堆叠。
	 */
	public static void clear(ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasNbt()) {
			return;
		}
		NbtCompound sub = stack.getSubNbt(SUB_TAG);
		if (sub == null) {
			return;
		}
		sub.remove(VALUE_KEY);
		if (sub.isEmpty()) {
			stack.removeSubNbt(SUB_TAG);
		}
	}

	/** 语言键：用于在 tooltip / HUD 上说明「这个价是加成后的」 */
	public static String translationKey() {
		return "wheel.minecraft_to_fish.value_bonus";
	}
}
