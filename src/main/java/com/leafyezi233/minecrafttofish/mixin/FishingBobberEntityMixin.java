package com.leafyezi233.minecrafttofish.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.leafyezi233.minecrafttofish.entity.AggressiveFishEntity;
import com.leafyezi233.minecrafttofish.entity.BrutalFishEntity;
import com.leafyezi233.minecrafttofish.entity.ModEntities;
import com.leafyezi233.minecrafttofish.entity.TimidFishEntity;

/**
 * 钓鱼时生成模组生物。
 * 钩到鱼钩的 tickFishingLogic：当钓上鱼（caughtFish 为 true）时，
 * <b>必定</b>从三种模组生物中随机生成一只（不再是各自独立掷骰）。
 * 相对权重：攻击性鱼 50 / 微缩鱼 30 / 凶猛的鱼 20。
 * （原版掉落物逻辑不受影响，钓上来照样有鱼获物品。）
 */
@Mixin(FishingBobberEntity.class)
public class FishingBobberEntityMixin {
	/** 攻击性鱼权重（最常见） */
	private static final int WEIGHT_AGGRESSIVE = 50;
	/** 凶猛的鱼权重（最稀有） */
	private static final int WEIGHT_BRUTAL = 20;
	/** 微缩鱼（胆小的鱼）权重 */
	private static final int WEIGHT_TIMID = 30;
	/** 权重总和 */
	private static final int WEIGHT_TOTAL = WEIGHT_AGGRESSIVE + WEIGHT_BRUTAL + WEIGHT_TIMID;

	// FishingBobberEntity 的私有字段：是否钓到了鱼
	@Shadow
	private boolean caughtFish;

	/** 本次咬钩是否已判定过生成，避免每 tick 重复生成 */
	@Unique
	private boolean mtf$handledCatch;

	@Inject(method = "tickFishingLogic", at = @At("TAIL"))
	private void mtf$spawnModFish(BlockPos pos, CallbackInfo ci) {
		FishingBobberEntity bobber = (FishingBobberEntity) (Object) this;
		if (bobber.getWorld().isClient()) {
			return;
		}

		if (this.caughtFish) {
			// 每次上钩只判定一次；咬钩期间 caughtFish 会持续为 true，不能每 tick 都摇
			if (!this.mtf$handledCatch) {
				this.mtf$handledCatch = true;
				// 只摇一次骰子，按权重决定出哪一只 —— 必定出一只
				int roll = bobber.getWorld().random.nextInt(WEIGHT_TOTAL);
				if (roll < WEIGHT_AGGRESSIVE) {
					this.mtf$spawnAggressive(bobber);
				} else if (roll < WEIGHT_AGGRESSIVE + WEIGHT_BRUTAL) {
					this.mtf$spawnBrutal(bobber);
				} else {
					this.mtf$spawnTimid(bobber);
				}
			}
		} else {
			// 收回/重置后允许下一次咬钩再判定
			this.mtf$handledCatch = false;
		}
	}

	/** 在鱼钩位置生成攻击性鱼 */
	@Unique
	private void mtf$spawnAggressive(FishingBobberEntity bobber) {
		AggressiveFishEntity fish = ModEntities.AGGRESSIVE_FISH.create(bobber.getWorld());
		if (fish != null) {
			fish.refreshPositionAndAngles(bobber.getX(), bobber.getY(), bobber.getZ(), 0.0f, 0.0f);
			bobber.getWorld().spawnEntity(fish);
		}
	}

	/** 在鱼钩位置生成凶猛的鱼 */
	@Unique
	private void mtf$spawnBrutal(FishingBobberEntity bobber) {
		BrutalFishEntity brutal = ModEntities.BRUTAL_FISH.create(bobber.getWorld());
		if (brutal != null) {
			brutal.refreshPositionAndAngles(bobber.getX(), bobber.getY(), bobber.getZ(), 0.0f, 0.0f);
			bobber.getWorld().spawnEntity(brutal);
		}
	}

	/** 在鱼钩位置生成微缩鱼，并让它沿抛物线飞向玩家脚底 */
	@Unique
	private void mtf$spawnTimid(FishingBobberEntity bobber) {
		TimidFishEntity timid = ModEntities.TIMID_FISH.create(bobber.getWorld());
		if (timid != null) {
			// 在鱼钩位置生成（起点就是鱼钩）
			timid.refreshPositionAndAngles(bobber.getX(), bobber.getY(), bobber.getZ(), 0.0f, 0.0f);
			bobber.getWorld().spawnEntity(timid);
			// 目标点：玩家脚底（没有玩家时朝鱼钩上方飞一下）
			if (bobber.getPlayerOwner() != null) {
				timid.startFlightTo(new Vec3d(
						bobber.getPlayerOwner().getX(),
						bobber.getPlayerOwner().getY() + 0.2,
						bobber.getPlayerOwner().getZ()));
			} else {
				timid.startFlightTo(new Vec3d(bobber.getX(), bobber.getY() + 1.5, bobber.getZ()));
			}
		}
	}
}
