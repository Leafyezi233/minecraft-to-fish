package com.leafyezi233.minecrafttofish.economy.bridge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leafyezi233.minecrafttofish.MyMod;

/**
 * 经济后端注册表（对外 SPI 入口）。
 *
 * <p>其他模组在 {@code onInitialize()} 里调用
 * {@link #register(EconomyBridge)} 即可把自己的经济系统接进来，
 * 之后在配置里把 {@code preferredBridge} 设成对应 id 就会优先使用。
 *
 * <p>注册顺序不影响结果：{@link EconomyBridges} 选择后端时按
 * 配置指定的 id 查找，找不到或不可用才降级。
 */
public final class EconomyBridgeRegistry {

	/** 已注册的桥：id -> 实例（保持插入顺序，便于日志展示） */
	private static final Map<String, EconomyBridge> BRIDGES = new LinkedHashMap<>();

	/** 内置钱包始终兜底，单独持有引用 */
	private static InternalEconomyBridge internal;

	private EconomyBridgeRegistry() {
	}

	/**
	 * 注册一个经济后端。
	 *
	 * @return 是否注册成功；id 已存在时返回 false（不覆盖已有实现）
	 */
	public static boolean register(EconomyBridge bridge) {
		if (bridge == null || bridge.id() == null || bridge.id().isEmpty()) {
			MyMod.LOGGER.warn("[economy] 忽略非法的经济后端注册：{}", bridge);
			return false;
		}
		if (BRIDGES.containsKey(bridge.id())) {
			MyMod.LOGGER.warn("[economy] 经济后端 id 重复，已忽略：{}", bridge.id());
			return false;
		}
		BRIDGES.put(bridge.id(), bridge);
		MyMod.LOGGER.info("[economy] 已注册经济后端：{}", bridge.id());
		return true;
	}

	/** 注册内置钱包（模组自己初始化时调用） */
	static void registerInternal(InternalEconomyBridge bridge) {
		internal = bridge;
		BRIDGES.put(bridge.id(), bridge);
	}

	/** 按 id 查找后端 */
	public static EconomyBridge get(String id) {
		return BRIDGES.get(id);
	}

	/** 内置钱包（永不为 null，初始化后） */
	public static InternalEconomyBridge internal() {
		return internal;
	}

	/** 所有已注册后端的 id，用于命令补全与日志 */
	public static List<String> ids() {
		return new ArrayList<>(BRIDGES.keySet());
	}
}
