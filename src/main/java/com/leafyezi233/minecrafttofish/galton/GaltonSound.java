package com.leafyezi233.minecrafttofish.galton;

/**
 * 高尔顿板音效的<b>纯函数</b>部分：音高与音量。
 *
 * <h2>为什么单独抽一个类，而且一行 Minecraft 代码都不引用</h2>
 * 音高/音量是「手感」参数，最容易凭感觉乱调。抽成纯函数有两个好处：
 * <ol>
 *   <li><b>能被自测覆盖</b>。「音高是否随下落升高」「是否越出原版可用范围」
 *       这类问题靠耳朵听要听很多次，写成断言一次就钉死；</li>
 *   <li><b>两端共用一份</b>。服务端播落地音、客户端播撞钉音，
 *       若各写一份映射，调了一边忘了另一边就会出现「撞钉和落地不在一个调上」。</li>
 * </ol>
 * 本类<b>不引用任何 Minecraft 类型</b>（连 {@code SoundEvent} 都不引用），
 * 所以它天然在服务端与客户端都能加载 —— 音效的「选哪个音效」留给调用方，
 * 「用什么音高/音量」统一在这里算。
 *
 * <h2>音高为什么随进度升高</h2>
 * 球的纵向插值是 {@code t²}（自由落体，见 {@link GaltonPath#yFractionAt}），
 * 所以球<b>越掉越快</b>，撞钉越来越密。音高同时上行，
 * 听感上就是「越来越急」—— 与画面的加速对上。
 * 若音高不变，密集成一片的撞击声会糊成一团噪音。
 *
 * <h2>为什么没有「每颗钉子随机微调音高」</h2>
 * 想过给每颗钉子的音高加一点抖动，听起来不那么像机关枪。
 * 但算下来<b>不可行</b>：抖动幅度必须小于相邻两颗的音高差才不破坏单调性，
 * 而排数越多音高差越小 —— 24 排时相邻差只有 {@code 0.8/23 ≈ 0.035}，
 * 抖动就得压到 1% 以内，那个量级<b>根本听不出来</b>。
 * 要么破坏单调、要么等于没加，所以改为<b>按「本次下落」整体移调</b>
 * （见 {@link #dropTranspose}）：同一次下落内音高差不变，单调性天然成立，
 * 而不同次下落的整体音高略有不同，不会每次听起来一模一样。
 */
public final class GaltonSound {

	// ---------------------------------------------------------------- 撞钉

	/**
	 * 撞钉音的最<b>低</b>音高（第一颗钉子）。
	 *
	 * <p>取 0.80 而不是 1.0：给上行留出空间。最高 1.60 正好是它的两倍，
	 * 整个下行过程跨一个八度 —— 明显听得出来在升，又不会尖到刺耳。
	 */
	public static final float PEG_PITCH_LOW = 0.80f;

	/** 撞钉音的最<b>高</b>音高（最后一颗钉子） */
	public static final float PEG_PITCH_HIGH = 1.60f;

	/**
	 * 撞钉音量。
	 *
	 * <p>刻意<b>保持恒定</b>，不随进度变化。理由：音高在上行、撞击频率在变密，
	 * 已经是两个维度同时在加强；音量再跟着涨会让整段声音「膨」起来，
	 * 听起来像音量失控而不是球在加速。
	 *
	 * <p>0.65 而不是 1.0：一次下落要响 6~7 声，单声太响会盖住环境音。
	 * 注意这与旧实现的 {@code 0.25} <b>不可直接比较</b> ——
	 * 旧实现是「无衰减、贴着耳朵播」，那个 0.25 在耳边等于很响；
	 * 现在是有距离衰减的定位音，0.65 在近处才接近旧响度。
	 */
	public static final float PEG_VOLUME = 0.65f;

	/**
	 * 每次下落整体移调的最大比例。
	 *
	 * <p>{@code 0.06} 表示整体音高在 {@code ±6%} 内浮动（约一个半音），
	 * 足以让连续几次下落听起来不完全一样，又不会让人觉得「音效变了」。
	 *
	 * <p>它是<b>整体</b>乘上去的（同一次下落里每颗钉子乘同一个系数），
	 * 所以不会破坏音高的单调上行 —— 这一点由自测钉死。
	 */
	public static final float DROP_DETUNE = 0.06f;

	// ---------------------------------------------------------------- 开落

	/** 开落（松手放球）的音量 */
	public static final float START_VOLUME = 0.85f;

	/**
	 * 开落的音高。
	 *
	 * <p>取 0.70，<b>低于</b>第一颗钉子的 0.80：开落声比撞钉声更低沉，
	 * 听起来像「机关松开」，与随后清脆的撞钉声区分开。
	 * 旧实现用同一个 {@code BLOCK_NOTE_BLOCK_HAT} 音效，
	 * 开落与撞钉是同一个声音，玩家分不出「刚开始掉」和「砸到钉子了」。
	 */
	public static final float START_PITCH = 0.70f;

