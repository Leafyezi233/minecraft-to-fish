package com.leafyezi233.minecrafttofish.galton;

import java.util.ArrayList;
import java.util.List;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.config.GaltonSlotConfig;

/**
 * 高尔顿板的槽位表：把配置里的一串槽位定义<b>校验、归一化</b>成可直接使用的运行时表，
 * 并按二项分布算出每个槽位的<b>真实被砸中概率</b>。
 *
 * <h2>⚠️ 概率是算出来的，不是配出来的</h2>
 * 这是与 {@code WheelTable} 最根本的区别。转盘的扇区带权重，
 * 「权重比例 = 角度比例 = 概率」，服主写什么就是什么。
 * 高尔顿板的球在每一排独立地左右弹，落点槽位服从
 * {@code Binomial(rows, pegRightChance)}：
 * <pre>
 *   P(槽位 k) = C(rows, k) · p^k · (1-p)^(rows-k)
 * </pre>
 * 服主能调的只有<b>倍率</b>（赔率）和 {@code pegRightChance}（整条分布的偏斜），
 * <b>配不出「让第 3 个槽位占 40%」</b>。这不是限制，而是这个游戏的玩法内核：
 * 玩家看着球在钉子上随机乱撞，最终落进哪个槽位是物理过程的结果。
 *
 * <h2>行数由槽位数推导，不单独配置</h2>
 * {@code rows = slots.size() - 1}。这样「行数和槽位数不匹配」这一类配置错误
 * <b>从根上不存在</b>，不需要额外的一致性校验。
 *
 * <h2>关于期望值</h2>
 * 与转盘一致：<b>期望值 &gt;= 1 不回退</b>，只打 WARN。
 * 服主有权把游戏配成对玩家有利的，本类只负责算出来并提醒。
 *
 * <h2>回退触发条件</h2>
 * 只在「配置在结构上不可用」时回退到内置默认表：
 * <ul>
 *   <li>列表为空</li>
 *   <li>存在负倍率（意图不明，且与 {@link GaltonSlot} 的不变量冲突）</li>
 *   <li>有效槽位少于 2 个（1 个槽位没有「弹跳」可言，不是高尔顿板）</li>
 *   <li>槽位数超过上限（路径位掩码装不下）</li>
 * </ul>
 */
public final class GaltonBoard {

	/** 倍率上限，防止 价值 × 倍率 溢出 long（与转盘一致） */
	public static final int MAX_MULTIPLIER = 1_000;

	/** 槽位数量上限 = {@link GaltonPath#MAX_ROWS} + 1 */
	public static final int MAX_SLOTS = GaltonPath.MAX_ROWS + 1;

	/**
	 * 板面宽度（格）—— <b>整块背板</b>的宽度，含左右边框。
	 *
	 * <p>盘面渲染在单方块内，方块横向只有 1 格，所以要留边距。
	 * 0.90 与转盘的盘面直径（0.80）同量级，视觉上两个小游戏体量相当。
	 *
	 * <p>⚠️ 槽位<b>不是</b>铺满这个宽度，而是铺满 {@link #playfieldWidth()}。
	 * 详见那里的说明。
	 */
	public static final float BOARD_WIDTH = 0.90f;

	/**
	 * 左右边框的<b>半宽</b>（格）。边框全宽 = 它的两倍。
	 *
	 * <p><b>为什么需要它，以及为什么是 0.032</b>：
	 * 第一版把槽位铺满整个 {@link #BOARD_WIDTH}，边框又画在板子边缘上，
	 * 于是最左/最右两个槽位被边框<b>盖掉了约 70%</b> ——
	 * 玩家看到的是「两头的格子不见了」。
	 *
	 * <p>修法不是简单把边框调细，而是<b>把可玩区和边框分开</b>：
	 * 槽位与钉子只占中间的 {@link #playfieldWidth()}，边框落在它外面。
	 * 这里取 0.032（原为 0.045）是在「边框看起来还像边框」与
	 * 「可玩区别被挤得太窄」之间的折中：再细就看不出一条边了，
	 * 再粗则槽宽变小、球跟着变小（球半径按槽宽取值）。
	 */
	public static final float RAIL_HALF_WIDTH = 0.032f;

