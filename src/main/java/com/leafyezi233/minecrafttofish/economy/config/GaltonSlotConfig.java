package com.leafyezi233.minecrafttofish.economy.config;

/**
 * 配置文件里的单个高尔顿板槽位（纯数据，Gson 直接按字段名序列化）。
 *
 * <p>刻意与运行时的 {@code GaltonSlot} record 分开：
 * 配置类是「玩家写什么」，运行类是「校验后确定可用的规则」。
 * 中间由 {@code GaltonBoard.build} 完成校验与回退，两者不混用。
 *
 * <p><b>⚠️ 没有 weight 字段</b>：高尔顿板的概率来自
 * {@code Binomial(槽位数 - 1, galtonPegRightChance)}，不是逐槽指定的权重。
 * 槽位在板上的宽度一律相等，这是几何决定的，配不了。
 *
 * <p>字段全部为 public 且不设 final，Gson 才能反序列化。
 */
public final class GaltonSlotConfig {

	/** 中奖倍率；0 = 未中奖（鱼消失、无返还）。负数为非法，会导致整板回退 */
	public int multiplier = 0;

	/** 槽位颜色（ARGB）；0 = 未指定，由调色板自动分配 */
	public int color = 0;

	/** 语言键后缀，如 "x10" / "lose"；留空按倍率自动生成 */
	public String labelKey = "";

	/** Gson 反序列化用 */
	public GaltonSlotConfig() {
	}

	/** 供默认值构造使用 */
	public GaltonSlotConfig(int multiplier, int color, String labelKey) {
		this.multiplier = multiplier;
		this.color = color;
		this.labelKey = labelKey;
	}
}
