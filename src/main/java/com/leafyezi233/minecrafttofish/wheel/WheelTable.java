package com.leafyezi233.minecrafttofish.wheel;

import java.util.ArrayList;
import java.util.List;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.config.WheelSectorConfig;

import net.minecraft.util.math.random.Random;

/**
 * 转盘扇区表：把配置里的一串扇区定义<b>校验、归一化</b>成可直接使用的运行时表。
 *
 * <h2>关于期望值</h2>
 * 内置默认表的期望倍率是 0.85（{@link #expectedMultiplier()}），但<b>期望值 &gt;= 1
 * 不再触发回退</b>：服主/整合包作者有权把转盘配成对玩家有利的，本类只负责计算并
 * 打日志提醒，不替他做决定。
 *
 * <h2>回退触发条件</h2>
 * 只在这些情况下回退到内置默认表 —— 它们都是「配置在结构上不可用」，
 * 而不是「配置不合我的口味」：
 * <ul>
 *   <li>列表为空 / 全是无效条目（没有任何扇区可抽）</li>
 *   <li>存在负倍率（意图不明，且与 {@link WheelSector} 的不变量冲突）</li>
 *   <li>所有扇区权重之和 &lt;= 0（{@code nextInt(0)} 会抛异常）</li>
 * </ul>
 *
 * <h2>角度约定</h2>
 * 0° 指向正上方（12 点方向），角度沿<b>顺时针</b>增长，总和恒为 360°。
 * 扇区角度按权重比例分配，所以转盘上每块的大小直观地等于它的概率。
 */
public final class WheelTable {

	/** 单个扇区权重的上限，防止权重过大导致整数溢出 */
	public static final int MAX_WEIGHT = 1_000_000;

	/** 倍率上限，防止 价值 × 倍率 溢出 long */
	public static final int MAX_MULTIPLIER = 1_000;

	/** 扇区数量上限，超过的部分忽略（再多也画不下） */
	public static final int MAX_SECTORS = 64;

	/** 未指定颜色时依次取用的调色板 */
	private static final int[] PALETTE = {
			0xFF4FA3D1, 0xFFD4AF37, 0xFF6B6B6B, 0xFF7FB069,
			0xFFB56576, 0xFF8E7DBE, 0xFFE0A458, 0xFF5B8E7D,
	};

	/** 内置默认扇区表：×2 = 30%、×5 = 5%、未中奖 = 65%，期望值 0.85 */
	private static final List<WheelSector> DEFAULT_SECTORS = List.of(
			new WheelSector(2, 30, 0xFF4FA3D1, "x2"),
			new WheelSector(5, 5, 0xFFD4AF37, "x5"),
			new WheelSector(0, 65, 0xFF6B6B6B, WheelSector.LABEL_LOSE));

	private final List<WheelSector> sectors;

	/** 每个扇区的结束角度（累计），最后一项恒为 360 */
	private final float[] endAngles;

	private final int totalWeight;

	private final double expectedMultiplier;

	/** true 表示配置非法、当前用的是内置默认表 */
	private final boolean usingDefaults;

	private WheelTable(List<WheelSector> sectors, float[] endAngles, int totalWeight,
			double expectedMultiplier, boolean usingDefaults) {
		this.sectors = sectors;
		this.endAngles = endAngles;
		this.totalWeight = totalWeight;
		this.expectedMultiplier = expectedMultiplier;
		this.usingDefaults = usingDefaults;
	}

	// ---------------------------------------------------------------- 当前实例

	/** 本地按配置构建的扇区表（<b>服务端权威</b>；客户端在没收到同步前也用它兜底） */
	private static volatile WheelTable cachedTable;

	/**
	 * 服务端下发的扇区表（<b>仅客户端</b>，收到同步包后设置）。
	 *
	 * <p><b>为什么需要它</b>：扇区表来自服务端的配置文件，联机时客户端本地没有。
	 * 若客户端拿自己的配置去画盘面，而服主改过扇区表，就会出现
	 * 「服务端抽中第 3 个扇区、客户端盘面上那个位置画的是别的颜色」——
	 * 更糟的是落点：客户端按自己的表算角度，指针会停在完全无关的格子上。
	 * 所以只要收到过服务端的表，就一律以它为准。
	 *
	 * <p>为 null 表示「没收到过」（单机、或还没同步完），此时退回本地配置构建的表。
	 */
	private static volatile WheelTable remoteTable;

