package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueRegistry;
import com.leafyezi233.minecrafttofish.game.FishGameBlockEntity;
import com.leafyezi233.minecrafttofish.game.GameSupport;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * 渔轮转盘的世界状态（<b>每个方块坐标一份，服务端权威</b>）。
 *
 * <h2>为什么状态必须搬到这里</h2>
 * 改造前，鱼和抽奖状态存在玩家的界面处理器里 ——
 * 而每个开界面的玩家各持一份，于是「每个人一台私人老虎机」，
 * 物理上不可能做到全员可见。搬到 BlockEntity 后，一个方块坐标只有一份状态，
 * 转盘才成为「村里那台公共转盘」。
 *
 * <h2>本类只负责「转盘」这件事</h2>
 * 鱼的持有 / 吸入 / 取出 / 同步 / 中断恢复全部上提到
 * {@link FishGameBlockEntity}，与高尔顿板共用同一份实现（那些逻辑每一条都踩过坑，
 * 复制一份等于把坑重踩一遍）。这里剩下的是<b>转盘独有</b>的部分：
 * 扇区下标、转动调度、落点抖动、冷却。
 *
 * <h2>为什么结果存「扇区下标」而不是「倍率」</h2>
 * 配置允许两个扇区配成相同倍率。若同步倍率、让客户端反查下标，
 * 两端都只会命中第一个匹配项 —— 服务端抽中 3 号 ×2，客户端指针却停在 0 号 ×2。
 * 下标没有歧义，是「所有人看到同一个落点」的前提。
 *
 * <h2>顺带修掉的两个旧隐患</h2>
 * <ul>
 *   <li>服务端崩溃时鱼会丢（旧的界面槽位不持久化）→ 现在随区块 NBT 落盘</li>
 *   <li>玩家断线要专门兜底交还 → 鱼不再挂在玩家会话上，断线兜底整条路径消失</li>
 * </ul>
 */
public class FishWheelBlockEntity extends FishGameBlockEntity {

	// ---------------------------------------------------------------- NBT 键

	private static final String KEY_SPIN_SEQUENCE = "SpinSeq";
	private static final String KEY_RESULT_SECTOR = "ResultSector";
	private static final String KEY_PREV_SECTOR = "PrevSector";
	private static final String KEY_SPINNING = "Spinning";
	private static final String KEY_SPIN_START = "SpinStart";
	private static final String KEY_SPIN_TICKS = "SpinTicks";
	/** 落点抖动；旧存档没有这个键 → 读出 0（停在扇区正中） */
	private static final String KEY_SPIN_JITTER = "SpinJitter";
	/** 上一次落点抖动，与 {@link #KEY_PREV_SECTOR} 配对 */
	private static final String KEY_PREV_JITTER = "PrevJitter";

	// ---------------------------------------------------------------- 状态

	/** 抽奖序号，每次成功开转 +1；客户端据此判断「有新结果」并起动画 */
	private int spinSequence = 0;

	/** 本次结果的扇区下标；{@link WheelConstants#NO_SECTOR} 表示尚无结果 */
	private int resultSectorIndex = WheelConstants.NO_SECTOR;

	/**
	 * <b>上一次</b>结果的扇区下标。<b>仅客户端渲染用</b>。
	 *
	 * <p><b>为什么需要它</b>：转动动画是「从上一个落点转到本次落点」。
	 * 没有这个起点，每次开转都只能从 0° 起转 ——
	 * 玩家会看到盘面在开转瞬间「跳」到 12 点方向再开始转，很突兀。
	 *
	 * <p>它由服务端在开转当刻写入并同步，因此所有客户端算出的起点也一致。
	 */
	private int prevSectorIndex = WheelConstants.NO_SECTOR;

	/** 是否正在转动（已掷骰、尚未落地） */
	private boolean spinning = false;

	/** 本次转动开始的游戏刻（{@code world.getTime()}），供中途入场者估算进度 */
	private long spinStartTick = 0L;

