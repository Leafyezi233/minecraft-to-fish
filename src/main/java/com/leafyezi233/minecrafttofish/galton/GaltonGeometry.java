package com.leafyezi233.minecrafttofish.galton;

/**
 * 高尔顿板的<b>几何</b>——盘面上每个东西该画在哪里。
 *
 * <h2>为什么这些量要放在「共享」包而不是渲染器里</h2>
 * 渲染器是客户端类，服务端自测<b>碰不到它</b>。
 * 而「球最终停在哪」「钉子在哪一行」「槽位边界在哪」全是<b>纯算术</b>，
 * 恰恰是最该被断言的东西：
 * <ul>
 *   <li>球的下落终点必须<b>精确等于</b>落点槽位的中心（差一点就说明轨迹公式错了）</li>
 *   <li>球在每一排的高度上必须<b>精确落在某颗钉子上</b>（不是从两颗之间穿过）</li>
 *   <li>整条轨迹必须留在盘面宽度内（越界 = 球飞出板外）</li>
 * </ul>
 * 这些断言在阶段 1 用 {@link GaltonPath} 钉过一次，这里把「盘面布局」这一层
 * 也做成可断言的形式。放在共享包里，服务端自测就能覆盖，不必等玩家肉眼发现。
 *
 * <h2>坐标约定</h2>
 * <b>局部坐标</b>：原点在方块中心、方块底面。
 * <ul>
 *   <li>{@code x} 沿盘面宽度方向，<b>0 = 盘面正中</b>，范围 {@code [-0.45, +0.45]}</li>
 *   <li>{@code y} 是方块内高度，{@code 0} = 方块底面</li>
 *   <li>{@code z} 沿盘面法线方向，<b>越大越靠前（越靠近玩家）</b></li>
 * </ul>
 * 渲染器负责把「局部坐标」经朝向旋转后搬到世界坐标。
 *
 * <h2>纵向布局（自下而上）</h2>
 * <pre>
 *   y = 1.95  ┌──────────────┐  BOARD_TOP_Y   盘面顶（超出方块 1 格）
 *             │  鱼 托 盘     │  TRAY  ← 鱼停在这里，不与钉子抢位置
 *   y = 1.72  ├──────────────┤  PEG_TOP_Y     球从这里落下
 *             │    ·         │
 *             │   · ·  钉子区 │  ← rows 排等距
 *             │  · · ·       │
 *   y = 0.305 ├──────────────┤  SLOT_TOP_Y    槽位带上沿
 *             │ 0 │ 1 │ 2 │..│  ← rows+1 个槽位
 *   y = 0.125 └──────────────┘  BOARD_BOTTOM_Y = 底座顶面（模型底板厚 2/16）
 * </pre>
 *
 * <h2>⚠️ 为什么要单独留一条「托盘」</h2>
 * 第一版把鱼画在钉子区正中，结果鱼的模型（约 0.4 格高）把中间几排钉子
 * <b>整片盖住</b>，玩家根本看不出这是个钉板。
 * 盘面只有 0.9 格宽，鱼和钉子在视觉上无法共存于同一块区域，
 * 所以顶部专门留一条 {@code TRAY_HEIGHT} 高的空带放鱼，
 * 钉子区从它下面开始。这样两者互不遮挡，读起来也更像真机器
 * （上面是投料口，下面是钉板）。
 *
 * <h2>前后分层（决定谁能挡住谁）</h2>
 * <pre>
 *   背板（最里）  →  钉子  →  球（最前）  →  鱼（更前）
 * </pre>
 * 球必须画在钉子<b>之前</b>（更靠前）。若把球和钉子放在同一个 z 上，
 * 球滚到钉子那一行时会看起来<b>穿进钉子里</b>；
 * 放在钉子前则读作「球在钉子上方滚过」，这才是高尔顿板该有的样子。
 */
public final class GaltonGeometry {

	/** 盘面宽度（格，含边框），与 {@link GaltonBoard#BOARD_WIDTH} 同源 */
	public static final float BOARD_WIDTH = GaltonBoard.BOARD_WIDTH;

	/** 可玩区宽度（格，不含边框）：槽位与钉子真正占据的范围 */
	public static float playfieldWidth() {
		return GaltonBoard.playfieldWidth();
	}

