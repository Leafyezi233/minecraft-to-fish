package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 渔轮转盘方块。
 *
 * <p><b>刻意不继承 {@code BlockWithEntity}</b>：槽位里的鱼属于「这次打开界面」的临时状态，
 * 不需要挂在方块坐标上，所以不需要 BlockEntity。方块本身只是个「开界面的开关」。
 *
 * <p>开界面的流程走原版 {@code onUse}：
 * <ul>
 *   <li>客户端只返回 {@link ActionResult#SUCCESS}，不做事</li>
 *   <li>服务端调 {@code openHandledScreen} 后返回 {@link ActionResult#CONSUME}</li>
 * </ul>
 * 这样两端不会各开一次界面。
 */
public class FishWheelBlock extends Block {

	public FishWheelBlock(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult onUse(BlockState state, World world, BlockPos pos,
			PlayerEntity player, Hand hand, BlockHitResult hit) {

		if (!EconomyConfig.get().wheelEnabled) {
			// 关闭时给玩家一个明确反馈，而不是"右键没反应"
			if (!world.isClient) {
				player.sendMessage(net.minecraft.text.Text.translatable(
						"wheel.minecraft_to_fish.disabled"), true);
			}
			return ActionResult.FAIL;
		}

		if (world.isClient) {
			// 客户端：返回 SUCCESS 让原版把手部摆动等表现做出来，实际开界面由服务端触发
			return ActionResult.SUCCESS;
		}

		NamedScreenHandlerFactory factory = createScreenHandlerFactory(state, world, pos);
		if (factory == null) {
			return ActionResult.PASS;
		}

		player.openHandledScreen(factory);
		return ActionResult.CONSUME;
	}

	@Override
	public NamedScreenHandlerFactory createScreenHandlerFactory(BlockState state, World world, BlockPos pos) {
		// 携带 BlockPos，供两端 canUse 做对称的距离/方块校验
		return new FishWheelFactory(pos);
	}
}
