package com.leafyezi233.minecrafttofish.economy.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.economy.bridge.EconomyBridgeRegistry;
import com.leafyezi233.minecrafttofish.economy.bridge.EconomyBridges;
import com.leafyezi233.minecrafttofish.economy.config.EconomyConfig;
import com.leafyezi233.minecrafttofish.economy.net.ValueTableSync;
import com.leafyezi233.minecrafttofish.economy.value.ItemValueRegistry;
import com.leafyezi233.minecrafttofish.economy.value.ValueResult;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgument;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * {@code /fishvalue}（别名 {@code /fv}）命令树。
 *
 * <h2>权限分级</h2>
 * <ul>
 *   <li>等级 0（所有玩家）：{@code query}、{@code balance}、{@code sell}</li>
 *   <li>等级 2（OP）：{@code list}、{@code set}、{@code reload}、{@code give}、{@code clear}</li>
 * </ul>
 *
 * <p>所有反馈文本走 lang 文件（{@code commands.minecraft_to_fish.fishvalue.*}），
 * 便于翻译与统一维护。
 */
public final class FishValueCommand {

	/** 命令字面量 */
	private static final String ROOT = "fishvalue";

	/** 每页显示条数（list 分页） */
	private static final int PAGE_SIZE = 10;

	private FishValueCommand() {
	}

	/** 注册命令（CommandRegistrationCallback 里调用） */
	public static void register() {
		CommandRegistrationCallback.EVENT.register(FishValueCommand::build);
	}

	private static void build(CommandDispatcher<ServerCommandSource> dispatcher,
			CommandRegistryAccess registryAccess,
			CommandManager.RegistrationEnvironment environment) {

		var root = CommandManager.literal(ROOT)
				.requires(source -> true);

		// ---------------- /fishvalue query <item> ----------------
		root.then(CommandManager.literal("query")
				.then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
						.executes(ctx -> query(ctx, ItemStackArgumentType.getItemStackArgument(ctx, "item")))));

		// ---------------- /fishvalue list [page] ----------------
		root.then(CommandManager.literal("list")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(ctx -> list(ctx, 1))
				.then(CommandManager.argument("page", IntegerArgumentType.integer(1))
						.executes(ctx -> list(ctx, IntegerArgumentType.getInteger(ctx, "page")))));