	/** 可玩区左沿的 x（格） */
	public static float playfieldLeftX() {
		return GaltonBoard.playfieldLeftX();
	}

	/** 可玩区右沿的 x（格） */
	public static float playfieldRightX() {
		return GaltonBoard.playfieldRightX();
	}

	/** 盘面底 = 底座顶面。方块模型里底板是 2/16 厚 */
	public static final float BOARD_BOTTOM_Y = 2.0f / 16.0f;

	/**
	 * 盘面顶高度（格）。
	 *
	 * <p>1.95 而不是 2.0：正好顶到第二格上沿会让盘面与上方方块的底面完全贴合，
	 * 从侧面看像「粘」在一起。留 0.05 的缝，视觉上清楚是两块。
	 *
	 * <p><b>这个值超过 1.0，是本方块「加高」决定的直接后果</b>，
	 * 也是渲染器必须声明 {@code rendersOutsideBoundingBox = true} 的原因。
	 */
	public static final float BOARD_TOP_Y = 1.95f;

	/** 顶部托盘高度（格）：专门放鱼，理由见类注释 */
	public static final float TRAY_HEIGHT = 0.23f;

	/** 钉子区上沿（球从这里开始下落）= 托盘下沿 */
	public static final float PEG_TOP_Y = BOARD_TOP_Y - TRAY_HEIGHT;

	/** 槽位带高度（格） */
	public static final float SLOT_BAND_HEIGHT = 0.18f;

	/** 槽位带上沿 */
	public static final float SLOT_TOP_Y = BOARD_BOTTOM_Y + SLOT_BAND_HEIGHT;

	/** 槽位带中心高度：球落定后停在这里（不是带的上沿，否则会「悬在格子上方」） */
	public static final float SLOT_CENTER_Y = (BOARD_BOTTOM_Y + SLOT_TOP_Y) / 2.0f;

	/** 背板前表面 z（钉子从这里往前长） */
	public static final float BACKBOARD_FRONT_Z = -0.10f;

	/** 背板厚度（向后方长，前表面位置不受影响） */
	public static final float BACKBOARD_THICKNESS = 0.05f;

	/** 钉子进深（格）：从背板前表面往前伸这么多 */
	public static final float PEG_DEPTH = 0.055f;

	/** 侧柱半宽（格）：盘面左右各一根，撑出「机器」的轮廓。与槽位表同源 */
	public static final float RAIL_HALF_WIDTH = GaltonBoard.RAIL_HALF_WIDTH;

	/** 侧柱进深（格） */
	public static final float RAIL_DEPTH = 0.075f;

	/**
	 * 球的 z（格）。
	 *
	 * <p>取钉子前端再往前一点点，让球在钉子<b>之前</b>，
	 * 滚过钉子时不会被钉子挡住。理由见类注释。
	 */
	public static final float BALL_Z = BACKBOARD_FRONT_Z + PEG_DEPTH + 0.012f;

	/** 鱼的 z（格）：比球再往前，鱼是「贴在板上给人取的东西」，不该被球压住 */
	public static final float FISH_Z = BALL_Z + 0.055f;

	/**
	 * 同一平面上的分层间距（格）。
	 *
	 * <p>与转盘同一个理由：槽位色块贴在背板前表面，两者若画在同一个 z 上会
	 * z-fighting（谁赢取决于驱动实现，表现为色块一闪一闪）。
	 * 0.002 格 = 2 毫米，肉眼看不出来，但足够让深度测试稳定。
	 */
	public static final float LAYER_EPS = 0.002f;

	private GaltonGeometry() {
	}

	// ---------------------------------------------------------------- 纵向

	/** 钉子区的纵向跨度（格）：从落球口到槽位带中心 */
	public static float dropSpan() {
		return PEG_TOP_Y - SLOT_CENTER_Y;
	}

	/**
	 * 纵向比例 {@code [0, 1]} → 盘面内的 y 坐标（格）。
	 *
	 * <p>{@code 0} = 落球口（{@link #PEG_TOP_Y}），{@code 1} = 槽位带中心。
	 * 落点取槽位带<b>中心</b>而不是上沿：球落定后应当停在格子中间，
	 * 停在格子上沿会看起来「悬在半空」。
	 *
	 * <p>比例一律由 {@link GaltonPath#yFractionAt} 或 {@link GaltonBoard#levelFraction}
	 * 给出，本方法<b>不自己算比例</b> —— 比例算两遍正是「球和钉子对不上」的温床。
	 */
	public static float yAtFraction(float fraction) {
		return PEG_TOP_Y - clamp01(fraction) * dropSpan();
	}

