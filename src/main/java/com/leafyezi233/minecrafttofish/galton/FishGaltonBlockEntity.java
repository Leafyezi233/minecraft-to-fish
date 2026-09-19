package com.leafyezi233.minecrafttofish.galton;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueRegistry;
import com.leafyezi233.minecrafttofish.game.FishGameBlockEntity;
import com.leafyezi233.minecrafttofish.game.GameSupport;
import com.leafyezi233.minecrafttofish.wheel.StackValueOverride;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * 高尔顿板的世界状态（<b>每个方块坐标一份，服务端权威</b>）。
 *
 * <h2>本类只负责「落球」这件事</h2>
 * 鱼的持有 / 吸入 / 取出 / 同步 / 中断恢复全部继承自 {@link FishGameBlockEntity}，
 * 与转盘共用同一份已验证的实现。这里剩下的是高尔顿板独有的部分：
 * <b>路径的掷出、下落调度、落点结算、冷却</b>。
 *
 * <h2>与转盘最关键的结构差异：没有第二份数据</h2>
 * 转盘要存「结果扇区下标」，而客户端靠角度反算指针位置 ——
 * 两份数据之间有一次换算，换算写反就会出现「指针停的位置和奖项对不上」。
 * <p>高尔顿板<b>只存一个 int（路径）</b>：
 * <ul>
 *   <li>球的轨迹 = 纯函数(路径, 进度)</li>
 *   <li>落点槽位 = 路径里 1 的个数</li>
 * </ul>
 * 同一个变量的两种读法，<b>结构上不可能对不上</b>。
 * 所以这里既没有「落点槽位」字段，也没有 {@code sectorAt} 那样的反查函数，
 * 更不需要抖动（球落在哪一格是数出来的，不是转出来的）。
 *
 * <h2>为什么路径必须由服务端掷</h2>
 * 与转盘的抖动同理：若各客户端自己随机，两个玩家会看到球落进不同的槽位。
 * 路径在开落当刻定下并随包下发，全体客户端据此画出同一条轨迹。
 */
public class FishGaltonBlockEntity extends FishGameBlockEntity {

	// ---------------------------------------------------------------- NBT 键

	private static final String KEY_RESULT_PATH = "ResultPath";
	private static final String KEY_PREV_PATH = "PrevPath";
	private static final String KEY_DROPPING = "Dropping";
	private static final String KEY_DROP_START = "DropStart";
	private static final String KEY_DROP_TICKS = "DropTicks";
	private static final String KEY_DROP_SEQUENCE = "DropSeq";

	// ---------------------------------------------------------------- 状态

	/**
	 * 本次结果的<b>路径</b>位掩码；{@link GaltonConstants#NO_PATH} 表示尚无结果。
	 *
	 * <p>存路径而不是存槽位下标，是本类最重要的决定：
	 * 客户端要画球的完整轨迹（每一排往左还是往右），只给一个槽位下标是画不出来的。
	 * 存了路径，槽位就是它数出来的副产品，不存在两份数据不一致的可能。
	 */
	private int resultPath = GaltonConstants.NO_PATH;

	/**
	 * <b>上一次</b>结果的路径。<b>仅渲染用</b>。
	 *
	 * <p>本次结果作废时（鱼被取走、下标失效），高亮要退回上一次的落点，
	 * 而不是整个板子熄灭 —— 与转盘「盘面不跳回 12 点方向」是同一个道理。
	 */
	private int prevPath = GaltonConstants.NO_PATH;

	/** 是否正在下落（已掷路径、尚未落地） */
	private boolean dropping = false;

	/** 本次下落开始的游戏刻（{@code world.getTime()}），供中途入场者估算进度 */
	private long dropStartTick = 0L;

	/**
	 * 本次下落的总刻数。
	 *
	 * <p><b>为什么要同步它</b>：客户端要算下落进度就需要「总时长」这个分母。
	 * 客户端本地配置里的时长可能和服务端不一致（服主改过配置），
	 * 若各自用各自的，联机时两人会在不同时刻看到球落地。
	 */
	private long dropTicks = 0L;

	/** 结算时刻的游戏刻。<b>仅服务端使用，不进 NBT</b> */
	private long settleAtTick = 0L;

	/** 下落序号，每次成功开落 +1；客户端据此判断「有新的一轮」 */
	private int dropSequence = 0;

