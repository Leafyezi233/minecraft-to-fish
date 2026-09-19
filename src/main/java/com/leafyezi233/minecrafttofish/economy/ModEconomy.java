package com.leafyezi233.minecrafttofish.economy;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.bridge.EconomyBridges;
import com.leafyezi233.minecrafttofish.economy.command.FishValueCommand;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.net.ValueTableSync;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueLoader;
import com.leafyezi233.minecrafttofish.galton.GaltonBoard;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;
import com.leafyezi233.minecrafttofish.wheel.net.WheelTableSync;

/**
 * 物品经济价值系统的唯一装配入口。
 *
 * <p>{@link MyMod#onInitialize()} 只需调用 {@link #init()} 一行，
 * 其余子系统（价值表加载、经济后端、命令）都由本类按顺序装配，
 * 与模组其他功能完全解耦。
 *
 * <h2>装配顺序</h2>
 * <ol>
 *   <li>读取配置（首次运行生成默认文件）</li>
 *   <li>注册数据包价值表重载监听（开档/ {@code /reload} 时触发加载）</li>
 *   <li>初始化经济后端（内置钱包 + 外部桥探测）</li>
 *   <li>注册 {@code /fishvalue} 命令</li>
 *   <li>注册价值表服务端同步（客户端 HUD 用）</li>
 * </ol>
 */
public final class ModEconomy {

	private static boolean initialized = false;

	private ModEconomy() {
	}

	/** 初始化经济系统（服务端与客户端都会执行，幂等） */
	public static void init() {
		if (initialized) {
			return;
		}
		initialized = true;

		EconomyConfig.load();
		WheelTable.rebuild();
		// 高尔顿板槽位表同样在配置加载后构建一次：非法配置在这里就打出日志并回退，
		// 不会等到玩家按下开始才发现问题
		GaltonBoard.rebuild();
		ItemValueLoader.register();
		EconomyBridges.init();
		FishValueCommand.register();
		ValueTableSync.register();
		// 转盘扇区表也要同步给客户端：客户端没有它就画不出正确的盘面
		WheelTableSync.register();

		// 高尔顿板槽位表同步（客户端要知道服务端配了多少排钉子、每格什么倍率）
		com.leafyezi233.minecrafttofish.galton.net.GaltonTableSync.register();

		MyMod.LOGGER.info("[economy] 物品经济价值系统已就绪：后端={}，货币={}，兜底估值={}",
				EconomyBridges.activeId(),
				EconomyConfig.get().currencyName,
				EconomyConfig.get().fallbackValueEnabled ? "开启" : "关闭");
	}
}
