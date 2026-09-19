package com.leafyezi233.minecrafttofish.galton;

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
import net.minecraft.world.WorldView;

/**
 * 高尔顿板方块。
 *
 * <h2>⚠️ 必须重写 {@link #getRenderType}</h2>
 * 已用字节码确认：{@code BlockWithEntity.getRenderType} 返回
 * {@link BlockRenderType#INVISIBLE}。不重写的话方块模型<b>直接消失</b>
 * （方块还在、还能挖，但看不见）。
 *
 * <h2>盘面向上超出 1 格，所以放置有额外要求</h2>
 * 竖着排 6 排钉子 + 7 个槽位在单格内画不下，盘面渲染向上延伸到约 y≈1.95。
 * 因此：
 * <ul>
 *   <li>放置时校验<b>上方两格是空气</b>（见 {@link #canPlaceAt}），
 *       否则盘面会插进上方的方块里</li>
 *   <li>碰撞箱<b>仍然只有</b>下面那块 1/8 格底板，不是两格高的墙 ——
 *       盘面是「看得见但不挡路」的表现层，玩家应该能站在板前面</li>
 * </ul>
 *
 * <h2>交互方式（无界面，与转盘同一套手势）</h2>
 * <table border="1">
 *   <tr><th>手势</th><th>结果</th></tr>
 *   <tr><td>手持鱼右键</td><td>放入（消耗 1 条）</td></tr>
 *   <tr><td>空手右键</td><td>开落（球开始往下掉）</td></tr>
 *   <tr><td>潜行 + 右键</td><td>把鱼取出、掉在地上</td></tr>
 *   <tr><td>下落中右键</td><td>一律拒绝</td></tr>
 * </table>
 * 另外「把鱼丢在板上」也会被自动吸入（由基类的 {@code suctionTick} 负责）。
 *
 * <h2>关于 {@code onUse} / {@code onStateReplaced} 的 deprecation 警告</h2>
 * 与转盘同理：这套 Yarn 映射里 {@code AbstractBlock} 的这两个方法被标了
 * {@code @Deprecated}（是本映射的普遍现象，并非针对本类），
 * 而 {@code Block} / {@code BlockState} 都没有声明替代方法，
 * 原版自己的 {@code CraftingTableBlock} 也照样重写 {@code onUse}。
 * 因此显式压掉，避免每次构建都留下看不懂的噪音。
 */
@SuppressWarnings("deprecation")
public class GaltonBlock extends BlockWithEntity {

	public GaltonBlock(Settings settings) {
		super(settings);
	}

