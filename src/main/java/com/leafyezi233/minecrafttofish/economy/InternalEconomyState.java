package com.leafyezi233.minecrafttofish.economy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

/**
 * 内置钱包的持久化数据（存在主世界 {@code level.dat} 的 data 段里）。
 *
 * <p>以 UUID 为键，天然支持多人服务器：每个玩家一份余额，随存档保存、重启不丢。
 * 单人模式即"玩家自己的钱包"。
 *
 * <h2>为什么用 PersistentState</h2>
 * 不依赖任何外部经济模组，也不写独立文件 —— 存档备份/迁移时余额跟着一起走。
 */
public class InternalEconomyState extends PersistentState {

	/** 存档里的数据键名 */
	private static final String DATA_KEY = "minecraft_to_fish_economy";

	/** NBT 里存余额列表的键 */
	private static final String NBT_BALANCES = "Balances";

	/** 玩家 UUID -> 余额 */
	private final Map<UUID, Long> balances = new HashMap<>();

	/** 无参构造：PersistentStateManager 从 NBT 读取时用 */
	public InternalEconomyState() {
	}

	public long getBalance(UUID playerId) {
		return balances.getOrDefault(playerId, 0L);
	}

	/** 直接设置余额（命令调试用） */
	public void setBalance(UUID playerId, long amount) {
		balances.put(playerId, Math.max(0L, amount));
		markDirty();
	}

	/**
	 * 加钱。
	 *
	 * @return 变动后的余额
	 */
	public long add(UUID playerId, long amount) {
		long updated = Math.max(0L, getBalance(playerId) + amount);
		balances.put(playerId, updated);
		markDirty();
		return updated;
	}

	/**
	 * 扣钱。
	 *
	 * @return 是否成功；余额不足返回 false 且不扣款
	 */
	public boolean subtract(UUID playerId, long amount) {
		long current = getBalance(playerId);
		if (amount < 0L || current < amount) {
			return false;
		}
		balances.put(playerId, current - amount);
		markDirty();
		return true;
	}

	// ---------------------------------------------------------------- 持久化

	/**
	 * 读取存档数据。
	 *
	 * @param nbt 存档里的 NBT
	 */
	public static InternalEconomyState fromNbt(NbtCompound nbt) {
		InternalEconomyState state = new InternalEconomyState();
		NbtCompound list = nbt.getCompound(NBT_BALANCES);
		for (String key : list.getKeys()) {
			try {
				state.balances.put(UUID.fromString(key), list.getLong(key));
			} catch (IllegalArgumentException e) {
				// 键不是合法 UUID，说明存档被手改过；跳过该条不影响其他玩家
				com.leafyezi233.minecrafttofish.MyMod.LOGGER.warn(
						"[economy] 存档中存在非法 UUID 键，已跳过：{}", key);
			}
		}
		return state;
	}

	@Override
	public NbtCompound writeNbt(NbtCompound nbt) {
		NbtCompound list = new NbtCompound();
		balances.forEach((id, amount) -> list.putLong(id.toString(), amount));
		nbt.put(NBT_BALANCES, list);
		return nbt;
	}

	/** 取（或建）本存档的经济数据 */
	public static InternalEconomyState get(MinecraftServer server) {
		return server.getOverworld()
				.getPersistentStateManager()
				.getOrCreate(InternalEconomyState::fromNbt, InternalEconomyState::new, DATA_KEY);
	}
}
