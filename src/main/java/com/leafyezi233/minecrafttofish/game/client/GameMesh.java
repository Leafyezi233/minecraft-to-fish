package com.leafyezi233.minecrafttofish.game.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * 小游戏方块渲染器共用的<b>顶点工具</b>（<b>仅客户端</b>）。
 *
 * <h2>为什么用「白色贴图 + 顶点颜色」画一切</h2>
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
 * 以及 {@code NoCull} 带来的<b>双面可见</b>（绕到背面也不会消失）。
 *
 * <h2>⚠️ 顶点必须按格式顺序写</h2>
 * 该层的顶点格式是 {@code POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL}，
 * 即 位置 → 颜色 → UV0 → UV1(overlay) → UV2(光照) → 法线。
 * 六项<b>一个都不能少、顺序也不能换</b>：少写或错序会让顶点数据整体错位，
 * 表现是颜色乱掉、几何糊成一团甚至整块消失。
 *
 * <h2>为什么单独抽这个类（而不是直接改转盘渲染器）</h2>
 * 转盘渲染器里有一份<b>功能完全相同</b>的私有副本。本可以把它提出来两边共用，
 * 但转盘的外观是<b>已经验收过</b>的，而我<b>无法自己跑客户端验证渲染</b>
 * （本地没有客户端资源）。动它等于冒着「改坏了却没人能立刻发现」的风险，
 * 只为了消除 50 行机械代码的重复 —— 不划算。
 * <p>所以这里新增一份供高尔顿板使用，<b>转盘原样不动</b>。
 * 等哪天转盘也要改外观时，再顺手把它迁过来（那是同一批改动，可一并验收）。
 */
@Environment(EnvType.CLIENT)
public final class GameMesh {

	/** 不透明：该渲染层走 cutout，半透明会被 alpha 测试直接裁掉 */
	public static final int ALPHA = 255;

	/**
	 * 原版自带的纯白贴图（实测 4×4，全部像素 255,255,255）。
	 *
	 * <p>用它当「颜色载体」：贴图是白的，乘上顶点颜色就只剩顶点颜色，
	 * 于是能借用原版实体渲染层的光照与双面渲染，而不用自己造渲染层。
	 */
	public static final Identifier WHITE_TEXTURE =
			new Identifier("minecraft", "textures/misc/white.png");

	/**
	 * 纯色几何用的渲染层（懒加载缓存）。
	 *
	 * <p>{@code getEntityCutoutNoCull} 是关键选择：{@code NoCull} 表示<b>双面可见</b>，
	 * 玩家绕到背面时几何不会整块消失。
	 */
	private static volatile RenderLayer solidLayer;

	private GameMesh() {
	}

	public static RenderLayer solidLayer() {
		RenderLayer layer = solidLayer;
		if (layer == null) {
			layer = RenderLayer.getEntityCutoutNoCull(WHITE_TEXTURE);
			solidLayer = layer;
		}
		return layer;
	}

	// ---------------------------------------------------------------- 基本图元

