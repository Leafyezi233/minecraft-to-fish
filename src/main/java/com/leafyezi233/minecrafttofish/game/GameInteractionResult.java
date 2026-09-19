package com.leafyezi233.minecrafttofish.game;

/**
 * 「尝试与方块交互」的结果，供方块层决定给玩家什么反馈。
 *
 * <p><b>为什么叫 GameInteractionResult 而不是 InteractionResult</b>：
 * 原版已经有 {@code net.minecraft.util.ActionResult} 等一组同名概念，
 * 而本模组两个游戏方块都要用它。名字里带 {@code Game} 是为了在
 * {@code import} 列表里一眼分得清哪个是原版的、哪个是本模组的。
 */
public enum GameInteractionResult {
	/** 成功，已改变状态 */
	OK,
	/** 盘上已有鱼，或正在演出 */
	BUSY,
	/** 手上这条鱼不能放（不是模组鱼 / 没有价值） */
	REJECTED,
	/** 手上是空的 */
	EMPTY,
}
