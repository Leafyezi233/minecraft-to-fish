package com.leafyezi233.minecrafttofish.economy.config;

/**
 * 配置文件里的单个转盘扇区（纯数据，Gson 直接按字段名序列化）。
 *
 * <p>刻意与运行时的 {@code WheelSector} record 分开：
 * 配置类是「玩家写什么」，运行类是「校验后确定可用的规则」。
 * 中间由 {@code WheelTable.build} 完成校验与回退，两者不混用。
 *
 * <p>字段全部为 public 且不设 final，Gson 才能反序列化。
 */
public final class WheelSectorConfig {

	/** 中奖倍率；0 = 未中奖（鱼消失、无返还）。负数为非法，会导致整表回退 */
	public int multiplier = 0;

	/** 抽取权重，必须 &gt; 0；只有相对大小有意义 */
	public int weight = 0;

	/** 扇区颜色（ARGB）；0 = 未指定，由调色板自动分配 */
	public int color = 0;

	/** 语言键后缀，如 "x2" / "lose"；留空按倍率自动生成 */
	public String labelKey = "";

	/** Gson 反序列化用 */
	public WheelSectorConfig() {
	}

	/** 供默认值构造使用 */
	public WheelSectorConfig(int multiplier, int weight, int color, String labelKey) {
		this.multiplier = multiplier;
		this.weight = weight;
		this.color = color;
		this.labelKey = labelKey;
	}
}