	/**
	 * 按当前配置重建扇区表，并缓存。
	 * <p>配置加载 / 数据包重载后调用；非法配置在这里就会打出 ERROR 日志并回退，
	 * 不会等到玩家点「开始」才发现问题。
	 */
	public static void rebuild() {
		cachedTable = build(EconomyConfig.get().wheelSectors);
	}

	/**
	 * 采用服务端下发的扇区表（<b>仅客户端调用</b>）。
	 * <p>校验与回退规则和本地配置一致；服务端发来一张坏表时退回本地表，
	 * 而不是让客户端崩在渲染线程上。
	 */
	public static void applyRemote(List<WheelSector> sectors) {
		WheelTable table = fromSectors(sectors);
		// fromSectors 校验失败时会退回「内置默认表」（usingDefaults=true）。
		// 这种情况不能把它当成服务端的表记下来，否则有两个坏处：
		// 一是 usingRemote() 会说谎（明明没用服务端的表却报 true），
		// 二是它会把本地配置表整个盖掉。
		// 所以判定为坏表时直接清空，让 current() 回到本地配置，与上面注释一致。
		remoteTable = table.usingDefaults() ? null : table;
	}

	/** 丢弃服务端下发的表（断线时调用），之后重新以本地配置为准 */
	public static void clearRemote() {
		remoteTable = null;
	}

	/** 是否正在使用服务端下发的扇区表 */
	public static boolean usingRemote() {
		return remoteTable != null;
	}

	/** 当前生效的扇区表；尚未构建过时按当前配置惰性构建一次 */
	public static WheelTable current() {
		// 服务端下发的表优先：联机时它才是权威，本地配置可能与服主不一致
		WheelTable remote = remoteTable;
		if (remote != null) {
			return remote;
		}

		WheelTable table = cachedTable;
		if (table == null) {
			synchronized (WheelTable.class) {
				table = cachedTable;
				if (table == null) {
					table = build(EconomyConfig.get().wheelSectors);
					cachedTable = table;
				}
			}
		}
		return table;
	}

	// ---------------------------------------------------------------- 构建

	/**
	 * 由配置构建扇区表；结构上不可用的配置才回退到内置默认表。
	 *
	 * @param configured 配置里的扇区列表，可为 null
	 */
	public static WheelTable build(List<WheelSectorConfig> configured) {
		if (configured == null || configured.isEmpty()) {
			return fallback("配置里没有扇区");
		}

		List<WheelSector> sanitized = new ArrayList<>();
		for (WheelSectorConfig config : configured) {
			if (config == null) {
				MyMod.LOGGER.warn("[wheel] 忽略一个空的扇区配置项");
				continue;
			}
			if (sanitized.size() >= MAX_SECTORS) {
				MyMod.LOGGER.warn("[wheel] 扇区数量超过上限 {}，多余部分已忽略", MAX_SECTORS);
				break;
			}

			if (config.multiplier < 0) {
				// 负倍率意图不明（是"倒扣价值"还是"写错了"？），不猜测，整表回退
				return fallback("存在负倍率：" + config.multiplier);
			}
			if (config.weight <= 0) {
				// 权重为 0 的扇区永远不会被抽到，丢掉即可，不影响其它扇区
				MyMod.LOGGER.warn("[wheel] 忽略权重非正的扇区（倍率 ×{}，权重 {}）",
						config.multiplier, config.weight);
				continue;
			}

			int multiplier = config.multiplier;
			if (multiplier > MAX_MULTIPLIER) {
				MyMod.LOGGER.warn("[wheel] 扇区倍率 ×{} 超过上限，已按 ×{} 处理", multiplier, MAX_MULTIPLIER);
				multiplier = MAX_MULTIPLIER;
			}

			int weight = config.weight;
			if (weight > MAX_WEIGHT) {
				MyMod.LOGGER.warn("[wheel] 扇区权重 {} 超过上限，已按 {} 处理", weight, MAX_WEIGHT);
				weight = MAX_WEIGHT;
			}

			int color = config.color;
			if (color == 0) {
				color = PALETTE[sanitized.size() % PALETTE.length];
			}

			sanitized.add(new WheelSector(multiplier, weight, color, config.labelKey));
		}

		if (sanitized.isEmpty()) {
			return fallback("没有有效的扇区（权重全部非正）");
		}

		long weightSum = 0L;
		for (WheelSector sector : sanitized) {
			weightSum += sector.weight();
		}
		if (weightSum <= 0L) {
			return fallback("扇区权重之和为 0");
		}

		double expected = expectedOf(sanitized, weightSum);
		if (expected >= 1.0d) {
			// 不回退：服主有权配成对玩家有利。只提醒一次，让日志里查得到。
			MyMod.LOGGER.warn("[wheel] 扇区表期望值 {} >= 1，玩家长期可获利（按配置生效，未回退）",
					String.format("%.4f", expected));
		}

		float[] angles = allocateAngles(sanitized, weightSum);
		MyMod.LOGGER.info("[wheel] 已加载扇区表：{} 个扇区，期望值 {}",
				sanitized.size(), String.format("%.4f", expected));
		return new WheelTable(List.copyOf(sanitized), angles, (int) weightSum, expected, false);
	}

