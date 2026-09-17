package com.leafyezi233.minecrafttofish.entity.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SalmonEntityModel;
import net.minecraft.util.Identifier;
import com.leafyezi233.minecrafttofish.entity.BrutalFishEntity;

/**
 * 凶猛的鱼的渲染器。
 * 复用原版鲑鱼(Salmon)的模型和贴图，让它和攻击性鱼（鳕鱼）外观明显不同。
 */
public class BrutalFishEntityRenderer extends MobEntityRenderer<BrutalFishEntity, SalmonEntityModel<BrutalFishEntity>> {
	// 原版鲑鱼贴图
	private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/fish/salmon.png");

	public BrutalFishEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new SalmonEntityModel<>(context.getPart(EntityModelLayers.SALMON)), 0.3f);
	}

	@Override
	public Identifier getTexture(BrutalFishEntity entity) {
		return TEXTURE;
	}
}