	/** 输出一个四边形（两个三角形），四个顶点按顺序给出，共享同一条法线 */
	public static void quad(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
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
	public static void vertex(VertexConsumer buffer, MatrixStack.Entry entry,
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

	// ---------------------------------------------------------------- 立体

	/**
	 * 画一个轴对齐的长方体（六个面）。
	 *
	 * <p>每个面用自己的外向法线，原版的光照因此能区分六个方向，
	 * 方块才有立体感而不是一块平色。
	 *
	 * <p>面朝向靠 {@code NoCull} 保证双面可见，所以即便绕序在某些视角下反了
	 * 也不会整面消失；法线仍按外向给，用于明暗。
	 */
	public static void box(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
			float x0, float y0, float z0, float x1, float y1, float z1) {

		// 南 (+Z)
		quad(buffer, entry, color, light, 0.0f, 0.0f, 1.0f,
				x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		// 北 (-Z)
		quad(buffer, entry, color, light, 0.0f, 0.0f, -1.0f,
				x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
		// 东 (+X)
		quad(buffer, entry, color, light, 1.0f, 0.0f, 0.0f,
				x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
		// 西 (-X)
		quad(buffer, entry, color, light, -1.0f, 0.0f, 0.0f,
				x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		// 上 (+Y)
		quad(buffer, entry, color, light, 0.0f, 1.0f, 0.0f,
				x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0);
		// 下 (-Y)
		quad(buffer, entry, color, light, 0.0f, -1.0f, 0.0f,
				x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
	}

	/**
	 * 画一个球（低多边形 UV 球）。
	 *
	 * <p>球体是「圆」这个观感的关键：高尔顿板的球用方块画会看起来像颗钉子，
	 * 玩家分不清哪颗是球、哪颗是钉。
	 *
	 * <p>分段数刻意压得较低（默认 8 环 × 12 段）：一块方块内球直径只有
	 * 约 0.1 格（常见视距下约 1.5 像素），再多分段纯属浪费 ——
	 * 顶点数翻倍而屏幕上完全看不出区别。
	 *
	 * @param radius 半径（格）
	 */
	public static void sphere(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
			float cx, float cy, float cz, float radius, int rings, int segments) {

		int r = Math.max(3, rings);
		int s = Math.max(4, segments);

		for (int i = 0; i < r; i++) {
			// 纬度：0 = 上极，r = 下极
			float phi0 = (float) Math.PI * i / r;
			float phi1 = (float) Math.PI * (i + 1) / r;

			float y0 = cosRad(phi0) * radius;
			float y1 = cosRad(phi1) * radius;
			float rad0 = sinRad(phi0) * radius;
			float rad1 = sinRad(phi1) * radius;

			for (int j = 0; j < s; j++) {
				float theta0 = (float) (Math.PI * 2.0) * j / s;
				float theta1 = (float) (Math.PI * 2.0) * (j + 1) / s;

				float x00 = cosRad(theta0) * rad0;
				float z00 = sinRad(theta0) * rad0;
				float x01 = cosRad(theta1) * rad0;
				float z01 = sinRad(theta1) * rad0;
				float x10 = cosRad(theta0) * rad1;
				float z10 = sinRad(theta0) * rad1;
				float x11 = cosRad(theta1) * rad1;
				float z11 = sinRad(theta1) * rad1;

				// 法线取该面片中心方向（归一化后仍是从球心指向外的单位向量近似）
				float nx = (x00 + x01 + x10 + x11) / 4.0f;
				float ny = (y0 + y1) / 2.0f;
				float nz = (z00 + z01 + z10 + z11) / 4.0f;
				float len = MathHelper.sqrt(nx * nx + ny * ny + nz * nz);
				if (len > 1.0E-4f) {
					nx /= len;
					ny /= len;
					nz /= len;
				} else {
					nx = 0.0f;
					ny = 1.0f;
					nz = 0.0f;
				}

				quad(buffer, entry, color, light, nx, ny, nz,
						cx + x00, cy + y0, cz + z00,
						cx + x01, cy + y0, cz + z01,
						cx + x11, cy + y1, cz + z11,
						cx + x10, cy + y1, cz + z10);
			}
		}
	}

	/** 画一个位于 XZ 平面的矩形（用于槽位色块这类薄片），法线朝 +Z */
	public static void flatQuadZ(VertexConsumer buffer, MatrixStack.Entry entry, int color, int light,
			float x0, float y0, float x1, float y1, float z, float normalZ) {

		quad(buffer, entry, color, light, 0.0f, 0.0f, normalZ,
				x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z);
	}

	private static float sinRad(float radians) {
		return MathHelper.sin(radians);
	}

	private static float cosRad(float radians) {
		return MathHelper.cos(radians);
	}
}
