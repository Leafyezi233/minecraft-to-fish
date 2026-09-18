package com.leafyezi233.minecrafttofish.wheel;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 渔轮转盘的界面工厂：把方块坐标随开屏数据一起下发给客户端。
 *
 * <p>为什么要下发坐标：客户端 {@code HandledScreen} 需要自己做 {@code canUse} 校验
 * （原版 {@code PlayerEntity.tick()} 会在校验失败时自动关界面）。
 * 两端拿到同一份坐标，校验逻辑才对称。
 */
public class FishWheelFactory implements ExtendedScreenHandlerFactory {

	private final BlockPos pos;

	public FishWheelFactory(BlockPos pos) {
		this.pos = pos;
	}

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.minecraft_to_fish." + WheelConstants.PATH);
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		// 服务端调用路径：坐标由本对象持有，不需要从缓冲区读
		return new FishWheelScreenHandler(syncId, playerInventory, pos);
	}

	@Override
	public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
		buf.writeBlockPos(pos);
	}
}
