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

	// ---------------------------------------------------------------- 配置项

	/** 货币显示名称，用于命令反馈与 tooltip */
	public String currencyName = "渔币";

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

	/** 是否启用 /fishvalue sell 卖鱼命令 */
	public boolean sellCommandEnabled = true;

	/** 是否输出调试日志 */
	public boolean debugLogging = false;

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
	 * 旧配置兼容钩子。
	 * 目前没有历史版本需要迁移，留空占位；后续改字段名时在这里补默认值。
	 */
	private void migrate() {
		if (currencyName == null || currencyName.isEmpty()) {
			currencyName = "渔币";
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
	}

	/** 金额格式化："120 渔币" */
	public String format(long amount) {
		return amount + " " + currencyName;
	}
}
