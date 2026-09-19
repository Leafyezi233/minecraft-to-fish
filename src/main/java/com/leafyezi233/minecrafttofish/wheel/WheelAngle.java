package com.leafyezi233.minecrafttofish.wheel;

import net.minecraft.util.math.Direction;

/**
 * 转盘角度的<b>纯函数</b>推导（两端共用，无任何可变状态）。
 *
 * <h2>为什么不是「每个客户端各播一份动画」</h2>
 * 改造前 {@code WheelAnimation} 是每个客户端各持一份状态机，用
 * {@code System.currentTimeMillis()} 计时、用 {@code Math.random()} 决定转几圈。
 * 三个后果：
 * <ul>
 *   <li><b>各人看到的落点时刻不同</b>：墙钟起点是「客户端收到包」的瞬间，
 *       网络延迟不同的玩家会先后停下；</li>
 *   <li><b>各人看到的圈数不同</b>：随机数各掷各的；</li>
 *   <li><b>中途入场的人会从头转一遍</b>：他收到的只是「正在转」，
 *       没有起点就没有进度，只能从 0 开始。</li>
 * </ul>
 * 本类把角度写成「(服务端下发的状态, 当前游戏刻) 的函数」：
 * 同一个服务端状态 + 同一个游戏刻 ⇒ <b>所有客户端算出同一个角度</b>。
 * 于是上面三个问题同时消失，而且<b>不需要任何每客户端可变状态</b> ——
 * 网络包重发、玩家进出区块都不会让盘面「跳一下」。
 *
 * <h2>输入只有四个</h2>
 * <ul>
 *   <li>{@code toSector} —— 本次结果扇区（服务端掷骰当场定下，权威）</li>
 *   <li>{@code fromSector} —— 上一次结果的扇区（决定本次从哪个角度起转）</li>
 *   <li>{@code spinSequence} —— 抽奖序号，用于<b>确定性地</b>决定转几整圈</li>
 *   <li>{@code elapsedTicks / totalTicks} —— 转动进度</li>
 * </ul>
 * 全是整数与浮点纯运算，不读时钟、不掷随机数。
 *
 * <h2>角度约定</h2>
 * 0° 指向正上方（12 点方向），沿顺时针增长，与 {@link WheelTable} 一致。
 * 指针<b>固定</b>在正上方，靠盘面旋转让目标扇区转到指针下。
 */
public final class WheelAngle {

	/** 至少转几整圈，保证有「转起来」的观感 */
	private static final int MIN_FULL_TURNS = 5;

	/** 额外增加的圈数上限（由序号确定性决定，让每次手感略有不同） */
	private static final int EXTRA_TURNS = 3;

	/**
	 * 扇区色块在两侧各内缩的角度上限（度），让出来的缝隙就是分割线。
	 *
	 * <p>分割线不是「另画一条线」，而是<b>色块之间露出的底色</b> ——
	 * 盘体整体先铺一层描边色，色块内缩后底色从缝里透出来。
	 * 这样不需要处理共面叠画的深度竞争（同 z 画两遍谁赢取决于驱动实现）。
	 */
	public static final float DIVIDER_HALF_ANGLE = 1.2f;

	private WheelAngle() {
	}

	// ---------------------------------------------------------------- 静止角

	/**
	 * 落点抖动上限：指针落在扇区<b>中段</b>这个比例范围内。
	 *
	 * <p>取 {@code 0.75} 表示落点可在中点两侧各 {@code 75%} 半宽内浮动 ——
	 * 既不会每次都停在正中（玩家看得出规律），也不会贴到分界线上
	 * （那样玩家分不清到底中了哪一格）。最小扇区（默认表的 ×5，占 18°）
	 * 仍有约 {@code 2.2°} 的边距。
	 */
	public static final float JITTER_MARGIN = 0.75f;

