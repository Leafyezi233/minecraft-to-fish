package com.leafyezi233.minecrafttofish.economy.value;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

/**
 * 从数据包加载物品价值表：{@code data/<namespace>/fish_values/*.json}。
 *
 * <h2>文件格式</h2>
 * <pre>{@code
 * {
 *   "replace": false,
 *   "values": {
 *     "minecraft_to_fish:aggressive_fish": 45,
 *     "minecraft:cod": 10
 *   },
 *   "tags": {
 *     "#minecraft:fishes": 8
 *   }
 * }
 * }</pre>
 *
 * <ul>
 *   <li>{@code replace: true} 表示应用本文件前<b>清空</b>已累积的价值表（整合包整体重定价用）</li>
 *   <li>多个文件按 路径字典序 依次应用，后加载的覆盖先加载的</li>
 *   <li>同路径的文件按资源包优先级取（玩家数据包可覆盖模组内置值）</li>
 * </ul>
 *
 * <h2>容错</h2>
 * 单个文件解析失败只记 ERROR 日志并跳过，<b>不影响其他文件、不崩服</b>；
 * 若一个文件都没成功读到，价值表保持为空（配合兜底开关可让万物有价）。
 */
public final class ItemValueLoader implements SimpleSynchronousResourceReloadListener {

	/** 价值表在数据包里的目录名 */
	public static final String DIRECTORY = "fish_values";

	/** 资源重载监听器的唯一 id */
	private static final Identifier LISTENER_ID =
			new Identifier(MyMod.MOD_ID, "fish_values");

	private static final ItemValueLoader INSTANCE = new ItemValueLoader();

	private ItemValueLoader() {
	}

	/** 注册数据包重载监听（服务端数据，随 /reload 与开档触发） */
	public static void register() {
		ResourceManagerHelper.get(ResourceType.SERVER_DATA).registerReloadListener(INSTANCE);
	}

	@Override
	public Identifier getFabricId() {
		return LISTENER_ID;
	}

	/**
	 * 声明依赖原版标签加载器，保证校验标签时标签已经就绪
	 * （否则会把所有标签都误报为"不存在"）。
	 */
	@Override
	public Collection<Identifier> getFabricDependencies() {
		return List.of(ResourceReloadListenerKeys.TAGS);
	}

	@Override
	public void reload(ResourceManager manager) {
		Map<Identifier, Long> items = new LinkedHashMap<>();
		Map<Identifier, Long> tags = new LinkedHashMap<>();

		// findResources 返回 path -> Resource；path 形如 "minecraft_to_fish/fish_values/default.json"
		Map<Identifier, Resource> found = manager.findResources(
				DIRECTORY,
				id -> id.getPath().endsWith(".json"));

		// 按路径字典序稳定排序，保证加载顺序可预期（与资源包优先级无关）
		List<Identifier> paths = new ArrayList<>(found.keySet());
		paths.sort(Comparator.comparing(Identifier::toString));

		int ok = 0;
		int failed = 0;

		for (Identifier path : paths) {
			Resource resource = found.get(path);
			try (BufferedReader reader = resource.getReader()) {
				JsonElement root = JsonParser.parseReader(reader);
				if (!root.isJsonObject()) {
					MyMod.LOGGER.error("[economy] 价值文件根节点不是对象，已跳过：{}", path);
					failed++;
					continue;
				}
				applyFile(root.getAsJsonObject(), items, tags, path);
				ok++;
			} catch (IOException | JsonSyntaxException | IllegalStateException | NumberFormatException e) {
				MyMod.LOGGER.error("[economy] 价值文件解析失败，已跳过：{} —— {}", path, e.toString());
				failed++;
			}
		}

		ItemValueRegistry.setContents(items, tags);
		ItemValueRegistry.setLoadStats(ok, failed);

		// 校验标签是否真实存在：写错标签名（例如把 Fabric 约定标签 #c:iron_ingots
		// 误写成 #minecraft:iron_ingots）不会报错，只会静默失效，这里主动提示。
		validateTags(tags);

		if (failed > 0) {
			MyMod.LOGGER.warn("[economy] 价值表加载完成：{} 个文件成功、{} 个失败；共 {} 条价值",
					ok, failed, items.size() + tags.size());
		} else {
			MyMod.LOGGER.info("[economy] 价值表加载完成：{} 个文件，{} 条物品价值 + {} 条标签价值",
					ok, items.size(), tags.size());
		}

		if (EconomyConfig.get().debugLogging) {
			items.forEach((id, value) -> MyMod.LOGGER.info("[economy][debug] 物品 {} = {}", id, value));
			tags.forEach((id, value) -> MyMod.LOGGER.info("[economy][debug] 标签 #{} = {}", id, value));
		}
	}

