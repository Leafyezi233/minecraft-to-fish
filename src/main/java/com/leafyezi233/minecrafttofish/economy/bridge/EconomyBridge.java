package com.leafyezi233.minecrafttofish.economy.bridge;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 经济后端抽象（SPI）。
 *
 * <p>本模组自带 {@link InternalEconomyBridge} 内置钱包，保证零依赖可运行；
 * 其他模组也可以通过 {@link EconomyBridgeRegistry#register(EconomyBridge)}
 * 注册自己的实现，把余额接到自家的经济系统上。
 *
 * <h2>实现约定</h2>
 * <ul>
 *   <li>所有方法都只在<b>服务端</b>被调用</li>
 *   <li>{@link #deposit} / {@link #withdraw} 必须保证原子性，失败返回 false 且不改动余额</li>
 *   <li>金额始终 &gt;= 0；具体货币单位由实现自行决定（本模组统一按"1 点 = 1 货币单位"）</li>
 *   <li>实现内部不要抛异常 —— 调用方已有兜底，但抛异常会被记为 WARN 并触发降级</li>
 * </ul>
 */
public interface EconomyBridge {

	/**
	 * 桥的唯一 id，例如 {@code "internal"}、{@code "common-economy"}。
	 * <p>用于配置项 {@code preferredBridge} 指定优先使用哪个后端。
	 */
	String id();

	/**
	 * 该后端当前是否可用（外部模组是否已加载、API 是否就绪）。
	 * <p>返回 false 时调用方会跳过它并尝试下一个候选。
	 */
	boolean isAvailable();

	/** 查询玩家余额；玩家无账户时返回 0 */
	long getBalance(ServerPlayerEntity player);

	/** 余额是否足够 */
	default boolean canAfford(ServerPlayerEntity player, long amount) {
		return amount >= 0L && getBalance(player) >= amount;
	}

	/**
	 * 加钱。
	 *
	 * @param reason 变动原因，便于外部经济模组记账（例如 {@code "minecraft_to_fish:sell"}）
	 * @return 是否成功
	 */
	boolean deposit(ServerPlayerEntity player, long amount, String reason);

	/**
	 * 扣钱。
	 *
	 * @return 是否成功；余额不足必须返回 false 且不扣款
	 */
	boolean withdraw(ServerPlayerEntity player, long amount, String reason);

	/** 把金额格式化成可读文本，例如 {@code "120 渔币"} */
	String format(long amount);
}