	/**
	 * 可玩区宽度（格）—— 槽位与钉子真正占据的横向范围。
	 *
	 * <p>= {@code BOARD_WIDTH - 4 × RAIL_HALF_WIDTH}（左右各去掉一个边框全宽）。
	 *
	 * <p><b>「可玩区」和「背板」必须分开定义</b>，这是修「两头槽位被挡」的关键：
	 * 两者若共用一个宽度，边框就必然压在边缘槽位上 ——
	 * 因为边框必须画在板的边缘，而边缘槽位也必须画在板的边缘。
	 * 分开之后，边框和槽位各占各的位置，从结构上不再冲突。
	 */
	public static float playfieldWidth() {
		return BOARD_WIDTH - 4.0f * RAIL_HALF_WIDTH;
	}

	/** 可玩区左沿的 x（格，以盘面中心为 0） */
	public static float playfieldLeftX() {
		return -playfieldWidth() / 2.0f;
	}

	/** 可玩区右沿的 x（格） */
	public static float playfieldRightX() {
		return playfieldWidth() / 2.0f;
	}

	/** 未指定颜色时依次取用的调色板（与转盘同一套，保持模组视觉统一） */
	private static final int[] PALETTE = {
			0xFF4FA3D1, 0xFFD4AF37, 0xFF6B6B6B, 0xFF7FB069,
			0xFFB56576, 0xFF8E7DBE, 0xFFE0A458, 0xFF5B8E7D,
	};

	/**
	 * 内置默认槽位表：7 槽（6 排钉子）。
	 *
	 * <p>概率来自对称二项分布 {@code Binomial(6, 0.5)}：
	 * <pre>
	 *   槽位 0 / 6 : C(6,0)=1   → 1/64  = 1.5625%   ×10
	 *   槽位 1 / 5 : C(6,1)=6   → 6/64  = 9.375%    ×3
	 *   槽位 2..4  : C=15,20,15 → 50/64 = 78.125%   ×0
	 * </pre>
	 * 期望倍率 = (2×1×10 + 2×6×3) / 64 = 56/64 = <b>0.875</b>，
	 * 中奖率 14/64 = <b>21.875%</b>。
	 *
	 * <p>与转盘默认表（期望 0.85、中奖率 35%）相比：高尔顿板<b>赢面更小、
	 * 但边缘是 ×10 的大奖</b>。两个游戏因此形成真正的取舍，而不是数值强弱之分。
	 *
	 * <p>倍率取整数是因为 {@code applyMultiplier} 与整个经济系统都是整数倍率；
	 * 这组值是<b>在整数约束下反解</b>出来的，没有硬凑成 0.85。
	 */
	private static final List<GaltonSlot> DEFAULT_SLOTS = List.of(
			new GaltonSlot(10, 0xFFD4AF37, "x10"),
			new GaltonSlot(3, 0xFF4FA3D1, "x3"),
			new GaltonSlot(0, 0xFF6B6B6B, GaltonSlot.LABEL_LOSE),
			new GaltonSlot(0, 0xFF6B6B6B, GaltonSlot.LABEL_LOSE),
			new GaltonSlot(0, 0xFF6B6B6B, GaltonSlot.LABEL_LOSE),
			new GaltonSlot(3, 0xFF4FA3D1, "x3"),
			new GaltonSlot(10, 0xFFD4AF37, "x10"));

	private final List<GaltonSlot> slots;

	/** 排数 = 槽位数 - 1 */
	private final int rows;

	/** 每排往右弹的概率 */
	private final float pegRightChance;

	/** 每个槽位被砸中的概率，下标与 {@link #slots} 对齐，总和为 1 */
	private final double[] probabilities;

	/** 期望倍率 = Σ(概率 × 倍率) */
	private final double expectedMultiplier;

	/** true 表示配置非法、当前用的是内置默认表 */
	private final boolean usingDefaults;

	private GaltonBoard(List<GaltonSlot> slots, float pegRightChance, boolean usingDefaults) {
		this.slots = slots;
		this.rows = slots.size() - 1;
		this.pegRightChance = pegRightChance;
		this.probabilities = binomial(rows, pegRightChance);
		this.expectedMultiplier = expectedOf(slots, probabilities);
		this.usingDefaults = usingDefaults;
	}

	// ---------------------------------------------------------------- 当前实例

	/** 本地按配置构建的槽位表（<b>服务端权威</b>；客户端在没收到同步前也用它兜底） */
	private static volatile GaltonBoard cachedBoard;

	/**
	 * 服务端下发的槽位表（<b>仅客户端</b>）。
	 *
	 * <p>与 {@code WheelTable} 同样的理由：槽位表来自服务端的配置文件，
	 * 联机时客户端本地没有。若客户端拿自己的配置去画槽位与倍率，
	 * 而服主改过表，就会出现「服务端算中第 3 槽、客户端那儿画的是别的倍率」。
	 *
	 * <p>为 null 表示「没收到过」，此时退回本地配置构建的表。
	 */
	private static volatile GaltonBoard remoteBoard;