	/**
	 * 本次转动的总刻数。
	 *
	 * <p><b>为什么要同步它</b>：客户端要算进度就需要「总时长」这个分母。
	 * 客户端本地配置里的时长可能和服务端不一致（服主改过配置），
	 * 若各自用各自的，联机时两人会在不同时刻停下。
	 * 由服务端连同起点一起下发，两端进度就完全一致。
	 */
	private long spinTicks = 0L;

	/**
	 * 本次落点在扇区内的<b>抖动</b>，取值 {@code [-1, 1]}。
	 *
	 * <p><b>为什么要同步它</b>：转盘不该每次都停在扇区正中（玩家一眼就看出规律）。
	 * 但抖动一旦由客户端各自随机，两个玩家看到的落点就会不同 ——
	 * 与「所有客户端算出的角度必须一致」这条设计前提直接冲突。
	 * 所以抖动由<b>服务端</b>在开转当刻定下，随包下发，
	 * 全体客户端用同一个值算出同一个落点。
	 */
	private float spinJitter = 0.0f;

	/**
	 * <b>上一次</b>落点的抖动，与 {@link #prevSectorIndex} 配对。
	 *
	 * <p>起点必须用「上次实际停下的角度」，所以上一次的抖动也要留着 ——
	 * 否则开转瞬间盘面会从上次落点跳回扇区正中。
	 */
	private float prevSpinJitter = 0.0f;

	/**
	 * 结算时刻的游戏刻。<b>仅服务端使用，不进 NBT</b> ——
	 * 它是「何时落地」的调度信息，客户端只需要知道「正在转」和「什么时候开始转的」。
	 */
	private long settleAtTick = 0L;

	/**
	 * 上次开转的游戏刻，用于冷却判定。<b>仅服务端，不进 NBT</b>。
	 *
	 * <p><b>冷却挂在方块上而不是玩家上</b>：转盘是公共设施，任何人可开转，
	 * 所以限制的是「这台转盘多久能转一次」，而不是「某个人多久能转一次」。
	 * 挂在玩家上会让十个人轮流按就绕过了冷却。
	 */
	private long lastSpinAtTick = Long.MIN_VALUE;

	// ---------------------------------------------------------------- 构造