	/**
	 * 某个扇区「停在指针下」时盘面应有的角度。
	 *
	 * <h2>推导（注意旋转方向）</h2>
	 * 渲染器对盘面施加 {@code RotationAxis.POSITIVE_Z.rotationDegrees(angle)}。
	 * 实测（JOML {@code Quaternionf.rotationZ} 数值验证）该旋转把位于盘面角度
	 * {@code a} 的点转到：
	 * <pre>
	 *   a' = (a - angle) mod 360
	 * </pre>
	 * 要让盘面角度 {@code target} 出现在指针处（{@code a' = 0}），需要
	 * {@code angle ≡ target (mod 360)}，也就是 <b>{@code angle = target}</b>。
	 *
	 * <p><b>这里曾经写成 {@code 360 - mid}（符号反了）</b>：盘面会朝着与目标扇区
	 * <b>相反</b>的方向偏 {@code 2·mid}，表现就是「停下的位置和拿到的奖项对不上」。
	 * 这是「指针停在哪」与「实际抽中哪个」之间唯一的一次换算，
	 * 一旦符号错，两者必然错位 —— 最该被自测钉死的等式。
	 *
	 * <h2>落点抖动</h2>
	 * {@code jitter} 在 {@code [-1, 1]} 内取值，把落点在中点附近平移，
	 * 使转盘不必每次都停在扇区正中。平移量按<b>扇区半宽</b>缩放，
	 * 所以不论扇区多窄，落点都稳稳留在本扇区内部。
	 *
	 * @param table  扇区表
	 * @param sector 扇区下标；越界或 {@link WheelConstants#NO_SECTOR} 时返回 0°
	 * @param jitter 落点抖动，{@code [-1, 1]}；超出范围会被夹住
	 * @return 角度，落在 {@code [0, 360)}
	 */
	public static float restAngle(WheelTable table, int sector, float jitter) {
		if (table == null || sector < 0 || sector >= table.size()) {
			return 0.0f;
		}

		float start = table.startAngle(sector);
		float end = table.endAngle(sector);
		float half = (end - start) / 2.0f;
		float mid = (start + end) / 2.0f;

		float clamped = Math.max(-1.0f, Math.min(1.0f, jitter));
		return normalize(mid + clamped * half * JITTER_MARGIN);
	}

	/**
	 * 不带抖动的落点角（等价于 {@code jitter = 0}）。
	 * <p>保留它是为了让「中点落点」这个基准仍然可被单独表达与测试。
	 */
	public static float restAngle(WheelTable table, int sector) {
		return restAngle(table, sector, 0.0f);
	}

	// ---------------------------------------------------------------- 扇区分割线

	/**
	 * 某个扇区色块在<b>某一侧</b>要内缩的角度（度）。
	 *
	 * <h2>为什么按半宽限制而不是固定值</h2>
	 * 内缩量固定的话，配置一个很窄的扇区（权重小）会被两侧各吃掉
	 * {@code DIVIDER_HALF_ANGLE} 而<b>直接消失</b>。这里按扇区张角的
	 * 一小部分限制，保证任何合法扇区都还剩至少一半宽度可见。
	 *
	 * <p>返回的是<b>角度</b>而非下标相关的量，纯函数，两端算法一致。
	 *
	 * @param sweep 该扇区的张角（度，非负）
	 * @return 内缩角度，落在 {@code [0, DIVIDER_HALF_ANGLE]}
	 */
	public static float dividerInset(float sweep) {
		float width = Math.abs(sweep);
		// 最多吃掉 1/4 半宽（即整宽的 1/8），窄扇区也一定留下可见色块
		float maxByWidth = width * 0.125f;
		return Math.min(DIVIDER_HALF_ANGLE, maxByWidth);
	}

	// ---------------------------------------------------------------- 落点反查