	/** 按当前配置重建槽位表并缓存 */
	public static void rebuild() {
		cachedBoard = build(EconomyConfig.get().galtonSlots, EconomyConfig.get().galtonPegRightChance);
	}

	/**
	 * 采用服务端下发的槽位表（<b>仅客户端调用</b>）。
	 * <p>服务端发来一张坏表时清空（退回本地配置），而不是让客户端崩在渲染线程上。
	 * 与 {@code WheelTable.applyRemote} 同款处理：坏表不能冒充成「服务端的表」。
	 */
	public static void applyRemote(List<GaltonSlot> slots, float pegRightChance) {
		GaltonBoard board = fromSlots(slots, pegRightChance);
		remoteBoard = board.usingDefaults() ? null : board;
	}

	/** 丢弃服务端下发的表（断线时调用） */
	public static void clearRemote() {
		remoteBoard = null;
	}

	/** 是否正在使用服务端下发的槽位表 */
	public static boolean usingRemote() {
		return remoteBoard != null;
	}

	/** 当前生效的槽位表；尚未构建过时按当前配置惰性构建一次 */
	public static GaltonBoard current() {
		GaltonBoard remote = remoteBoard;
		if (remote != null) {
			return remote;
		}

		GaltonBoard board = cachedBoard;
		if (board == null) {
			synchronized (GaltonBoard.class) {
				board = cachedBoard;
				if (board == null) {
					board = build(EconomyConfig.get().galtonSlots,
							EconomyConfig.get().galtonPegRightChance);
					cachedBoard = board;
				}
			}
		}
		return board;
	}

	// ---------------------------------------------------------------- 构建

	/**
	 * 由配置构建槽位表；结构上不可用的配置才回退到内置默认表。
	 *
	 * @param configured 配置里的槽位列表，可为 null
	 * @param chance     每排往右弹的概率，会被夹到 {@code [0, 1]}
	 */
	public static GaltonBoard build(List<GaltonSlotConfig> configured, float chance) {
		if (configured == null || configured.isEmpty()) {
			return fallback("配置里没有槽位");
		}

		float p = clampChance(chance);

		List<GaltonSlot> sanitized = new ArrayList<>();
		for (GaltonSlotConfig config : configured) {
			if (config == null) {
				MyMod.LOGGER.warn("[galton] 忽略一个空的槽位配置项");
				continue;
			}
			if (sanitized.size() >= MAX_SLOTS) {
				MyMod.LOGGER.warn("[galton] 槽位数量超过上限 {}，多余部分已忽略", MAX_SLOTS);
				break;
			}

			if (config.multiplier < 0) {
				// 负倍率意图不明（是"倒扣价值"还是"写错了"？），不猜测，整表回退
				return fallback("存在负倍率：" + config.multiplier);
			}

			int multiplier = config.multiplier;
			if (multiplier > MAX_MULTIPLIER) {
				MyMod.LOGGER.warn("[galton] 槽位倍率 ×{} 超过上限，已按 ×{} 处理",
						multiplier, MAX_MULTIPLIER);
				multiplier = MAX_MULTIPLIER;
			}

			int color = config.color;
			if (color == 0) {
				color = PALETTE[sanitized.size() % PALETTE.length];
			}

			sanitized.add(new GaltonSlot(multiplier, color, config.labelKey));
		}

		if (sanitized.size() < 2) {
			// 1 个槽位没有"弹跳"可言，不是高尔顿板；0 个更不用说
			return fallback("有效槽位少于 2 个（实际 " + sanitized.size() + "）");
		}

		return finish(sanitized, p, false, true);
	}

	/**
	 * 由<b>已经校验过的槽位</b>直接构建（网络同步路径用）。
	 *
	 * <p>与 {@link #build} 的区别：{@code build} 面向「玩家手写的配置」，
	 * 允许 null 字段、0 颜色，负责清理；本方法面向「服务端已经清理过一遍的表」，
	 * 且<b>不重复打日志</b>（否则每个客户端都会再刷一遍）。
	 *
	 * <p>仍做最小校验：版本不一致时收到的可能是坏数据，此时退回内置默认表，
	 * 绝不让客户端崩在渲染线程上。
	 */
	public static GaltonBoard fromSlots(List<GaltonSlot> slots, float chance) {
		if (slots == null || slots.isEmpty()) {
			return fallback("同步来的槽位表为空");
		}

		List<GaltonSlot> sanitized = new ArrayList<>(Math.min(slots.size(), MAX_SLOTS));
		for (GaltonSlot slot : slots) {
			if (slot == null) {
				continue;
			}
			if (sanitized.size() >= MAX_SLOTS) {
				break;
			}
			sanitized.add(slot);
		}
		if (sanitized.size() < 2) {
			return fallback("同步来的槽位表少于 2 个有效条目");
		}

		return finish(sanitized, clampChance(chance), false, false);
	}

