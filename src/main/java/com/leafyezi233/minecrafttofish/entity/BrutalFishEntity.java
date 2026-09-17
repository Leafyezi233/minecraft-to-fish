package com.leafyezi233.minecrafttofish.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

/**
 * 第二条鱼：凶猛的鱼（比攻击性鱼更强、更大）。
 * - 和 AggressiveFishEntity 逻辑几乎一样（弹跳、离水不受伤、追击玩家、掉鱼）
 * - 差异：更大（注册时尺寸更大）、血量更高、攻击更高、掉落鲑鱼、贴图用鲑鱼
 */
public class BrutalFishEntity extends FishEntity {
	/** 陆地跳跃高度（垂直速度） */
	private static final double JUMP_HEIGHT = 0.8;
	/** 上一 tick 是否触水，用来检测"刚碰到水面"的瞬间 */
	private boolean prevTouchingWater;
	/** 上次起跳的 age（tick），保证每次落地都能起跳，形成清晰跳跃节奏 */
	private int lastJumpAge = -100;

	public BrutalFishEntity(EntityType<? extends FishEntity> entityType, World world) {
		super(entityType, world);
		// 自定义移动控制：水中游泳 + 陆地弹跳
		this.moveControl = new BrutalFishMoveControl(this);
		this.prevTouchingWater = this.isTouchingWater();
	}

	// 属性：更大的血量和攻击力
	public static DefaultAttributeContainer.Builder createBrutalFishAttributes() {
		return FishEntity.createFishAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 16.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		this.goalSelector.add(0, new SwimGoal(this));
		// 主动攻击玩家（不要求视线：小鱼视线太低，按视线判定锁不上）
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
		this.goalSelector.add(2, new BrutalFishAttackGoal(this));
	}

	/**
	 * 略微增大攻击范围：6.0 * width² + 目标宽度。
	 */
	public static class BrutalFishAttackGoal extends MeleeAttackGoal {
		public BrutalFishAttackGoal(BrutalFishEntity mob) {
			super(mob, 1.4, false);
		}

		@Override
		protected double getSquaredMaxAttackDistance(LivingEntity target) {
			return this.mob.getWidth() * 6.0 * this.mob.getWidth() + target.getWidth();
		}
	}

	/**
	 * 摔落伤害极低（0.05 倍），几乎不受掉落伤害。
	 */
	@Override
	protected int computeFallDamage(float fallDistance, float damageMultiplier) {
		return MathHelper.ceil((fallDistance - 3.0f) * damageMultiplier * 0.05f);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.ENTITY_COD_FLOP;
	}