	public FishWheelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.FISH_WHEEL_BLOCK_ENTITY, pos, state);
	}

	// ---------------------------------------------------------------- 基类契约

	@Override
	protected String logTag() {
		return "[wheel]";
	}

	@Override
	protected boolean requireModFish() {
		return EconomyConfig.get().wheelRequireModFish;
	}

	@Override
	protected boolean isBusy() {
		return spinning;
	}

	/** 到点结算（吸入与中断恢复由基类负责） */
	@Override
	protected void tickGame(ServerWorld world) {
		if (spinning && world.getTime() >= settleAtTick) {
			settle(world);
		}
	}

	/**
	 * 存档在转动途中被关掉 → 没有调度者了，复位为空闲。
	 * <p>只服务端做：客户端不跑 ticker，它的 {@code spinning} 要留着给渲染器算角度。
	 */
	@Override
	protected void recoverFromInterrupted() {
		spinning = false;
		spinTicks = 0L;
		clearResultKeepingVisual();
		MyMod.LOGGER.warn("[wheel] 转盘 {} 上次在转动途中被关闭，已复位为空闲（鱼原样保留）", pos);
	}

	@Override
	protected void onFishRemoved() {
		clearResultKeepingVisual();
	}

	/** 方块正在消失：未落地的结算一并作废，鱼按原样掉出（不吞不掉） */
	@Override
	protected void onFishRemovedForDrop() {
		spinning = false;
		clearResultKeepingVisual();
	}

	// ---------------------------------------------------------------- 只读访问

	/** 抽奖序号 */
	public int spinSequence() {
		return spinSequence;
	}

	/** 本次结果的扇区下标；{@link WheelConstants#NO_SECTOR} 表示尚无结果 */
	public int resultSectorIndex() {
		return resultSectorIndex;
	}

	/** 上一次结果的扇区下标；{@link WheelConstants#NO_SECTOR} 表示还没转过 */
	public int prevSectorIndex() {
		return prevSectorIndex;
	}

	/** 是否正在转动 */
	public boolean isSpinning() {
		return spinning;
	}

	/** 本次转动开始的游戏刻 */
	public long spinStartTick() {
		return spinStartTick;
	}

	/** 本次转动的总刻数；不在转动中时为 0 */
	public long spinTicks() {
		return spinTicks;
	}

	/** 本次落点的扇区内抖动，{@code [-1, 1]}；0 表示停在扇区正中 */
	public float spinJitter() {
		return spinJitter;
	}

	/** 上一次落点的扇区内抖动（起点角用），与 {@link #prevSectorIndex()} 配对 */
	public float prevSpinJitter() {
		return prevSpinJitter;
	}

	/**
	 * 盘面「静止时」应该停在哪一个扇区（<b>渲染用</b>）。
	 *
	 * <p>本次结果被作废时（鱼被取走、下标失效），这里退回<b>上一次</b>的落点，
	 * 而不是返回 0°。否则盘面会在鱼被拿走的瞬间「啪」地跳回 12 点方向 ——
	 * 玩家看到的是「转盘自己动了一下」，很像是 bug。
	 */
	public int restingSector() {
		return resultSectorIndex != WheelConstants.NO_SECTOR ? resultSectorIndex : prevSectorIndex;
	}

	/**
	 * 与 {@link #restingSector()} <b>配对</b>的抖动。
	 *
	 * <p>落点是「扇区 + 抖动」两个值共同决定的，两者必须一起取，
	 * 否则会算出「A 扇区的位置配 B 扇区的抖动」这种错位角度。
	 * 这里把配对关系收在一处，调用方不必自己判断该用哪个抖动。
	 */
	public float restingJitter() {
		return resultSectorIndex != WheelConstants.NO_SECTOR ? spinJitter : prevSpinJitter;
	}

	/**
	 * 清空「本次结果」，但<b>把盘面留在原地</b>。
	 *
	 * <p>本次结果作废的场合不止一种（鱼被取走、下标失效、存档复位）。
	 * 直接写 {@code resultSectorIndex = NO_SECTOR} 会让
	 * {@link #restingSector()} 退回<b>更早</b>的落点，盘面于是向后跳一格。
	 * 统一走这里：先把当前落点记进 {@link #prevSectorIndex}，再清结果，
	 * 视觉位置就不会动。
	 *
	 * <p><b>抖动必须一起搬过去</b>：落点是「扇区 + 抖动」两个值共同决定的，
	 * 只搬扇区不搬抖动，盘面会从上次的落点<b>跳回扇区正中</b>
	 * （宽扇区可达 40°，肉眼非常明显）。
	 */
	private void clearResultKeepingVisual() {
		if (resultSectorIndex != WheelConstants.NO_SECTOR) {
			prevSectorIndex = resultSectorIndex;
			prevSpinJitter = spinJitter;
		}
		resultSectorIndex = WheelConstants.NO_SECTOR;
	}

	// ---------------------------------------------------------------- 持久化

	@Override
	protected void writeGameNbt(NbtCompound nbt) {
		nbt.putInt(KEY_SPIN_SEQUENCE, spinSequence);
		nbt.putInt(KEY_RESULT_SECTOR, resultSectorIndex);
		nbt.putInt(KEY_PREV_SECTOR, prevSectorIndex);
		nbt.putBoolean(KEY_SPINNING, spinning);
		nbt.putLong(KEY_SPIN_START, spinStartTick);
		nbt.putLong(KEY_SPIN_TICKS, spinTicks);
		nbt.putFloat(KEY_SPIN_JITTER, spinJitter);
		nbt.putFloat(KEY_PREV_JITTER, prevSpinJitter);
	}

	@Override
	protected void readGameNbt(NbtCompound nbt) {
		spinSequence = nbt.getInt(KEY_SPIN_SEQUENCE);
		resultSectorIndex = nbt.contains(KEY_RESULT_SECTOR)
				? nbt.getInt(KEY_RESULT_SECTOR)
				: WheelConstants.NO_SECTOR;
		prevSectorIndex = nbt.contains(KEY_PREV_SECTOR)
				? nbt.getInt(KEY_PREV_SECTOR)
				: WheelConstants.NO_SECTOR;
		spinning = nbt.getBoolean(KEY_SPINNING);
		spinStartTick = nbt.getLong(KEY_SPIN_START);
		spinTicks = nbt.getLong(KEY_SPIN_TICKS);
		// 旧存档没有这个键 → 读出 0，等价于「停在扇区正中」，与旧行为一致
		spinJitter = nbt.getFloat(KEY_SPIN_JITTER);
		prevSpinJitter = nbt.getFloat(KEY_PREV_JITTER);

		// 存档里若停在「转动中」，说明服务端是在转动途中被关掉的。
		// 此时没有 ticker 在跑，若原样恢复就会永久卡在转动中 —— 玩家再也动不了它。
		//
		// 这里只「打标记」，绝不直接改状态：readNbt 在客户端也会被调用，
		// 当场清掉 spinning 会让客户端永远画不出转动动画。真正的复位在 serverTick 里做。
		if (spinning) {
			markNeedsRecovery();
		}
	}

	// ---------------------------------------------------------------- 开转

	/**
	 * 开始转动（<b>仅服务端</b>）：掷骰、记下结果、上锁，<b>但不立刻改鱼</b>。
	 *
	 * <p>与改造前同样的两段式：掷骰在开转当刻完成，真正改鱼要等转盘落地。
	 * 这样鱼在转动全程都留在盘上、所有人看得见，
	 * 不会「还没开始转鱼就没了」而提前剧透结果。
	 *
	 * @return 实际抽取到的扇区；盘上无鱼时返回 null（不产生任何状态变化）
	 */
	public WheelSector startSpin(ServerWorld world) {
		if (spinning || !hasFish()) {
			return null;
		}

		WheelTable table = WheelTable.current();
		int index = table.rollIndex(world.getRandom());
		WheelSector sector = table.sector(index);

		// 上一次的落点成为本次的起点：客户端据此从「盘面当前所在的位置」起转，
		// 而不是每次都从 12 点方向重来。
		this.prevSectorIndex = this.resultSectorIndex;
		this.prevSpinJitter = this.spinJitter;
		this.resultSectorIndex = index;
		this.spinning = true;
		this.spinStartTick = world.getTime();
		this.spinTicks = GameSupport.ticksOf(EconomyConfig.get().wheelSpinDurationMs);
		this.settleAtTick = this.spinStartTick + this.spinTicks;

		// 落点抖动：在扇区内部随机偏移，让转盘不必每次停在正中。
		// 必须在服务端掷、并随包下发 —— 若各客户端自己随机，两人看到的落点会不同。
		this.spinJitter = world.getRandom().nextFloat() * 2.0f - 1.0f;

		this.spinSequence++;

		// 开转只播「开始转」的音，且<b>与输赢无关</b>。
		//
		// 这里曾经播的是「中奖/未中奖」两种音效之一 —— 有两个问题：
		//   1. 它在中奖<b>之前</b>就响了（结算要等转动结束），听起来像「还没转完就出结果」；
		//   2. 音效本身按 sector.isWin() 分岔，等于<b>用耳朵剧透</b>了结果。
		// 现在开转统一一声轻响，胜负音效挪到 settle() 里播。
		playSoundSafely(world, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 0.8f, 1.2f);

		if (EconomyConfig.get().debugLogging) {
			MyMod.LOGGER.info("[wheel] 转盘 {} 开转：下标 {}，倍率 ×{}，{} tick 后结算",
					pos, index, sector.multiplier(), settleAtTick - spinStartTick);
		}

		sync();
		return sector;
	}

	/**
	 * 尝试开转（<b>仅服务端</b>）：空手右键时调用。
	 *
	 * <p>冷却用「距离上次开转的刻数」判定，而不是墙上时钟 ——
	 * 游戏刻会被 {@code /tick freeze} 与暂停影响，用刻数才和转盘自身的节奏一致。
	 *
	 * @return 抽中的扇区；不可开转时返回 null
	 */
	public WheelSector trySpin(ServerWorld world, PlayerEntity player) {
		if (spinning) {
			return null;
		}
		if (!hasFish()) {
			return null;
		}

		long now = world.getTime();
		long cooldownTicks = GameSupport.ticksOf(EconomyConfig.get().wheelSpinCooldownMs);
		// lastSpinAtTick 初值是 MIN_VALUE，这里必须用减法而不是绝对值比较，否则会溢出
		if (lastSpinAtTick != Long.MIN_VALUE && now - lastSpinAtTick < cooldownTicks) {
			return null;
		}

		WheelSector sector = startSpin(world);
		if (sector != null) {
			lastSpinAtTick = now;
		}
		return sector;
	}

	/** 冷却是否已过（供方块层区分「冷却中」与「其它原因」） */
	public boolean isCoolingDown(ServerWorld world) {
		if (lastSpinAtTick == Long.MIN_VALUE) {
			return false;
		}
		return world.getTime() - lastSpinAtTick < GameSupport.ticksOf(EconomyConfig.get().wheelSpinCooldownMs);
	}

	// ---------------------------------------------------------------- 结算

	/**
	 * 到点结算（<b>仅服务端</b>）：把掷骰结果真正落到盘上的鱼。
	 *
	 * <ul>
	 *   <li><b>中奖</b>：加成写在鱼自身 NBT 上，鱼<b>留在盘上</b>，任何人可取走</li>
	 *   <li><b>未中奖</b>：鱼<b>直接销毁</b></li>
	 * </ul>
	 */
	private void settle(ServerWorld world) {
		spinning = false;
		spinTicks = 0L;

		ItemStack stack = fishStack();
		int index = resultSectorIndex;
		WheelTable table = WheelTable.current();

		if (stack.isEmpty()) {
			// 转动途中鱼被取走（正常流程下不可能，但状态不能因此卡死）
			clearResultKeepingVisual();
			spinTicks = 0L;
			sync();
			return;
		}

		if (index < 0 || index >= table.size()) {
			// 表在转动途中被重载且变短了。下标失效时无法判断输赢，
			// 按「不吞不掉」处理：鱼原样留下，只把结果作废。
			MyMod.LOGGER.warn("[wheel] 转盘 {} 的结果下标 {} 已超出当前扇区表（{} 个），本次作废，鱼保留",
					pos, index, table.size());
			clearResultKeepingVisual();
			spinTicks = 0L;
			sync();
			return;
		}

		WheelSector sector = table.sector(index);

		// 胜负音效在<b>落地这一刻</b>播：转盘停了才出结果，听觉与视觉同步。
		// （原先在开转时播，会早于转动结束，听起来像结果提前揭晓。）
		playSoundSafely(world,
				sector.isWin() ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
				1.0f, 1.0f);

		if (sector.isWin()) {
			long baseValue = ItemValueRegistry.resolve(stack).value();
			long newValue = WheelTable.applyMultiplier(baseValue, sector.multiplier());
			long cap = EconomyConfig.get().wheelMaxValue;
			if (cap > 0L && newValue > cap) {
				newValue = cap;
			}

			// 加成写在物品实例的 NBT 上：价值体系是按物品类型定价的，
			// 表达不了「这一条鱼」的加成，只有 NBT 能做到。
			ItemStack result = stack.copy();
			StackValueOverride.write(result, newValue);
			setFish(result);

			if (EconomyConfig.get().debugLogging) {
				MyMod.LOGGER.info("[wheel] 转盘 {} 中奖 ×{}：价值 {} -> {}",
						pos, sector.multiplier(), baseValue, newValue);
			}
		} else {
			// 未中奖：鱼在落地这一刻消失
			setFish(ItemStack.EMPTY);
		}

		sync();
	}
}
