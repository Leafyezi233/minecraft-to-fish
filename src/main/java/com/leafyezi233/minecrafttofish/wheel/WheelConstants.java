package com.leafyezi233.minecrafttofish.wheel;

import com.leafyezi233.minecrafttofish.MyMod;

import net.minecraft.util.Identifier;

/**
 * 渔轮转盘的共享常量。
 *
 * <p>集中放这里是为了让服务端（方块/界面）与客户端（界面/绘制）引用同一份定义，
 * 避免两边各写一份字符串导致对不上。
 */
public final class WheelConstants {

	/** 方块与界面共用的路径名 */
	public static final String PATH = "fish_wheel";

	/** 方块 id */
	public static final Identifier BLOCK_ID = new Identifier(MyMod.MOD_ID, PATH);

	/** 界面类型 id */
	public static final Identifier SCREEN_HANDLER_ID = new Identifier(MyMod.MOD_ID, PATH);

	// ---------------------------------------------------------------- 界面尺寸

	/** 面板宽度（原版标准容器宽度） */
	public static final int SCREEN_WIDTH = 176;

	/** 面板高度（比原版标准容器高，因为上面要放转盘） */
	public static final int SCREEN_HEIGHT = 200;

	/** 鱼槽在面板内的坐标 */
	public static final int SLOT_X = 26;
	public static final int SLOT_Y = 24;

	/** 转盘圆心与半径（面板相对坐标） */
	public static final int WHEEL_CENTER_X = 110;
	public static final int WHEEL_CENTER_Y = 58;
	public static final int WHEEL_RADIUS = 30;

	/** 「开始」按钮位置与尺寸 */
	public static final int BUTTON_X = 20;
	public static final int BUTTON_Y = 96;
	public static final int BUTTON_WIDTH = 56;
	public static final int BUTTON_HEIGHT = 20;

	/** 结果文字基线（转盘下方，居中于转盘圆心） */
	public static final int RESULT_TEXT_Y = 96;

	/** 玩家背包区起点（面板相对坐标） */
	public static final int PLAYER_INV_X = 8;
	public static final int PLAYER_INV_Y = 118;

	/** 快捷栏区起点 */
	public static final int HOTBAR_X = 8;
	public static final int HOTBAR_Y = 176;

	/** 背包标题基线 */
	public static final int INVENTORY_TITLE_Y = 108;

	// ---------------------------------------------------------------- 按钮 id

	/** 「开始」按钮的 id（原版按钮通道的 buttonId） */
	public static final int BUTTON_SPIN = 0;

	// ---------------------------------------------------------------- 属性槽

	/** 属性槽数量：序号、倍率、转动中、金额低/高、金额就绪 */
	public static final int PROPERTY_COUNT = 6;

	/** 抽奖序号：每次成功抽奖 +1，客户端据此判断「有新结果」并播动画 */
	public static final int PROPERTY_SPIN_SEQUENCE = 0;

	/** 本次结果倍率（-1 表示还没有结果） */
	public static final int PROPERTY_RESULT_MULTIPLIER = 1;

	/**
	 * 是否正在结算（1 = 转盘转动中，尚未落地）。
	 * <p>用同步属性而不是服务端字段，是为了让两端的槽位锁行为一致 ——
	 * 否则客户端会允许拖走物品、再被服务端拒绝，出现「物品弹回去」的怪手感。
	 */
	public static final int PROPERTY_SPINNING = 2;

	/**
	 * 结算后的奖励金额（低位 32 位）。
	 * <p><b>为什么要把金额同步过来，而不是让客户端自己读槽位算</b>：
	 * 客户端动画结束是<b>本地时钟</b>决定的，与服务端何时结算、何时发包没有同步关系。
	 * 让客户端在"动画结束时"去读槽位，就会和服务端的包赛跑 ——
	 * 槽位包晚到一步，读到的就是加成前的旧值，界面显示错误金额。
	 * 直接把权威金额推过来，客户端只负责显示，时序问题从根本上消失。
	 */
	public static final int PROPERTY_RESULT_VALUE_LOW = 3;

	/** 结算后的奖励金额（高位 32 位）。未中奖或尚未结算时为 0 */
	public static final int PROPERTY_RESULT_VALUE_HIGH = 4;

	/**
	 * 奖励金额是否已就绪（1 = 已结算，可以显示）。
	 * <p><b>为什么需要这个标志</b>：金额是 64 位，只能拆成高低两个 int 同步。
	 * 若客户端在「低位已更新、高位还是旧值」的中间状态读取，会拼出一个巨大的乱码数字。
	 * 用这个标志把「两个分量都写完了」这件事显式表达出来，客户端只认就绪状态，
	 * 就不依赖任何包顺序假设了。
	 */
	public static final int PROPERTY_RESULT_VALUE_READY = 5;

	/** 结果倍率的初始值，表示「尚无结果」 */
	public static final int NO_RESULT = -1;

	/**
	 * 结果金额的初始值，表示「服务端尚未结算」。
	 * <p>注意与「未中奖」区分：未中奖时金额为 {@code 0}（合法的已结算状态），
	 * 只有这个值才代表还没结算。
	 */
	public static final long NO_RESULT_VALUE = -1L;

	private WheelConstants() {
	}
}
