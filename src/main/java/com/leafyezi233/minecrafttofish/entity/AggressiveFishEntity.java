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
 * 一条有攻击性的鱼。
 * - 继承鱼类基类（是水生生物，会游泳）
 * - 会主动攻击玩家
 * - 在地上通过弹跳移动
 * - 离开水不会受到伤害（覆写了 FishEntity 的离水 dryOut 逻辑）
 * - 击杀掉落一条鱼（通过战利品表）
 */
public class AggressiveFishEntity extends FishEntity {
	/** 陆地跳跃间隔（tick） */
	private static final int JUMP_INTERVAL = 16;
	/** 陆地跳跃高度（垂直速度） */
	private static final double JUMP_HEIGHT = 0.7;
	/** 上一 tick 是否触水，用来检测"刚碰到水面"的瞬间 */
	private boolean prevTouchingWater;
	/** 上次起跳的 age（tick），用来保证每次落地都能起跳，形成清晰跳跃节奏 */
	private int lastJumpAge = -100;

	public AggressiveFishEntity(EntityType<? extends FishEntity> entityType, World world) {
		super(entityType, world);
		// 自定义移动控制：水中游泳 + 陆地弹跳
		this.moveControl = new AggressiveFishMoveControl(this);
		this.prevTouchingWater = this.isTouchingWater();
	}