	/** 收尾：算概率、算期望、必要时打日志 */
	private static GaltonBoard finish(List<GaltonSlot> slots, float p,
			boolean usingDefaults, boolean verbose) {

		GaltonBoard board = new GaltonBoard(List.copyOf(slots), p, usingDefaults);

		if (board.expectedMultiplier >= 1.0d) {
			// 不回退：服主有权配成对玩家有利。只提醒一次，让日志里查得到。
			MyMod.LOGGER.warn("[galton] 槽位表期望值 {} >= 1，玩家长期可获利（按配置生效，未回退）",
					String.format("%.4f", board.expectedMultiplier));
		}

		if (verbose) {
			MyMod.LOGGER.info("[galton] 已加载槽位表：{} 个槽位（{} 排钉子，p={}），期望值 {}",
					board.slots.size(), board.rows,
					String.format("%.4f", board.pegRightChance),
					String.format("%.4f", board.expectedMultiplier));
		}
		return board;
	}

	/** 回退到内置默认表，并打 ERROR 日志说明原因 */
	private static GaltonBoard fallback(String reason) {
		MyMod.LOGGER.error("[galton] 槽位配置非法（{}），已回退到内置默认表（7 槽 / 期望值 0.8750）",
				reason);
		return new GaltonBoard(DEFAULT_SLOTS, 0.5f, true);
	}

	// ---------------------------------------------------------------- 概率

	/**
	 * 对称/偏斜二项分布：{@code P(k) = C(rows, k) · p^k · (1-p)^(rows-k)}。
	 *
	 * <p><b>为什么用 double 递推而不是直接算阶乘</b>：
	 * {@code C(24,12) = 2704156} 还好，但 {@code rows!} 在 21 就溢出 long 了。
	 * 这里用递推 {@code P(k+1) = P(k) · (rows-k)/(k+1) · p/(1-p)} 逐个推，
	 * 全程不出现阶乘，也就不会溢出。
	 *
	 * <p><b>边界 p=0 / p=1 单独处理</b>：递推里的 {@code p/(1-p)} 会除零。
	 * 这两种极端配置下分布退化成「全部落在某一端」，是合法配置（虽然不好玩），
	 * 不该让整表回退。
	 *
	 * @return 长度为 {@code rows+1} 的概率数组，总和为 1（误差在浮点范围内）
	 */
	public static double[] binomial(int rows, float p) {
		int n = Math.max(1, Math.min(GaltonPath.MAX_ROWS, rows));
		double[] out = new double[n + 1];

		double pd = clampChance(p);

		if (pd <= 0.0d) {
			// 永不往右弹：全部落在最左槽位
			out[0] = 1.0d;
			return out;
		}
		if (pd >= 1.0d) {
			// 永远往右弹：全部落在最右槽位
			out[n] = 1.0d;
			return out;
		}

		// P(0) = (1-p)^n
		out[0] = Math.pow(1.0d - pd, n);
		double ratio = pd / (1.0d - pd);

		for (int k = 0; k < n; k++) {
			out[k + 1] = out[k] * (double) (n - k) / (double) (k + 1) * ratio;
		}

		// 归一化：递推的浮点误差累积后总和可能偏离 1，
		// 不修正的话「概率之和」这类断言会莫名其妙地差一点点
		normalizeInPlace(out);
		return out;
	}

	/** 把数组归一化成总和 1；全 0 时退化为均匀分布 */
	private static void normalizeInPlace(double[] values) {
		double sum = 0.0d;
		for (double v : values) {
			sum += v;
		}
		if (sum <= 0.0d) {
			// 理论上不可达；真到了这里说明整条分布算坏了，给均匀分布兜底
			double uniform = 1.0d / values.length;
			for (int i = 0; i < values.length; i++) {
				values[i] = uniform;
			}
			return;
		}
		for (int i = 0; i < values.length; i++) {
			values[i] /= sum;
		}
	}

	/** 期望值 = Σ(概率 × 倍率) */
	private static double expectedOf(List<GaltonSlot> slots, double[] probabilities) {
		double sum = 0.0d;
		for (int i = 0; i < slots.size() && i < probabilities.length; i++) {
			sum += probabilities[i] * slots.get(i).multiplier();
		}
		return sum;
	}

