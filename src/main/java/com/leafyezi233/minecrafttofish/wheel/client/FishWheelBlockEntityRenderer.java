package com.leafyezi233.minecrafttofish.wheel.client;

import com.leafyezi233.minecrafttofish.wheel.FishWheelBlockEntity;
import com.leafyezi233.minecrafttofish.wheel.WheelAngle;
import com.leafyezi233.minecrafttofish.wheel.WheelConstants;
import com.leafyezi233.minecrafttofish.wheel.WheelSector;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * 渔轮转盘的世界渲染（<b>仅客户端</b>）—— 这就是「所有人在世界里直接看到转盘」的实现。
 *
 * <h2>为什么需要 BlockEntityRenderer</h2>
 * 方块模型是静态几何，原版只会在方块状态变化时重建它，
 * <b>不可能每帧跟着服务端状态转动</b>。要让盘面连续旋转、要显示盘上的鱼和结果文字，
 * 就必须用方块实体渲染器：它每帧都会被调用，且拿到的是同步后的方块实体状态。
 *
 * <h2>画了什么</h2>
 * <ol>
 *   <li><b>盘体</b>：有厚度的竖直圆盘（前盖 + 侧壁 + 后盖），按扇区表分块上色</li>
 *   <li><b>分割线</b>：扇区色块两侧内缩，缝隙露出底色</li>
 *   <li><b>指针高亮</b>：指针正下方那一格自动提亮，外圈对应弧段一起亮</li>
 *   <li><b>指针</b>：固定在正上方的红色三角</li>
 *   <li><b>支架</b>：盘后一根立柱，把盘「撑」在底座上</li>
 *   <li><b>盘上的鱼</b>：中奖后留在盘上、谁都能取走的那条，画在盘心</li>
 *   <li><b>空盘提示</b>：没鱼时盘上一圈虚线，提示「这里可以放鱼」</li>
 *   <li><b>结果文字</b>：落地后在方块上方浮出「×2！」或「未中奖」，带半透明底衬</li>
 * </ol>
 *
 * <h2>为什么角度是纯函数算出来的</h2>
 * 见 {@link WheelAngle}：角度只由「服务端下发的状态 + 当前游戏刻」决定，
 * 不读墙钟、不掷随机数，所以<b>所有客户端在同一时刻算出同一个角度</b>，
 * 中途走进来的玩家也会直接看到转动的中间态，而不是从头转一遍。
 *
 * <h2>为什么用「白色贴图 + 顶点颜色」画盘面</h2>
 * 原版没有「纯色但有光照」的公开渲染层可用：
 * 自建渲染层需要 {@code RenderLayer.MultiPhaseParameters}，
 * 而它是 {@code RenderLayer} 的 <b>protected</b> 嵌套类，模组从外部<b>无法访问</b>
 * （javap 的 InnerClasses 属性里明确是 {@code protected static final}），
 * 所以「自己拼一个 POSITION_COLOR_LIGHT 层」这条路走不通。
 * <p>退而求其次用原版实体的渲染层，配原版自带的
 * {@code textures/misc/white.png}（实测是 4×4 纯白）：
 * 贴图是纯白，最终颜色就等于顶点颜色，效果与纯色绘制相同，
 * 同时白拿三样东西——<b>世界光照</b>（UV2 光照贴图）、
 * <b>方向性明暗</b>（法线参与 {@code minecraft_mix_light}）、
 * 以及 {@code NoCull} 带来的<b>双面可见</b>（绕到盘背面也不会消失）。
 *
 * <h2>顶点必须按格式顺序写</h2>
 * 该层的顶点格式是 {@code POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL}，
 * 即 位置 → 颜色 → UV0 → UV1(overlay) → UV2(光照) → 法线。
 * 六项<b>一个都不能少、顺序也不能换</b>：少写或错序会让顶点数据整体错位，
 * 表现是颜色乱掉、盘面糊成一团甚至整块消失。
 *
 * <h2>盘体为什么向「后」加厚而不是向「前」</h2>
 * 前表面保持在原来的 {@link #PLANE_OFFSET}，厚度全部往后长。
 * 这样指针与盘上的鱼的 z 偏移<b>一个都不用改</b> ——
 * 若改成往前加厚，前表面会外移 0.06 格，指针（只在盘前 0.012）和鱼（0.06）
 * 就会被埋进盘体里，表现为「指针不见了、鱼只剩一半」。
 */
@Environment(EnvType.CLIENT)
public class FishWheelBlockEntityRenderer implements BlockEntityRenderer<FishWheelBlockEntity> {

	// ---------------------------------------------------------------- 几何常量

	/** 盘面半径（格）。0.40 略小于半格，避免与相邻方块视觉重叠 */
	private static final float RADIUS = 0.40f;

	/** 盘面外圈描边的外半径（格） */
	private static final float RIM_OUTER = RADIUS + 0.03f;

	/** 盘面离方块中心的距离（格）。底座厚 2/16，盘面贴在它前面 */
	private static final float PLANE_OFFSET = 0.30f;

	/**
	 * 盘体厚度（格）。
	 *
	 * <p><b>为什么需要它</b>：改造前所有几何都躺在同一个 z 上（z 恒为 0），
	 * 盘面是零厚度的纸片，从侧面看就是一条线。
	 *
	 * <p>厚度<b>向后方生长</b>，前表面位置不变，理由见类注释。
	 */
	private static final float DISC_THICKNESS = 0.06f;

	/** 盘面中心高度（格，相对方块底面） */
	private static final float CENTER_Y = 0.55f;

	/** 方块底座顶面高度（格）。方块模型里底座是 2/16 厚 */
	private static final float BASE_TOP = 2.0f / 16.0f;

	/** 支架立柱的半宽（格） */
	private static final float PILLAR_HALF_WIDTH = 0.055f;

	/** 支架立柱的进深（格），从盘体背面往后再留这么厚 */
	private static final float PILLAR_DEPTH = 0.09f;

	/** 每个扇区至少细分成几段，避免大扇区看起来是多边形 */
	private static final int MIN_SEGMENTS_PER_SECTOR = 6;

	/** 整圈最少分段数（扇区很少时也保证圆看起来是圆的） */
	private static final int MIN_TOTAL_SEGMENTS = 48;

	/** 盘面内半径比例：圆心留一小块不放扇区色，改画轮毂 */
	private static final float INNER_RATIO = 0.12f;

	/**
	 * 同一平面上的分层间距（格）。
	 *
	 * <p>分割线不是「另画一条线」，而是<b>色块底下露出的底色</b>。
	 * 底色与色块若画在同一个 z 上就会 z-fighting（谁赢取决于驱动实现，
	 * 表现为缝隙一闪一闪）。这里给色块一个极小的前移量把两者分开，
	 * 0.002 格 = 2 毫米，肉眼看不出来，但足够让深度测试稳定。
	 */
	private static final float LAYER_EPS = 0.002f;

	/** 分割线底色（同时也是盘体侧壁与轮毂的颜色） */
	private static final int RIM_COLOR = 0xFF2B2B2B;

	/** 盘体背面色（比正面更暗，一眼能分出正反面） */
	private static final int DISC_BACK_COLOR = 0xFF1E1E1E;

	/** 支架立柱颜色（比盘体略亮，否则从侧面看会和盘糊成一块） */
	private static final int PILLAR_COLOR = 0xFF4A4A4A;

	/** 空盘提示虚线环颜色 */
	private static final int PLACEHOLDER_COLOR = 0xFF6E6E6E;

	/**
	 * 指针颜色（亮红，ARGB）。
	 *
	 * <p><b>为什么不用金色</b>：原先用的是 {@code 0xFFD4AF37}，而内置默认扇区表里
	 * 「×5」那一格的颜色<b>正好也是 {@code 0xFFD4AF37}</b>（同一个调色板常量）。
	 * 两者完全相同 ⇒ 每次 ×5 转到指针下面，指针就<b>整块隐形</b>，
	 * 玩家会以为指针消失了。
	 * <p>亮红与默认表的三色（蓝 {@code 4FA3D1} / 金 {@code D4AF37} / 灰 {@code 6B6B6B}）
	 * 都有足够反差，服主自定义颜色时也不容易撞色。
	 * <p>常量本体放在 {@link WheelConstants}，因为服务端自测要断言它不与任何扇区撞色。
	 */
	private static final int POINTER_COLOR = WheelConstants.POINTER_COLOR;

	/** 结果文字在方块上方的高度（格） */
	private static final float LABEL_Y = 1.30f;

	/**
	 * 结果文字的半透明底衬（ARGB）。
	 *
	 * <p>原先给 {@code TextRenderer.draw} 的背景参数传的是 {@code 0}，
	 * 即「不画背景」。文字在雪地、水面或逆光下会糊得看不清。
	 *
	 * <p>用深色而非纯黑不透明：alpha 取 {@code 0xB0}（约 69%），
	 * 既压得住亮背景，又能透出一点环境色，不至于像贴了块黑板。
	 *
	 * <p><b>这个参数确实会被原版用上</b>（不是想当然）：
	 * {@code TextRenderer$Drawer.drawLayer(int, float)} 的字节码对背景色先
	 * {@code ifeq} 判断「为 0 就跳过」，否则按 ARGB 拆出 alpha 参与绘制；
	 * 而 {@code RenderLayer} 静态块里 {@code text_background} 用的是
	 * {@code TRANSLUCENT_TRANSPARENCY}（半透明混合，不是 alpha 裁剪）。
	 */
	private static final int LABEL_BG_COLOR = 0xB0101010;

	/** 不透明：该渲染层走 cutout，半透明会被 alpha 测试直接裁掉 */
	private static final int ALPHA = 255;

	/**
	 * 原版自带的纯白贴图（实测 4×4，全部像素 255,255,255）。
	 *
	 * <p>用它当「颜色载体」：贴图是白的，乘上顶点颜色就只剩顶点颜色，
	 * 于是能借用原版实体渲染层的光照与双面渲染，而不用自己造渲染层。
	 */
	private static final Identifier WHITE_TEXTURE = new Identifier("minecraft", "textures/misc/white.png");

	private final TextRenderer textRenderer;

	/**
	 * 盘面用的渲染层（懒加载缓存）。
	 *
	 * <p>{@code getEntityCutoutNoCull} 是关键选择：{@code NoCull} 表示<b>双面可见</b>，
	 * 玩家绕到盘背面时盘面不会整块消失。
	 */
	private static volatile RenderLayer wheelLayer;

	private static RenderLayer wheelLayer() {
		RenderLayer layer = wheelLayer;
		if (layer == null) {
			layer = RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE);
			wheelLayer = layer;
		}
		return layer;
	}

	public FishWheelBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
		this.textRenderer = context.getTextRenderer();
	}

	// ---------------------------------------------------------------- 主入口

	@Override
	public void render(FishWheelBlockEntity wheel, float tickDelta, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light, int overlay) {

		WheelTable table = WheelTable.current();
		if (table == null || table.size() == 0) {
			return;
		}

		// 用方块自身位置算光照：转盘因此与周围方块的明暗一致
		int packedLight = lightFor(wheel, light);

		float yaw = yawOf(wheel.facing());
		// 当前盘面角度（含帧插值，转动才够顺滑）
		float angle = currentAngle(wheel, table, tickDelta);

		// 指针此刻指着哪一格：用于「指针下方高亮」。
		// 这是 restAngle 的逆运算，两者互为逆运算由自测钉死。
		int pointerSector = WheelAngle.sectorAt(table, angle);

		VertexConsumer buffer = vertexConsumers.getBuffer(wheelLayer());

		// ---- 盘体（跟着 angle 转）
		matrices.push();
		matrices.translate(0.5, CENTER_Y, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		// 先把盘面推到方块前表面，再绕自身轴旋转。
		// 顺序不能反：先转再平移会让盘面绕方块中心画圆，直接飞出方块外。
		matrices.translate(0.0, 0.0, PLANE_OFFSET);
		matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));

		renderDisc(buffer, matrices.peek(), table, pointerSector, !wheel.hasFish(), packedLight);

		matrices.pop();

		// ---- 指针与支架（都不跟着盘转，所以另起矩阵且不施加 angle）
		matrices.push();
		matrices.translate(0.5, CENTER_Y, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		matrices.translate(0.0, 0.0, PLANE_OFFSET + 0.012);
		renderPointer(buffer, matrices.peek(), packedLight);
		matrices.pop();

		// 支架的坐标基准是「方块底面 + 盘面法线方向」，与盘面中心无关，
		// 所以这里单独平移，不能复用上面两个矩阵
		matrices.push();
		matrices.translate(0.5, 0.0, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		renderPillar(buffer, matrices.peek(), packedLight);
		matrices.pop();

		renderFish(wheel, yaw, matrices, vertexConsumers, packedLight);
		renderLabel(wheel, table, matrices, vertexConsumers, packedLight);
	}

	/** 取方块所在位置的光照坐标；世界不可用时退回传入的 light */
	private static int lightFor(FishWheelBlockEntity wheel, int fallback) {
		if (wheel == null || wheel.getWorld() == null) {
			return fallback;
		}
		try {
			return WorldRenderer.getLightmapCoordinates(wheel.getWorld(), wheel.getPos());
		} catch (Throwable t) {
			// 区块边界等极端情况下取不到光照，不该让渲染崩掉
			return fallback;
		}
	}

	// ---------------------------------------------------------------- 角度

	/**
	 * 当前盘面角度（度）。
	 *
	 * <p>转动中：按「已过刻数 / 总刻数」求进度，交给 {@link WheelAngle#spinAngle} 缓动。
	 * 已落地或从未转过：直接取落点角。
	 */
	private static float currentAngle(FishWheelBlockEntity wheel, WheelTable table, float tickDelta) {
		if (!wheel.isSpinning()) {
			// 静止：停在上次落点（扇区与抖动必须成对取）
			return WheelAngle.restAngle(table, wheel.restingSector(), wheel.restingJitter());
		}

		long totalTicks = wheel.spinTicks();
		if (totalTicks <= 0L) {
			// 时长为 0 的配置：没有转动过程，直接就是落点
			return WheelAngle.restAngle(table, wheel.resultSectorIndex(), wheel.spinJitter());
		}

		// 客户端与服务端的 world.getTime() 起点一致（登录时同步过），差值可靠
		long elapsed = clientTime() - wheel.spinStartTick();
		if (elapsed < 0L) {
			// 服务端时钟略超前（包刚到、本端还没走到那一 tick）：按刚开始处理。
			// 不夹住的话缓动会算出反向角度，盘面会倒着弹一下。
			elapsed = 0L;
		}

		// 起止抖动都来自服务端，所有客户端因此算出完全相同的落点
		return WheelAngle.spinAngle(table,
				wheel.prevSectorIndex(), wheel.prevSpinJitter(),
				wheel.resultSectorIndex(), wheel.spinSequence(), wheel.spinJitter(),
				elapsed + tickDelta, (float) totalTicks);
	}

	/** 客户端当前游戏刻；拿不到世界时返回 0（渲染不会因此崩） */
	private static long clientTime() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return 0L;
		}
		return client.world.getTime();
	}

	/**
	 * 朝向 → 绕 Y 轴旋转角度。
	 *
	 * <p>实现已挪到 {@link WheelAngle#yawOf}，因为那是个纯角度约定，
	 * 放在共享类里能被服务端自测覆盖（朝向画反只有联机时才看得出来）。
	 * 这里只做转发，不再维护第二份映射 —— 两份映射正是当初东西写反的温床。
	 */
	private static float yawOf(Direction facing) {
		return WheelAngle.yawOf(facing);
	}

	// ---------------------------------------------------------------- 盘体

	/**
	 * 画整个盘体：侧壁 + 后盖 + 前盖。
	 *
	 * <p>局部坐标里前表面是 {@code z = 0}，后表面是 {@code z = -DISC_THICKNESS}，
	 * 厚度向后长（理由见类注释）。
	 *
	 * <h2>前盖的绘制顺序（决定分割线能不能出来）</h2>
	 * <ol>
	 *   <li>先在 {@code z = 0} 铺一层<b>整圈底色</b>（{@link #RIM_COLOR}）</li>
	 *   <li>再把各扇区色块<b>两侧内缩</b>后画在 {@code z = LAYER_EPS}，
	 *       内缩露出的缝隙就是分割线</li>
	 *   <li>外圈描边与轮毂单独画，与扇区色块半径不重叠</li>
	 * </ol>
	 * 第 2 步的前移量不是装饰：同 z 画两层会 z-fighting，缝隙会一闪一闪。
	 *
	 * @param empty 盘上无鱼时画虚线提示
	 */
	private static void renderDisc(VertexConsumer buffer, MatrixStack.Entry entry,
			WheelTable table, int pointerSector, boolean empty, int light) {

		float inner = RADIUS * INNER_RATIO;
		float back = -DISC_THICKNESS;

		// 侧壁：盘体厚度就体现在这里
		discWall(buffer, entry, back, 0.0f, RIM_OUTER, RIM_COLOR, light);

		// 后盖：整块暗色，从背面看是个实心盘
		ringFlat(buffer, entry, back, -1, 0.0f, RIM_OUTER, 0.0f, 360.0f, DISC_BACK_COLOR, light);

		// 前盖底色：分割线就是它从扇区缝隙里透出来的部分
		ringFlat(buffer, entry, 0.0f, 1, inner, RADIUS, 0.0f, 360.0f, RIM_COLOR, light);

		// 前盖扇区色块（两侧内缩 → 缝隙 = 分割线）
		for (int i = 0; i < table.size(); i++) {
			WheelSector sector = table.sector(i);
			float start = table.startAngle(i);
			float end = table.endAngle(i);
			float inset = WheelAngle.dividerInset(end - start);

			float from = start + inset;
			float to = end - inset;
			if (to <= from) {
				// 理论上不会发生（内缩量按张角比例夹过），兜底跳过而不是画出负宽度
				continue;
			}

			// 指针正下方那一格提亮：玩家一眼看出「现在指着哪」
			boolean lit = i == pointerSector;
			int color = lit
					? WheelConstants.highlight(sector.color(), WheelConstants.HIGHLIGHT_STRENGTH)
					: sector.color();

			ringFlat(buffer, entry, LAYER_EPS, 1, inner, RADIUS, from, to, color, light);

			if (lit) {
				// 外圈对应弧段一起亮，让高亮在远处也看得见
				ringFlat(buffer, entry, LAYER_EPS, 1, RADIUS, RIM_OUTER, from, to, color, light);
			}
		}

		// 外圈描边（扇区之外的环带，与扇区半径不重叠）
		ringFlat(buffer, entry, 0.0f, 1, RADIUS, RIM_OUTER, 0.0f, 360.0f, RIM_COLOR, light);

		// 轮毂：盘心那块原本什么都不画，做成实心盘后必须补上，否则中间是个洞
		ringFlat(buffer, entry, LAYER_EPS, 1, 0.0f, inner, 0.0f, 360.0f, RIM_COLOR, light);

		if (empty) {
			renderPlaceholder(buffer, entry, light);
		}
	}

	/**
	 * 空盘提示：盘上一圈虚线。
	 *
	 * <p>空盘与「有鱼但还没转」在远处看起来差不多，玩家不知道这里能不能放鱼。
	 * 虚线用短弧拼出来，只在<b>确实空着</b>时画。
	 *
	 * <p>半径取盘面中段（{@code 0.42~0.60} 个半径）：太靠内会被轮毂挤掉，
	 * 太靠外会和扇区外缘的分割线混在一起。
	 */
	private static void renderPlaceholder(VertexConsumer buffer, MatrixStack.Entry entry, int light) {
		final int dashes = 12;
		final float span = 360.0f / dashes;

		for (int i = 0; i < dashes; i++) {
			float from = i * span + span * 0.15f;
			float to = from + span * 0.7f;
			ringFlat(buffer, entry, LAYER_EPS * 2, 1,
					RADIUS * 0.42f, RADIUS * 0.60f, from, to, PLACEHOLDER_COLOR, light);
		}
	}

	/**
	 * 盘体侧壁（厚度那一圈）。
	 *
	 * <p>法线取该段<b>中角</b>的径向方向，而不是统一给 (0,0,1)：
	 * 侧壁是一圈朝外的曲面，法线跟着转才能让原版的
	 * {@code minecraft_mix_light} 算出正确的方向性明暗 ——
	 * 否则整圈侧壁会是一块死板的平色。
	 */
	private static void discWall(VertexConsumer buffer, MatrixStack.Entry entry,
			float zBack, float zFront, float radius, int color, int light) {

		int segments = segmentsFor(360.0f);
		float step = 360.0f / segments;

		for (int i = 0; i < segments; i++) {
			float a0 = step * i;
			float a1 = a0 + step;
			float mid = (a0 + a1) / 2.0f;

			float sin0 = sin(a0);
			float cos0 = cos(a0);
			float sin1 = sin(a1);
			float cos1 = cos(a1);

			quad3(buffer, entry, color, light,
					sin(mid), cos(mid), 0.0f,
					sin0 * radius, cos0 * radius, zBack,
					sin1 * radius, cos1 * radius, zBack,
					sin1 * radius, cos1 * radius, zFront,
					sin0 * radius, cos0 * radius, zFront);
		}
	}

	/**
	 * 支架立柱：从底座顶面顶到盘心，位于盘体<b>后方</b>。
	 *
	 * <p>局部坐标基准是「方块底面 + 盘面法线方向」，所以 y 直接就是方块内高度，
	 * z 是相对方块中心的偏移。
	 *
	 * <p><b>为什么在盘后</b>：盘面下缘到 y=0.15，而底座顶面在 y=0.125，
	 * 中间只有 0.025 格 —— 盘前根本没有能站柱子的地方。
	 * 立在盘后既符合真实转盘的构造，也从侧面/背面一眼看出盘是被撑起来的。
	 */
	private static void renderPillar(VertexConsumer buffer, MatrixStack.Entry entry, int light) {
		float back = PLANE_OFFSET - DISC_THICKNESS;
		box(buffer, entry, PILLAR_COLOR, light,
				-PILLAR_HALF_WIDTH, BASE_TOP, back - PILLAR_DEPTH,
				PILLAR_HALF_WIDTH, CENTER_Y, back);
	}

	// ---------------------------------------------------------------- 指针

	/**
	 * 指针：一个朝下的三角，固定在正上方（0°）。
	 *
	 * <p>尺寸都按盘面半径取值，改 {@link #RADIUS} 时指针会自动跟着缩放。
	 * <ul>
	 *   <li><b>变细</b>：半宽由 {@code 0.26} 收到 {@code 0.13} 个半径（整宽减半），
	 *       原先粗得像一块楔子</li>
	 *   <li><b>尖端只到外圈内侧</b>：尖端 {@code 0.86} 个半径，紧贴扇区色块的外缘。
	 *       原先尖端伸到 {@code 0.42} 个半径（接近盘心），会把指针所指的那一格
	 *       <b>盖掉一大块</b> —— 玩家反而看不清自己中没中</li>
	 *   <li>底边 {@code 1.10} 个半径，落在描边圈外，视觉上像「插在盘沿上」</li>
	 * </ul>
	 * 整体高度不超出方块顶面，不会戳出方块外。
	 */
	private static void renderPointer(VertexConsumer buffer, MatrixStack.Entry entry, int light) {
		float tipY = RADIUS * 0.86f;
		float baseY = RADIUS * 1.10f;
		float halfWidth = RADIUS * 0.13f;

		// 三角形用 QUADS 画：末顶点重复一次，退化成三角形
		quad3(buffer, entry, POINTER_COLOR, light, 0.0f, 0.0f, 1.0f,
				0.0f, tipY, 0.0f,
				-halfWidth, baseY, 0.0f,
				halfWidth, baseY, 0.0f,
				halfWidth, baseY, 0.0f);
	}

	// ---------------------------------------------------------------- 盘上的鱼

	/** 把盘上的鱼画在盘心（所有人可见、谁都能取走的那条） */
	private static void renderFish(FishWheelBlockEntity wheel, float yaw, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {

		ItemStack fish = wheel.fishStack();
		if (fish == null || fish.isEmpty()) {
			return;
		}

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.getItemRenderer() == null) {
			return;
		}

		matrices.push();
		matrices.translate(0.5, CENTER_Y, 0.5);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
		// 浮在盘面前方，避免与盘面共面导致闪烁（z-fighting）
		matrices.translate(0.0, 0.0, PLANE_OFFSET + 0.06);
		// 物品模型默认躺着，转正让它像贴在盘上
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
		matrices.scale(0.5f, 0.5f, 0.5f);

		client.getItemRenderer().renderItem(fish, ModelTransformationMode.FIXED,
				light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers,
				client.world, 0);

		matrices.pop();
	}

	// ---------------------------------------------------------------- 结果文字

	/**
	 * 落地后在方块上方显示结果；转动中不显示，避免剧透。
	 *
	 * <p>文字<b>始终正对镜头</b>（billboard），用的是原版命名牌（nametag）那一套：
	 * 平移到位后乘上相机旋转，再缩放 {@code (-0.025, -0.025, 0.025)}。
	 * 不这么做的话，文字会固定贴在盘面上，玩家绕到侧面或背面就变成一条线看不见了。
	 *
	 * <p>底色由 {@link #LABEL_BG_COLOR} 提供（原先传 0 = 不画背景，逆光下看不清）。
	 */
	private void renderLabel(FishWheelBlockEntity wheel, WheelTable table, MatrixStack matrices,
			VertexConsumerProvider vertexConsumers, int light) {

		if (wheel.isSpinning()) {
			return;
		}
		int index = wheel.resultSectorIndex();
		if (index < 0 || index >= table.size()) {
			return;
		}

		WheelSector sector = table.sector(index);
		// 语言文件里已经是「×%s！」/「No win」，这里只传倍率数字，
		// 不能再拼一个「×」上去，否则会显示成「××2！」
		Text text = sector.isWin()
				? Text.translatable("wheel.minecraft_to_fish.result.win", sector.multiplier())
				: Text.translatable("wheel.minecraft_to_fish.result.lose");
		int color = sector.isWin() ? 0xFFFFD24A : 0xFFB0B0B0;

		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.gameRenderer == null) {
			return;
		}

		matrices.push();
		matrices.translate(0.5, LABEL_Y, 0.5);
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

	// ---------------------------------------------------------------- 顶点工具

	/**
	 * 画一段圆环（扇区色块 / 描边 / 轮毂），坐标在盘面局部 XY 平面上。
	 *
	 * <p>角度约定与 {@link WheelTable} 一致：0° 在正上方，顺时针增长。
	 * 因此屏幕坐标是 {@code x = sin(a) * r}、{@code y = cos(a) * r} ——
	 * 用 cos 作 y 才能让 0° 落在正上方，用 sin 作 x 才能让角度顺时针增长。
	 *
	 * @param z          该层所在的 z（分层用，见 {@link #LAYER_EPS}）
	 * @param normalSign {@code +1} 画正面、{@code -1} 画背面
	 */
	private static void ringFlat(VertexConsumer buffer, MatrixStack.Entry entry, float z, int normalSign,
			float innerRadius, float outerRadius, float startDeg, float endDeg, int color, int light) {

		float sweep = endDeg - startDeg;
		if (sweep <= 0.0f || outerRadius <= innerRadius) {
			return;
		}

		int segments = segmentsFor(sweep);
		float step = sweep / segments;

		for (int i = 0; i < segments; i++) {
			float a0 = startDeg + step * i;
			float a1 = a0 + step;

			float sin0 = sin(a0);
			float cos0 = cos(a0);
			float sin1 = sin(a1);
			float cos1 = cos(a1);

			quad3(buffer, entry, color, light, 0.0f, 0.0f, normalSign,
					sin0 * innerRadius, cos0 * innerRadius, z,
					sin1 * innerRadius, cos1 * innerRadius, z,
					sin1 * outerRadius, cos1 * outerRadius, z,
					sin0 * outerRadius, cos0 * outerRadius, z);
		}
	}

	/**
	 * 画一个轴对齐的长方体（六个面）。
	 *
	 * <p>每个面用自己的外向法线，原版的光照因此能区分六个方向，
	 * 方块才有立体感而不是一块平色。
	 *
	 * <p>面朝向靠 {@code getEntityCutoutNoCull} 的 {@code NoCull} 保证双面可见，
	 * 所以即便绕序在某些视角下反了也不会整面消失；法线仍按外向给，用于明暗。
	 */
	private static void box(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
			float x0, float y0, float z0, float x1, float y1, float z1) {

		// 南 (+Z)
		quad3(buffer, entry, color, light, 0.0f, 0.0f, 1.0f,
				x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		// 北 (-Z)
		quad3(buffer, entry, color, light, 0.0f, 0.0f, -1.0f,
				x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		// 东 (+X)
		quad3(buffer, entry, color, light, 1.0f, 0.0f, 0.0f,
				x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		// 西 (-X)
		quad3(buffer, entry, color, light, -1.0f, 0.0f, 0.0f,
				x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		// 上 (+Y)
		quad3(buffer, entry, color, light, 0.0f, 1.0f, 0.0f,
				x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		// 下 (-Y)
		quad3(buffer, entry, color, light, 0.0f, -1.0f, 0.0f,
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	/** 输出一个四边形（两个三角形），四个顶点按顺序给出，共享同一条法线 */
	private static void quad3(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
			float nx, float ny, float nz,
			float x0, float y0, float z0,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3) {

		vertex(buffer, entry, x0, y0, z0, color, light, nx, ny, nz);
		vertex(buffer, entry, x1, y1, z1, color, light, nx, ny, nz);
		vertex(buffer, entry, x2, y2, z2, color, light, nx, ny, nz);
		vertex(buffer, entry, x3, y3, z3, color, light, nx, ny, nz);
	}

	/**
	 * 写一个顶点。
	 *
	 * <p>调用顺序<b>必须</b>是 位置 → 颜色 → UV0 → overlay → 光照 → 法线，
	 * 与该渲染层的顶点格式一一对应；错序或漏写会让顶点数据错位。
	 */
	private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry,
			float x, float y, float z, int color, int light,
			float nx, float ny, float nz) {

		buffer.vertex(entry.getPositionMatrix(), x, y, z)
				.color(color >>> 16 & 0xFF, color >>> 8 & 0xFF, color & 0xFF, ALPHA)
				.texture(0.0f, 0.0f)
				.overlay(OverlayTexture.DEFAULT_UV)
				.light(light)
				.normal(entry.getNormalMatrix(), nx, ny, nz)
				.next();
	}

	// ---------------------------------------------------------------- 小工具

	/** 按张角决定细分段数 */
	private static int segmentsFor(float sweepDegrees) {
		int bySweep = (int) Math.ceil(Math.abs(sweepDegrees) / 360.0f * MIN_TOTAL_SEGMENTS);
		return Math.max(MIN_SEGMENTS_PER_SECTOR, bySweep);
	}

	private static float sin(float degrees) {
		return MathHelper.sin(degrees * MathHelper.RADIANS_PER_DEGREE);
	}

	private static float cos(float degrees) {
		return MathHelper.cos(degrees * MathHelper.RADIANS_PER_DEGREE);
	}

	// ---------------------------------------------------------------- 渲染距离

	/**
	 * 转盘比方块本体略大（盘面直径 0.8 格，上方还有结果文字），
	 * 包围盒外也要渲染，否则走到边缘时盘面会被提前裁掉。
	 */
	@Override
	public boolean rendersOutsideBoundingBox(FishWheelBlockEntity wheel) {
		return true;
	}

	/** 渲染距离（格）。转盘是小物件，没必要远处也画 */
	@Override
	public int getRenderDistance() {
		return 48;
	}
}
