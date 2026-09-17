package com.leafyezi233.minecrafttofish.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * 微缩鱼（原名"胆小的鱼"）：钓上来时从鱼钩飞向玩家脚底，
 * 落地后拼命往最近的水里逃，一碰到水就"噗通"一声消失（溜走）。
 * - 没有攻击性，不会咬人
 * - 始终带原版发光描边（setGlowing）
 * - 击杀照样会掉"微缩鱼"物品（战利品表）
 */
public class TimidFishEntity extends FishEntity {
	/** 陆地跳跃高度（垂直速度，已调低） */
	private static final double JUMP_HEIGHT = 0.30;
	/** 上次起跳的 age（tick） */
	private int lastJumpAge = -100;
	/** 当前要逃向的水方块（每 30 tick 重新搜索） */
	private BlockPos waterTarget;

	/** 飞行阶段目标点（钓上来时从鱼钩飞向玩家），null 表示不在飞行中 */
	private Vec3d flightTarget;
	/** 飞行剩余 tick 数（约 0.75 秒的抛物线飞行） */
	private int flightTicks;

	public TimidFishEntity(EntityType<? extends FishEntity> entityType, World world) {
		super(entityType, world);
		this.moveControl = new TimidFishMoveControl(this);
		// 原版发光效果：始终带一圈发光描边（和"发光"状态效果同一种机制）
		this.setGlowing(true);
	}

	/**
	 * 从鱼钩位置"飞"向玩家脚底：启动一段抛物线飞行。
	 * 目标点会在飞行途中持续跟随玩家（玩家跑动也追得上）。
	 */
	public void startFlightTo(Vec3d target) {
		this.flightTarget = target;
		this.flightTicks = 15; // 15 tick ≈ 0.75 秒
		this.setNoGravity(true);
		double dx = target.x - this.getX();
		double dz = target.z - this.getZ();
		// 先给一个朝玩家的初始速度，让起飞有冲劲
		this.setVelocity(dx * 0.18, 0.42, dz * 0.18);
	}

	/** 是否正在飞行（飞行中不参与陆地逃跑逻辑） */
	public boolean isFlyingToPlayer() {
		return this.flightTarget != null && this.flightTicks > 0;
	}

	public static DefaultAttributeContainer.Builder createTimidFishAttributes() {
		return FishEntity.createFishAttributes()
				.add(EntityAttributes.GENERIC_MAX_HEALTH, 4.0)
				.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
	}

	@Override
	protected void initGoals() {
		// 不装攻击目标：它只想逃跑
		this.goalSelector.add(0, new SwimGoal(this));
	}

	@Override
	protected int computeFallDamage(float fallDistance, float damageMultiplier) {
		// 几乎不受摔落伤害
		return MathHelper.ceil((fallDistance - 3.0f) * damageMultiplier * 0.05f);
	}

	@Override
	protected SoundEvent getFlopSound() {
		return SoundEvents.ENTITY_TROPICAL_FISH_FLOP;
	}

	@Override
	public ItemStack getBucketItem() {
		return new ItemStack(Items.TROPICAL_FISH_BUCKET);
	}

	@Override
	public void tick() {
		super.tick();
		if (!this.getWorld().isClient()) {
			// 始终维持原版发光描边（setFlag 在值未变化时不会产生额外网络同步）
			this.setGlowing(true);
			// 飞行阶段：朝玩家飞过去（抛物线），落地后转为逃跑
			if (this.isFlyingToPlayer()) {
				// 让目标持续跟随玩家（玩家走动也追得上）
				if (this.flightTarget != null) {
					double dx = this.flightTarget.x - this.getX();
					double dz = this.flightTarget.z - this.getZ();
					double dy = this.flightTarget.y - this.getY();
					double h = Math.sqrt(dx * dx + dz * dz);
					// 水平方向持续加速追向玩家
					double speed = Math.min(0.55, 0.12 + h * 0.06);
					double vy = this.getVelocity().y;
					if (h > 0.8) {
						vy = Math.max(dy * 0.16, -0.12);
					} else {
						// 快到玩家头顶：转为下落，准备落地
						vy = Math.min(vy, -0.18);
					}
					this.setVelocity(dx / h * speed, vy, dz / h * speed);
					// 面朝飞行方向
					float yaw = (float) (Math.atan2(dz, dx) * 57.295776f) - 90.0f;
					this.setYaw(yaw);
					this.setBodyYaw(yaw);
					this.setHeadYaw(yaw);
				}
				this.flightTicks--;
				if (this.flightTicks <= 0) {
					// 飞行结束：恢复重力，在玩家脚底落地
					this.flightTarget = null;
					this.setNoGravity(false);
				}
				return; // 飞行中不做入水消失判定
			}
			// 防止刚生成的 1 秒内直接消失
			if (this.age < 20) {
				return;
			}
			this.setAir(this.getMaxAir());
			// 一碰到水：播放入水声，然后消失（成功逃跑！）
			if (this.isTouchingWater()) {
				this.getWorld().playSound(
						null,
						this.getBlockPos(),
						SoundEvents.ENTITY_GENERIC_SPLASH,
						this.getSoundCategory(),
						1.0f,
						1.3f);
				this.discard();
			}
		}
	}