	// ---------------------------------------------------------------- 查询

	/** 槽位列表（只读） */
	public List<GaltonSlot> slots() {
		return slots;
	}

	public int size() {
		return slots.size();
	}

	public GaltonSlot slot(int index) {
		return slots.get(index);
	}

	/** 排数 = 槽位数 - 1 */
	public int rows() {
		return rows;
	}

	/** 每排往右弹的概率 */
	public float pegRightChance() {
		return pegRightChance;
	}

	/** 槽位 {@code index} 被砸中的概率 */
	public double probabilityOf(int index) {
		if (index < 0 || index >= probabilities.length) {
			return 0.0d;
		}
		return probabilities[index];
	}

	/** 期望倍率 */
	public double expectedMultiplier() {
		return expectedMultiplier;
	}

	/** 中奖率 = Σ(倍率 &gt; 0 的槽位概率) */
	public double winChance() {
		double sum = 0.0d;
		for (int i = 0; i < slots.size() && i < probabilities.length; i++) {
			if (slots.get(i).isWin()) {
				sum += probabilities[i];
			}
		}
		return sum;
	}

	/** 是否正在使用内置默认表（配置非法） */
	public boolean usingDefaults() {
		return usingDefaults;
	}

	// ---------------------------------------------------------------- 几何

	/**
	 * 一个槽位的宽度（格）。
	 *
	 * <p>{@code rows+1} 个槽位铺满<b>可玩区</b> {@link #playfieldWidth()}，
	 * <b>不是</b>铺满整块背板 —— 左右边框要占掉两边各一个边框宽度。
	 *
	 * <p>钉子的水平间距<b>等于</b>槽宽，这是高尔顿板的真实几何：
	 * 最外侧的两个槽位分别在<b>最外两颗钉子之外</b>，
	 * 所以板宽要按「槽位数」而不是「钉子数」来分。
	 */
	public float slotWidth() {
		return playfieldWidth() / (rows + 1);
	}

	/**
	 * 第 {@code level} 层的纵向比例 {@code [0, 1]}。
	 *
	 * <p>{@code 0} = 板顶（球起点），{@code 1} = 槽位入口。
	 * 共 {@code rows+1} 层，等距分布。
	 */
	public float levelFraction(int level) {
		int levels = rows + 1;
		return Math.max(0.0f, Math.min(1.0f, (float) level / levels));
	}

	/**
	 * 槽位 {@code k} 的中心横向比例 {@code [0, 1]}，用于渲染。
	 * <p>{@code 0} = 板最左，{@code 1} = 板最右。
	 */
	public float slotCenterFraction(int k) {
		return (float) (Math.max(0, Math.min(rows, k)) + 0.5f) / (rows + 1);
	}

	// ---------------------------------------------------------------- 抽取

	/**
	 * 掷出一条路径（<b>服务端权威</b>）。
	 *
	 * <p>注意返回的是<b>路径</b>而不是槽位：槽位由路径数出来（{@link GaltonPath#slotOf}），
	 * 两者是同一个变量的两种读法，所以不存在「传错了对不上」的可能。
	 * 这也是为什么本类<b>没有</b> {@code rollIndex} 那样的「按下标抽取」方法 ——
	 * 高尔顿板不需要传下标，传路径就够了，而且更完整（客户端要靠它画整条轨迹）。
	 */
	public int rollPath(net.minecraft.util.math.random.Random random) {
		return GaltonPath.rollPath(random, rows, pegRightChance);
	}

	/** 路径 → 槽位下标 */
	public int slotOf(int path) {
		return GaltonPath.slotOf(path, rows);
	}

	// ---------------------------------------------------------------- 计算

	/**
	 * 把倍率作用到价值上，<b>饱和运算</b>：溢出时返回 {@link Long#MAX_VALUE} 而不是回绕成负数。
	 * <p>倍率为 0（未中奖）时返回 0。
	 *
	 * <p>实现与 {@code WheelTable.applyMultiplier} 一致（两边都是「价值 × 整数倍率」，
	 * 溢出语义必须相同，否则同一个经济系统里两个游戏对「爆表」的处理会不一样）。
	 */
	public static long applyMultiplier(long value, int multiplier) {
		if (value <= 0L || multiplier <= 0) {
			return 0L;
		}
		if (value > Long.MAX_VALUE / multiplier) {
			return Long.MAX_VALUE;
		}
		return value * multiplier;
	}

	private static float clampChance(float chance) {
		if (Float.isNaN(chance)) {
			return 0.5f;
		}
		return Math.max(0.0f, Math.min(1.0f, chance));
	}
}
