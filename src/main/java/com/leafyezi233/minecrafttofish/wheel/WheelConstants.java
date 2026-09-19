package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.minecraft.util.Identifier;

/**
 * 渔轮转盘的共享常量。
 *
 * <h2>为什么现在只剩这么点</h2>
 * 改造前这里还放着界面尺寸、按钮 id、6 个属性槽 —— 那些都是「私人老虎机」时代的产物：
 * 状态存在每个玩家各自的 {@code ScreenHandler} 里，才需要把尺寸和同步槽位写死在常量里。
 * 状态搬进方块实体后，界面被整体删除，这些常量<b>全部失去意义</b>，
 * 留着只会让人以为界面还在。
 */
public final class WheelConstants {

	/** 方块与方块实体共用的路径名 */
	public static final String PATH = "fish_wheel";

	/** 方块 id（同时也是方块实体类型 id） */
	public static final Identifier BLOCK_ID = new Identifier(MyMod.MOD_ID, PATH);

	/**
	 * 无效的扇区下标，表示「尚无结果」。
	 *
	 * <p><b>结果用下标而不是倍率传递</b>：配置允许两个扇区配成相同倍率，
	 * 若客户端拿倍率去反查（{@code indexOfMultiplier}）只会命中第一个，
	 * 多人同看一个转盘时就会出现「结果一样、指针停在不同扇区」的错位。
	 * 下标是服务端在掷骰当场定下的，全体客户端据此落点必然一致。
	 */
	public static final int NO_SECTOR = -1;

	/**
	 * 指针颜色（亮红，ARGB）。
	 *
	 * <p><b>放在这里是为了能被自测覆盖</b>：指针曾经用金色 {@code 0xFFD4AF37}，
	 * 而内置默认扇区表里「×5」那一格的颜色<b>正好也是 {@code 0xFFD4AF37}</b>
	 * （同一个调色板常量）。两者完全相同 ⇒ 每次 ×5 转到指针下面，指针就整块隐形，
	 * 玩家会以为指针消失了。
	 *
	 * <p>这类「常量撞色」是纯数值问题，不该只靠人眼在游戏里发现，
	 * 所以指针颜色必须放在共享类里（渲染器是客户端专属类，服务端自测碰不到）。
	 * 自测会断言它与默认表的每个扇区颜色都不同。
	 */
	public static final int POINTER_COLOR = 0xFFE53935;

	/**
	 * 指针下方那一格的提亮幅度（0 = 不提亮，1 = 全白）。
	 *
	 * <p>取 {@code 0.30}：足够让玩家一眼看出「指针指着这格」，
	 * 又不至于把扇区本身的颜色冲掉 —— 服主自定义的颜色仍要认得出来。
	 */
	public static final float HIGHLIGHT_STRENGTH = 0.30f;

	/**
	 * 把颜色朝白色方向提亮。
	 *
	 * <h2>为什么用「向白插值」而不是乘一个系数</h2>
	 * 乘法提亮对深色几乎无效（灰 {@code 0x6B6B6B} 乘 1.3 仍是灰），
	 * 而默认表里恰好就有一格是灰色。向白插值对深色、浅色都成立，
	 * 且不会像加法那样把颜色推过 255 再截断（截断会让色相偏移）。
	 *
	 * <p>放在共享类里是为了能被服务端自测覆盖：这是纯数值函数，
	 * 「提亮后必须确实变了、且必须仍然可辨识」都能断言，
	 * 不该只靠人眼在游戏里看。
	 *
	 * @param color  ARGB 颜色
	 * @param amount 提亮幅度，会被夹到 {@code [0, 1]}
	 * @return 提亮后的 ARGB 颜色（alpha 保持不变）
	 */
	public static int highlight(int color, float amount) {
		float t = Math.max(0.0f, Math.min(1.0f, amount));
		int a = color >>> 24 & 0xFF;
		int r = color >>> 16 & 0xFF;
		int g = color >>> 8 & 0xFF;
		int b = color & 0xFF;

		r = Math.round(r + (255 - r) * t);
		g = Math.round(g + (255 - g) * t);
		b = Math.round(b + (255 - b) * t);

		return a << 24 | r << 16 | g << 8 | b;
	}

	private WheelConstants() {
	}
}
