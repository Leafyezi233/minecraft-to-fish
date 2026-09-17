package com.leafyezi233.minecrafttofish.economy.bridge;

import java.lang.reflect.Method;
import java.util.UUID;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * 外部经济 API 的<b>反射</b>适配器（以 Common Economy API 为目标）。
 *
 * <h2>为什么用反射</h2>
 * 编译期依赖需要额外 maven 仓库与版本对齐，而外部 API 的方法签名在不同版本间有变动；
 * 反射做到<b>零新增依赖、零构建风险</b>，代价是缺少编译期校验 ——
 * 用 {@code FabricLoader.isModLoaded} 门控 + 启动自检日志 + 失败自动降级来弥补。
 *
 * <h2>调用链</h2>
 * <pre>
 * CommonEconomy.getAccount(player, currencyId)   -> Account
 * account.balance()                              -> long
 * account.increaseBalance(amount) / decreaseBalance(amount)
 * </pre>
 *
 * <p>任何一步反射失败（类不存在、方法名不符、签名不匹配）都只记 WARN，
 * 由 {@link EconomyBridges} 降级到内置钱包，绝不崩服。
 */
public class CommonEconomyBridge implements EconomyBridge {

	/** 桥 id */
	public static final String ID = "common-economy";

	/** Common Economy 的主类名 */
	private static final String CLASS_COMMON_ECONOMY = "eu.pb4.common.economy.api.CommonEconomy";

	/** 账户接口类名 */
	private static final String CLASS_ACCOUNT = "eu.pb4.common.economy.api.EconomyAccount";

	/** 目标模组 id（用于 isModLoaded 门控） */
	private static final String[] TARGET_MOD_IDS = {"common-economy", "commoneconomy"};

	// 反射出来的成员，createIfPresent() 里一次性解析
	private final Method getAccountMethod;
	private final Method balanceMethod;
	private final Method increaseMethod;
	private final Method decreaseMethod;

	private CommonEconomyBridge(Method getAccountMethod, Method balanceMethod,
			Method increaseMethod, Method decreaseMethod) {
		this.getAccountMethod = getAccountMethod;
		this.balanceMethod = balanceMethod;
		this.increaseMethod = increaseMethod;
		this.decreaseMethod = decreaseMethod;
	}

	/**
	 * 若 Common Economy 已加载且 API 结构符合预期，则构造桥实例；否则返回 null。
	 * <p>本方法只做<b>探测</b>，不注册；由 {@link EconomyBridges} 决定是否使用。
	 */
	public static CommonEconomyBridge createIfPresent() {
		boolean loaded = false;
		for (String modId : TARGET_MOD_IDS) {
			if (FabricLoader.getInstance().isModLoaded(modId)) {
				loaded = true;
				break;
			}
		}
		if (!loaded) {
			return null;
		}

		try {
			Class<?> commonEconomy = Class.forName(CLASS_COMMON_ECONOMY);
			Class<?> account = Class.forName(CLASS_ACCOUNT);

			// getAccount(ServerPlayerEntity, Identifier) —— 用参数个数与类型名宽松匹配
			Method getAccount = findMethod(commonEconomy, "getAccount", 2);
			Method balance = findMethod(account, "balance", 0);
			Method increase = findMethod(account, "increaseBalance", 1);
			Method decrease = findMethod(account, "decreaseBalance", 1);

			if (getAccount == null || balance == null || increase == null || decrease == null) {
				MyMod.LOGGER.warn("[economy] 检测到 Common Economy，但 API 结构与预期不符"
						+ "（getAccount={}, balance={}, increase={}, decrease={}），已跳过",
						getAccount != null, balance != null, increase != null, decrease != null);
				return null;
			}

			MyMod.LOGGER.info("[economy] 检测到 Common Economy API，已构造反射适配器");
			return new CommonEconomyBridge(getAccount, balance, increase, decrease);
		} catch (ClassNotFoundException | LinkageError e) {
			MyMod.LOGGER.warn("[economy] 加载 Common Economy API 失败，已跳过：{}", e.toString());
			return null;
		}
	}

	/** 按名称与参数个数查找方法；找不到返回 null（不抛异常） */
	private static Method findMethod(Class<?> owner, String name, int paramCount) {
		for (Method method : owner.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == paramCount) {
				method.setAccessible(true);
				return method;
			}
		}
		return null;
	}

	@Override
	public String id() {
		return ID;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public long getBalance(ServerPlayerEntity player) {
		Object account = resolveAccount(player);
		if (account == null) {
			return 0L;
		}
		try {
			Object result = balanceMethod.invoke(account);
			return result instanceof Number number ? number.longValue() : 0L;
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 读取 Common Economy 余额失败：{}", t.toString());
			return 0L;
		}
	}

	@Override
	public boolean deposit(ServerPlayerEntity player, long amount, String reason) {
		if (amount <= 0L) {
			return amount == 0L;
		}
		Object account = resolveAccount(player);
		if (account == null) {
			return false;
		}
		try {
			increaseMethod.invoke(account, amount);
			return true;
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] Common Economy 加钱失败：{}", t.toString());
			return false;
		}
	}

	@Override
	public boolean withdraw(ServerPlayerEntity player, long amount, String reason) {
		if (amount <= 0L) {
			return amount == 0L;
		}
		Object account = resolveAccount(player);
		if (account == null) {
			return false;
		}
		// 先查余额，避免外部实现"允许负余额"导致刷钱
		if (getBalance(player) < amount) {
			return false;
		}
		try {
			decreaseMethod.invoke(account, amount);
			return true;
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] Common Economy 扣钱失败：{}", t.toString());
			return false;
		}
	}

	@Override
	public String format(long amount) {
		// 外部 API 的格式化方法版本差异大，直接用本模组的货币名，稳定可预期
		return EconomyConfig.get().format(amount);
	}

	/** 取玩家的账户对象；任何失败返回 null */
	private Object resolveAccount(ServerPlayerEntity player) {
		try {
			return getAccountMethod.invoke(null, player, currencyId());
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[economy] 获取 Common Economy 账户失败（玩家 {}）：{}",
					player.getGameProfile().getName(), t.toString());
			return null;
		}
	}

	/** 配置里的货币 id，解析失败时退回默认值 */
	private static Object currencyId() {
		String raw = EconomyConfig.get().externalCurrencyId;
		try {
			return new net.minecraft.util.Identifier(raw);
		} catch (Exception e) {
			MyMod.LOGGER.warn("[economy] 非法货币 id '{}'，使用 common-economy:default", raw);
			return new net.minecraft.util.Identifier("common-economy", "default");
		}
	}

	/** 便于日志：玩家 UUID 字符串 */
	@SuppressWarnings("unused")
	private static String describe(UUID id) {
		return id == null ? "null" : id.toString();
	}
}
