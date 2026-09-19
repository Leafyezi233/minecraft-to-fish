package com.leafyezi233.minecrafttofish.game;

/**
 * 小游戏方块的三态（由「有没有鱼」与「是否正在演出」推导，<b>不单独存字段</b>）。
 *
 * <p>两个小游戏共用：转盘的「转动中」与高尔顿板的「球在下落」是同一件事 ——
 * 都在占用方块、都拒绝交互。所以状态名取通用的 {@code BUSY}，
 * 而不是各自定义一个 {@code SPINNING} / {@code DROPPING}。
 */
public enum GameState {
	/** 空盘，可放入 */
	EMPTY,
	/** 有鱼且不在演出，可开始 / 可取走 */
	READY,
	/** 演出中，拒绝一切交互 */
	BUSY,
}
