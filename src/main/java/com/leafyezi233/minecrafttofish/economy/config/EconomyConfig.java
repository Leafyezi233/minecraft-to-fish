package com.leafyezi233.minecrafttofish.economy.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import com.leafyezi233.minecrafttofish.MyMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * 经济系统配置（config/minecraft_to_fish.json）。
 * <p>手写 Gson 读写，不引入 Cloth Config / AutoConfig 等外部依赖。
 * 首次运行自动生成默认文件；解析失败时保留默认值并打 ERROR 日志，不影响游戏启动。
 * <p><b>注意</b>：本类字段全部为 public，Gson 直接按字段名序列化。
 * 重命名字段会导致旧配置文件丢值，改字段名时请在 {@link #migrate()} 里做兼容。
 */
public final class EconomyConfig {

	private static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.disableHtmlEscaping()
			.create();

	/** 当前生效的配置实例 */
	private static volatile EconomyConfig instance = new EconomyConfig();

	/** 配置文件路径，load() 时确定 */
	private static volatile Path configPath;

	// ---------------------------------------------------------------- 常量

	/** 货币默认名称 */
	public static final String DEFAULT_CURRENCY_NAME = "金币";

	// ---------------------------------------------------------------- 配置项

	/** 货币显示名称，用于命令反馈与 tooltip */
	public String currencyName = DEFAULT_CURRENCY_NAME;

	/** 首选经济桥 id（"internal" = 内置钱包）。不可用时自动降级到 internal */
	public String preferredBridge = "internal";

	/** 是否优先使用外部经济模组（装了 Common Economy 之类的模组时） */
	public boolean useExternalEconomyIfPresent = false;

	/** 外部经济 API 使用的货币 id */
	public String externalCurrencyId = "common-economy:default";

	/** 是否给未登记价值的物品套用兜底价值 */
	public boolean fallbackValueEnabled = false;

	/**
	 * 兜底价值。大于 0 时直接使用该数值；
	 * 等于 0 时按物品堆叠数推导（不可堆叠 = 16，可堆叠 = 4）。
	 */
	public long fallbackValue = 0L;

	/** 是否在物品 tooltip 里显示价值（Phase 4 客户端同步使用） */
	public boolean showValueInTooltip = true;

	/**
	 * 是否显示"手持模组鱼时的价值悬浮窗"（客户端）。
	 * <p>关闭后 HUD 完全不渲染；价值表同步本身不受影响。
	 */
	public boolean showValueHud = true;

	/** 是否启用 /fishvalue sell 卖鱼命令 */
	public boolean sellCommandEnabled = true;

	/** 是否输出调试日志 */
	public boolean debugLogging = false;

	// ------------------------------------------------------------ 渔轮转盘

	/** 是否启用渔轮转盘方块 */
	public boolean wheelEnabled = true;

	/**
	 * 转盘扇区表。<b>权重只有相对大小有意义</b>，扇区在盘面上占的角度按权重比例分配。
	 * <p>默认：×2 = 30、×5 = 5、未中奖 = 65，期望倍率 0.85。
	 * <p><b>期望值 &gt;= 1 不会回退</b> —— 想配成对玩家有利的也可以，只会打一条 WARN 日志。
	 * 只有结构上不可用的配置才回退到内置默认表，见 {@code WheelTable}：
	 * 空列表、负倍率、权重之和非正。
	 */
	public java.util.List<WheelSectorConfig> wheelSectors = defaultWheelSectors();

	/** 转盘旋转动画时长（毫秒） */
	public int wheelSpinDurationMs = 2000;

	/** 两次抽奖之间的冷却（毫秒），防止连点刷屏 */
	public int wheelSpinCooldownMs = 500;

	/**
	 * 单条鱼的价值上限；0 = 不限制。
	 * <p>只用于防止极端幸运时数字爆炸。
	 */
	public long wheelMaxValue = 0L;

	/** 是否只允许放入本模组的鱼（false = 任意物品都能放，按开始时才校验） */
	public boolean wheelRequireModFish = true;

	/** 内置默认扇区表：×2 = 30%、×5 = 5%、未中奖 = 65%，期望倍率 0.85 */
	private static java.util.List<WheelSectorConfig> defaultWheelSectors() {
		return new java.util.ArrayList<>(java.util.List.of(
				new WheelSectorConfig(2, 30, 0xFF4FA3D1, "x2"),
				new WheelSectorConfig(5, 5, 0xFFD4AF37, "x5"),
				new WheelSectorConfig(0, 65, 0xFF6B6B6B, "lose")));
	}

	// ---------------------------------------------------------------- 读写

	/** 当前配置（永不返回 null） */
	public static EconomyConfig get() {
		return instance;
	}

	/** 读取配置；文件不存在则生成默认文件 */
	public static void load() {
		configPath = FabricLoader.getInstance().getConfigDir().resolve(MyMod.MOD_ID + ".json");

		if (Files.exists(configPath)) {
			try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
				EconomyConfig parsed = GSON.fromJson(reader, EconomyConfig.class);
				if (parsed != null) {
					parsed.migrate();
					instance = parsed;
					MyMod.LOGGER.info("[economy] 已加载配置：{}", configPath);
				}
			} catch (IOException | JsonSyntaxException e) {
				MyMod.LOGGER.error("[economy] 配置读取失败，使用默认值：{}", e.toString());
			}
		} else {
			instance = new EconomyConfig();
			save();
			MyMod.LOGGER.info("[economy] 已生成默认配置：{}", configPath);
		}
	}

	/** 保存配置 */
	public static void save() {
		if (configPath == null) {
			return;
		}
		try {
			Files.createDirectories(configPath.getParent());
			try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			MyMod.LOGGER.error("[economy] 配置保存失败：{}", e.toString());
		}
	}

	/**
	 * 配置兜底钩子：把缺失或非法的字段补成默认值。
	 * <p>开发阶段不做前向兼容，字段改名/改默认值直接生效，不迁移旧值。
	 */
	private void migrate() {
		if (currencyName == null || currencyName.isEmpty()) {
			currencyName = DEFAULT_CURRENCY_NAME;
		}
		if (preferredBridge == null || preferredBridge.isEmpty()) {
			preferredBridge = "internal";
		}
		if (externalCurrencyId == null || externalCurrencyId.isEmpty()) {
			externalCurrencyId = "common-economy:default";
		}
		if (fallbackValue < 0L) {
			fallbackValue = 0L;
		}

		// --- 渔轮转盘 ---
		if (wheelSectors == null) {
			// 老配置文件没有这个字段，或玩家手写成了 null
			wheelSectors = defaultWheelSectors();
		}
		if (wheelSpinDurationMs < 0) {
			wheelSpinDurationMs = 0;
		}
		if (wheelSpinCooldownMs < 0) {
			wheelSpinCooldownMs = 0;
		}
		if (wheelMaxValue < 0L) {
			wheelMaxValue = 0L;
		}
	}

	/** 金额格式化："120 金币" */
	public String format(long amount) {
		return amount + " " + currencyName;
	}
}