	/**
	 * 上次开落的游戏刻，用于冷却判定。<b>仅服务端，不进 NBT</b>。
	 *
	 * <p><b>冷却挂在方块上而不是玩家上</b>：高尔顿板是公共设施，任何人可开落，
	 * 所以限制的是「这块板多久能落一次」，而不是「某个人多久能落一次」。
	 * 挂在玩家上会让十个人轮流按就绕过了冷却。
	 */
	private long lastDropAtTick = Long.MIN_VALUE;

	// ---------------------------------------------------------------- 客户端记账
	//
	// 下面两个字段<b>只由客户端渲染器读写</b>：用来判断「这一帧该不该响一声撞钉音」。
	// 它们不持久化、不进同步包、服务端从不读 —— 是纯表现层的记账。
	//
	// 为什么放在方块实体上而不是渲染器里：渲染器每帧被调用，需要跨帧记住
	// 「上一帧砸到第几颗钉子」。用静态 Map<BlockPos, ...> 会随方块被破坏而泄漏，
	// 放在方块实体上则随它一起被回收，天然不会泄漏。
	// 只有 int 的读写，跨线程竞争无害（网络线程读 NBT 与此无交集）。

	/** 客户端上一帧已响过音的钉子数 */
	private int clientPegsHit = 0;

	/** 客户端记住的下落序号；变化即表示「新的一轮」，据此复位计数 */
	private int clientDropSequence = -1;

	/** 客户端上一帧已响过音的钉子数（仅渲染器使用） */
	public int clientPegsHit() {
		return clientPegsHit;
	}

	/** 记录客户端已响过音的钉子数（仅渲染器使用） */
	public void setClientPegsHit(int value) {
		this.clientPegsHit = value;
	}

	/** 客户端记住的下落序号（仅渲染器使用） */
	public int clientDropSequence() {
		return clientDropSequence;
	}

	/** 记录客户端看到的下落序号（仅渲染器使用） */
	public void setClientDropSequence(int value) {
		this.clientDropSequence = value;
	}

	// ---------------------------------------------------------------- 构造

	public FishGaltonBlockEntity(BlockPos pos, BlockState state) {
		super(GaltonBlocks.FISH_GALTON_BLOCK_ENTITY, pos, state);
	}

	// ---------------------------------------------------------------- 基类契约

	@Override
	protected String logTag() {
		return "[galton]";
	}

	@Override
	protected boolean requireModFish() {
		return EconomyConfig.get().galtonRequireModFish;
	}

	@Override
	protected boolean isBusy() {
		return dropping;
	}

	/** 到点结算（吸入与中断恢复由基类负责） */
	@Override
	protected void tickGame(ServerWorld world) {
		if (dropping && world.getTime() >= settleAtTick) {
			settle(world);
		}
	}

	/**
	 * 存档在下落途中被关掉 → 没有调度者了，复位为空闲。
	 * <p>只服务端做：客户端不跑 ticker，它的 {@code dropping} 要留着给渲染器算轨迹。
	 */
	@Override
	protected void recoverFromInterrupted() {
		dropping = false;
		dropTicks = 0L;
		clearResultKeepingVisual();
		MyMod.LOGGER.warn("[galton] 高尔顿板 {} 上次在下落途中被关闭，已复位为空闲（鱼原样保留）", pos);
	}

	@Override
	protected void onFishRemoved() {
		clearResultKeepingVisual();
	}

	/** 方块正在消失：未落地的结算一并作废，鱼按原样掉出（不吞不掉） */
	@Override
	protected void onFishRemovedForDrop() {
		dropping = false;
		clearResultKeepingVisual();
	}

	// ---------------------------------------------------------------- 只读访问

	/** 本次结果的路径；{@link GaltonConstants#NO_PATH} 表示尚无结果 */
	public int resultPath() {
		return resultPath;
	}

	/** 上一次结果的路径 */
	public int prevPath() {
		return prevPath;
	}

	/** 下落序号 */
	public int dropSequence() {
		return dropSequence;
	}

	/** 是否正在下落 */
	public boolean isDropping() {
		return dropping;
	}

	/** 本次下落开始的游戏刻 */
	public long dropStartTick() {
		return dropStartTick;
	}

	/** 本次下落的总刻数；不在下落中时为 0 */
	public long dropTicks() {
		return dropTicks;
	}

	/** 结算时刻（<b>仅服务端有意义</b>） */
	public long settleAtTick() {
		return settleAtTick;
	}

	/**
	 * 当前生效的槽位表。
	 * <p>客户端在收到同步前会用本地配置兜底，联机时应以服务端下发的为准。
	 */
	public GaltonBoard board() {
		return GaltonBoard.current();
	}