	// ---------------------------------------------------------------- 方块实体

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new FishGaltonBlockEntity(pos, state);
	}

	/**
	 * 碰撞箱 / 选取框 = 那块 1/8 格厚的底板，与模型一致。
	 *
	 * <p><b>刻意不覆盖上方那两格</b>：盘面虽然画到 y≈1.95，
	 * 但那是渲染层。若把碰撞箱也做成两格高，玩家就没法贴着板站、也没法从上方看，
	 * 而且会在通道里凭空多出一堵两格高的墙。
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
	 * 服务端每 tick 驱动落球结算。
	 * <p>客户端返回 {@code null} —— 客户端不需要 tick 方块实体，
	 * 它的表现（球的轨迹）由渲染层按同步来的路径与进度算出来。
	 */
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
			BlockEntityType<T> type) {

		if (world.isClient) {
			return null;
		}
		return checkType(type, GaltonBlocks.FISH_GALTON_BLOCK_ENTITY,
				(BlockEntityTicker<FishGaltonBlockEntity>) (tickWorld, tickPos, tickState, galton) -> {
					if (tickWorld instanceof ServerWorld serverWorld) {
						galton.serverTick(serverWorld);
					}
				});
	}

	// ---------------------------------------------------------------- 放置

	/**
	 * 盘面上方是否有空间。
	 *
	 * <p><b>这是「加高」这一决定的唯一判定点</b>：{@link #canPlaceAt} 与
	 * {@link GaltonBlockItem} 的提示逻辑<b>都</b>调用它。
	 *
	 * <p>之所以抽成静态方法，是因为第一版把这条规则在 item 里<b>又写了一遍</b>，
	 * 而且写法自相矛盾：那边用
	 * {@code state.canPlaceAt(...) && !上方可替换} 判断「是不是被这条规则挡住的」，
	 * 但 {@code canPlaceAt} 本身<b>已经包含</b>了这条规则 ——
	 * 被挡住时它必然是 false，整个条件恒为假，提示永远不会发出。
	 * 规则只有一份，才不会出现这种「两个地方各写一遍、然后对不上」的事。
	 */
	public static boolean hasRoomAbove(WorldView world, BlockPos pos) {
		// 可替换 = 空气 / 草 / 雪层 / 水等，盘面盖过去没有视觉冲突
		return world.getBlockState(pos.up()).isReplaceable();
	}

	/**
	 * 盘面要向上占约 2 格，所以上方必须是空气。
	 *
	 * <p><b>为什么需要这条</b>：盘面渲染向上延伸到 y≈1.95，
	 * 若上方有方块，盘面会直接插进去 —— 表现为钉子、槽位、球
	 * 「糊在墙里」，玩家看不出这是个游戏机。
	 * 与其让它穿模，不如在放置时就拒绝，并由 {@link GaltonBlockItem} 给出明确提示。
	 *
	 * <p>只查 y+1（盘面顶到 1.95，仍在第二格内）。
	 */
	@Override
	public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
		return super.canPlaceAt(state, world, pos) && hasRoomAbove(world, pos);
	}

	/**
	 * 放置时记下盘面朝向（朝向玩家，也就是「正面朝向你」）。
	 *
	 * <p><b>为什么写进方块实体而不是方块状态</b>：新增一个 {@code BlockState} 属性会改变
	 * 本方块的状态总数与 id 分配，旧存档里已放置的方块会按 id 落到别的状态上。
	 * 存方块实体对存档是纯增量，旧数据只是没有这个键。
	 */
	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
		super.onPlaced(world, pos, state, placer, itemStack);

		if (world.isClient || placer == null) {
			return;
		}
		FishGaltonBlockEntity galton = blockEntityAt(world, pos);
		if (galton != null) {
			// getHorizontalFacing() = 玩家面朝的方向。盘面朝向玩家，所以取反。
			galton.setFacing(placer.getHorizontalFacing().getOpposite());
			galton.sync();
		}
	}

	// ---------------------------------------------------------------- 破坏兜底

	/**
	 * 方块被破坏 / 被替换时，把板上的鱼掉出来。
	 *
	 * <p><b>为什么必须有这一步</b>：鱼存在 BlockEntity 里，
	 * 而 BlockEntity 会随方块一起被原版移除。不接管的话，
	 * 玩家一镐子下去，板上那条鱼（可能还带着加成）就凭空消失了 ——
	 * 这是把状态搬进方块实体后必然引入的丢物品路径，必须堵上。
	 *
	 * <p>覆盖所有移除路径：玩家挖掘、爆炸、{@code /setblock}、活塞推动。
	 * 下落途中被破坏时，未落地的结算一并作废，鱼<b>按原样</b>掉出（不吞不掉）。
	 */
	@Override
	public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
		// 同一个方块的属性变化不算被替换，别把鱼弹出来
		if (!state.isOf(newState.getBlock()) && world instanceof ServerWorld) {
			BlockEntity entity = world.getBlockEntity(pos);
			if (entity instanceof FishGaltonBlockEntity galton) {
				// 用 removeFishForDrop 而不是 takeFish：方块正在消失，
				// 此刻再去 updateListeners 广播一个即将不存在的位置没有意义。
				ItemStack fish = galton.removeFishForDrop();
				if (!fish.isEmpty()) {
					dropStack(world, pos, fish);
				}
			}
		}
		super.onStateReplaced(state, world, pos, newState, moved);
	}

	// ---------------------------------------------------------------- 交互

	/**
	 * 右键交互（<b>无界面三态</b>，手势表见类注释）。
	 *
	 * <h2>⚠️ 潜行取回的前提是「空手」</h2>
	 * 原版在 {@code ServerPlayerInteractionManager.interactBlock} 与客户端对应实现里
	 * 有一段<b>完全一致</b>的短路（已逐条比对字节码）：
	 * <pre>
	 *   sneaking &amp;&amp; (主手非空 || 副手非空)  →  不调用 onUse
	 * </pre>
	 * 也就是说潜行且手上拿着东西时，本方法<b>根本不会被调用</b>。
	 * 所以「潜行取回」这个手势天然只能是空手潜行。
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

		if (!EconomyConfig.get().galtonEnabled) {
			// 关闭时给玩家一个明确反馈，而不是「右键没反应」
			if (!world.isClient) {
				player.sendMessage(Text.translatable("galton.minecraft_to_fish.disabled"), true);
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

		FishGaltonBlockEntity galton = blockEntityAt(world, pos);
		if (galton == null) {
			// 方块实体还没建好（极端时序）；不消耗这次交互，让玩家重试
			return ActionResult.PASS;
		}

		ItemStack held = player.getStackInHand(hand);

		// 1) 潜行 = 取回。放在最前面：潜行是明确的「我要取出」意图，
		//    不能因为手上拿着鱼就变成「放入」。
		if (player.isSneaking()) {
			if (galton.isDropping()) {
				player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.dropping"), true);
				return ActionResult.FAIL;
			}
			if (galton.tryRetrieve(serverWorld, player)) {
				player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.retrieved"), true);
				return ActionResult.SUCCESS;
			}
			player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.empty"), true);
			return ActionResult.FAIL;
		}

		// 2) 手上有东西 = 放入
		if (!held.isEmpty()) {
			return switch (galton.tryInsert(serverWorld, held)) {
				case OK -> {
					// 创造模式不扣物品，否则玩家放一条少一条
					if (!player.getAbilities().creativeMode) {
						held.decrement(1);
					}
					player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.inserted"), true);
					yield ActionResult.SUCCESS;
				}
				case BUSY -> {
					player.sendMessage(Text.translatable(
							galton.isDropping()
									? "galton.minecraft_to_fish.msg.dropping"
									: "galton.minecraft_to_fish.msg.occupied"), true);
					yield ActionResult.FAIL;
				}
				case REJECTED -> {
					player.sendMessage(Text.translatable(
							EconomyConfig.get().galtonRequireModFish && !GameSupport.isModFish(held)
									? "galton.minecraft_to_fish.msg.not_mod_fish"
									: "galton.minecraft_to_fish.msg.no_value"), true);
					yield ActionResult.FAIL;
				}
				case EMPTY -> ActionResult.PASS;
			};
		}

		// 3) 空手 = 开落
		if (galton.isDropping()) {
			player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.dropping"), true);
			return ActionResult.FAIL;
		}
		if (!galton.hasFish()) {
			player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.empty"), true);
			return ActionResult.FAIL;
		}
		if (galton.isCoolingDown(serverWorld)) {
			player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.cooldown"), true);
			return ActionResult.FAIL;
		}

		if (galton.tryDrop(serverWorld, player) == GaltonConstants.NO_SLOT) {
			// 竞态：判定通过后状态又变了（比如另一个玩家抢先开落）
			player.sendMessage(Text.translatable("galton.minecraft_to_fish.msg.dropping"), true);
			return ActionResult.FAIL;
		}

		// 广播给所有人：高尔顿板是公共设施，谁按的都该让全场知道。
		// 注意这里只说「谁开落了」，不说输赢 —— 结果要等球落地才揭晓，
		// 现在就把输赢喊出来等于提前剧透。
		Text message = Text.translatable("galton.minecraft_to_fish.broadcast.drop", player.getDisplayName());
		for (ServerPlayerEntity viewer : serverWorld.getPlayers()) {
			viewer.sendMessage(message, false);
		}
		return ActionResult.SUCCESS;
	}

	/** 供后续阶段与自测使用：按坐标取高尔顿板状态 */
	public static FishGaltonBlockEntity blockEntityAt(World world, BlockPos pos) {
		BlockEntity entity = world.getBlockEntity(pos);
		return entity instanceof FishGaltonBlockEntity galton ? galton : null;
	}
}