	/**
	 * 给定盘面当前角度，反查<b>指针（正上方）此刻指着哪个扇区</b>。
	 *
	 * <h2>为什么需要它</h2>
	 * B2 方案要把「指针下方那一格」提亮。指针固定在 {@code a' = 0}，
	 * 而盘面旋转 {@code angle} 后，原本在角度 {@code a} 的点会落到
	 * {@code (a - angle) mod 360}（推导见 {@link #restAngle}）。
	 * 令其等于 0 得 {@code a = angle} —— 也就是「盘面角度<b>本身</b>
	 * 就是此刻位于指针下的那个盘面角度」。
	 *
	 * <p>这正好是 {@link #restAngle} 的逆运算：{@code restAngle} 由扇区求角度，
	 * 这里由角度求扇区。两者必须互为逆运算，否则会出现
	 * 「指针停在 A 格、却把 B 格提亮」—— 与当初「停下位置和奖项对不上」
	 * 是同一类错误，所以由自测钉死这组往返一致性。
	 *
	 * @param table 扇区表
	 * @param angle 盘面旋转角度（度，可超出 {@code [0, 360)}）
	 * @return 扇区下标；表为空时返回 {@link WheelConstants#NO_SECTOR}
	 */
	public static int sectorAt(WheelTable table, float angle) {
		if (table == null || table.size() == 0) {
			return WheelConstants.NO_SECTOR;
		}

		float a = normalize(angle);
		for (int i = 0; i < table.size(); i++) {
			float start = table.startAngle(i);
			float end = table.endAngle(i);
			if (a >= start && a < end) {
				return i;
			}
		}

		// 浮点误差可能让 a 恰好落在 360 与 0 的缝里（或末扇区的端点外），
		// 兜底给最后一格 —— 它本来就覆盖到 360°
		return table.size() - 1;
	}

	/**
	 * 本次转动要转的整圈数。
	 *
	 * <p><b>由抽奖序号确定性推导</b>，而不是掷随机数：所有客户端拿到同一个序号，
	 * 必然算出同一个圈数，盘面才会完全同步。
	 *
	 * @param spinSequence 抽奖序号
	 * @return {@code [MIN_FULL_TURNS, MIN_FULL_TURNS + EXTRA_TURNS]} 之间的整数
	 */
	public static int fullTurns(int spinSequence) {
		// Knuth 乘法散列 + 高位回灌：相邻序号（1,2,3...）会散到差异很大的圈数，
		// 否则玩家会发现「每次都是转 5 圈」。
		int hash = spinSequence * 0x9E3779B1;
		hash ^= (hash >>> 16);
		return MIN_FULL_TURNS + Math.floorMod(hash, EXTRA_TURNS + 1);
	}

	// ---------------------------------------------------------------- 转动角

	/**
	 * 转动过程中的盘面角度。
	 *
	 * <p>{@code progress >= 1} 时<b>精确</b>返回落点角，不依赖缓动函数的浮点误差 ——
	 * 否则指针可能停在两个扇区的分界线上，玩家看不出到底中了哪个。
	 *
	 * @param table        扇区表
	 * @param fromSector   上一次结果的扇区；无上次结果时传 {@link WheelConstants#NO_SECTOR}
	 * @param toSector     本次结果的扇区
	 * @param spinSequence 抽奖序号（决定圈数）
	 * @param fromJitter   上一次落点的抖动（起点角），保证开转瞬间盘面<b>不跳</b>
	 * @param jitter       本次落点抖动 {@code [-1, 1]}，由<b>服务端</b>定下并随包下发，
	 *                     保证所有客户端停在同一处
	 * @param elapsedTicks 已经过的游戏刻（可为小数，含帧插值）
	 * @param totalTicks   本次转动的总刻数；非正数时视为瞬间完成
	 * @return 盘面旋转角度（度），未归一化（含整圈，便于连续插值）
	 */
	public static float spinAngle(WheelTable table, int fromSector, float fromJitter,
			int toSector, int spinSequence, float jitter,
			float elapsedTicks, float totalTicks) {

		float to = restAngle(table, toSector, jitter);

		if (totalTicks <= 0.0f) {
			// 时长为 0：直接就是落点，不存在转动过程
			return to;
		}

		float progress = elapsedTicks / totalTicks;
		if (progress >= 1.0f) {
			// 已落地：精确返回落点，不受缓动浮点误差影响
			return to;
		}

		// 起点取「上一次实际停下的角度」——必须带上上一次的抖动，
		// 否则开转瞬间盘面会从上次的落点「跳」回扇区正中（宽扇区可达 40°）。
		float from = restAngle(table, fromSector, fromJitter);

		if (progress <= 0.0f) {
			// 还没开始：停在起点
			return from;
		}

		// 顺时针方向从 from 走到 to 需要补的角度，落在 [0, 360)
		float delta = to - from;
		delta = normalize(delta);

		// 总行程 = 整圈 + 补角，再套缓动（先快后慢，末尾自然减速）
		float travel = fullTurns(spinSequence) * 360.0f + delta;
		return from + travel * easeOutCubic(progress);
	}