	/** 第 {@code row} 排钉子的纵向比例（{@code row} 从 0 起） */
	public static float pegRowFraction(int row, int rows) {
		int n = Math.max(1, rows);
		int r = Math.max(0, Math.min(n - 1, row));
		// 第 r 排对应 GaltonPath 的「层 r+1」，层比例 = (r+1)/(rows+1)
		return (r + 1) / (float) (n + 1);
	}

	/** 第 {@code row} 排钉子的 y 坐标（格） */
	public static float pegRowY(int row, int rows) {
		return yAtFraction(pegRowFraction(row, rows));
	}

	// ---------------------------------------------------------------- 横向

	/** 可玩区左沿的 x（格） */
	public static float boardLeftX() {
		return playfieldLeftX();
	}

	/** 可玩区右沿的 x（格） */
	public static float boardRightX() {
		return playfieldRightX();
	}

	/** 槽位 {@code k} 左沿的 x（格） */
	public static float slotLeftX(int k, int rows) {
		int n = Math.max(1, rows);
		int clamped = Math.max(0, Math.min(n, k));
		return playfieldLeftX() + clamped / (float) (n + 1) * playfieldWidth();
	}

	/** 槽位 {@code k} 右沿的 x（格） */
	public static float slotRightX(int k, int rows) {
		int n = Math.max(1, rows);
		int clamped = Math.max(0, Math.min(n, k));
		return playfieldLeftX() + (clamped + 1) / (float) (n + 1) * playfieldWidth();
	}

	/**
	 * 槽位 {@code k} 中心的 x（格）。
	 *
	 * <p><b>必须与 {@link GaltonPath#xAt} 在进度 1 处的取值完全相等</b>，
	 * 否则球会落在格子边界上而不是格心。这个等式由自测逐槽断言。
	 */
	public static float slotCenterX(int k, int rows) {
		int n = Math.max(1, rows);
		int clamped = Math.max(0, Math.min(n, k));
		return (clamped - n / 2.0f) * slotWidth(rows);
	}

	/** 一个槽位的宽度（格）：{@code rows+1} 个槽位铺满可玩区（不含边框） */
	public static float slotWidth(int rows) {
		return playfieldWidth() / (Math.max(1, rows) + 1);
	}

	/**
	 * 第 {@code row} 排第 {@code index} 颗钉子的 x（<b>槽宽为单位</b>）。
	 *
	 * <p>第 {@code row} 排有 {@code row+1} 颗钉子，等距分布在
	 * {@code -row/2 .. +row/2}。这是高尔顿板的标准三角排列。
	 *
	 * <p><b>为什么用「槽宽为单位」而不是直接给格</b>：
	 * {@link GaltonPath} 的轨迹也以槽宽为单位（{@link GaltonPath#xAtLevel}）。
	 * 两边同一个单位，才能直接断言「球砸在钉子上」——
	 * 若这里换成格，断言就得先乘一次槽宽，那个乘法写错就再也对不上了。
	 * 渲染器最后统一乘 {@link #slotWidth} 转成格。
	 */
	public static float pegOffsetX(int row, int index) {
		return index - row / 2.0f;
	}

	/** 第 {@code row} 排的钉子数 = {@code row + 1} */
	public static int pegCount(int row) {
		return Math.max(0, row) + 1;
	}

	/** 钉子总数（{@code rows} 排）= {@code rows(rows+1)/2} */
	public static int totalPegs(int rows) {
		int n = Math.max(0, rows);
		return n * (n + 1) / 2;
	}

	// ---------------------------------------------------------------- 尺寸