	/**
	 * 自定义移动控制：不追人，只朝最近的水逃跑（边跳边爬）。
	 */
	public static class TimidFishMoveControl extends MoveControl {
		private final TimidFishEntity fish;
		/** 找不到水时的随机扑腾方向 */
		private Vec3d panicDir;

		public TimidFishMoveControl(TimidFishEntity fish) {
			super(fish);
			this.fish = fish;
		}

		/**
		 * 在 16 格半径内搜索最近的水方块（粗略扫描，每列找到一层水即可）。
		 */
		private BlockPos findWater(World world, BlockPos from) {
			BlockPos best = null;
			double bestDist = Double.MAX_VALUE;
			for (int dx = -16; dx <= 16; dx += 2) {
				for (int dz = -16; dz <= 16; dz += 2) {
					for (int dy = -4; dy <= 4; dy++) {
						BlockPos p = from.add(dx, dy, dz);
						if (world.getFluidState(p).isIn(FluidTags.WATER)) {
							double d = dx * dx + dz * dz;
							if (d < bestDist) {
								bestDist = d;
								best = p;
							}
							break; // 这一列只需要找到一个
						}
					}
				}
			}
			return best;
		}

		@Override
		public void tick() {
			// 飞行阶段由实体 tick() 接管速度，移动控制不插手
			if (this.fish.isFlyingToPlayer()) {
				return;
			}
			World world = this.fish.getWorld();
			// 每 30 tick 重新搜一次最近的水（降低搜索频率）
			if (this.fish.age % 30 == 0) {
				this.fish.waterTarget = this.findWater(world, this.fish.getBlockPos());
			}

			double dirX;
			double dirZ;
			if (this.fish.waterTarget != null) {
				// 朝最近的水逃
				dirX = this.fish.waterTarget.getX() + 0.5 - this.fish.getX();
				dirZ = this.fish.waterTarget.getZ() + 0.5 - this.fish.getZ();
			} else {
				// 周围找不到水：慌乱地随机扑腾
				if (this.panicDir == null || this.fish.age % 70 == 0) {
					double rad = this.fish.getRandom().nextDouble() * Math.PI * 2.0;
					this.panicDir = new Vec3d(Math.cos(rad), 0.0, Math.sin(rad));
				}
				dirX = this.panicDir.x;
				dirZ = this.panicDir.z;
			}

			double dist = Math.hypot(dirX, dirZ);
			if (dist > 0.3) {
				// 面朝逃跑方向
				this.faceTo(this.fish.getX() + dirX, this.fish.getY(), this.fish.getZ() + dirZ);
				if (!this.fish.isTouchingWater()) {
					// 陆地：每 70 tick 跳一次，朝水边蹦过去（降低跳跃频率）
					if (this.fish.age - this.fish.lastJumpAge >= 70) {
						this.fish.lastJumpAge = this.fish.age;
						double hopBoost = 0.9;  // 降低横向跳跃速度
						this.fish.setVelocity(dirX / dist * hopBoost, JUMP_HEIGHT, dirZ / dist * hopBoost);
					} else {
						// 非跳跃时小幅滑行（降低滑行速度）
						Vec3d v = this.fish.getVelocity();
						double drift = 0.015;
						this.fish.setVelocity(
								v.x * 0.9 + dirX / dist * drift,
								v.y,
								v.z * 0.9 + dirZ / dist * drift);
					}
				}
			}
		}

		/** 平滑转向指定点（同另外两条鱼的实现） */
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
