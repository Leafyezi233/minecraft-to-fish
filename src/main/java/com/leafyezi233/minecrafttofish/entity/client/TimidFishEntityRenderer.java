package com.leafyezi233.minecrafttofish.entity.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SmallTropicalFishEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.entity.TimidFishEntity;

/**
 * 胆小的鱼的渲染器。
 * 复用原版热带鱼(Tropical Fish)的模型和贴图。
 * 整体缩小一半，让它看起来更小更可怜（碰撞箱同步缩小，见 ModEntities）。
 */
public class TimidFishEntityRenderer extends MobEntityRenderer<TimidFishEntity, SmallTropicalFishEntityModel<TimidFishEntity>> {
	private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/fish/tropical_a.png");
	/** 模型缩放倍数：0.5 = 缩小一半 */
	private static final float MODEL_SCALE = 0.5f;

	public TimidFishEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new SmallTropicalFishEntityModel<>(context.getPart(EntityModelLayers.TROPICAL_FISH_SMALL)), 0.08f);
	}

	@Override
	protected void scale(TimidFishEntity entity, MatrixStack matrices, float amount) {
		matrices.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
	}

	@Override
	public Identifier getTexture(TimidFishEntity entity) {
		return TEXTURE;
	}
}
