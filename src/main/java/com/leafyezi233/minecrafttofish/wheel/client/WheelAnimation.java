package com.leafyezi233.minecrafttofish.wheel.client;

import com.leafyezi233.minecrafttofish.wheel.WheelTable;

/**
 * 转盘旋转动画的状态机（<b>仅客户端</b>）。
 *
 * <h2>为什么动画由「服务端序号」驱动</h2>
 * 点「开始」时不立刻播动画，而是等服务端把「抽奖序号」属性推回来才播。
 * 这样服务端拒绝（空槽 / 非模组鱼 / 无价值 / 冷却中）时不会出现「客户端白转一圈」的假象。
 * 代价是一次网络往返，换来的是结果完全不可伪造。
 *
 * <h2>落点由服务端倍率决定</h2>
 * 缓动目标角度取「本地扇区表里第一个倍率匹配的扇区中点」。
 * 即使客户端配置与服务端不一致，指针也一定停在和服务端结果一致的位置。
 */
public final class WheelAnimation {

	/** 缓动时长兜底值（毫秒），配置为 0 或负数时使用 */
	private static final long DEFAULT_DURATION_MS = 2000L;

	/** 至少转几整圈，保证有「转起来」的观感 */
	private static final int MIN_FULL_TURNS = 5;

	/** 每次抽奖多转的圈数上限（随机，让每次手感略有不同） */
	private static final int EXTRA_TURNS = 3;

	/** 已播放到的抽奖序号；与服务端属性比较来判断"有新结果" */
	private int playedSequence = 0;

	/** 动画开始时间（毫秒） */
	private long startTime = 0L;

	/** 动画时长（毫秒） */
	private long duration = DEFAULT_DURATION_MS;

	/** 起始角度（度） */
	private float fromAngle = 0.0f;

	/** 目标角度（度），已含整圈 */
	private float toAngle = 0.0f;

	/** 本次结果的倍率 */
	private int resultMultiplier = 0;

	/** 当前是否正在播放 */
	private boolean spinning = false;

	/** 动画结束后停留的角度 */
	private float restingAngle = 0.0f;

	/**
	 * 根据服务端序号与倍率同步动画状态。
	 *
	 * @param serverSequence   服务端当前的抽奖序号
	 * @param serverMultiplier 服务端本次结果的倍率
	 * @param table            本地扇区表（用于把倍率换算成角度）
	 * @param durationMs       配置的动画时长
	 * @return true 表示本次调用启动了新动画
	 */
	public boolean sync(int serverSequence, int serverMultiplier, WheelTable table, int durationMs) {
		if (serverSequence <= playedSequence) {
			// 没有新结果；正在播的动画继续播
			return false;
		}

		playedSequence = serverSequence;
		resultMultiplier = serverMultiplier;

		// 倍率 → 落点角度。找不到匹配扇区时退回 0°（指针指向正上方）
		int index = table.indexOfMultiplier(serverMultiplier);
		float targetSectorAngle = index >= 0 ? table.midAngle(index) : 0.0f;

		// 指针固定在正上方，所以要让「落点角度」转到 0° 位置：
		// 盘面旋转 angle 后，原本位于 a 的扇区会出现在 (a + angle) mod 360。
		// 想让 a 出现在 0°，需要 angle = -a (mod 360)，即 360 - a。
		float landingAngle = (360.0f - targetSectorAngle) % 360.0f;

		this.duration = durationMs > 0 ? durationMs : DEFAULT_DURATION_MS;
		this.fromAngle = restingAngle;

		// 从当前角度出发，转到「落点角度 + 若干整圈」
		int turns = MIN_FULL_TURNS + (int) (Math.random() * (EXTRA_TURNS + 1));
		float delta = landingAngle - (fromAngle % 360.0f);
		if (delta < 0.0f) {
			delta += 360.0f;
		}
		this.toAngle = fromAngle + turns * 360.0f + delta;

		this.startTime = System.currentTimeMillis();
		this.spinning = true;
		return true;
	}

	/** 推进动画；每帧调用一次 */
	public void update() {
		if (!spinning) {
			return;
		}
		if (System.currentTimeMillis() - startTime >= duration) {
			// 动画结束：精确落在目标角度，避免缓动误差导致指针偏一点
			spinning = false;
			restingAngle = toAngle % 360.0f;
			if (restingAngle < 0.0f) {
				restingAngle += 360.0f;
			}
		}
	}

	/** 当前盘面旋转角度（度） */
	public float currentAngle() {
		if (!spinning) {
			return restingAngle;
		}

		long elapsed = System.currentTimeMillis() - startTime;
		float progress = duration <= 0L ? 1.0f : (float) elapsed / (float) duration;
		if (progress > 1.0f) {
			progress = 1.0f;
		}
		if (progress < 0.0f) {
			progress = 0.0f;
		}

		return fromAngle + (toAngle - fromAngle) * easeOutCubic(progress);
	}

	/** 是否正在旋转 */
	public boolean isSpinning() {
		return spinning;
	}

	/** 本次结果倍率；尚未抽过奖时无意义 */
	public int resultMultiplier() {
		return resultMultiplier;
	}

	/** 是否已经抽过至少一次奖（用于决定要不要显示结果文字） */
	public boolean hasResult() {
		return playedSequence > 0;
	}

	/**
	 * 缓动函数：先快后慢，末尾自然减速停下 —— 转盘的手感就来自这里。
	 * <p>{@code 1 - (1-t)^3}
	 */
	public static float easeOutCubic(float t) {
		float inv = 1.0f - t;
		return 1.0f - inv * inv * inv;
	}
}
