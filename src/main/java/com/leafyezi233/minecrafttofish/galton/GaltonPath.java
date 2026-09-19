package com.leafyezi233.minecrafttofish.galton;

import net.minecraft.util.math.random.Random;

/**
 * 高尔顿板的<b>路径</b>——本小游戏的全部随机性与几何都由这一个 int 决定。
 *
 * <h2>为什么「路径」就是一切</h2>
 * 服务端在开落当刻掷出 {@code rows} 个<b>独立的</b>左右弹跳，
 * 第 {@code i} 位（{@code bit i}）为 1 表示第 {@code i} 排钉子往右弹。
 * 于是：
 * <ul>
 *   <li><b>球的轨迹</b> = 纯函数(路径, 进度) —— 见 {@link #xAt} / {@link #yFractionAt}</li>
 *   <li><b>落点槽位</b> = 路径里 1 的个数 —— 见 {@link #slotOf}</li>
 * </ul>
 *
 * <p><b>这两者是同一个变量的两种读法。</b> 这是本设计最重要的性质：
 * 转盘那边「指针停在哪」和「实际抽中哪个」是<b>两份</b>数据（扇区下标 vs 盘面角度），
 * 一旦换算写反就会出现「停下位置和奖项对不上」——我们为此专门加了
 * {@code WheelAngle.sectorAt} 反查并写了自测。
 * 高尔顿板<b>结构上不可能发生这种事</b>：没有第二份数据可以对不上，
 * 也就没有换算、没有反查、不需要额外的「结果槽位」字段。
 *
 * <h2>坐标约定（整数算术，无浮点误差）</h2>
 * 以<b>一个槽位的宽度</b>为 1 个单位，板中心为 0。
 * {@code rows} 排钉子共 {@code rows+1} 个槽位，槽位 {@code k} 的中心在
 * {@code k - rows/2}。球在「第 L 层」的横坐标（用半单位表示，恒为整数）是：
 * <pre>
 *   count(L) = clamp(L - 1, 0, rows)
 *   x2(L)    = 2 * bitCount(path &amp; ((1&lt;&lt;count) - 1)) - count
 * </pre>
 * <b>先乘 2 再算</b>，是因为每次弹跳横移半个槽宽 —— 用半单位能让整条轨迹落在整数上，
 * 于是「球最终停在槽位中心」这件事可以被精确断言，而不是「约等于」。
 *
 * <h2>层次结构</h2>
 * <pre>
 *   层 0            起点（板顶正中，x = 0）
 *   层 1            第 0 排钉子（1 颗，x = 0）
 *   层 2            第 1 排钉子（2 颗）
 *   ...
 *   层 rows         第 rows-1 排钉子
 *   层 rows+1       槽位（落点）
 * </pre>
 * 共 {@code rows+1} 个阶段，阶段 {@code p} 跨越「层 p → 层 p+1」。
 * 阶段 {@code p} 结束时球到达第 {@code p} 排钉子（{@code p < rows}）或槽位（{@code p == rows}）。
 *
 * <p>⚠️ 注意层 {@code L} 用的是 {@code count = L-1} 位：
 * 球在层 {@code L} 的横坐标由「到达该层之前已经发生过的弹跳」决定。
 * 写成 {@code count = L} 会让整条轨迹整体错半格，表现是球<b>穿过钉子而不是砸在钉子上</b>。
 */
public final class GaltonPath {

	/**
	 * 排数上限。
	 *
	 * <p>路径存在 int 里，每位一排，所以理论上限是 31 排（第 31 位是符号位，不能用）。
	 * 取 24 更保守，也远超实际可用：一块方块内画 25 个槽位已经细到看不清了。
	 */
	public static final int MAX_ROWS = 24;

	private GaltonPath() {
	}

	// ---------------------------------------------------------------- 掷路径

	/**
	 * 掷出一条路径（<b>服务端权威</b>）。
	 *
	 * <p>每一排<b>独立</b>判定，这是「二项分布」的来源：
	 * 落点槽位 = 往右弹的次数，服从 {@code Binomial(rows, rightChance)}。
	 * 概率不是配出来的，是这个过程算出来的 —— 这正是高尔顿板与转盘的根本区别。
	 *
	 * @param random       随机源
	 * @param rows         排数，会被夹到 {@code [1, MAX_ROWS]}
	 * @param rightChance  每次往右弹的概率，会被夹到 {@code [0, 1]}；
	 *                     {@code 0.5} 为对称分布
	 * @return 路径位掩码
	 */
	public static int rollPath(Random random, int rows, float rightChance) {
		int n = clampRows(rows);
		float p = clampChance(rightChance);

		int path = 0;
		for (int i = 0; i < n; i++) {
			if (random.nextFloat() < p) {
				path |= 1 << i;
			}
		}
		return path;
	}

	// ---------------------------------------------------------------- 结果

	/**
	 * 路径 → 落点槽位下标。
	 *
	 * <p>就是「往右弹了几次」。<b>这是唯一的落点定义</b>，
	 * 渲染器画球的位置也走同一套坐标，所以两者不可能不一致。
	 *
	 * @param path 路径位掩码
	 * @param rows 排数，用于屏蔽高位脏数据
	 * @return 槽位下标，落在 {@code [0, rows]}
	 */
	public static int slotOf(int path, int rows) {
		int n = clampRows(rows);
		return Integer.bitCount(path & mask(n));
	}

