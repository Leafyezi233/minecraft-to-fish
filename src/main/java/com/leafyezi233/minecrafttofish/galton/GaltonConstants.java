package com.leafyezi233.minecrafttofish.galton;

import com.leafyezi233.minecrafttofish.MyMod;

import net.minecraft.util.Identifier;

/**
 * 高尔顿板的共享常量。
 *
 * <h2>为什么这里没有「扇区角度」这类东西</h2>
 * 转盘的 {@code WheelConstants} 里全是角度相关的约定（0° 朝上、顺时针增长、
 * 扇区张角按权重分配）—— 因为转盘的玩法就是「一个圆盘转到某个角度」。
 * 高尔顿板没有角度：球的落点是<b>数出来的</b>（路径里 1 的个数），
 * 不需要任何角度约定，也就不需要在这里写死任何角度常量。
 * <p>几何相关的量（板宽、槽宽、层比例）全部由 {@link GaltonBoard} 从
 * <b>槽位数量</b>推导，不在这里重复定义 —— 写两份必然会有一天对不上。
 */
public final class GaltonConstants {

	/** 方块与方块实体共用的路径名 */
	public static final String PATH = "fish_galton";

	/** 方块 id（同时也是方块实体类型 id） */
	public static final Identifier BLOCK_ID = new Identifier(MyMod.MOD_ID, PATH);

	/**
	 * 无效的落点槽位下标，表示「尚无结果」。
	 *
	 * <p>与转盘的 {@code NO_SECTOR} 同义。注意<b>落点从来不单独存</b>：
	 * 它是由 {@code resultPath} 数出来的（{@link GaltonPath#slotOf}），
	 * 所以不存在「存的下标和路径对不上」这种可能。
	 * 这个常量只用于「压根还没有结果」这一个语义。
	 */
	public static final int NO_SLOT = -1;

	/**
	 * 无效的路径，表示「尚无结果」。
	 *
	 * <p>用 -1 而不是 0：{@code 0} 是一条<b>合法</b>路径（每次弹跳都往左，
	 * 落进最左槽位）。若拿 0 当「无结果」，最左槽位就永远显示不出结果 ——
	 * 这类「拿合法值当哨兵」的错误极难发现，因为只有 1/64 的概率会撞上。
	 */
	public static final int NO_PATH = -1;

	private GaltonConstants() {
	}
}
