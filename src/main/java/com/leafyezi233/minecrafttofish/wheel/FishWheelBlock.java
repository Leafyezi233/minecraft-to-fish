package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.game.GameSupport;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * 渔轮转盘方块。
 *
 * <h2>为什么改成 {@link BlockWithEntity}</h2>
 * 改造前本方块刻意不挂 BlockEntity —— 那时鱼存在「打开界面的那个玩家」的
 * {@code ScreenHandler} 里，方块只是个开界面的开关。
 * 但这也意味着状态是<b>每人一份</b>，物理上做不到「所有玩家可见」。
 * 挂上 BlockEntity 后，一个坐标只有一份状态，转盘才成为公共设施。
 *
 * <h2>⚠️ 必须重写 {@link #getRenderType}</h2>
 * 已用字节码确认：{@code BlockWithEntity.getRenderType} 返回
 * {@link BlockRenderType#INVISIBLE}。不重写的话方块模型<b>直接消失</b>
 * （方块还在、还能挖，但看不见）。
 *
 * <h2>交互方式（无界面）</h2>
 * 手持鱼右键放入、空手右键开转、潜行右键取回（鱼掉在地上）、
 * 或者干脆把鱼丢在盘上等它被自动吸入。转盘是公共设施，任何人都能操作，
 * 收益加在鱼身上，谁捡到算谁的。
 *
 * <h2>关于 {@code onUse} / {@code onStateReplaced} 的 deprecation 警告</h2>
 * 编译时这两个重写会报「使用了已过时的 API」。已用字节码确认原因：
 * 这套 Yarn 映射里 {@code AbstractBlock} 的这两个方法被标了 {@code @Deprecated}
 * （{@code AbstractBlock} 共 41 个实例方法被标记，是本映射的普遍现象，
 * 并非针对本类）。而 {@code Block} / {@code BlockState} <b>都没有</b>声明这两个方法，
 * 原版自己的 {@code CraftingTableBlock} 也照样重写 {@code onUse}，
 * 说明这就是当前版本唯一的挂载点。
 * 因此这里用 {@link SuppressWarnings} 显式压掉，避免每次构建都留下看不懂的噪音；
 * 将来升级映射时若出现替代 API，应改用新 API 而不是继续压警告。
 */
@SuppressWarnings("deprecation")
public class FishWheelBlock extends BlockWithEntity {

	public FishWheelBlock(Settings settings) {
		super(settings);
	}

	// ---------------------------------------------------------------- 方块实体

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FishWheelBlockEntity(pos, state);
	}

	/**
	 * 碰撞箱 / 选取框 = 那块 1/8 格厚的底板，与模型一致。
	 *
	 * <p>默认是整格立方体。若不改，玩家会撞到盘面下方那半格空气，
	 * 看上去像有一堵看不见的墙；挖掘时的高亮框也会是整格，与薄板模型对不上。
	 */
	private static final VoxelShape SHAPE = Block.createCuboidShape(1.0, 0.0, 1.0, 15.0, 2.0, 15.0);

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	/**
	 * 让方块模型正常显示。
	 * <p><b>漏了这一步方块会隐形</b>：{@code BlockWithEntity} 的默认实现返回
	 * {@link BlockRenderType#INVISIBLE}（已用字节码确认）。
	 */
	@Override
	public BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	/**
	 * 服务端每 tick 驱动转盘结算。
	 * <p>客户端返回 {@code null} —— 客户端不需要 tick 方块实体，
	 * 它的表现（转动动画）由后续阶段的渲染层按同步来的序号驱动。
	 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {

		if (world.isClient) {
			return null;
		}
		return checkType(type, ModBlocks.FISH_WHEEL_BLOCK_ENTITY,
				(BlockEntityTicker<FishWheelBlockEntity>) (tickWorld, tickPos, tickState, wheel) -> {
					if (tickWorld instanceof ServerWorld serverWorld) {
						wheel.serverTick(serverWorld);
					}
				});
	}

	// ---------------------------------------------------------------- 放置

	/**
	 * 放置时记下盘面朝向（朝向玩家，也就是「正面朝向你」）。
	 *
	 * <p><b>为什么写进方块实体而不是方块状态</b>：新增一个 {@code BlockState} 属性会改变
	 * 本方块的状态总数与 id 分配，旧存档里已放置的转盘会按 id 落到别的状态上。
	 * 存方块实体对存档是纯增量，旧数据只是没有这个键。
	 */
	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);

		if (world.isClient || placer == null) {
			return;
		}
		FishWheelBlockEntity wheel = blockEntityAt(world, pos);
		if (wheel != null) {
			// getHorizontalFacing() = 玩家面朝的方向。盘面朝向玩家，所以取反。
			wheel.setFacing(placer.getHorizontalFacing().getOpposite());
			wheel.sync();
		}
	}

	// ---------------------------------------------------------------- 破坏兜底

	/**
	 * 方块被破坏 / 被替换时，把盘上的鱼掉出来。
	 *
	 * <p><b>为什么必须有这一步</b>：鱼现在存在 BlockEntity 里，
	 * 而 BlockEntity 会随方块一起被原版移除。不接管的话，
	 * 玩家一镐子下去，盘上的鱼（可能还带着转盘加成）就凭空消失了 ——
	 * 这是把状态搬进方块实体后新引入的丢物品路径，必须堵上。
	 *
	 * <p>覆盖所有移除路径：玩家挖掘、爆炸、{@code /setblock}、活塞推动。
	 * 转动途中被破坏时，未落地的结算一并作废，鱼<b>按原样</b>掉出（不吞不掉）。
	 */
	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		// 同一个方块的属性变化（比如后续可能加朝向）不算被替换，别把鱼弹出来
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld) {
			BlockEntity entity = world.getBlockEntity(pos);
			if (entity instanceof FishWheelBlockEntity wheel) {
				// 用 removeFishForDrop 而不是 takeFish：方块正在消失，
				// 此刻再去 updateListeners 广播一个即将不存在的位置没有意义。
				ItemStack fish = wheel.removeFishForDrop();
				if (!fish.isEmpty()) {
					dropStack(world, pos, fish);
				}
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	// ---------------------------------------------------------------- 交互

	/**
	 * 右键交互（<b>无界面三态</b>）。
	 *
	 * <h2>手势表</h2>
	 * <table border="1">
	 *   <tr><th>手势</th><th>结果</th></tr>
	 *   <tr><td>手持鱼右键</td><td>放入（消耗 1 条）</td></tr>
	 *   <tr><td>空手右键</td><td>开转</td></tr>
	 *   <tr><td>潜行 + 右键</td><td>把鱼取出、掉在地上</td></tr>
	 *   <tr><td>转动中右键</td><td>一律拒绝</td></tr>
	 * </table>
	 *
	 * <p>另外「把鱼丢在盘上」也会被自动吸入（见
	 * {@link FishWheelBlockEntity#serverTick}），两条入口并存。
	 *
	 * <h2>⚠️ 潜行取回的前提是「空手」</h2>
	 * 原版在 {@code ServerPlayerInteractionManager.interactBlock} 与客户端对应实现里
	 * 有一段<b>完全一致</b>的短路（已逐条比对字节码）：
	 * <pre>
	 *   sneaking &amp;&amp; (主手非空 || 副手非空)  →  不调用 onUse
	 * </pre>
	 * 也就是说潜行且手上拿着东西时，本方法<b>根本不会被调用</b>。
	 * 所以「潜行取回」这个手势天然只能是空手潜行 —— 手上拿着东西时，
	 * 客户端连包都不会发，这里再怎么判都无济于事。
	 * 好在「手上有东西」本来就走放入分支，两个意图不会打架。
	 *
	 * <h2>为什么客户端也走一遍</h2>
	 * 两端都执行同一套判定，客户端才能立刻给出正确的反馈
	 * （手部摆动、音效、消息），而不是等服务端包回来才动。
	 * 真正改状态的只有服务端 —— 客户端分支里所有写操作都被 {@code world.isClient} 挡掉。
	 */
	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {

		if (!EconomyConfig.get().wheelEnabled) {
			// 关闭时给玩家一个明确反馈，而不是「右键没反应」
			if (!world.isClient) {
				player.sendMessage(Text.translatable("wheel.minecraft_to_fish.disabled"), true);
			}
			return ActionResult.FAIL;
		}

		if (world.isClient) {
			// 客户端只负责表现，返回 SUCCESS 让原版做手部摆动
			return ActionResult.SUCCESS;
		}

		if (!(world instanceof ServerWorld serverWorld)) {
			return ActionResult.PASS;
		}

		FishWheelBlockEntity wheel = blockEntityAt(world, pos);
		if (wheel == null) {
			// 方块实体还没建好（极端时序）；不消耗这次交互，让玩家重试
			return ActionResult.PASS;
		}

		ItemStack held = player.getStackInHand(hand);

		// 1) 潜行 = 取回。放在最前面：潜行是明确的「我要取出」意图，
		//    不能因为手上拿着鱼就变成「放入」。
		if (player.isSneaking()) {
			if (wheel.isSpinning()) {
				player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.spinning"), true);
				return ActionResult.FAIL;
			}
			if (wheel.tryRetrieve(serverWorld, player)) {
				player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.retrieved"), true);
				return ActionResult.SUCCESS;
			}
			player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.empty"), true);
			return ActionResult.FAIL;
		}

		// 2) 手上有东西 = 放入
		if (!held.isEmpty()) {
			return switch (wheel.tryInsert(serverWorld, held)) {
				case OK -> {
					// 创造模式不扣物品，否则玩家放一条少一条
					if (!player.getAbilities().creativeMode) {
						held.decrement(1);
					}
					player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.inserted"), true);
					yield ActionResult.SUCCESS;
				}
				case BUSY -> {
					player.sendMessage(Text.translatable(
							wheel.isSpinning()
									? "wheel.minecraft_to_fish.msg.spinning"
									: "wheel.minecraft_to_fish.msg.occupied"), true);
					yield ActionResult.FAIL;
				}
				case REJECTED -> {
					player.sendMessage(Text.translatable(
							EconomyConfig.get().wheelRequireModFish && !GameSupport.isModFish(held)
									? "wheel.minecraft_to_fish.msg.not_mod_fish"
									: "wheel.minecraft_to_fish.msg.no_value"), true);
					yield ActionResult.FAIL;
				}
				case EMPTY -> ActionResult.PASS;
			};
		}

		// 3) 空手 = 开转
		if (wheel.isSpinning()) {
			player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.spinning"), true);
			return ActionResult.FAIL;
		}
		if (!wheel.hasFish()) {
			player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.empty"), true);
			return ActionResult.FAIL;
		}
		if (wheel.isCoolingDown(serverWorld)) {
			player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.cooldown"), true);
			return ActionResult.FAIL;
		}

		if (wheel.trySpin(serverWorld, player) == null) {
			// 竞态：判定通过后状态又变了（比如另一个玩家抢先开转）
			player.sendMessage(Text.translatable("wheel.minecraft_to_fish.msg.spinning"), true);
			return ActionResult.FAIL;
		}

		// 广播给所有人：转盘是公共设施，谁按的都该让全场知道。
		// 注意这里只说「谁开转了」，不说输赢 —— 结果要等转盘落地才揭晓，
		// 现在就把输赢喊出来等于提前剧透。
		Text message = Text.translatable("wheel.minecraft_to_fish.broadcast.spin", player.getDisplayName());
		for (ServerPlayerEntity viewer : serverWorld.getPlayers()) {
			viewer.sendMessage(message, false);
		}
		return ActionResult.SUCCESS;
	}

	/** 供后续阶段与自测使用：按坐标取转盘状态 */
	public static FishWheelBlockEntity blockEntityAt(World world, BlockPos pos) {
		BlockEntity entity = world.getBlockEntity(pos);
		return entity instanceof FishWheelBlockEntity wheel ? wheel : null;
	}
}