	/**
	 * 本次结果的落点槽位下标；无结果时返回 {@link GaltonConstants#NO_SLOT}。
	 * <p><b>由路径数出来</b>（{@link GaltonPath#slotOf}），不是另存的一份数据。
	 */
	public int resultSlot() {
		if (resultPath == GaltonConstants.NO_PATH) {
			return GaltonConstants.NO_SLOT;
		}
		return GaltonPath.slotOf(resultPath, board().rows());
	}

	/**
	 * 盘面「静止时」应该高亮哪一个槽位（<b>渲染用</b>）。
	 *
	 * <p>本次结果被作废时退回<b>上一次</b>的落点，而不是整个板子熄灭 ——
	 * 否则鱼被拿走的瞬间高亮会突然消失，看起来像「板子自己重置了」。
	 *
	 * <p>⚠️ <b>下落中不要用这个方法</b>！它读的是 {@code resultPath}，
	 * 而 {@code resultPath} 在<b>开落当刻</b>就已经写好（见 {@link #startDrop}）——
	 * 用它决定高亮会在球刚开始掉的时候就把落点照亮，<b>等于剧透</b>。
	 * 下落中该用 {@link #revealedSlot()}。
	 */
	public int restingSlot() {
		if (resultPath != GaltonConstants.NO_PATH) {
			return GaltonPath.slotOf(resultPath, board().rows());
		}
		if (prevPath != GaltonConstants.NO_PATH) {
			return GaltonPath.slotOf(prevPath, board().rows());
		}
		return GaltonConstants.NO_SLOT;
	}

	/**
	 * 当前<b>应当照亮</b>的槽位（<b>渲染用</b>）—— 下落中返回「无」。
	 *
	 * <h2>为什么必须与 {@link #restingSlot()} 分开</h2>
	 * 这是玩家实际报出来的 bug：球刚开始往下掉，落点那一格就亮了，
	 * 玩家在球落地前就知道结果了，整个游戏的意义没了。
	 *
	 * <p>根因是<b>「结果已定」与「结果已揭晓」被当成了同一件事</b>。
	 * 服务端为了「掷骰与结算分离」在开落当刻就掷好路径并下发（这是对的，
	 * 客户端要靠它画轨迹），但渲染器不该把「已掷好」当成「可以显示了」。
	 *
	 * <p>所以把「揭晓」显式表达成一个概念：
	 * <b>下落中 = 未揭晓 = 不亮任何格子</b>；
	 * 球落地（{@code dropping} 转 false）之后才亮。
	 * 这样「提前剧透」不再是「记得别用错方法」的约定，而是结构上做不到。
	 */
	public int revealedSlot() {
		if (dropping) {
			// 球还在空中：落点已定但<b>尚未揭晓</b>，什么都不该亮
			return GaltonConstants.NO_SLOT;
		}
		return restingSlot();
	}

	/**
	 * 清空「本次结果」，但<b>把高亮留在原地</b>。
	 *
	 * <p>本次结果作废的场合不止一种（鱼被取走、下标失效、存档复位）。
	 * 直接写 {@code resultPath = NO_PATH} 会让 {@link #restingSlot()} 退回
	 * 更早的落点，高亮于是向后跳一格。统一走这里：
	 * 先把当前路径记进 {@link #prevPath}，再清结果，视觉位置就不会动。
	 */
	private void clearResultKeepingVisual() {
		if (resultPath != GaltonConstants.NO_PATH) {
			prevPath = resultPath;
		}
		resultPath = GaltonConstants.NO_PATH;
	}

	// ---------------------------------------------------------------- 持久化

	@Override
	protected void writeGameNbt(NbtCompound nbt) {
		nbt.putInt(KEY_RESULT_PATH, resultPath);
		nbt.putInt(KEY_PREV_PATH, prevPath);
		nbt.putBoolean(KEY_DROPPING, dropping);
		nbt.putLong(KEY_DROP_START, dropStartTick);
		nbt.putLong(KEY_DROP_TICKS, dropTicks);
		nbt.putInt(KEY_DROP_SEQUENCE, dropSequence);
	}

