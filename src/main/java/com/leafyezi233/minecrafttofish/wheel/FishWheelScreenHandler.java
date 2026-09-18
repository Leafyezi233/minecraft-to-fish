package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueRegistry;
import com.leafyezi233.minecrafttofish.economy.value.ValueResult;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * 渔轮转盘的界面处理器 —— <b>全部权威逻辑都在这里（服务端）</b>。
 *
 * <h2>为什么不需要自定义网络包</h2>
 * 「开始」按钮走<b>原版按钮通道</b>：客户端 {@code clickButton} → 服务端 {@code onButtonClick}。
 * 原版 {@code ServerPlayNetworkHandler.onButtonClick} 已经校验了 {@code syncId} 与非旁观状态，
 * 并且在本方法返回 {@code true} 时自动 {@code sendContentUpdates()} ——
 * 槽位内容与属性一起下发。所以本功能的网络代码量是 0，且天然服务端权威、无法伪造。
 *
 * <h2>物品绝不丢失</h2>
 * 见 {@link #onClosed(PlayerEntity)} 与 {@link WheelSafety}。
 * 这里有两个原版<b>不会</b>帮你处理的坑：
 * <ol>
 *   <li>玩家开着界面直接断线时，原版<b>不会</b>调 {@code onClosed} → 由 {@link WheelSafety} 兜</li>
 *   <li>{@code onClosed} 在客户端也会被调用 → 必须判断 {@code ServerPlayerEntity}</li>
 * </ol>
 */
public class FishWheelScreenHandler extends ScreenHandler {

	/** 鱼槽数量：严格一条 */
	private static final int FISH_SLOT_COUNT = 1;

	/** 玩家背包主区 3×9 */
	private static final int PLAYER_INV_ROWS = 3;
	private static final int PLAYER_INV_COLS = 9;

	/** 本界面的鱼槽（1 格） */
	private final Inventory fishInventory;

	/** 方块坐标，用于 canUse 校验 */
	private final BlockPos pos;

	/** 校验上下文；坐标不可用时为 EMPTY */
	private final ScreenHandlerContext context;

	/** 同步给客户端的属性：抽奖序号 + 结果倍率 */
	private final PropertyDelegate properties;

	/** 上次抽奖时间戳（毫秒），用于冷却 */
	private long lastSpinAt = 0L;

	/** 是否有一次「已掷骰但尚未结算」的抽奖 */
	private boolean settling = false;

	/** 待结算的倍率 */
	private int pendingMultiplier = 0;

	/** 结算时间点（毫秒）；到点后才真正改动物品栏 */
	private long settleAt = 0L;

	/** 本界面已关闭，防止重复交还物品 */
	private boolean closed = false;

	// ---------------------------------------------------------------- 构造

	/** 服务端构造：坐标已知 */
	public FishWheelScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, pos, new SimpleInventory(FISH_SLOT_COUNT),
				new ArrayPropertyDelegate(WheelConstants.PROPERTY_COUNT));
	}

	/** 客户端构造：坐标从开屏数据里读出 */
	public FishWheelScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
		this(syncId, playerInventory, buf.readBlockPos(), new SimpleInventory(FISH_SLOT_COUNT),
				new ArrayPropertyDelegate(WheelConstants.PROPERTY_COUNT));
	}

	private FishWheelScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos,
			Inventory fishInventory, PropertyDelegate properties) {
		super(ModBlocks.FISH_WHEEL_SCREEN_HANDLER, syncId);

		this.fishInventory = fishInventory;
		this.pos = pos;
		this.context = ScreenHandlerContext.create(playerInventory.player.getWorld(), pos);
		this.properties = properties;

		properties.set(WheelConstants.PROPERTY_SPIN_SEQUENCE, 0);
		properties.set(WheelConstants.PROPERTY_RESULT_MULTIPLIER, WheelConstants.NO_RESULT);
		setResultValue(WheelConstants.NO_RESULT_VALUE);

		// 鱼槽：只能放 1 个。
		// 转动期间锁定槽位，防止玩家在动画播完前把鱼拿走，
		// 导致「结果已定但物品已换人」的不一致。
		this.addSlot(new Slot(fishInventory, 0, WheelConstants.SLOT_X, WheelConstants.SLOT_Y) {
			@Override
			public int getMaxItemCount() {
				return 1;
			}

			@Override
			public boolean canTakeItems(PlayerEntity player) {
				// 用同步属性判断，两端行为一致（canTakeItems 在服务端被强制调用，锁是真实生效的）
				return !isSpinning();
			}

			@Override
			public boolean canInsert(ItemStack stack) {
				return !isSpinning();
			}
		});

		// 玩家背包主区 3×9
		for (int row = 0; row < PLAYER_INV_ROWS; row++) {
			for (int col = 0; col < PLAYER_INV_COLS; col++) {
				this.addSlot(new Slot(playerInventory,
						col + row * PLAYER_INV_COLS + PLAYER_INV_COLS,
						WheelConstants.PLAYER_INV_X + col * 18,
						WheelConstants.PLAYER_INV_Y + row * 18));
			}
		}

		// 快捷栏 9 格
		for (int col = 0; col < PLAYER_INV_COLS; col++) {
			this.addSlot(new Slot(playerInventory, col,
					WheelConstants.HOTBAR_X + col * 18,
					WheelConstants.HOTBAR_Y));
		}

		this.addProperties(properties);
	}

	// ---------------------------------------------------------------- 访问器

	/** 转盘用的扇区表（服务端权威） */
	public WheelTable wheelTable() {
		return WheelTable.current();
	}

	/** 当前结果倍率；{@link WheelConstants#NO_RESULT} 表示还没有结果 */
	public int resultMultiplier() {
		return properties.get(WheelConstants.PROPERTY_RESULT_MULTIPLIER);
	}

	/** 抽奖序号，每次成功抽奖 +1 */
	public int spinSequence() {
		return properties.get(WheelConstants.PROPERTY_SPIN_SEQUENCE);
	}

	/** 鱼槽里的物品（只读用途） */
	public ItemStack fishStack() {
		return fishInventory.getStack(0);
	}

	public BlockPos wheelPos() {
		return pos;
	}

	// ---------------------------------------------------------------- 校验

	@Override
	public boolean canUse(PlayerEntity player) {
		// 原版实现：方块位置仍是本方块 && 距离 <= 8 格（与箱子一致）
		return canUse(context, player, ModBlocks.FISH_WHEEL);
	}

	// ---------------------------------------------------------------- 按钮

	/**
	 * 「开始」按钮（服务端权威）。
	 *
	 * <p>返回 {@code true} 时原版会自动 {@code sendContentUpdates()}，
	 * 把槽位内容与属性一起同步给客户端。
	 */
	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id != WheelConstants.BUTTON_SPIN) {
			return false;
		}
		// 客户端也会走到这里（原版行为），只让服务端产生结果
		if (!(player instanceof ServerPlayerEntity serverPlayer)) {
			return false;
		}

		return spin(serverPlayer);
	}

	/** 抽奖主流程（仅服务端） */
	private boolean spin(ServerPlayerEntity player) {
		EconomyConfig config = EconomyConfig.get();

		// 1) 冷却
		long now = System.currentTimeMillis();
		if (now - lastSpinAt < config.wheelSpinCooldownMs) {
			notifyPlayer(player, "wheel.minecraft_to_fish.msg.cooldown");
			return false;
		}

		// 2) 空槽
		ItemStack stack = fishInventory.getStack(0);
		if (stack.isEmpty()) {
			notifyPlayer(player, "wheel.minecraft_to_fish.msg.empty");
			return false;
		}

		// 3) 是否模组鱼
		if (config.wheelRequireModFish && !WheelSupport.isModFish(stack)) {
			notifyPlayer(player, "wheel.minecraft_to_fish.msg.not_mod_fish");
			return false;
		}

		// 4) 是否登记价值
		ValueResult current = ItemValueRegistry.resolve(stack);
		if (!current.hasValue()) {
			notifyPlayer(player, "wheel.minecraft_to_fish.msg.no_value");
			return false;
		}

		// 5) 掷骰（服务端随机源）—— 结果此刻已经确定，无法伪造
		WheelTable table = WheelTable.current();
		Random random = player.getWorld().getRandom();
		WheelSector sector = table.roll(random);

		// 6) <b>不立刻改动物品栏</b>：先把结果推给客户端播动画，
		//    等动画播完（settleAt 到点）再由 tickSettlement 真正结算。
		//    这样鱼在转动期间一直可见，消失/加成恰好发生在转盘停下的那一刻，
		//    不会「开始转之前鱼就没了」而提前剧透结果。
		pendingMultiplier = sector.multiplier();
		settling = true;
		settleAt = now + Math.max(0, config.wheelSpinDurationMs);
		// 上锁：转动期间两端都不能拿走/放入，保证结算时槽里还是那条鱼
		properties.set(WheelConstants.PROPERTY_SPINNING, 1);
		// 清空上一次的金额，否则转动期间客户端会读到上一次的结果
		setResultValue(WheelConstants.NO_RESULT_VALUE);

		// 7) 写属性：序号 +1，结果倍率。原版会在 onButtonClick 返回 true 后自动同步
		lastSpinAt = now;
		properties.set(WheelConstants.PROPERTY_SPIN_SEQUENCE, spinSequence() + 1);
		properties.set(WheelConstants.PROPERTY_RESULT_MULTIPLIER, sector.multiplier());

		// 8) 音效（纯表现，失败也不影响结果）
		try {
			player.getWorld().playSound(null, pos,
					sector.isWin() ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(),
					SoundCategory.BLOCKS, 1.0f, 1.0f);
		} catch (Throwable t) {
			MyMod.LOGGER.warn("[wheel] 播放抽奖音效失败：{}", t.toString());
		}

		if (config.debugLogging) {
			MyMod.LOGGER.info("[wheel] {} 抽奖：倍率 ×{}，价值 {}，{} ms 后结算",
					player.getName().getString(), sector.multiplier(), current.value(),
					config.wheelSpinDurationMs);
		}

		return true;
	}

	// ---------------------------------------------------------------- 延迟结算

	/**
	 * 是否正在转动、尚未结算。
	 * <p>读的是<b>同步属性</b>而非服务端字段，所以客户端拿到的值与服务端一致，
	 * 槽位锁在两端表现相同。
	 */
	public boolean isSpinning() {
		return properties.get(WheelConstants.PROPERTY_SPINNING) != 0;
	}

	/** 是否有一次抽奖正在等待结算（服务端用） */
	public boolean isSettling() {
		return settling;
	}

	/**
	 * 结算后的奖励金额。
	 *
	 * <p>由服务端在 {@link #tickSettlement()} 里写入，随属性一起同步给客户端。
	 * 客户端直接显示这个值，<b>不要</b>自己去读槽位算 ——
	 * 动画结束是客户端本地时钟决定的，与结算包到达时刻存在竞态，读到旧值就会显示错误金额。
	 *
	 * @return 奖励金额；{@link WheelConstants#NO_RESULT_VALUE} 表示尚未结算或未中奖
	 */
	public long resultValue() {
		if (properties.get(WheelConstants.PROPERTY_RESULT_VALUE_READY) == 0) {
			return WheelConstants.NO_RESULT_VALUE;
		}
		int low = properties.get(WheelConstants.PROPERTY_RESULT_VALUE_LOW);
		int high = properties.get(WheelConstants.PROPERTY_RESULT_VALUE_HIGH);
		return ((long) high << 32) | (low & 0xFFFFFFFFL);
	}

	/**
	 * 写入奖励金额（拆成高低两个 32 位，因为属性槽只能传 int）。
	 * <p>先写两个分量、再置就绪标志，保证客户端不会读到「半新半旧」的组合。
	 */
	private void setResultValue(long value) {
		if (value == WheelConstants.NO_RESULT_VALUE) {
			properties.set(WheelConstants.PROPERTY_RESULT_VALUE_READY, 0);
			return;
		}
		properties.set(WheelConstants.PROPERTY_RESULT_VALUE_LOW, (int) value);
		properties.set(WheelConstants.PROPERTY_RESULT_VALUE_HIGH, (int) (value >> 32));
		properties.set(WheelConstants.PROPERTY_RESULT_VALUE_READY, 1);
	}

	/**
	 * 到点后真正结算（每 tick 由服务端调用）。
	 *
	 * <p>掷骰在 {@link #spin} 里已经完成，这里只负责把结果落到物品栏上，
	 * 让「鱼消失 / 价值变化」与客户端动画结束对齐。
	 */
	public void tickSettlement() {
		if (!settling || System.currentTimeMillis() < settleAt) {
			return;
		}
		settling = false;

		ItemStack stack = fishInventory.getStack(0);
		if (stack.isEmpty()) {
			// 转动期间物品被拿走（比如界面被关掉后又交还过），没什么可结算的
			properties.set(WheelConstants.PROPERTY_SPINNING, 0);
			setResultValue(WheelConstants.NO_RESULT_VALUE);
			return;
		}

		if (pendingMultiplier > 0) {
			// 中奖：把加成写在物品自身 NBT 上，留在槽里
			long baseValue = ItemValueRegistry.resolve(stack).value();
			long newValue = WheelTable.applyMultiplier(baseValue, pendingMultiplier);
			long cap = EconomyConfig.get().wheelMaxValue;
			if (cap > 0L && newValue > cap) {
				newValue = cap;
			}
			ItemStack result = stack.copy();
			StackValueOverride.write(result, newValue);
			fishInventory.setStack(0, result);

			// 权威金额：客户端直接显示这个值，不去猜槽位同步的时机
			setResultValue(newValue);
		} else {
			// 未中奖：鱼在转盘停下的这一刻消失。
			// 金额写 0 并置就绪 —— 这是「已结算但没收益」的合法状态，
			// 与「尚未结算」(-1) 区分开，客户端才能正确决定显不显示结果文字。
			fishInventory.setStack(0, ItemStack.EMPTY);
			setResultValue(0L);
		}
		fishInventory.markDirty();

		// 解锁放在最后：确保客户端收到本次同步时，槽位数据与金额已经是最终值
		properties.set(WheelConstants.PROPERTY_SPINNING, 0);

		// 同步给客户端，让槽位显示与动画结束同步
		this.sendContentUpdates();
	}

	/** 放弃尚未落地的结算（界面关闭时调用，避免关掉界面后凭空改背包） */
	private void cancelSettlement() {
		settling = false;
		pendingMultiplier = 0;
		properties.set(WheelConstants.PROPERTY_SPINNING, 0);
		setResultValue(WheelConstants.NO_RESULT_VALUE);
	}

	private static void notifyPlayer(ServerPlayerEntity player, String key) {
		player.sendMessage(Text.translatable(key), true);
	}

	// ---------------------------------------------------------------- 物品移动

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasStack()) {
			return ItemStack.EMPTY;
		}

		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();

		final int fishSlotIndex = 0;
		final int mainStart = FISH_SLOT_COUNT;
		final int mainEnd = mainStart + PLAYER_INV_ROWS * PLAYER_INV_COLS;
		final int hotbarStart = mainEnd;
		final int hotbarEnd = hotbarStart + PLAYER_INV_COLS;

		if (index == fishSlotIndex) {
			// 鱼槽 → 背包。转动期间禁止（canTakeItems 不拦 quickMove，要单独判）
			if (isSpinning()) {
				return ItemStack.EMPTY;
			}
			if (!this.insertItem(stack, mainStart, hotbarEnd, true)) {
				return ItemStack.EMPTY;
			}
			slot.onQuickTransfer(stack, original);
		} else if (index >= mainStart && index < hotbarEnd) {
			// 背包 → 先试鱼槽；转动期间鱼槽已锁，直接走背包内部的移动
			if (!isSpinning() && this.insertItem(stack, fishSlotIndex, fishSlotIndex + 1, false)) {
				// 已放进鱼槽
			} else if (index < mainEnd) {
				// 主背包 → 快捷栏
				if (!this.insertItem(stack, hotbarStart, hotbarEnd, false)) {
					return ItemStack.EMPTY;
				}
			} else {
				// 快捷栏 → 主背包
				if (!this.insertItem(stack, mainStart, mainEnd, false)) {
					return ItemStack.EMPTY;
				}
			}
		} else {
			return ItemStack.EMPTY;
		}

		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	// ---------------------------------------------------------------- 关闭与交还

	@Override
	public void onClosed(PlayerEntity player) {
		super.onClosed(player);

		// 这个方法在客户端也会被调用（HandledScreen.removed()），不判断就会往客户端背包塞东西
		if (!(player instanceof ServerPlayerEntity)) {
			return;
		}
		returnFish(player);
	}

	/**
	 * 把鱼槽里的物品交还玩家（幂等）。
	 * <p>由三处调用：正常关闭、断线（{@link WheelSafety}）、停服（{@link WheelSafety}）。
	 */
	public void returnFish(PlayerEntity player) {
		if (closed) {
			return;
		}
		closed = true;

		// 界面关掉后就不该再动物品栏了：放弃尚未落地的结算。
		// 此时掷骰结果作废，鱼按原样归还 —— 玩家没看到动画，就不该承担结果。
		cancelSettlement();

		ItemStack stack = fishInventory.getStack(0);
		if (stack.isEmpty()) {
			return;
		}
		fishInventory.setStack(0, ItemStack.EMPTY);
		fishInventory.markDirty();

		// 玩家已死时直接掉在地上，避免塞进即将被清空的尸体背包
		if (!player.isAlive()) {
			player.dropItem(stack, false);
			return;
		}

		// 活着：优先塞背包，装不下自动掉在脚下
		if (!player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
	}

	/** 该界面是否已经交还过物品 */
	public boolean isClosedAndReturned() {
		return closed;
	}
}