	/** 槽位总数 = 排数 + 1 */
	public static int slotCount(int rows) {
		return clampRows(rows) + 1;
	}

	/**
	 * 槽位 {@code k} 的中心横坐标（单位：一个槽宽）。
	 *
	 * @return {@code k - rows/2}；{@code k} 越界时按边界槽位返回
	 */
	public static float slotCenterX(int k, int rows) {
		int n = clampRows(rows);
		int clamped = Math.max(0, Math.min(n, k));
		return clamped - n / 2.0f;
	}

	// ---------------------------------------------------------------- 轨迹

	/**
	 * 球在「第 level 层」的横坐标（单位：一个槽宽）。
	 *
	 * <p>{@code level} 允许是小数（层与层之间插值），也允许取 {@code rows+1}（槽位层）。
	 * 返回值精确落在半格上（因为内部用半单位整数算再除以 2）。
	 */
	public static float xAtLevel(int path, int rows, float level) {
		int n = clampRows(rows);
		int maxLevel = n + 1;

		// 夹到 [0, maxLevel]：越界不该抛异常，渲染器每帧都会调它
		float l = Math.max(0.0f, Math.min((float) maxLevel, level));

		int low = (int) Math.floor(l);
		float t = l - low;

		if (low >= maxLevel) {
			return halfUnitsToX(x2AtLevel(path, n, maxLevel));
		}
		return halfUnitsToX(lerp(x2AtLevel(path, n, low), x2AtLevel(path, n, low + 1), t));
	}

	/**
	 * 球在整个下落过程中的横坐标。
	 *
	 * @param progress 下落进度 {@code [0, 1]}
	 */
	public static float xAt(int path, int rows, float progress) {
		int n = clampRows(rows);
		return xAtLevel(path, n, progressToLevel(progress, n));
	}

	/**
	 * 球在整个下落过程中的纵向位置比例。
	 *
	 * <p>返回 {@code [0, 1]}：{@code 0} 在板顶、{@code 1} 在槽位入口。
	 *
	 * <h2>为什么段内用 t² 而不是 t</h2>
	 * 自由落体是匀加速，位移随时间平方增长。用线性插值看起来像「匀速平移」，
	 * 球会显得轻飘飘的；用 t² 才有「越掉越快」的重量感。
	 * 段边界处 {@code t=1} 给出精确的层号，所以曲线连续、不会在钉子处跳一下。
	 */
	public static float yFractionAt(int rows, float progress) {
		int n = clampRows(rows);
		int levels = n + 1;

		float phase = clamp01(progress) * levels;
		int p = (int) Math.floor(phase);
		if (p >= levels) {
			// 落到底了：精确返回 1，不受浮点误差影响
			return 1.0f;
		}
		float t = phase - p;
		return (p + t * t) / levels;
	}

	/**
	 * 球已经砸过的钉子数（用于放撞击音效）。
	 *
	 * <p>返回 {@code [0, rows]}：每完成一个阶段就多砸一颗。
	 * 客户端拿它和上一帧的值比较，变了就响一声。
	 * <b>中途加入的玩家会从当前排开始响</b>，这是正确行为 ——
	 * 他本来就没听到前面几颗。
	 */
	public static int pegsHit(int rows, float progress) {
		int n = clampRows(rows);
		int levels = n + 1;

		int phase = (int) Math.floor(clamp01(progress) * levels);
		return Math.max(0, Math.min(n, phase));
	}

	/** 进度 → 层号（含小数） */
	public static float progressToLevel(float progress, int rows) {
		int n = clampRows(rows);
		return clamp01(progress) * (n + 1);
	}

	// ---------------------------------------------------------------- 内部

	/**
	 * 第 {@code level} 层的横坐标，<b>以半槽宽为单位的整数</b>。
	 *
	 * <p>{@code count = clamp(level - 1, 0, rows)} 是这条曲线的核心：
	 * 它表示「到达该层之前已经弹过几次」。写成 {@code level} 就会整体错半格。
	 */
	private static int x2AtLevel(int path, int rows, int level) {
		int count = Math.max(0, Math.min(rows, level - 1));
		int ones = Integer.bitCount(path & mask(count));
		return 2 * ones - count;
	}

	private static float halfUnitsToX(float x2) {
		return x2 / 2.0f;
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** 低 {@code bits} 位全 1 的掩码；{@code bits == 0} 时为 0 */
	private static int mask(int bits) {
		if (bits <= 0) {
			return 0;
		}
		if (bits >= 31) {
			return Integer.MAX_VALUE;
		}
		return (1 << bits) - 1;
	}

	private static int clampRows(int rows) {
		return Math.max(1, Math.min(MAX_ROWS, rows));
	}

	private static float clampChance(float chance) {
		if (Float.isNaN(chance)) {
			return 0.5f;
		}
		return Math.max(0.0f, Math.min(1.0f, chance));
	}

	private static float clamp01(float v) {
		if (Float.isNaN(v)) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