	/**
	 * 由<b>已经校验过的扇区</b>直接构建表（网络同步路径用）。
	 *
	 * <p>与 {@link #build} 的区别：{@code build} 面向「玩家手写的配置」，
	 * 允许 null 字段、0 颜色、越界权重，负责清理；
	 * 本方法面向「服务端已经清理过一遍的表」，只需重算角度，
	 * 且<b>不重复打日志</b>（否则每个客户端都会再刷一遍扇区表日志）。
	 *
	 * <p>仍然做最小校验：服务端与本模组版本不一致时，收到的可能是坏数据，
	 * 此时退回内置默认表，绝不让客户端崩在渲染线程上。
	 *
	 * @param sectors 扇区列表，可为 null
	 */
	public static WheelTable fromSectors(List<WheelSector> sectors) {
		if (sectors == null || sectors.isEmpty()) {
			return fallback("同步来的扇区表为空");
		}

		List<WheelSector> sanitized = new ArrayList<>(Math.min(sectors.size(), MAX_SECTORS));
		for (WheelSector sector : sectors) {
			if (sector == null || sector.weight() <= 0) {
				continue;
			}
			if (sanitized.size() >= MAX_SECTORS) {
				break;
			}
			sanitized.add(sector);
		}
		if (sanitized.isEmpty()) {
			return fallback("同步来的扇区表没有有效条目");
		}

		long weightSum = 0L;
		for (WheelSector sector : sanitized) {
			weightSum += sector.weight();
		}
		if (weightSum <= 0L) {
			return fallback("同步来的扇区权重之和为 0");
		}

		float[] angles = allocateAngles(sanitized, weightSum);
		return new WheelTable(List.copyOf(sanitized), angles, (int) weightSum,
				expectedOf(sanitized, weightSum), false);
	}

	/** 回退到内置默认表，并打 ERROR 日志说明原因 */
	private static WheelTable fallback(String reason) {
		MyMod.LOGGER.error("[wheel] 扇区配置非法（{}），已回退到内置默认表（期望值 0.8500）", reason);

		long weightSum = 0L;
		for (WheelSector sector : DEFAULT_SECTORS) {
			weightSum += sector.weight();
		}
		float[] angles = allocateAngles(DEFAULT_SECTORS, weightSum);
		return new WheelTable(DEFAULT_SECTORS, angles, (int) weightSum,
				expectedOf(DEFAULT_SECTORS, weightSum), true);
	}

	/** 期望值 = Σ(倍率 × 权重) / Σ权重 */
	private static double expectedOf(List<WheelSector> sectors, long weightSum) {
		long weighted = 0L;
		for (WheelSector sector : sectors) {
			weighted += (long) sector.multiplier() * sector.weight();
		}
		return (double) weighted / (double) weightSum;
	}