	/** 不带抖动的转动角（起止都取扇区中点） */
	public static float spinAngle(WheelTable table, int fromSector, int toSector, int spinSequence,
			float elapsedTicks, float totalTicks) {
		return spinAngle(table, fromSector, 0.0f, toSector, spinSequence, 0.0f, elapsedTicks, totalTicks);
	}

	// ---------------------------------------------------------------- 朝向

	/**
	 * 盘面朝向 → 绕 Y 轴的旋转角度。
	 *
	 * <p><b>为什么放在这里而不是渲染器里</b>：这是一个纯角度约定，
	 * 和 {@link #restAngle} 是同一套「0° 在正上方」的世界观；
	 * 放在共享类里就能被服务端自测覆盖 —— 朝向画反属于
	 * 「只有联机时玩家才发现」的那类 bug，能自动测到就不该只靠人眼。
	 *
	 * <h2>推导</h2>
	 * 盘面几何的局部法线是 <b>+Z（南）</b>，要让盘面朝向某方向，
	 * 就得把 +Z 转到那个方向。{@code RotationAxis.POSITIVE_Y.rotationDegrees(t)}
	 * 是绕 +Y 的右手旋转，把 +Z 映射到 {@code (sin t, 0, cos t)}：
	 * <ul>
	 *   <li>0° → {@code (0, 0, 1)} = 南</li>
	 *   <li>90° → {@code (1, 0, 0)} = 东</li>
	 *   <li>180° → {@code (0, 0, -1)} = 北</li>
	 *   <li>270° → {@code (-1, 0, 0)} = 西</li>
	 * </ul>
	 *
	 * <p><b>曾经把东、西写反</b>（WEST=90 / EAST=270），表现为 X 轴方向放置时
	 * 盘面朝着玩家对面。南北两向恰好不受影响：0° 与 180° 互为反角，
	 * 交换后仍是自己 —— 这正是「Z 轴正常、X 轴镜像」那个现象的由来。
	 *
	 * @param facing 朝向；竖直方向或 null 时退回 0°（南）
	 * @return 旋转角度（度）
	 */
	public static float yawOf(Direction facing) {
		if (facing == null) {
			return 0.0f;
		}
		return switch (facing) {
			case SOUTH -> 0.0f;
			case EAST -> 90.0f;
			case NORTH -> 180.0f;
			case WEST -> 270.0f;
			// 竖直朝向不该出现（放置时只取水平方向），兜底给 0
			default -> 0.0f;
		};
	}

	// ---------------------------------------------------------------- 工具

	/**
	 * 缓动函数：先快后慢，末尾自然减速停下 —— 转盘的手感就来自这里。
	 * <p>{@code 1 - (1-t)^3}
	 */
	public static float easeOutCubic(float t) {
		float inv = 1.0f - t;
		return 1.0f - inv * inv * inv;
	}

	/**
	 * 把角度归一化到 {@code [0, 360)}。
	 * <p>对负数与超过 360 的输入都成立（{@code %} 在 Java 里对负数返回负值，
	 * 直接用它会让角度变成负的，进而让盘面反向转）。
	 */
	public static float normalize(float angle) {
		float result = angle % 360.0f;
		if (result < 0.0f) {
			result += 360.0f;
		}
		return result;
	}
}