	/**
	 * 校验标签 id 是否真实存在。
	 * <p>标签写错不会抛异常，只会让整条规则静默失效 —— 这类问题极难排查，
	 * 所以在加载时主动扫描并告警（只警告，不中断加载）。
	 */
	private static void validateTags(Map<Identifier, Long> tags) {
		// 已加载的物品标签集合，一次性取出避免每个标签都扫一遍注册表
		Set<Identifier> loaded;
		try {
			loaded = Registries.ITEM.streamTags()
					.map(TagKey::id)
					.collect(Collectors.toSet());
		} catch (Throwable t) {
			// 标签尚未就绪（极端情况）时不校验，避免误报
			MyMod.LOGGER.warn("[economy] 标签校验跳过：{}", t.toString());
			return;
		}

		for (Identifier id : tags.keySet()) {
			if (!loaded.contains(id)) {
				MyMod.LOGGER.warn("[economy] 标签 #{} 不存在或为空，该规则不会生效"
						+ "（提示：铁锭/金锭等请用 Fabric 约定标签 #c:iron_ingots）", id);
			}
		}
	}

	/** 应用单个文件的内容到累积表 */
	private static void applyFile(JsonObject root,
			Map<Identifier, Long> items,
			Map<Identifier, Long> tags,
			Identifier path) {
		// replace: true -> 先清空已累积的内容
		JsonElement replaceElement = root.get("replace");
		if (replaceElement != null && replaceElement.isJsonPrimitive()
				&& replaceElement.getAsJsonPrimitive().isBoolean()
				&& replaceElement.getAsBoolean()) {
			items.clear();
			tags.clear();
			if (EconomyConfig.get().debugLogging) {
				MyMod.LOGGER.info("[economy][debug] {} 声明 replace=true，已清空累积价值表", path);
			}
		}

		readSection(root.getAsJsonObject("values"), items, false, path);
		readSection(root.getAsJsonObject("tags"), tags, true, path);
	}

	/** 读取 values / tags 段 */
	private static void readSection(JsonObject section,
			Map<Identifier, Long> target,
			boolean tagSection,
			Identifier path) {
		if (section == null) {
			return;
		}
		for (Map.Entry<String, JsonElement> entry : section.entrySet()) {
			String rawKey = entry.getKey();
			JsonElement valueElement = entry.getValue();

			// 标签段允许写 "#minecraft:fishes" 或 "minecraft:fishes"，统一去掉 # 前缀
			String key = tagSection && rawKey.startsWith("#") ? rawKey.substring(1) : rawKey;

			Identifier id;
			try {
				id = new Identifier(key);
			} catch (Exception e) {
				MyMod.LOGGER.error("[economy] 非法 id，已跳过：{} -> {}（文件 {}）", rawKey, key, path);
				continue;
			}

			if (!valueElement.isJsonPrimitive() || !valueElement.getAsJsonPrimitive().isNumber()) {
				MyMod.LOGGER.error("[economy] 价值必须是数字，已跳过：{}（文件 {}）", rawKey, path);
				continue;
			}

			long value = valueElement.getAsLong();
			if (value < 0L) {
				MyMod.LOGGER.warn("[economy] 价值为负数，按 0 处理：{} = {}（文件 {}）", rawKey, value, path);
				value = 0L;
			}
			target.put(id, value);
		}
	}
}