	/** 按权重比例把 360° 分配给各扇区，最后一项强制收在 360° 以免累积误差留下缝隙 */
	private static float[] allocateAngles(List<WheelSector> sectors, long weightSum) {
		float[] angles = new float[sectors.size()];
		float cursor = 0.0f;
		for (int i = 0; i < sectors.size(); i++) {
			cursor += (float) (sectors.get(i).weight() * 360.0d / weightSum);
			angles[i] = cursor;
		}
		angles[angles.length - 1] = 360.0f;
		return angles;
	}

	// ---------------------------------------------------------------- 查询

	/** 扇区列表（只读） */
	public List<WheelSector> sectors() {
		return sectors;
	}

	public int size() {
		return sectors.size();
	}

	public WheelSector sector(int index) {
		return sectors.get(index);
	}

	/** 权重总和 */
	public int totalWeight() {
		return totalWeight;
	}

	/** 期望倍率。内置默认表为 0.85；配置成 &gt;= 1 也照常生效，只会打一条 WARN */
	public double expectedMultiplier() {
		return expectedMultiplier;
	}

	/** 是否正在使用内置默认表（配置非法） */
	public boolean usingDefaults() {
		return usingDefaults;
	}

	/** 扇区起始角度（度，0 = 正上方，顺时针增长） */
	public float startAngle(int index) {
		return index == 0 ? 0.0f : endAngles[index - 1];
	}

	/** 扇区结束角度（度） */
	public float endAngle(int index) {
		return endAngles[index];
	}

	/** 扇区中点角度（度），指针落点取这个值 */
	public float midAngle(int index) {
		return (startAngle(index) + endAngle(index)) / 2.0f;
	}

	/**
	 * 找第一个倍率匹配的扇区下标。
	 * <p>客户端用它把服务端返回的倍率换算成指针落点：即使客户端配置与服务端不一致，
	 * 指针也一定停在和服务端结果一致的位置。
	 *
	 * @return 下标；没有匹配时返回 -1
	 */
	public int indexOfMultiplier(int multiplier) {
		for (int i = 0; i < sectors.size(); i++) {
			if (sectors.get(i).multiplier() == multiplier) {
				return i;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- 抽取

	/**
	 * 按权重抽一个扇区，返回<b>下标</b>（<b>服务端权威</b>）。
	 * <p>用累计权重法：{@code nextInt(total)} 落在哪个区间就取哪个扇区，
	 * 一次随机数调用完成，无浮点误差。
	 *
	 * <p><b>为什么对外传下标而不是扇区/倍率</b>：配置允许两个扇区倍率相同。
	 * 若把倍率同步出去让客户端反查，两端只会命中第一个匹配项，
	 * 于是「服务端抽中的是第 3 个 ×2，客户端指针却停在 0 号 ×2」。
	 * 下标没有歧义，是让所有观看者看到同一个落点的前提。
	 */
	public int rollIndex(Random random) {
		int target = random.nextInt(totalWeight);
		int cursor = 0;
		for (int i = 0; i < sectors.size(); i++) {
			cursor += sectors.get(i).weight();
			if (target < cursor) {
				return i;
			}
		}
		// 理论上不可达（target < totalWeight）；真到了这里说明表被改坏了，取最后一项兜底
		return sectors.size() - 1;
	}

	/** 按权重抽一个扇区（{@link #rollIndex} 的便捷包装） */
	public WheelSector roll(Random random) {
		return sectors.get(rollIndex(random));
	}

	// ---------------------------------------------------------------- 计算

	/**
	 * 把倍率作用到价值上，<b>饱和运算</b>：溢出时返回 {@link Long#MAX_VALUE} 而不是回绕成负数。
	 * <p>倍率为 0（未中奖）时返回 0。
	 */
	public static long applyMultiplier(long value, int multiplier) {
		if (value <= 0L || multiplier <= 0) {
			return 0L;
		}
		if (value > Long.MAX_VALUE / multiplier) {
			return Long.MAX_VALUE;
		}
		return value * multiplier;
	}
}
