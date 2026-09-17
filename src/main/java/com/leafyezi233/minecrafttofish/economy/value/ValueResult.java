package com.leafyezi233.minecrafttofish.economy.value;

import net.minecraft.util.Identifier;

/**
 * 一次价值查询的结果，带上命中的来源，方便命令里显示"这个价是从哪来的"。
 *
 * @param value     价值，0 表示无价值
 * @param source    命中的来源
 * @param matchedId 命中的物品 id / 标签 id；兜底与未命中时为 null
 */
public record ValueResult(long value, ValueSource source, Identifier matchedId) {

	/** 无价值（未登记且未开启兜底） */
	public static final ValueResult NONE = new ValueResult(0L, ValueSource.NONE, null);

	/** 价值来源，优先级从高到低 */
	public enum ValueSource {
		/** 运行时覆盖（/fishvalue set），优先级最高，重启失效 */
		RUNTIME,
		/** 数据包里登记的精确物品价值 */
		ITEM,
		/** 数据包里登记的物品标签价值 */
		TAG,
		/** 兜底公式（需在配置里开启） */
		FALLBACK,
		/** 未登记 */
		NONE
	}

	public boolean hasValue() {
		return value > 0L;
	}

	/** 来源的可读描述，用于命令输出 */
	public String sourceName() {
		return switch (source) {
			case RUNTIME -> "runtime";
			case ITEM -> "item";
			case TAG -> "tag:" + matchedId;
			case FALLBACK -> "fallback";
			case NONE -> "none";
		};
	}
}