	/**
	 * 球的半径（格）。
	 *
	 * <p>按槽宽取值，所以槽位数一变球会自动跟着缩放，不会出现「球比槽还宽」。
	 * 取槽宽的 {@code 0.40} 倍（直径 {@code 0.80} 个槽宽）：
	 * 球要能明显看出是圆的，又不能宽到盖住整个槽位 ——
	 * 否则球落进相邻槽时看起来会连成一片。
	 *
	 * <p><b>已知的观感风险</b>：默认 7 槽（6 排）时槽宽 {@code 0.90/7 ≈ 0.129}，
	 * 球直径只有 {@code 0.103} 格，在常见视距下约 <b>1.5 像素</b>。
	 * 这是「一块方块内塞下 7 个槽位」的必然结果，不是参数没调好。
	 * 想看得更清楚只能减少槽位数（比如 5 槽 / 4 排），
	 * 但那会同时改变概率分布（见 {@link GaltonBoard} 的默认表说明）。
	 */
	public static float ballRadius(int rows) {
		return Math.max(0.014f, slotWidth(rows) * 0.40f);
	}

	/** 钉子半径（格）。取槽宽的 0.17 倍，比球略小，视觉上「球比钉子大」 */
	public static float pegRadius(int rows) {
		return Math.max(0.008f, slotWidth(rows) * 0.17f);
	}

	/** 托盘中心高度（格）：鱼画在这里 */
	public static float trayCenterY() {
		return (PEG_TOP_Y + BOARD_TOP_Y) / 2.0f;
	}

	/** 结果文字的悬浮高度（格）：盘面顶再往上一点，不与盘面抢位置 */
	public static float labelY() {
		return BOARD_TOP_Y + 0.22f;
	}

	/**
	 * 第 {@code level} 层对应的下落进度 {@code [0, 1]}。
	 *
	 * <p>是 {@link GaltonPath#progressToLevel} 的<b>逆运算</b>：
	 * 层 0 = 起点（进度 0），层 {@code rows+1} = 槽位（进度 1）。
	 * 共 {@code rows+1} 个阶段，所以进度 = {@code level / (rows+1)}。
	 *
	 * <p>与 {@link GaltonBoard#levelFraction} 算的是同一个量。
	 * 这里再给一个静态版本，是为了让「给定排数」的几何断言
	 * 不必先构造一个 {@link GaltonBoard} —— 测试要枚举 1..12 排，
	 * 而 {@code GaltonBoard} 只能从配置构建。
	 */
	public static float progressAtLevel(int level, int rows) {
		int n = Math.max(1, rows);
		int clamped = Math.max(0, Math.min(n + 1, level));
		return clamped / (float) (n + 1);
	}

	// ---------------------------------------------------------------- 球的位置

	/**
	 * 球在下落进度 {@code progress} 时的局部坐标。
	 *
	 * <p><b>这是渲染器唯一允许用来算球位置的入口。</b>
	 * 渲染器<b>不得</b>自己写轨迹公式 —— 一旦渲染器和
	 * {@link GaltonPath} 各算一遍，两者迟早对不上，
	 * 表现就是「球停在的位置和实际结算的槽位不一致」，
	 * 而这正是本设计用「只存一个路径」从结构上消灭掉的错误。
	 * 放在共享包里的另一个好处：这些等式能被<b>服务端自测</b>逐条断言，
	 * 不必等玩家肉眼发现。
	 *
	 * @param path     路径位掩码
	 * @param rows     排数
	 * @param progress 下落进度 {@code [0, 1]}
	 * @return 长度为 2 的数组 {@code [x, y]}，单位为格（局部坐标）
	 */
	public static float[] ballLocal(int path, int rows, float progress) {
		int n = Math.max(1, rows);
		// x 以「槽宽」为单位算出，再乘槽宽转成格。
		// 这个乘法必须在<b>这里</b>统一做：渲染器若自己再乘一次就会重复缩放。
		float x = GaltonPath.xAt(path, n, progress) * slotWidth(n);
		float y = yAtFraction(GaltonPath.yFractionAt(n, progress));
		return new float[] { x, y };
	}

	/**
	 * 球落定后所在的位置（局部坐标）。
	 *
	 * <p>等价于 {@code ballLocal(path, rows, 1)}，单独提供是因为
	 * 「落定位置」是个会被反复断言的关键量，直接调它比传一个 1 更不容易写错。
	 */
	public static float[] ballRestLocal(int path, int rows) {
		return ballLocal(path, rows, 1.0f);
	}

	// ---------------------------------------------------------------- 工具

	private static float clamp01(float v) {
		if (Float.isNaN(v)) {
			return 0.0f;
		}
		return Math.max(0.0f, Math.min(1.0f, v));
	}
}
