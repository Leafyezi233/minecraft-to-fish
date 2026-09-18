package com.leafyezi233.minecrafttofish.wheel;

/**
 * 转盘上的一个扇区定义。
 *
 * <p><b>不可变</b>：配置加载后构造一次，之后只读。{@link WheelTable} 负责把一组扇区
 * 校验、归一化，并算好每个扇区的角度范围供客户端绘制。
 *
 * @param multiplier 中奖倍率；<b>0 表示未中奖</b>（鱼消失、无返还）。必须 &gt;= 0
 * @param weight     抽取权重，必须 &gt; 0；只有相对大小有意义
 * @param color      扇区填充色（ARGB，如 {@code 0xFFD4AF37}）
 * @param labelKey   语言键后缀，用于客户端显示扇区文字与结果提示
 */
public record WheelSector(int multiplier, int weight, int color, String labelKey) {

	/** 未中奖扇区的语言键后缀 */
	public static final String LABEL_LOSE = "lose";

	public WheelSector {
		if (multiplier < 0) {
			throw new IllegalArgumentException("扇区倍率不能为负数：" + multiplier);
		}
		if (weight <= 0) {
			throw new IllegalArgumentException("扇区权重必须大于 0：" + weight);
		}
		if (color == 0) {
			// 完全透明的扇区等于没画，给个兜底色而不是抛异常
			color = 0xFF808080;
		}
		if (labelKey == null || labelKey.isEmpty()) {
			labelKey = multiplier <= 0 ? LABEL_LOSE : ("x" + multiplier);
		}
	}

	/** 是否为中奖扇区（倍率 &gt; 0） */
	public boolean isWin() {
		return multiplier > 0;
	}

	/** 完整语言键，例如 {@code wheel.minecraft_to_fish.sector.x2} */
	public String labelTranslationKey() {
		return "wheel.minecraft_to_fish.sector." + labelKey;
	}
}