	@Override
	protected void readGameNbt(NbtCompound nbt) {
		resultPath = nbt.contains(KEY_RESULT_PATH)
				? nbt.getInt(KEY_RESULT_PATH)
				: GaltonConstants.NO_PATH;
		prevPath = nbt.contains(KEY_PREV_PATH)
				? nbt.getInt(KEY_PREV_PATH)
				: GaltonConstants.NO_PATH;
		dropping = nbt.getBoolean(KEY_DROPPING);
		dropStartTick = nbt.getLong(KEY_DROP_START);
		dropTicks = nbt.getLong(KEY_DROP_TICKS);
		dropSequence = nbt.getInt(KEY_DROP_SEQUENCE);

		// 存档里若停在「下落中」，说明服务端是在下落途中被关掉的。
		// 此时没有 ticker 在跑，若原样恢复就会永久卡住 —— 玩家再也动不了它。
		//
		// 这里只「打标记」，绝不直接改状态：readNbt 在客户端也会被调用
		// （每次收到方块实体更新包都会走一遍），当场清掉 dropping 会让客户端
		// 永远画不出下落动画。真正的复位在 serverTick 里做。
		if (dropping) {
			markNeedsRecovery();
		}
	}

	// ---------------------------------------------------------------- 开落

	/**
	 * 开始一次下落（<b>仅服务端</b>）：掷路径、记下结果、上锁，<b>但不立刻改鱼</b>。
	 *
	 * <p>与转盘同样的两段式：掷骰在开落当刻完成，真正改鱼要等球落地。
	 * 这样鱼在下落全程都留在板上、所有人看得见，
	 * 不会「球还没掉下去鱼就没了」而提前剧透结果。
	 *
	 * @return 落点槽位下标；板上无鱼时返回 {@link GaltonConstants#NO_SLOT}（不产生任何状态变化）
	 */
	public int startDrop(ServerWorld world) {
		if (dropping || !hasFish()) {
			return GaltonConstants.NO_SLOT;
		}

		GaltonBoard board = GaltonBoard.current();
		int path = board.rollPath(world.getRandom());
		int slot = board.slotOf(path);

		// 上一次的落点成为「高亮退路」：本次结果作废时高亮留在这里
		this.prevPath = this.resultPath;
		this.resultPath = path;
		this.dropping = true;
		this.dropStartTick = world.getTime();
		this.dropTicks = GameSupport.ticksOf(EconomyConfig.get().galtonDropDurationMs);
		this.settleAtTick = this.dropStartTick + this.dropTicks;
		this.dropSequence++;

		// 开落只播一声轻响，且<b>与输赢无关</b>。
		// 转盘那边踩过的坑：开演时按输赢分岔播音效，等于<b>用耳朵剧透</b>结果，
		// 而且声音会早于动画结束。胜负音效一律挪到 settle() 里播。
		//
		// 音效换成 BLOCK_LEVER_CLICK（拨杆「咔哒」）而不是原来那个撞钉音效：
		// 原来开落和撞钉用同一个声音，玩家分不出「刚开始掉」和「砸到钉子了」。
		// 音高取 0.70，低于第一颗钉子的 0.80，听感上「机关松开」在前、清脆撞击在后。
		playSoundSafely(world, SoundEvents.BLOCK_LEVER_CLICK, GaltonSound.START_VOLUME * soundScale(),
				GaltonSound.START_PITCH);

		if (EconomyConfig.get().debugLogging) {
			MyMod.LOGGER.info("[galton] 板 {} 开落：路径 {}（{} 排），落点槽位 {}，{} tick 后结算",
					pos, Integer.toBinaryString(path), board.rows(), slot,
					settleAtTick - dropStartTick);
		}

		sync();
		return slot;
	}

	/**
	 * 尝试开落（<b>仅服务端</b>）：空手右键时调用。
	 *
	 * <p>冷却用「距离上次开落的刻数」判定，而不是墙上时钟 ——
	 * 游戏刻会被 {@code /tick freeze} 与暂停影响，用刻数才和板子自身的节奏一致。
	 *
	 * @return 落点槽位下标；不可开落时返回 {@link GaltonConstants#NO_SLOT}
	 */
	public int tryDrop(ServerWorld world, net.minecraft.entity.player.PlayerEntity player) {
		if (dropping) {
			return GaltonConstants.NO_SLOT;
		}
		if (!hasFish()) {
			return GaltonConstants.NO_SLOT;
		}

		long now = world.getTime();
		long cooldownTicks = GameSupport.ticksOf(EconomyConfig.get().galtonCooldownMs);
		// lastDropAtTick 初值是 MIN_VALUE，这里必须用减法而不是绝对值比较，否则会溢出
		if (lastDropAtTick != Long.MIN_VALUE && now - lastDropAtTick < cooldownTicks) {
			return GaltonConstants.NO_SLOT;
		}

		int slot = startDrop(world);
		if (slot != GaltonConstants.NO_SLOT) {
			lastDropAtTick = now;
		}
		return slot;
	}

