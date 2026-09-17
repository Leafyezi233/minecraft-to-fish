package com.leafyezi233.minecrafttofish.entity.client;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.model.CodEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;
import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.entity.AggressiveFishEntity;

/**
 * 攻击性鱼的渲染器。
 * 复用原版鳕鱼(Cod)的模型和贴图，让它先有个样子。
 */
public class AggressiveFishEntityRenderer extends MobEntityRenderer<AggressiveFishEntity, CodEntityModel<AggressiveFishEntity>> {
	// 先用原版鳕鱼贴图，之后可换成自定义贴图
	private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/fish/cod.png");

	public AggressiveFishEntityRenderer(EntityRendererFactory.Context context) {
		super(context, new CodEntityModel<>(context.getPart(EntityModelLayers.COD)), 0.3f);
	}

	@Override
	public Identifier getTexture(AggressiveFishEntity entity) {
		return TEXTURE;
	}
}