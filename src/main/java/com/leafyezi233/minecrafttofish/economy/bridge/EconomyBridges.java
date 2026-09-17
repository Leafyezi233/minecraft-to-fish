package com.leafyezi233.minecrafttofish.economy.bridge;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 经济后端选择器：决定当前实际使用哪个 {@link EconomyBridge}。
 *
 * <h2>选择流程</h2>
 * <ol>
 *   <li>配置里 {@code preferredBridge} 指向的后端存在且 {@code isAvailable()} → 用它</li>
 *   <li>否则尝试自动探测外部后端（仅在 {@code useExternalEconomyIfPresent} 开启时）</li>
 *   <li>都不可用 → 降级到内置钱包，并记一次 WARN</li>
 * </ol>
 *
 * <p><b>永不失败</b>：内置钱包永远可用，所以任何情况下都能拿到一个可用后端。
 * 所有对外部后端的调用都包在 try/catch 里，外部 API 出错只会降级，不会崩服。
 */
public final class EconomyBridges {

	/** 当前生效的后端；初始化后永不为 null */
	private static volatile EconomyBridge active;

	/** 是否已经打印过降级警告，避免刷屏 */
	private static volatile boolean warnedAboutFallback = false;

	private EconomyBridges() {
	}

	/** 装配：注册内置钱包并完成首次选择 */
	public static void init() {
		InternalEconomyBridge internal = new InternalEconomyBridge();
		EconomyBridgeRegistry.registerInternal(internal);
		active = internal;
		select();
	}

	/**
	 * 按当前配置重新选择后端。
	 * <p>配置重载后可再次调用。
	 */
	public static void select() {
		EconomyConfig cfg = EconomyConfig.get();
		InternalEconomyBridge internal = EconomyBridgeRegistry.internal();

		// 1) 配置指定的后端
		String preferred = cfg.preferredBridge;
		if (preferred != null && !preferred.isEmpty() && !InternalEconomyBridge.ID.equals(preferred)) {
			EconomyBridge candidate = EconomyBridgeRegistry.get(preferred);
			if (candidate == null) {
				MyMod.LOGGER.warn("[economy] 配置指定的经济后端 '{}' 未注册，降级到内置钱包", preferred);
			} else if (!safeIsAvailable(candidate)) {
				MyMod.LOGGER.warn("[economy] 经济后端 '{}' 当前不可用，降级到内置钱包", preferred);
			} else {
				active = candidate;
				MyMod.LOGGER.info("[economy] 当前经济后端：{}（来自配置 preferredBridge）", candidate.id());
				return;
			}
		}

		// 2) 自动探测外部后端
		if (cfg.useExternalEconomyIfPresent) {
			EconomyBridge detected = detectExternal();
			if (detected != null) {
				active = detected;
				MyMod.LOGGER.info("[economy] 当前经济后端：{}（自动探测）", detected.id());
				return;
			}
		}

		// 3) 内置钱包兜底
		active = internal;
		if (!warnedAboutFallback) {
			MyMod.LOGGER.info("[economy] 当前经济后端：internal（内置钱包）");
			warnedAboutFallback = true;
		}
	}

	/** 自动探测：优先 Common Economy，其次是其他模组注册进来的桥 */
	private static EconomyBridge detectExternal() {
		EconomyBridge common = CommonEconomyBridge.createIfPresent();
		if (common != null && safeIsAvailable(common)) {
			// 探测成功才注册，避免占用 id 导致后续无法注册
			if (EconomyBridgeRegistry.get(common.id()) == null) {
				EconomyBridgeRegistry.register(common);
			}
			return common;
		}
		return null;
	}

	/** 当前生效的后端；未初始化时返回内置钱包（可能为 null，调用方需容忍） */
	public static EconomyBridge active() {
		return active;
	}

	/** 当前后端 id，未初始化返回 "internal" */
	public static String activeId() {
		EconomyBridge bridge = active;
		return bridge == null ? InternalEconomyBridge.ID : bridge.id();
	}

	// ---------------------------------------------------------------- 安全包装

	/** 包一层 try/catch 的可用性检查 —— 外部实现抛异常时视为不可用 */
	private static boolean safeIsAvailable(EconomyBridge bridge) {
		try {
			return bridge.isAvailable();
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 后端 '{}' 的 isAvailable() 抛异常，视为不可用：{}",
					bridge.id(), t.toString());
			return false;
		}
	}

	/** 查询余额；任何异常都降级为 0 并记 WARN */
	public static long getBalance(ServerPlayerEntity player) {
		EconomyBridge bridge = active;
		if (bridge == null) {
			return 0L;
		}
		try {
			return bridge.getBalance(player);
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 后端 '{}' 查询余额失败：{}", bridge.id(), t.toString());
			return 0L;
		}
	}

	/** 加钱；异常返回 false */
	public static boolean deposit(ServerPlayerEntity player, long amount, String reason) {
		EconomyBridge bridge = active;
		if (bridge == null) {
			return false;
		}
		try {
			return bridge.deposit(player, amount, reason);
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 后端 '{}' 加钱失败：{}", bridge.id(), t.toString());
			return false;
		}
	}

	/** 扣钱；异常返回 false */
	public static boolean withdraw(ServerPlayerEntity player, long amount, String reason) {
		EconomyBridge bridge = active;
		if (bridge == null) {
			return false;
		}
		try {
			return bridge.withdraw(player, amount, reason);
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 后端 '{}' 扣钱失败：{}", bridge.id(), t.toString());
			return false;
		}
	}

	/** 格式化金额；异常时退回简单拼接 */
	public static String format(long amount) {
		EconomyBridge bridge = active;
		if (bridge == null) {
			return amount + " " + EconomyConfig.get().currencyName;
		}
		try {
			return bridge.format(amount);
		} catch (Throwable t) {
			return amount + " " + EconomyConfig.get().currencyName;
		}
	}
}
