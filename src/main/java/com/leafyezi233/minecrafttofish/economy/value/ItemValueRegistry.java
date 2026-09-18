package com.leafyezi233.minecrafttofish.economy.value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.value.ValueResult.ValueSource;
import com.leafyezi233.minecrafttofish.wheel.StackValueOverride;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

/**
 * 物品价值表（服务端权威）。
 *
 * <h2>解析优先级（从高到低）</h2>
 * <ol>
 *   <li><b>物品栈级覆盖</b> —— 写在物品自身 NBT 上（转盘加成），描述「这一个物品值多少」</li>
 *   <li>运行时覆盖 —— {@code /fishvalue set}，只存在内存里，重启失效</li>
 *   <li>精确物品价值 —— 数据包 {@code values} 段</li>
 *   <li>物品标签价值 —— 数据包 {@code tags} 段；多个标签同时命中时取<b>最大</b>值</li>
 *   <li>兜底公式 —— 需在配置里开启，默认关闭</li>
 *   <li>无价值（0）</li>
 * </ol>
 *
 * <p><b>注意第 1 项必须排在缓存之前</b>：{@link #CACHE} 按 {@link Item} 建键，
 * 描述的是「这个物品类型值多少」；而栈级覆盖描述的是「这一个物品值多少」。
 * 若把栈级覆盖放到缓存查询之后，两条带不同加成的同类鱼会互相污染。
 *
 * <p>标签归属是物品级别的，所以第 3~5 项的结果按 {@link Item} 缓存是安全的，
 * 数据包重载时整体清空。
 */
public final class ItemValueRegistry {

	/** 精确物品价值：物品 id -> 价值 */
	private static volatile Map<Identifier, Long> itemValues = Collections.emptyMap();

	/** 标签价值：标签 id（不含 #） -> 价值 */
	private static volatile Map<Identifier, Long> tagValues = Collections.emptyMap();

	/** 运行时覆盖：/fishvalue set 写入，优先级最高 */
	private static final Map<Item, Long> RUNTIME_OVERRIDES = new ConcurrentHashMap<>();

	/** 解析结果缓存 */
	private static final Map<Item, ValueResult> CACHE = new ConcurrentHashMap<>();

	/** 上次加载统计，供 /fishvalue reload 与日志显示 */
	private static volatile int loadedFileCount = 0;
	private static volatile int loadedValueCount = 0;
	private static volatile int failedFileCount = 0;

	private ItemValueRegistry() {
	}

	// ---------------------------------------------------------------- 查询

	/**
	 * 查询物品价值（主入口）。
	 *
	 * @param stack 待查物品；空栈返回 {@link ValueResult#NONE}
	 */
	public static ValueResult resolve(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return ValueResult.NONE;
		}
		Item item = stack.getItem();

		// 0) 物品栈级覆盖（转盘加成）—— 必须放在最前面。
		//    CACHE 是按 Item 建键的，同一个物品类型的不同加成值会互相污染，
		//    所以这个判断绝不能挪到 CACHE.get(item) 之后。
		long stackOverride = StackValueOverride.read(stack);
		if (stackOverride >= 0L) {
			return new ValueResult(stackOverride, ValueSource.STACK, Registries.ITEM.getId(item));
		}

		Long override = RUNTIME_OVERRIDES.get(item);
		if (override != null) {
			return new ValueResult(override, ValueSource.RUNTIME, Registries.ITEM.getId(item));
		}

		ValueResult cached = CACHE.get(item);
		if (cached != null) {
			return cached;
		}