	/**
	 * 用"陆地导航"而不是原版鱼的水中导航，避免陆地追击时被拖向水。
	 */
	@Override
	protected EntityNavigation createNavigation(World world) {
		return new MobNavigation(this, world);
	}

	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(Items.COD_BUCKET);
	}

	/**
	 * 每 tick 保持满空气：离水不受伤。
	 */
	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient()) {
			this.setAir(this.getMaxAir());
		}
	}

	@Override
	public void tickMovement() {
		// 必须调用 super.tickMovement()：重力、移动、移动控制、导航都在父类链推进
		super.tickMovement();

		boolean touchingWater = this.isTouchingWater();
		if (touchingWater && !this.prevTouchingWater) {
			// 刚碰到水面：向上跳起扑腾一下
			this.setVelocity(this.getVelocity().x, JUMP_HEIGHT, this.getVelocity().z);
		}
		this.prevTouchingWater = touchingWater;
	}

	/**
	 * 自定义移动控制：和攻击性鱼相同，陆地以跳跃为主 + 轻微滑行。
	 */
	public static class BrutalFishMoveControl extends MoveControl {
		private final BrutalFishEntity fish;
		/** 当前随机游走的方向（水平单位向量） */
		private Vec3d wanderDir;

		public BrutalFishMoveControl(BrutalFishEntity fish) {
			super(fish);
			this.fish = fish;
		}

		@Override
		public void tick() {
			LivingEntity target = this.fish.getTarget();
			if (target != null) {
				// 有锁定目标：每个 tick 都转向目标，再按水/陆地追击
				this.fish.setSwimming(this.fish.isTouchingWater());
				this.faceTo(target.getX(), target.getEyeY(), target.getZ());
				if (this.fish.isTouchingWater()) {
					// 水中：朝目标滑翔推进
					Vec3d dir = new Vec3d(
							target.getX() - this.fish.getX(),
							target.getEyeY() - this.fish.getY() - 0.3,
							target.getZ() - this.fish.getZ()).normalize();
					Vec3d v = this.fish.getVelocity();
					this.fish.setVelocity(v.multiply(0.9).add(dir.multiply(0.16)));
				} else {
					// 陆地：跳跃是主要移动方式 —— 每 60 tick 无条件起跳
					// 不能用 isOnGround() 作触发条件：FishEntity 的扑腾让 onGround 恒为 false
					double dx = target.getX() - this.fish.getX();
					double dz = target.getZ() - this.fish.getZ();
					double dist = Math.sqrt(dx * dx + dz * dz);
					if (dist > 0.5) {
						if (this.fish.age - this.fish.lastJumpAge >= 60) {
							this.fish.lastJumpAge = this.fish.age;
							// 靠近玩家时略微缩短跳跃距离：距离越近，冲量和高度越小
							//（6 格外全力跳，贴脸时缩小到 45%）
							double closeness = MathHelper.clamp((dist - 1.0) / 5.0, 0.0, 1.0);
							double hopBoost = 2.0 * (0.45 + 0.55 * closeness);
							double jumpY = JUMP_HEIGHT * (0.6 + 0.4 * closeness);
							this.fish.setVelocity(dx / dist * hopBoost, jumpY, dz / dist * hopBoost);
						} else {
							// 非跳跃时：在地面上轻微朝目标滑行移动
							Vec3d v = this.fish.getVelocity();
							double drift = 0.02;
							this.fish.setVelocity(
									v.x * 0.9 + dx / dist * drift,
									v.y,
									v.z * 0.9 + dz / dist * drift);
						}
					}
				}
				return;
			}

			// 没有锁定目标：随机小范围游走
			this.fish.setSwimming(this.fish.isTouchingWater());
			if (this.wanderDir == null || this.fish.age % 60 == 0) {
				double rad = this.fish.getRandom().nextDouble() * Math.PI * 2.0;
				this.wanderDir = new Vec3d(Math.cos(rad), 0.0, Math.sin(rad));
			}
			this.faceTo(this.fish.getX() + this.wanderDir.x, this.fish.getY(), this.fish.getZ() + this.wanderDir.z);
			Vec3d v = this.fish.getVelocity();
			if (this.fish.isTouchingWater()) {
				this.fish.setVelocity(v.multiply(0.95).add(this.wanderDir.multiply(0.04)));
			} else if (this.fish.isOnGround() && this.fish.age % 30 == 0) {
				this.fish.setVelocity(this.wanderDir.x * 0.4, JUMP_HEIGHT * 0.7, this.wanderDir.z * 0.4);
			}
		}

		/**
		 * 让鱼朝向指定的点：平滑地把 yaw / bodyYaw / headYaw 转向目标。
		 */
		private void faceTo(double x, double y, double z) {
			double dx = x - this.fish.getX();
			double dy = y - this.fish.getY();
			double dz = z - this.fish.getZ();
			double hDist = Math.sqrt(dx * dx + dz * dz);
			if (hDist < 1.0E-4) {
				return;
			}
			float yaw = (float) (Math.atan2(dz, dx) * 57.295776f) - 90.0f;
			float pitch = (float) (-(Math.atan2(dy, hDist) * 57.295776f));
			this.fish.setYaw(this.wrapDegrees(this.fish.getYaw(), yaw, 20.0f));
			this.fish.setPitch(this.wrapDegrees(this.fish.getPitch(), pitch, 20.0f));
			this.fish.setBodyYaw(this.fish.getYaw());
			this.fish.setHeadYaw(this.fish.getYaw());
		}
	}
}