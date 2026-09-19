package com.leafyezi233.minecrafttofish.galton;

/**
 * 高尔顿板底部的一个槽位定义。
 *
 * <p><b>不可变</b>：配置加载后构造一次，之后只读。{@link GaltonBoard} 负责校验、
 * 归一化，并按二项分布算出每个槽位被砸中的概率。
 *
 * <h2>⚠️ 这里<b>没有</b> weight 字段，和转盘的 {@code WheelSector} 不同</h2>
 * 转盘的扇区带权重，概率由权重<b>直接指定</b>（权重比例 = 角度比例 = 概率）。
 * 高尔顿板的概率<b>不是配置项，而是几何形状的数学后果</b>：
 * 球在每一排钉子上独立地左右弹，最后落在哪个槽位服从
 * {@code Binomial(rows, pegRightChance)}。
 * <p>也就是说，服主配不出「第 3 个槽位占 40%」这种表 ——
 * 他能调的只有<b>倍率</b>（赔率）和 {@code pegRightChance}（整条分布的偏斜）。
 * 这正是两种游戏在玩法上的根本区别：转盘是「按权重抽奖」，
 * 高尔顿板是「物理过程的概率」。
 *
 * <h2>⚠️ 允许重复倍率（与转盘不同）</h2>
 * 转盘曾因「两个扇区倍率相同」而出现「服务端抽中第 3 个 ×2、客户端指针停在 0 号 ×2」
 * 的错位，最后靠<b>传递下标</b>而不是倍率解决。
 * 高尔顿板天生没有这个问题：服务端传的是<b>整条路径</b>，
 * 槽位由路径数出来（见 {@link GaltonPath#slotOf}），
 * 所以「两个槽位倍率相同」不产生任何歧义，不需要额外的下标字段。
 *
 * @param multiplier 中奖倍率；<b>0 表示未中奖</b>（鱼消失、无返还）。必须 &gt;= 0
 * @param color      槽位填充色（ARGB，如 {@code 0xFFD4AF37}）
 * @param labelKey   语言键后缀，用于客户端显示槽位文字与结果提示
 */
public record GaltonSlot(int multiplier, int color, String labelKey) {

	/** 未中奖槽位的语言键后缀 */
	public static final String LABEL_LOSE = "lose";

	public GaltonSlot {
		if (multiplier < 0) {
			throw new IllegalArgumentException("槽位倍率不能为负数：" + multiplier);
		}
		if (color == 0) {
			// 完全透明的槽位等于没画，给个兜底色而不是抛异常
			color = 0xFF808080;
		}
		if (labelKey == null || labelKey.isEmpty()) {
			labelKey = multiplier <= 0 ? LABEL_LOSE : ("x" + multiplier);
		}
	}

	/** 是否为中奖槽位（倍率 &gt; 0） */
	public boolean isWin() {
		return multiplier > 0;
	}

	/** 完整语言键，例如 {@code galton.minecraft_to_fish.slot.x10} */
	public String labelTranslationKey() {
		return "galton.minecraft_to_fish.slot." + labelKey;
	}
}
