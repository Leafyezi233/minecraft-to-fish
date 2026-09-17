package com.leafyezi233.minecrafttofish.economy.bridge;

import com.leafyezi233.minecrafttofish.economy.InternalEconomyState;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 内置钱包后端 —— 模组独立运行的保证。
 *
 * <p>余额存在存档里（见 {@link InternalEconomyState}），不依赖任何外部模组，
 * 单人/多人服务器开箱即用。任何外部经济后端不可用时都会降级到这里。
 */
public class InternalEconomyBridge implements EconomyBridge {

	/** 桥 id，配置里 {@code preferredBridge: "internal"} 即使用本后端 */
	public static final String ID = "internal";

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean isAvailable() {
		// 内置钱包永远可用
		return true;
	}

	@Override
	public long getBalance(ServerPlayerEntity player) {
		return state(player).getBalance(player.getUuid());
	}

	@Override
	public boolean deposit(ServerPlayerEntity player, long amount, String reason) {
		if (amount <= 0L) {
			return amount == 0L;
		}
		state(player).add(player.getUuid(), amount);
		return true;
	}

	@Override
	public boolean withdraw(ServerPlayerEntity player, long amount, String reason) {
		if (amount <= 0L) {
			return amount == 0L;
		}
		return state(player).subtract(player.getUuid(), amount);
	}

	@Override
	public String format(long amount) {
		return EconomyConfig.get().format(amount);
	}

	/** 取当前存档的经济数据 */
	private static InternalEconomyState state(ServerPlayerEntity player) {
		return InternalEconomyState.get(player.getServer());
	}
}
