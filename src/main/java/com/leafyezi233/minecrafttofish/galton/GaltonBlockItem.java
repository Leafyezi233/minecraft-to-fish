package com.leafyezi233.minecrafttofish.galton;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 高尔顿板的物品：<b>放置失败时给玩家一句说明</b>。
 *
 * <h2>为什么需要这个子类</h2>
 * 盘面要向上占约 2 格，所以 {@link GaltonBlock#canPlaceAt} 会在上方不是空气时返回 false。
 * 而原版 {@code BlockItem.place} 在 {@code canPlace} 为 false 时只是
 * <b>静默返回 FAIL</b>（已核字节码：偏移 22 调 {@code canPlace()}，
 * 为假则偏移 28 直接 {@code areturn} FAIL）：
 * 玩家右键半天，方块不出现，也<b>没有任何提示</b> ——
 * 他只会以为「这个物品坏了」或「模组没装上」，而不会想到「头上那格有方块」。
 *
 * <p>这种「无反馈的失败」在本项目别处也踩过：转盘关闭时原本也是右键没反应，
 * 后来专门加了一句「已在配置中关闭」。同一个道理，这里必须说清楚原因。
 *
 * <h2>为什么服务端发消息就够了（已核字节码）</h2>
 * 客户端 {@code ClientPlayerInteractionManager.interactBlock} 在偏移 28–56
 * <b>无条件</b> {@code sendSequencedPacket}（只有「目标在世界边界外」才提前返回），
 * 随后才在 {@code interactBlockInternal} 里跑 {@code onUse} / 放置。
 * 也就是说<b>无论客户端判定成功还是失败，包都会发到服务端</b>，
 * 服务端一定会走到 {@link #place}。因此这里只需要服务端分支，
 * 不必再维护一份客户端提示（两份提示会重复显示）。
 *
 * <h2>为什么判定要用 {@link GaltonBlock#hasRoomAbove}</h2>
 * 第一版在这里<b>把规则又写了一遍</b>，而且自相矛盾：用
 * {@code state.canPlaceAt(...) && !上方可替换} 判断「是不是被这条规则挡住的」，
 * 但 {@code canPlaceAt} 本身已包含该规则 —— 被挡住时它必然为 false，
 * 整个条件恒为假，提示<b>永远不会发出</b>（就是玩家看到的静默失败）。
 * 现在两边共用同一个静态判定，从结构上消除这种不一致。
 */
public class GaltonBlockItem extends BlockItem {

	public GaltonBlockItem(Block block, Settings settings) {
		super(block, settings);
	}

	@Override
	public ActionResult place(ItemPlacementContext context) {
		ActionResult result = super.place(context);

		// 只处理「因为上方没空间而失败」这一种情况。
		// 其它失败原因（手太短、目标被占用、维度限制等）原版有自己的处理，不该乱插话。
		if (result == ActionResult.FAIL && !context.getWorld().isClient) {
			World world = context.getWorld();
			BlockPos pos = context.getBlockPos();

			// 唯一判定来源，与 canPlaceAt 用的是同一个方法
			if (!GaltonBlock.hasRoomAbove(world, pos) && context.getPlayer() != null) {
				context.getPlayer().sendMessage(
						Text.translatable("galton.minecraft_to_fish.msg.need_space"), true);
			}
		}
		return result;
	}
}