		// ---------------- /fishvalue set <item> <value> ----------------
		root.then(CommandManager.literal("set")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
						.then(CommandManager.argument("value", LongArgumentType.longArg(0L))
								.executes(ctx -> set(ctx,
										ItemStackArgumentType.getItemStackArgument(ctx, "item"),
										LongArgumentType.getLong(ctx, "value"))))));

		// ---------------- /fishvalue reload ----------------
		root.then(CommandManager.literal("reload")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(FishValueCommand::reload));

		// ---------------- /fishvalue balance [player] ----------------
		root.then(CommandManager.literal("balance")
				.executes(ctx -> balance(ctx, null))
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.requires(source -> source.hasPermissionLevel(2))
						.executes(ctx -> balance(ctx, EntityArgumentType.getPlayer(ctx, "player")))));

		// ---------------- /fishvalue give <player> <amount> ----------------
		root.then(CommandManager.literal("give")
				.requires(source -> source.hasPermissionLevel(2))
				.then(CommandManager.argument("player", EntityArgumentType.player())
						.then(CommandManager.argument("amount", LongArgumentType.longArg(1L))
								.executes(ctx -> give(ctx,
										EntityArgumentType.getPlayer(ctx, "player"),
										LongArgumentType.getLong(ctx, "amount"))))));

		// ---------------- /fishvalue sell ----------------
		root.then(CommandManager.literal("sell")
				.executes(FishValueCommand::sell));

		// ---------------- /fishvalue bridge ----------------
		root.then(CommandManager.literal("bridge")
				.requires(source -> source.hasPermissionLevel(2))
				.executes(FishValueCommand::bridgeInfo)
				.then(CommandManager.argument("id", StringArgumentType.word())
						.suggests(bridgeSuggestions())
						.executes(ctx -> setBridge(ctx, StringArgumentType.getString(ctx, "id")))));

		dispatcher.register(root);

		// 别名 /fv
		dispatcher.register(CommandManager.literal("fv")
				.requires(source -> true)
				.redirect(dispatcher.getRoot().getChild(ROOT)));
	}

	// ---------------------------------------------------------------- 子命令实现

	/** 查询单个物品价值 */
	private static int query(CommandContext<ServerCommandSource> ctx, ItemStackArgument argument) {
		ItemStack stack = new ItemStack(argument.getItem());
		ValueResult result = ItemValueRegistry.resolve(stack);
		Identifier itemId = Registries.ITEM.getId(argument.getItem());

		if (!result.hasValue()) {
			ctx.getSource().sendFeedback(() -> Text.translatable(
					"commands.minecraft_to_fish.fishvalue.query.none", itemId.toString()), false);
			return 0;
		}

		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.query.result",
				itemId.toString(),
				EconomyBridges.format(result.value()),
				result.sourceName()), false);
		return 1;
	}

	/** 分页列出已登记价值 */
	private static int list(CommandContext<ServerCommandSource> ctx, int page) {
		Map<Identifier, Long> items = ItemValueRegistry.itemValues();
		Map<Identifier, Long> tags = ItemValueRegistry.tagValues();

		List<String> lines = new ArrayList<>(items.size() + tags.size());
		items.forEach((id, value) -> lines.add(id + " = " + value));
		tags.forEach((id, value) -> lines.add("#" + id + " = " + value));
		lines.sort(String::compareTo);

		if (lines.isEmpty()) {
			ctx.getSource().sendFeedback(() -> Text.translatable(
					"commands.minecraft_to_fish.fishvalue.list.empty"), false);
			return 0;
		}

		int totalPages = (lines.size() + PAGE_SIZE - 1) / PAGE_SIZE;
		int safePage = Math.max(1, Math.min(page, totalPages));
		int from = (safePage - 1) * PAGE_SIZE;
		int to = Math.min(from + PAGE_SIZE, lines.size());

		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.list.header",
				safePage, totalPages, lines.size()), false);

		for (int i = from; i < to; i++) {
			String line = lines.get(i);
			ctx.getSource().sendFeedback(() -> Text.literal("  " + line), false);
		}
		return to - from;
	}

	/** 运行时覆盖价值（内存生效，重启失效） */
	private static int set(CommandContext<ServerCommandSource> ctx, ItemStackArgument argument, long value) {
		Item item = argument.getItem();
		Identifier itemId = Registries.ITEM.getId(item);

		// 覆盖前先取"当前生效值"（可能来自数据包/标签/兜底），比只报告旧覆盖值更有参考意义
		ValueResult before = ItemValueRegistry.resolve(new ItemStack(item));
		ItemValueRegistry.setRuntimeValue(item, value);

		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.set.done",
				itemId.toString(),
				EconomyBridges.format(value),
				before.hasValue() ? EconomyBridges.format(before.value()) : "—"), true);

		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.set.hint"), false);

		// 改价后立刻把新价值表推给客户端，HUD 无需重连即可看到新价格
		ValueTableSync.broadcast(ctx.getSource().getServer());
		return 1;
	}

	/** 重载数据包价值表 */
	private static int reload(CommandContext<ServerCommandSource> ctx) {
		try {
			ctx.getSource().getServer().reloadResources(ctx.getSource().getServer().getDataPackManager().getEnabledNames());
			ctx.getSource().sendFeedback(() -> Text.translatable(
					"commands.minecraft_to_fish.fishvalue.reload.done"), true);
			return 1;
		} catch (Exception e) {
			MyMod.LOGGER.error("[economy] 手动重载数据包失败：{}", e.toString());
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.reload.failed", e.toString()));
			return 0;
		}
	}

	/** 查询余额 */
	private static int balance(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target) {
		ServerPlayerEntity player = target;
		if (player == null) {
			try {
				player = ctx.getSource().getPlayerOrThrow();
			} catch (Exception e) {
				ctx.getSource().sendError(Text.translatable(
						"commands.minecraft_to_fish.fishvalue.error.player_only"));
				return 0;
			}
		}

		ServerPlayerEntity finalPlayer = player;
		long amount = EconomyBridges.getBalance(player);
		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.balance.result",
				finalPlayer.getGameProfile().getName(),
				EconomyBridges.format(amount)), false);
		return 1;
	}

	/** 给玩家加钱（管理员） */
	private static int give(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity target, long amount) {
		boolean ok = EconomyBridges.deposit(target, amount, MyMod.MOD_ID + ":command_give");
		if (!ok) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.give.failed", target.getGameProfile().getName()));
			return 0;
		}
		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.give.done",
				EconomyBridges.format(amount),
				target.getGameProfile().getName(),
				EconomyBridges.format(EconomyBridges.getBalance(target))), true);
		return 1;
	}

	/** 卖掉主手物品（单件） */
	private static int sell(CommandContext<ServerCommandSource> ctx) {
		if (!EconomyConfig.get().sellCommandEnabled) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.sell.disabled"));
			return 0;
		}

		ServerPlayerEntity player;
		try {
			player = ctx.getSource().getPlayerOrThrow();
		} catch (Exception e) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.error.player_only"));
			return 0;
		}

		ItemStack stack = player.getMainHandStack();
		if (stack.isEmpty()) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.sell.empty_hand"));
			return 0;
		}

		ValueResult result = ItemValueRegistry.resolve(stack);
		if (!result.hasValue()) {
			Identifier itemId = Registries.ITEM.getId(stack.getItem());
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.sell.no_value", itemId.toString()));
			return 0;
		}

		// 只卖主手这一格里的 1 个（本次范围：单件）
		ItemStack sold = stack.copyWithCount(1);
		long unitValue = result.value();

		if (!EconomyBridges.deposit(player, unitValue, MyMod.MOD_ID + ":sell")) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.sell.failed"));
			return 0;
		}

		// 扣物品：加钱成功后才移除，避免"钱货两空"
		stack.decrement(1);
		if (stack.isEmpty()) {
			player.getInventory().setStack(player.getInventory().selectedSlot, ItemStack.EMPTY);
		}

		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.sell.done",
				sold.getName(),
				EconomyBridges.format(unitValue),
				EconomyBridges.format(EconomyBridges.getBalance(player))), false);
		return 1;
	}

	/** 查看当前经济后端 */
	private static int bridgeInfo(CommandContext<ServerCommandSource> ctx) {
		String activeId = EconomyBridges.activeId();
		String available = String.join(", ", EconomyBridgeRegistry.ids());
		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.bridge.info", activeId, available), false);
		return 1;
	}

	/** 切换首选经济后端（写入配置并立即生效） */
	private static int setBridge(CommandContext<ServerCommandSource> ctx, String id) {
		if (EconomyBridgeRegistry.get(id) == null) {
			ctx.getSource().sendError(Text.translatable(
					"commands.minecraft_to_fish.fishvalue.bridge.unknown", id));
			return 0;
		}
		EconomyConfig.get().preferredBridge = id;
		EconomyConfig.save();
		EconomyBridges.select();
		ctx.getSource().sendFeedback(() -> Text.translatable(
				"commands.minecraft_to_fish.fishvalue.bridge.switched",
				EconomyBridges.activeId()), true);
		return 1;
	}

	/** 经济后端 id 补全 */
	private static SuggestionProvider<ServerCommandSource> bridgeSuggestions() {
		return (ctx, builder) -> {
			for (String id : EconomyBridgeRegistry.ids()) {
				if (id.toLowerCase().startsWith(builder.getRemainingLowerCase())) {
					builder.suggest(id);
				}
			}
			return builder.buildFuture();
		};
	}
}
