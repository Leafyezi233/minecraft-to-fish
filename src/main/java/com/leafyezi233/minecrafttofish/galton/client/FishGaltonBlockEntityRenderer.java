package com.leafyezi233.minecrafttofish.galton.client;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.galton.FishGaltonBlockEntity;
import com.leafyezi233.minecrafttofish.galton.GaltonBoard;
import com.leafyezi233.minecrafttofish.galton.GaltonConstants;
import com.leafyezi233.minecrafttofish.galton.GaltonGeometry;
import com.leafyezi233.minecrafttofish.galton.GaltonPath;
import com.leafyezi233.minecrafttofish.galton.GaltonSlot;
import com.leafyezi233.minecrafttofish.galton.GaltonSound;
import com.leafyezi233.minecrafttofish.game.client.GameMesh;
import com.leafyezi233.minecrafttofish.wheel.WheelAngle;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * 高尔顿板的世界渲染（<b>仅客户端</b>）。
 *
 * <h2>画了什么</h2>
 * <ol>
 *   <li><b>背板</b>：盘面的底，深色木板</li>
 *   <li><b>侧柱</b>：左右各一根，撑出「机器」的轮廓</li>
 *   <li><b>钉子</b>：{@code rows} 排三角排列，数量由槽位表<b>推导</b></li>
 *   <li><b>槽位</b>：{@code rows+1} 个色块，颜色/文字来自服务端下发的表</li>
 *   <li><b>球</b>：按同步来的路径与当前刻算出位置</li>
 *   <li><b>托盘上的鱼</b>：那条谁都能取走的鱼</li>
 *   <li><b>结果文字</b>：落地后浮在盘顶</li>
 * </ol>
 *
 * <h2>⚠️ 为什么所有位置都从共享类取，渲染器不自己算</h2>
 * 球的轨迹、钉子位置、槽位边界全部来自 {@link GaltonGeometry} 与 {@link GaltonPath}。
 * 渲染器<b>一行轨迹公式都不写</b>。
 * <p>理由是结构性的：结算用的是 {@code path}，画球用的也是 {@code path}，
 * 两者只要走同一套函数就<b>不可能</b>出现「球停在的位置和奖项对不上」。
 * 若渲染器自己推一遍位置，就等于把「同一份几何」实现两次 ——
 * 而那正是转盘当初踩过的坑（指针位置与奖项对不上）。
 *
 * <h2>为什么必须声明 {@code rendersOutsideBoundingBox}</h2>
 * 盘面画到 y≈1.95，<b>超出方块自己的 1 格</b>。
 * 不声明的话超出的部分会被原版裁掉 —— 表现为盘面<b>上半截凭空消失</b>。
 *
 * <h2>为什么钉子/槽位不画成方块模型</h2>
 * 数量取决于配置（{@code rows = 槽位数 - 1}），必须由渲染器按服务端下发的表来画。
 * 写死在模型里的话，服主一改配置，画出的钉子就和球的实际弹跳路径对不上 ——
 * 表现为球从钉子旁边穿过去。
 *
 * <h2>音效为什么在客户端算</h2>
 * 撞钉音必须<b>和画面对齐到帧</b>。若服务端按刻播音，玩家看到的球和听到的声音
 * 会因网络延迟错开，听起来像「球还没到就已经响了」。
 * 客户端每帧算出「砸到第几颗」（{@link GaltonPath#pegsHit}），
 * 与上一帧不同就响一声 —— 声音天然跟着画面走。
 */
@Environment(EnvType.CLIENT)
public class FishGaltonBlockEntityRenderer implements BlockEntityRenderer<FishGaltonBlockEntity> {

	// ---------------------------------------------------------------- 配色

	/** 背板颜色（深色木） */
	private static final int BACKBOARD_COLOR = 0xFF3E3226;

	/** 背板侧面/边缘颜色（更暗，从侧面能看出厚度） */
	private static final int BACKBOARD_EDGE_COLOR = 0xFF2A2119;

	/** 侧柱颜色（比背板亮，撑出轮廓） */
	private static final int RAIL_COLOR = 0xFF5A4632;

	/** 钉子颜色（金属色，与背板形成反差，否则看不清钉在哪） */
	private static final int PEG_COLOR = 0xFFC8C8C8;

	/** 球颜色（亮红，与所有槽位颜色都不撞） */
	private static final int BALL_COLOR = 0xFFFF4444;

	/** 槽位未高亮时的暗化比例（0 = 全黑，1 = 原色） */
	private static final float SLOT_DIM = 0.55f;

	/** 槽位高亮强度（1.0 = 原色，>1 提亮） */
	private static final float SLOT_HIGHLIGHT = 1.45f;

	/**
	 * 结果文字的半透明底衬（ARGB，<b>int</b>）。
	 *
	 * <p>用深色而非纯黑不透明：alpha 取 {@code 0xB0}（约 69%），
	 * 既压得住亮背景，又能透出一点环境色，不至于像贴了块黑板。
	 * 不画背景（传 0）的话，文字在雪地、水面或逆光下会糊得看不清。
	 *
	 * <p><b>类型必须是 int，不能是 float</b>：{@code TextRenderer.draw} 第 9 个参数
	 * 就是背景色（已核字节码），类型是 {@code int}。
	 * 写成 float 连编译都过不去（十六进制字面量也初始化不了 float）。
	 */
	private static final int LABEL_BG_COLOR = 0xB0101010;

	/** 球的分段（环 × 段）。球在屏幕上约 1.5 像素，再多分段纯属浪费 */
	private static final int BALL_RINGS = 6;
	private static final int BALL_SEGMENTS = 10;

	/** 槽位色块的前移量（避免与背板 z-fighting），取自共享几何 */
	private static final float LAYER_EPS = GaltonGeometry.LAYER_EPS;

	private final TextRenderer textRenderer;

	public FishGaltonBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	// ---------------------------------------------------------------- 主入口

	@Override
	public void render(FishGaltonBlockEntity galton, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {

		GaltonBoard board = GaltonBoard.current();
		if (board == null || board.size() < 2) {
			// 表不可用时什么都不画，但也不能抛异常 —— 渲染线程上崩溃会直接踢出游戏
			return;
		}

		int rows = board.rows();

		// 用方块自身位置算光照：板子因此与周围方块的明暗一致
		int packedLight = lightFor(galton, light);

		float yaw = yawOf(galton.facing());

		VertexConsumer buffer = vertexConsumers.getBuffer(GameMesh.solidLayer());

		// 整块板子的局部坐标系：原点在方块中心、方块底面，
		// 然后按朝向绕 Y 轴旋转。所有几何都用「盘面局部坐标」表达，
		// 这样朝向只在这一个地方参与运算，不可能出现某个部件忘转朝向。
		matrices.push();
		matrices.translate(0.5, 0.0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));

		renderBackboard(buffer, matrices.peek(), packedLight);
		renderRails(buffer, matrices.peek(), packedLight);
		renderPegs(buffer, matrices.peek(), rows, packedLight);
		// 用 revealedSlot() 而不是 restingSlot()：下落中它返回「无」，
		// 因此球还在空中时不会有任何槽位被照亮（否则等于提前剧透，见该方法的注释）
		renderSlots(buffer, matrices.peek(), board, galton.revealedSlot(), packedLight);
		renderBall(buffer, matrices.peek(), galton, rows, tickDelta, packedLight);

		matrices.pop();

		// 鱼与文字用世界坐标单独摆（它们不参与「盘面局部坐标」那套旋转）
		renderFish(galton, yaw, matrices, vertexConsumers, packedLight);
		renderLabel(galton, board, matrices, vertexConsumers, packedLight);

		// 撞钉音效：跟着画面走，理由见类注释
		playPegSounds(galton, rows, tickDelta);
	}

	/** 取方块所在位置的光照坐标；世界不可用时退回传入的 light */
	private static int lightFor(FishGaltonBlockEntity galton, int fallback) {
		if (galton == null || galton.getWorld() == null) {
			return fallback;
		}
		try {
			return WorldRenderer.getLightmapCoordinates(galton.getWorld(), galton.getPos());
		} catch (Throwable t) {
			// 区块边界等极端情况下取不到光照，不该让渲染崩掉
			return fallback;
		}
	}

	/**
	 * 朝向 → 绕 Y 轴旋转角度。
	 *
	 * <p>转发给 {@link WheelAngle#yawOf}，<b>不再维护第二份映射</b>。
	 * 两份映射正是当初转盘「东西方向写反」的温床，而朝向画反
	 * 只有联机时从特定角度看才看得出来，极难发现。
	 */
	private static float yawOf(Direction facing) {
		return WheelAngle.yawOf(facing);
	}

	// ---------------------------------------------------------------- 背板与侧柱

	/** 背板：一块竖直的薄板，盘面的一切都长在它的前表面上 */
	private static void renderBackboard(VertexConsumer buffer, MatrixStack.Entry entry, int light) {
		float halfWidth = GaltonGeometry.BOARD_WIDTH / 2.0f;
		float zFront = GaltonGeometry.BACKBOARD_FRONT_Z;
		float zBack = zFront - GaltonGeometry.BACKBOARD_THICKNESS;

		GameMesh.box(buffer, entry, BACKBOARD_COLOR, light,
				-halfWidth, GaltonGeometry.BOARD_BOTTOM_Y, zBack,
				halfWidth, GaltonGeometry.BOARD_TOP_Y, zFront);

		// 顶沿与底沿压一条更暗的边，从侧面看能分清盘面的上下边界
		GameMesh.box(buffer, entry, BACKBOARD_EDGE_COLOR, light,
				-halfWidth, GaltonGeometry.BOARD_TOP_Y - 0.02f, zBack,
				halfWidth, GaltonGeometry.BOARD_TOP_Y, zFront + 0.002f);
	}

	/** 侧柱：盘面左右各一根，把盘面「框」起来 */
	private static void renderRails(VertexConsumer buffer, MatrixStack.Entry entry, int light) {
		float half = GaltonGeometry.BOARD_WIDTH / 2.0f;
		float rail = GaltonGeometry.RAIL_HALF_WIDTH;
		float zFront = GaltonGeometry.BALL_Z + 0.02f;
		float zBack = GaltonGeometry.BACKBOARD_FRONT_Z - GaltonGeometry.BACKBOARD_THICKNESS;

		// 左柱
		GameMesh.box(buffer, entry, RAIL_COLOR, light,
				-half, GaltonGeometry.BOARD_BOTTOM_Y, zBack,
				-half + rail * 2.0f, GaltonGeometry.BOARD_TOP_Y, zFront);
		// 右柱
		GameMesh.box(buffer, entry, RAIL_COLOR, light,
				half - rail * 2.0f, GaltonGeometry.BOARD_BOTTOM_Y, zBack,
				half, GaltonGeometry.BOARD_TOP_Y, zFront);
	}

	// ---------------------------------------------------------------- 钉子

	/**
	 * 钉子：{@code rows} 排三角排列。
	 *
	 * <p>位置全部来自 {@link GaltonGeometry#pegOffsetX}（以槽宽为单位），
	 * 再统一乘槽宽转成格。这样钉子的横坐标与 {@link GaltonPath} 的轨迹
	 * 用的是同一套单位，两者<b>必然</b>对得上。
	 */
	private static void renderPegs(VertexConsumer buffer, MatrixStack.Entry entry, int rows, int light) {
		float slotWidth = GaltonGeometry.slotWidth(rows);
		float pegRadius = GaltonGeometry.pegRadius(rows);
		float zFront = GaltonGeometry.BACKBOARD_FRONT_Z + GaltonGeometry.PEG_DEPTH;
		float zBack = GaltonGeometry.BACKBOARD_FRONT_Z;

		for (int row = 0; row < rows; row++) {
			float y = GaltonGeometry.pegRowY(row, rows);
			int count = GaltonGeometry.pegCount(row);

			for (int i = 0; i < count; i++) {
				float x = GaltonGeometry.pegOffsetX(row, i) * slotWidth;

				// 钉子画成一个短圆柱的近似：用小方块即可 —— 球直径约 0.1 格，
				// 钉子更小，屏幕上不足 1 像素，「圆不圆」完全看不出来。
				GameMesh.box(buffer, entry, PEG_COLOR, light,
						x - pegRadius, y - pegRadius, zBack,
						x + pegRadius, y + pegRadius, zFront);
			}
		}
	}

	// ---------------------------------------------------------------- 槽位

	/**
	 * 槽位：{@code rows+1} 个色块。
	 *
	 * <p>颜色来自<b>服务端下发的表</b>（{@link GaltonBoard#current()}）。
	 * 未高亮的槽位整体压暗，只有落点那个用原色提亮 ——
	 * 这样玩家一眼看出球落进了哪一格。
	 *
	 * <p>每个槽位右侧画一条细缝（间隔色）：相邻槽位若同色会连成一片，
	 * 看不出到底有几个格子。
	 */
	private static void renderSlots(VertexConsumer buffer, MatrixStack.Entry entry,
			GaltonBoard board, int highlightSlot, int light) {

		int rows = board.rows();
		float y0 = GaltonGeometry.BOARD_BOTTOM_Y;
		float y1 = GaltonGeometry.SLOT_TOP_Y;
		float z = GaltonGeometry.BACKBOARD_FRONT_Z + LAYER_EPS;

		for (int k = 0; k < board.size(); k++) {
			GaltonSlot slot = board.slot(k);
			float x0 = GaltonGeometry.slotLeftX(k, rows);
			float x1 = GaltonGeometry.slotRightX(k, rows);

			int color = k == highlightSlot
					? brighten(slot.color(), SLOT_HIGHLIGHT)
					: dim(slot.color(), SLOT_DIM);

			// 色块本体，左右各留一条极细的缝作为分隔
			float gap = 0.004f;
			GameMesh.flatQuadZ(buffer, entry, color, light,
					x0 + gap, y0, x1 - gap, y1, z, 1.0f);
		}

		// 槽位带的上沿压一条暗线：把「钉子区」和「槽位区」在视觉上分开
		GameMesh.flatQuadZ(buffer, entry, BACKBOARD_EDGE_COLOR, light,
				GaltonGeometry.boardLeftX(), y1 - 0.012f,
				GaltonGeometry.boardRightX(), y1, z + LAYER_EPS, 1.0f);
	}

	// ---------------------------------------------------------------- 球

	/**
	 * 球：按同步来的路径与当前游戏刻算出位置。
	 *
	 * <p><b>位置是纯函数</b>：{@code (路径, 进度)} → 坐标。
	 * 不读墙钟、不掷随机数，所以所有客户端在同一时刻算出同一个位置，
	 * 中途走进来的玩家也会直接看到球在中间某处，而不是从头掉一遍。
	 *
	 * <p>进度用「客户端当前刻 − 服务端记录的开始刻」算，含 {@code tickDelta} 插值，
	 * 球因此是平滑移动而不是每刻跳一格。
	 */
	private static void renderBall(VertexConsumer buffer, MatrixStack.Entry entry,
			FishGaltonBlockEntity galton, int rows, float tickDelta, int light) {

		if (!galton.isDropping()) {
			// 不在下落：把球画在落点（或上一次落点）上，让玩家看到「上次落在哪」。
			// 没有落点时干脆不画 —— 一块全新的板子上不该凭空有颗球。
			int resting = galton.restingSlot();
			if (resting == GaltonConstants.NO_SLOT || galton.resultPath() == GaltonConstants.NO_PATH
					&& galton.prevPath() == GaltonConstants.NO_PATH) {
				return;
			}
			int path = galton.resultPath() != GaltonConstants.NO_PATH
					? galton.resultPath()
					: galton.prevPath();
			float[] rest = GaltonGeometry.ballRestLocal(path, rows);
			drawBall(buffer, entry, rest[0], rest[1], rows, light);
			return;
		}

		float progress = dropProgress(galton, tickDelta);
		float[] pos = GaltonGeometry.ballLocal(galton.resultPath(), rows, progress);
		drawBall(buffer, entry, pos[0], pos[1], rows, light);
	}

	private static void drawBall(VertexConsumer buffer, MatrixStack.Entry entry,
			float x, float y, int rows, int light) {
		GameMesh.sphere(buffer, entry, BALL_COLOR, light,
				x, y, GaltonGeometry.BALL_Z, GaltonGeometry.ballRadius(rows),
				BALL_RINGS, BALL_SEGMENTS);
	}

	/**
	 * 当前下落进度 {@code [0, 1]}。
	 *
	 * <p>时长用<b>服务端同步来的</b> {@code dropTicks} 作分母，
	 * 不用客户端本地配置：服主改过配置时，各客户端本地时长会和服务端不一致，
	 * 各自用各自的会导致两人在不同时刻看到球落地。
	 */
	private static float dropProgress(FishGaltonBlockEntity galton, float tickDelta) {
		long total = galton.dropTicks();
		if (total <= 0L) {
			// 时长为 0 的配置：没有下落过程，直接就是落点
			return 1.0f;
		}

		long elapsed = clientTime() - galton.dropStartTick();
		if (elapsed < 0L) {
			// 服务端时钟略超前（包刚到、本端还没走到那一 tick）：按刚开始处理。
			// 不夹住的话进度会变成负数，球会往上倒着飞。
			elapsed = 0L;
		}

		float progress = (elapsed + tickDelta) / (float) total;
		return Math.max(0.0f, Math.min(1.0f, progress));
	}

	/** 客户端当前游戏刻；拿不到世界时返回 0（渲染不会因此崩） */
	private static long clientTime() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return 0L;
		}
		return client.world.getTime();
	}

	// ---------------------------------------------------------------- 撞钉音效

	/**
	 * 球每砸过一颗钉子响一声（<b>客户端本地</b>）。
	 *
	 * <p>记账存在方块实体上（见 {@code FishGaltonBlockEntity} 的客户端记账字段），
	 * 随方块被破坏一起回收，不会像静态 Map 那样泄漏。
	 *
	 * <p>用 {@code dropSequence} 判断「是不是新的一轮」：新的一轮要把计数归零，
	 * 否则第二轮的球会从上一轮的计数接着涨，前几颗钉子不响。
	 */
	private static void playPegSounds(FishGaltonBlockEntity galton, int rows, float tickDelta) {
		if (!galton.isDropping()) {
			// 不在下落：清掉记账，让下一轮从头开始响
			galton.setClientDropSequence(-1);
			galton.setClientPegsHit(0);
			return;
		}

		if (galton.clientDropSequence() != galton.dropSequence()) {
			galton.setClientDropSequence(galton.dropSequence());
			galton.setClientPegsHit(0);
		}

		int hit = GaltonPath.pegsHit(rows, dropProgress(galton, tickDelta));
		int previous = galton.clientPegsHit();

		if (hit > previous) {
			galton.setClientPegsHit(hit);
			// 只在「刚砸到新的一颗」时响，且一次最多响一声 ——
			// 一帧内跨过多颗（卡顿后追帧）不该叠成一片噪音
			//
			// pegIndex 用 hit - 1：pegsHit 返回的是「已撞过的<b>颗数</b>」（0 起），
			// 而音高函数要的是「第几颗」的<b>下标</b>。差一的话
			// 最后一颗钉子会取到 rows 这个越界下标，被夹到同一个音高 ——
			// 表现为「最后两颗钉子音高一样」。
			playClick(galton, hit - 1, rows);
		} else if (hit < previous) {
			// 进度倒退（时钟回退、/tick 操作）：把记账拉回来，避免从此再不响
			galton.setClientPegsHit(hit);
		}
	}

	/**
	 * 放一声撞钉音。
	 *
	 * <h2>⚠️ 这里曾经用 {@code PositionedSoundInstance.master(...)}，那是错的</h2>
	 * 查字节码确认过 {@code master(sound, X)} 的实际行为：
	 * <pre>
	 *   master(sound, X) → master(sound, pitch=X, volume=0.25f)
	 *   → 类别 = MASTER，衰减 = NONE，relative = true，坐标 = (0,0,0)
	 * </pre>
	 * 也就是说它是个「UI 音效」工厂，播出来的是：
	 * <ul>
	 *   <li><b>贴在自己耳朵上</b>（{@code relative=true} + 坐标全 0）；
	 *   <li><b>无距离衰减</b>（{@code AttenuationType.NONE}）——
	 *       板子在 48 格外（渲染距离上限）也<b>一样响</b>；</li>
	 *   <li>走 <b>MASTER</b> 通道 —— 玩家把「方块」音量拉到底也照样响，
	 *       因为那不是方块音。</li>
	 * </ul>
	 * 三个后果叠起来就是「别人的板子在很远的地方掉球，声音却像在我头上响」。
	 *
	 * <p>改用 8 参构造器（字节码确认它给的是 {@code LINEAR} 衰减、
	 * {@code relative=false}），音效才真正长在方块位置上：
	 * 走近了变大、走远了变小、超出可听距离就听不到。
	 */
	private static void playClick(FishGaltonBlockEntity galton, int pegIndex, int rows) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getSoundManager() == null || galton.getWorld() == null) {
			return;
		}
		try {
			Vec3d at = soundPos(galton);

			client.getSoundManager().play(new PositionedSoundInstance(
					SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
					SoundCategory.BLOCKS,
					GaltonSound.pegVolume(pegIndex, rows) * soundScale(),
					GaltonSound.pegPitch(pegIndex, rows, galton.dropSequence()),
					SoundInstance.createRandom(),
					at.x, at.y, at.z));
		} catch (Throwable t) {
			MyMod.LOGGER.debug("[galton] 撞钉音效播放失败：{}", t.toString());
		}
	}

	/**
	 * 音效的世界坐标：方块中心、盘面半高。
	 *
	 * <p>取盘面中部而不是方块底面：盘面从 0.125 一直长到 1.95，
	 * 声源放在底面会让「球在顶部撞钉」听起来比实际低一截。
	 */
	private static Vec3d soundPos(FishGaltonBlockEntity galton) {
		BlockPos p = galton.getPos();
		return new Vec3d(p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5);
	}

	/** 服主配置的音量倍率；拿不到配置时按 1.0（不影响游戏） */
	private static float soundScale() {
		try {
			return Math.max(0.0f, EconomyConfig.get().galtonSoundVolume);
		} catch (Throwable t) {
			return 1.0f;
		}
	}

	// ---------------------------------------------------------------- 鱼

	/** 把托盘上的鱼画出来（所有人可见、谁都能取走的那条） */
	private static void renderFish(FishGaltonBlockEntity galton, float yaw, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {

		ItemStack fish = galton.fishStack();
		if (fish == null || fish.isEmpty()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getItemRenderer() == null) {
			return;
		}

		matrices.push();
		matrices.translate(0.5, GaltonGeometry.trayCenterY(), 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		// 浮在盘面前方，避免与背板共面导致闪烁（z-fighting）
		matrices.translate(0.0, 0.0, GaltonGeometry.FISH_Z);
		// 物品模型默认躺着，转正让它像贴在板上
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
		// 缩小到能塞进 0.9 格宽的托盘
		matrices.scale(0.42f, 0.42f, 0.42f);

		client.getItemRenderer().renderItem(fish, ModelTransformationMode.FIXED,
				light, net.minecraft.client.render.OverlayTexture.DEFAULT_UV,
				matrices, vertexConsumers, client.world, 0);

		matrices.pop();
	}

	// ---------------------------------------------------------------- 结果文字

	/**
	 * 落地后在盘顶显示结果；下落中不显示，避免剧透。
	 *
	 * <p>文字<b>始终正对镜头</b>（billboard），用的是原版命名牌那一套：
	 * 平移到位后乘上相机旋转，再缩放 {@code (-0.025, -0.025, 0.025)}。
	 * 不这么做的话，文字会固定贴在盘面上，玩家绕到侧面或背面就变成一条线看不见了。
	 */
	private void renderLabel(FishGaltonBlockEntity galton, GaltonBoard board, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {

		if (galton.isDropping()) {
			return;
		}
		int index = galton.revealedSlot();
		if (index < 0 || index >= board.size()) {
			return;
		}

		GaltonSlot slot = board.slot(index);
		// 用槽位自己的语言键（来自配置的 labelKey），而不是另写一套结果文案。
		// 好处：服主自定义 labelKey 时，板子上浮出的文字跟着变，不必改代码。
		// 默认表里 "x10" → "×10"、"lose" → "未中奖"，正是该显示的内容。
		Text text = Text.translatable(slot.labelTranslationKey());
		int color = slot.isWin() ? 0xFFFFD24A : 0xFFB0B0B0;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.gameRenderer == null) {
			return;
		}

		matrices.push();
		matrices.translate(0.5, GaltonGeometry.labelY(), 0.5);
		// 乘相机旋转 = 永远正对镜头
		matrices.multiply(client.gameRenderer.getCamera().getRotation());
		matrices.scale(-0.025f, -0.025f, 0.025f);

		float width = textRenderer.getWidth(text);
		textRenderer.draw(text, -width / 2.0f, 0.0f, color, false,
				matrices.peek().getPositionMatrix(), vertexConsumers,
				TextRenderer.TextLayerType.SEE_THROUGH,
				LABEL_BG_COLOR, light);

		matrices.pop();
	}

	// ---------------------------------------------------------------- 颜色工具

	/** 按比例压暗一个 ARGB 颜色（保留 alpha） */
	private static int dim(int argb, float factor) {
		return scale(argb, factor);
	}

	/** 按比例提亮一个 ARGB 颜色（保留 alpha，超出 255 时夹住） */
	private static int brighten(int argb, float factor) {
		return scale(argb, factor);
	}

	private static int scale(int argb, float factor) {
		int a = argb >>> 24 & 0xFF;
		int r = clampChannel((int) ((argb >>> 16 & 0xFF) * factor));
		int g = clampChannel((int) ((argb >>> 8 & 0xFF) * factor));
		int b = clampChannel((int) ((argb & 0xFF) * factor));
		return a << 24 | r << 16 | g << 8 | b;
	}

	private static int clampChannel(int v) {
		return Math.max(0, Math.min(255, v));
	}

	// ---------------------------------------------------------------- 渲染距离

	/**
	 * 盘面画到 y≈1.95，<b>超出方块自己的 1 格</b>，
	 * 所以必须在包围盒外也渲染，否则走到边缘时盘面上半截会被提前裁掉。
	 */
	@Override
	public boolean rendersOutsideBoundingBox(FishGaltonBlockEntity galton) {
		return true;
	}

	/** 渲染距离（格）。与转盘一致：小物件没必要远处也画 */
	@Override
	public int getRenderDistance() {
		return 48;
	}
}