	// 属性：血量、攻击力（在 ModEntities 注册时通过 createMobAttributes() 使用）
	public static DefaultAttributeContainer.Builder createAggressiveFishAttributes() {
		return FishEntity.createFishAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 10.0)
				.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
	}

	@Override
	protected void initGoals() {
		// 水中游动（往随机目标游，被 AwayFromWaterGoal 等替代时仍能移动）
		this.goalSelector.add(0, new SwimGoal(this));
		// 主动攻击玩家（不要求视线：小鱼在地面视线太低，按视线判定经常锁不上玩家）
		this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, false));
		// 近战攻击（自定义子类，增大了攻击范围）
		this.goalSelector.add(2, new AggressiveFishAttackGoal(this));
	}

	/**
	 * 略微增大攻击范围：默认近战攻击距离是 4.0 * width² + 目标宽度，
	 * 这里放大到 6.0 * width² + 目标宽度，让鱼在稍微远一点的地方就能咬到玩家。
	 */
	public static class AggressiveFishAttackGoal extends MeleeAttackGoal {
		public AggressiveFishAttackGoal(AggressiveFishEntity mob) {
			super(mob, 1.4, false);
		}

		@Override
		protected double getSquaredMaxAttackDistance(LivingEntity target) {
			return this.mob.getWidth() * 6.0 * this.mob.getWidth() + target.getWidth();
		}
	}

	/**
	 * 略微增加摔落伤害抗性：摔落伤害减半（0.5 倍）。
	 */
	@Override
	protected int computeFallDamage(float fallDistance, float damageMultiplier) {
		// 摔落伤害极低（0.05 倍），几乎不受掉落伤害
		return MathHelper.ceil((fallDistance - 3.0f) * damageMultiplier * 0.05f);
	}

	// FishEntity 的抽象方法：鱼在陆地上扑腾时的声音
	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.ENTITY_COD_FLOP;
	}

	/**
	 * 用"陆地导航"而不是原版鱼的水中导航。
	 * 原版 WaterNavigation 只在水中寻路，陆地追击玩家时会找不到路而把鱼拖向最近的水——
	 * 这就是"鱼在地上即使锁定玩家也会主动靠近水"的根因。
	 * 改成 MobNavigation 后，鱼会直接朝玩家走/跳，不再主动靠近水。
	 */
	@Override
	protected EntityNavigation createNavigation(World world) {
		return new MobNavigation(this, world);
	}

	// Bucketable 接口的抽象方法：用桶装这条鱼时返回的桶装物品
	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(Items.COD_BUCKET);
	}

	/**
	 * 每 tick 保持满空气：离水干死伤害由 LivingEntity 呼吸逻辑处理（不在 tickMovement 里），
	 * 这里强制回满空气，确保"离开水不会受到伤害"。
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
		// 必须调用 super.tickMovement()：重力、移动(move 把速度转成位移)、
		// 移动控制、导航都在父类链里推进，不调用则鱼完全不动。
		// FishEntity 的"随机扑腾"会让鱼在地面轻微乱蹦，但跳跃本身由 MoveControl
		// 可靠触发（age - lastJumpAge >= 30，每次落地必跳），大跳占主导，仍能看出跳跃。
		super.tickMovement();

		boolean touchingWater = this.isTouchingWater();
		if (touchingWater && !this.prevTouchingWater) {
			// 刚碰到水面：向上跳起扑腾一下
			this.setVelocity(this.getVelocity().x, JUMP_HEIGHT, this.getVelocity().z);
		}
		this.prevTouchingWater = touchingWater;
	}

	/**
	 * 自定义移动控制：不依赖原版导航（鱼的 SwimNavigation 追击基本不动），
	 * 直接计算朝目标的方向给速度——水中滑翔追击，陆地上朝目标弹跳。
	 * 没有锁定目标时，随机选一个方向小幅游走。
	 */
	public static class AggressiveFishMoveControl extends MoveControl {
		private final AggressiveFishEntity fish;
		/** 当前随机游走的方向（水平单位向量） */
		private Vec3d wanderDir;

		public AggressiveFishMoveControl(AggressiveFishEntity fish) {
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
					// 水中：朝目标滑翔推进（水平速度更高）
					Vec3d dir = new Vec3d(
							target.getX() - this.fish.getX(),
							target.getEyeY() - this.fish.getY() - 0.3,
							target.getZ() - this.fish.getZ()).normalize();
					Vec3d v = this.fish.getVelocity();
					this.fish.setVelocity(v.multiply(0.9).add(dir.multiply(0.16)));
				} else {
					// 陆地：跳跃是主要移动方式 —— 每 30 tick 无条件起跳一次，向目标猛扑
					// 注意：不能用 isOnGround() 作触发条件——FishEntity 的扑腾让鱼一直离地
					// onGround 恒为 false，加了它跳跃就永远不会触发（调试日志已证实）。
					double dx = target.getX() - this.fish.getX();
					double dz = target.getZ() - this.fish.getZ();
					double dist = Math.sqrt(dx * dx + dz * dz);
					if (dist > 0.5) {
						// 每 60 tick（约3秒）无条件起跳一次，向目标猛扑
						if (this.fish.age - this.fish.lastJumpAge >= 60) {
							this.fish.lastJumpAge = this.fish.age;
							// 起跳：施加较大的水平冲量（跳跃是主要推进力）
							// 靠近玩家时略微缩短跳跃距离：距离越近，冲量和高度越小
							//（6 格外全力跳，贴脸时缩小到 45%，避免一下从玩家头顶飞过去咬不到）
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

			// 没有锁定目标：随机小范围游走，每个 tick 也朝向游走方向
			this.fish.setSwimming(this.fish.isTouchingWater());
			if (this.wanderDir == null || this.fish.age % 60 == 0) {
				double rad = this.fish.getRandom().nextDouble() * Math.PI * 2.0;
				this.wanderDir = new Vec3d(Math.cos(rad), 0.0, Math.sin(rad));
			}
			this.faceTo(this.fish.getX() + this.wanderDir.x, this.fish.getY(), this.fish.getZ() + this.wanderDir.z);
			Vec3d v = this.fish.getVelocity();
			if (this.fish.isTouchingWater()) {
				// 水中缓慢朝游走方向滑
				this.fish.setVelocity(v.multiply(0.95).add(this.wanderDir.multiply(0.04)));
			} else if (this.fish.isOnGround() && this.fish.age % 30 == 0) {
				// 陆地小幅朝游走方向弹跳
				this.fish.setVelocity(this.wanderDir.x * 0.4, JUMP_HEIGHT * 0.7, this.wanderDir.z * 0.4);
			}
		}

		/**
		 * 让鱼朝向指定的点：每个 tick 都调用，平滑地把
		 * yaw / bodyYaw / headYaw 转向目标（玩家看到的身形朝向），
		 * 否则鱼只平移、不转头（"不改变朝向"的 bug）。
		 * 用 wrapDegrees 平滑转向，避免鱼身瞬间乱转。
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
			// wrapDegrees(current, target, max) 平滑转向，最多每 tick 转 max 度
			this.fish.setYaw(this.wrapDegrees(this.fish.getYaw(), yaw, 20.0f));
			this.fish.setPitch(this.wrapDegrees(this.fish.getPitch(), pitch, 20.0f));
			this.fish.setBodyYaw(this.fish.getYaw());
			this.fish.setHeadYaw(this.fish.getYaw());
		}
	}
}