		ValueResult computed = compute(stack);
		CACHE.put(item, computed);
		return computed;
	}

	/**
	 * 按物品查询价值。
	 * <p>内部会构造一个数量为 1 的临时物品栈以支持标签匹配，命令与 tooltip 场景可直接用。
	 */
	public static ValueResult resolve(Item item) {
		return resolve(new ItemStack(item));
	}

	/** 只要数值，不关心来源；未登记返回 0 */
	public static long getValue(ItemStack stack) {
		return resolve(stack).value();
	}

	private static ValueResult compute(ItemStack stack) {
		Item item = stack.getItem();
		Identifier itemId = Registries.ITEM.getId(item);

		// 1) 精确物品价值
		Long direct = itemValues.get(itemId);
		if (direct != null) {
			return new ValueResult(direct, ValueSource.ITEM, itemId);
		}

		// 2) 标签价值：多个标签命中时取最大值，结果确定且可预期
		Identifier bestTag = null;
		long bestValue = 0L;
		for (Map.Entry<Identifier, Long> entry : tagValues.entrySet()) {
			long value = entry.getValue();
			if (value <= bestValue) {
				continue;
			}
			TagKey<Item> key = TagKey.of(RegistryKeys.ITEM, entry.getKey());
			if (stack.isIn(key)) {
				bestValue = value;
				bestTag = entry.getKey();
			}
		}
		if (bestTag != null) {
			return new ValueResult(bestValue, ValueSource.TAG, bestTag);
		}

		// 3) 兜底
		long fallback = ItemValueFallback.valueFor(stack, EconomyConfig.get());
		if (fallback > 0L) {
			return new ValueResult(fallback, ValueSource.FALLBACK, null);
		}

		return ValueResult.NONE;
	}

	// ---------------------------------------------------------------- 写入

	/**
	 * 用数据包加载结果整体替换价值表（重载时调用）。
	 *
	 * @param items 物品 id -> 价值
	 * @param tags  标签 id -> 价值
	 */
	public static void setContents(Map<Identifier, Long> items, Map<Identifier, Long> tags) {
		itemValues = Collections.unmodifiableMap(new LinkedHashMap<>(items));
		tagValues = Collections.unmodifiableMap(new LinkedHashMap<>(tags));
		CACHE.clear();
		// 运行时覆盖与数据包无关，但缓存已清空，重新解析时会重新生效
		loadedValueCount = items.size() + tags.size();
	}

	/** 记录本次加载的文件统计 */
	public static void setLoadStats(int files, int failed) {
		loadedFileCount = files;
		failedFileCount = failed;
	}

	/**
	 * 运行时覆盖某个物品的价值（内存生效，重启失效）。
	 *
	 * @return 覆盖前的旧值，没有则为 null
	 */
	public static Long setRuntimeValue(Item item, long value) {
		CACHE.remove(item);
		return RUNTIME_OVERRIDES.put(item, Math.max(0L, value));
	}

	/** 清空运行时覆盖 */
	public static void clearRuntimeOverrides() {
		RUNTIME_OVERRIDES.clear();
		CACHE.clear();
	}

	/** 清空全部内容（数据包重载失败时保留旧表的兜底用，一般不直接调用） */
	public static void clear() {
		itemValues = Collections.emptyMap();
		tagValues = Collections.emptyMap();
		RUNTIME_OVERRIDES.clear();
		CACHE.clear();
	}

	// ---------------------------------------------------------------- 只读视图

	/** 精确物品价值表（只读） */
	public static Map<Identifier, Long> itemValues() {
		return itemValues;
	}

	/** 标签价值表（只读） */
	public static Map<Identifier, Long> tagValues() {
		return tagValues;
	}

	/**
	 * 运行时覆盖表（只读），物品 id -&gt; 价值。
	 * <p>用于价值表同步：网络快照需要把覆盖合并进精确物品价值，保证客户端 HUD
	 * 与服务端 {@link #resolve(ItemStack)} 的结果一致。
	 */
	public static Map<Identifier, Long> runtimeOverrides() {
		Map<Identifier, Long> snapshot = new LinkedHashMap<>();
		for (Map.Entry<Item, Long> entry : RUNTIME_OVERRIDES.entrySet()) {
			snapshot.put(Registries.ITEM.getId(entry.getKey()), entry.getValue());
		}
		return snapshot;
	}

	public static int loadedFileCount() {
		return loadedFileCount;
	}

	public static int loadedValueCount() {
		return loadedValueCount;
	}

	public static int failedFileCount() {
		return failedFileCount;
	}
}