	// ---------------------------------------------------------------- 落地

	/** 中奖落地的音量 */
	public static final float WIN_VOLUME = 1.00f;

	/** 中奖落地的音高（原音，{@code ENTITY_PLAYER_LEVELUP} 本身就是上行音阶） */
	public static final float WIN_PITCH = 1.00f;

	/** 未中奖落地的音量 */
	public static final float LOSE_VOLUME = 0.85f;

	/**
	 * 未中奖落地的音高。
	 *
	 * <p>取 0.60 把 {@code BLOCK_NOTE_BLOCK_BASS} 压得更低，成为一声「闷响」。
	 * 与中奖的明亮上行音阶形成<b>听觉上的两极</b> ——
	 * 玩家不用看屏幕就知道结果，这是刻意的。
	 */
	public static final float LOSE_PITCH = 0.60f;

	// ---------------------------------------------------------------- 范围

	/**
	 * 原版音效可用的音高下限。
	 *
	 * <p>超出这个范围原版会做钳制，但钳制是「悄悄改掉你给的值」，
	 * 不如在这里显式夹住并让自测能检查到。
	 */
	public static final float MIN_PITCH = 0.5f;

	/** 原版音效可用的音高上限 */
	public static final float MAX_PITCH = 2.0f;

	private GaltonSound() {
	}

	/**
	 * 第 {@code pegIndex} 颗钉子的撞击音高。
	 *
	 * @param pegIndex     已撞过的钉子序号，<b>从 0 起</b>（0 = 第一颗）
	 * @param rows         钉子排数；非正数时按 1 处理
	 * @param dropSequence 本次下落的序号，用于整体移调
	 * @return 音高，已夹在 {@link #MIN_PITCH} 与 {@link #MAX_PITCH} 之间
	 */
	public static float pegPitch(int pegIndex, int rows, int dropSequence) {
		int n = Math.max(1, rows);
		int i = Math.max(0, Math.min(n - 1, pegIndex));

		// 只有一颗钉子时没有「上行」可言，直接取下限
		float t = n <= 1 ? 0.0f : i / (float) (n - 1);
		float base = PEG_PITCH_LOW + (PEG_PITCH_HIGH - PEG_PITCH_LOW) * t;

		return clampPitch(base * dropTranspose(dropSequence));
	}

	/** 第 {@code pegIndex} 颗钉子的撞击音量 */
	public static float pegVolume(int pegIndex, int rows) {
		// 参数留着是为了「以后想调成渐变时不用改签名」，
		// 但当前是恒定值 —— 理由见 PEG_VOLUME 的注释
		return PEG_VOLUME;
	}

	/**
	 * 本次下落的整体移调系数，落在 {@code [1 - DROP_DETUNE, 1 + DROP_DETUNE]}。
	 *
	 * <h2>为什么用下落序号而不是随机数</h2>
	 * 下落序号是<b>服务端下发</b>的（{@code DropSeq}），同一个下落在所有客户端上
	 * 取同一个值，所以每个玩家听到的移调完全一致 —— 联机时不会出现
	 * 「我听到的和队友听到的不是一个调」。
	 * 若改用 {@code Math.random()}，各客户端各掷各的，
	 * 而撞钉音本来就是<b>各客户端本地播</b>的，结果就是同一场下落每人听到的音高都不同。
	 *
	 * <h2>为什么用哈希而不是取模</h2>
	 * 取模（{@code seq % 7}）会让相邻的下落序号产生相邻的移调，
	 * 听感上像「音高在一格一格地爬」。乘法散列把相邻序号打散，
	 * 连续几次下落的移调听起来才是无规律的。
	 *
	 * @param dropSequence 下落序号
	 * @return 移调系数，恒为正
	 */
	public static float dropTranspose(int dropSequence) {
		// Knuth 乘法散列 + 高位回灌，与 WheelAngle.fullTurns 同一手法
		int hash = dropSequence * 0x9E3779B1;
		hash ^= (hash >>> 16);

		// 取 16 位映射到 [0, 1]，再线性拉到 [-1, 1]
		float unit = (hash & 0xFFFF) / 65535.0f;
		float signed = unit * 2.0f - 1.0f;

		return 1.0f + signed * DROP_DETUNE;
	}

	/** 落地音量 */
	public static float landingVolume(boolean win) {
		return win ? WIN_VOLUME : LOSE_VOLUME;
	}

	/** 落地音高 */
	public static float landingPitch(boolean win) {
		return win ? WIN_PITCH : LOSE_PITCH;
	}

	/** 把音高夹进原版可用范围 */
	public static float clampPitch(float pitch) {
		if (Float.isNaN(pitch)) {
			// NaN 参与比较永远为 false，不单独判会一路传下去
			return 1.0f;
		}
		return Math.max(MIN_PITCH, Math.min(MAX_PITCH, pitch));
	}
}