	/** 冷却是否已过（供方块层区分「冷却中」与「其它原因」） */
	public boolean isCoolingDown(ServerWorld world) {
		if (lastDropAtTick == Long.MIN_VALUE) {
			return false;
		}
		return world.getTime() - lastDropAtTick < GameSupport.ticksOf(EconomyConfig.get().galtonCooldownMs);
	}

	// ---------------------------------------------------------------- 结算

	/**
	 * 到点结算（<b>仅服务端</b>）：把掷出的路径真正落到板上的鱼。
	 *
	 * <ul>
	 *   <li><b>中奖</b>：加成写在鱼自身 NBT 上，鱼<b>留在板上</b>，任何人可取走</li>
	 *   <li><b>未中奖</b>：鱼<b>直接销毁</b></li>
	 * </ul>
	 *
	 * <p><b>这里不会出现「球停的位置和奖项对不上」</b>：落点槽位由
	 * {@code resultPath} 数出来，与客户端画球用的是同一个路径，
	 * 结构上不存在第二份可以对错的数据。
	 */
	private void settle(ServerWorld world) {
		dropping = false;
		dropTicks = 0L;

		ItemStack stack = fishStack();
		int path = resultPath;
		GaltonBoard board = GaltonBoard.current();

		if (stack.isEmpty()) {
			// 下落途中鱼被取走（正常流程下不可能，但状态不能因此卡死）
			clearResultKeepingVisual();
			dropTicks = 0L;
			sync();
			return;
		}

		if (path == GaltonConstants.NO_PATH) {
			// 路径缺失：无法判断输赢，按「不吞不掉」处理，鱼原样留下
			MyMod.LOGGER.warn("[galton] 板 {} 缺少结果路径，本次作废，鱼保留", pos);
			clearResultKeepingVisual();
			dropTicks = 0L;
			sync();
			return;
		}

		int slot = GaltonPath.slotOf(path, board.rows());
		if (slot < 0 || slot >= board.size()) {
			// 表在下落途中被重载且变短了。下标失效时无法判断输赢，
			// 按「不吞不掉」处理：鱼原样留下，只把结果作废。
			MyMod.LOGGER.warn("[galton] 板 {} 的结果落点 {} 已超出当前槽位表（{} 个），本次作废，鱼保留",
					pos, slot, board.size());
			clearResultKeepingVisual();
			dropTicks = 0L;
			sync();
			return;
		}

		GaltonSlot landed = board.slot(slot);

		// 胜负音效在<b>落地这一刻</b>播：球落定了才出结果，听觉与视觉同步。
		//
		// 音量与音高都从 GaltonSound 取，而不是在这里写死 ——
		// 客户端播撞钉音用的是同一套函数，两边共用一个来源
		// 才不会出现「撞钉在一个调上、落地在另一个调上」。
		playSoundSafely(world,
				landed.isWin() ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
				GaltonSound.landingVolume(landed.isWin()) * soundScale(),
				GaltonSound.landingPitch(landed.isWin()));

		if (landed.isWin()) {
			long baseValue = ItemValueRegistry.resolve(stack).value();
			long newValue = GaltonBoard.applyMultiplier(baseValue, landed.multiplier());
			long cap = EconomyConfig.get().galtonMaxValue;
			if (cap > 0L && newValue > cap) {
				newValue = cap;
			}

			// 加成写在物品实例的 NBT 上：价值体系是按物品类型定价的，
			// 表达不了「这一条鱼」的加成，只有 NBT 能做到。
			ItemStack result = stack.copy();
			StackValueOverride.write(result, newValue);
			setFish(result);

			if (EconomyConfig.get().debugLogging) {
				MyMod.LOGGER.info("[galton] 板 {} 落点 {} 中奖 ×{}：价值 {} -> {}",
						pos, slot, landed.multiplier(), baseValue, newValue);
			}
		} else {
			// 未中奖：鱼在落地这一刻消失
			setFish(ItemStack.EMPTY);
		}

		sync();
	}

	// ---------------------------------------------------------------- 音效

	/**
	 * 服主配置的音量倍率（{@code 0} = 静音）。
	 *
	 * <p>服务端播的开落/落地音与客户端播的撞钉音<b>共用这一个倍率</b>，
	 * 否则把音量调小之后会出现「撞钉没了、落地还在响」这种半截效果。
	 */
	private static float soundScale() {
		return Math.max(0.0f, EconomyConfig.get().galtonSoundVolume);
	}
}
