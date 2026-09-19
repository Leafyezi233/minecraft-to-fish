package com.leafyezi233.minecrafttofish.galton.net;

import java.util.ArrayList;
import java.util.List;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.galton.GaltonBoard;
import com.leafyezi233.minecrafttofish.galton.GaltonSlot;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端：把高尔顿板槽位表推给客户端。
 *
 * <h2>为什么必须同步</h2>
 * 与转盘同因：槽位表来自服务端的 {@code config/minecraft_to_fish.json}，客户端本地没有。
 * 客户端要画钉子行数、槽位宽度、每个槽位的颜色与倍率文字，就必须知道服务端配了什么。
 *
 * <h2>⚠️ 为什么连 {@code pegRightChance} 也一起传（转盘没有对应物）</h2>
 * 这是本包与 {@link com.leafyezi233.minecrafttofish.wheel.net.WheelTableSyncPayload}
 * 最本质的区别，值得写清楚：
 * <ul>
 *   <li><b>转盘</b>：概率由权重<b>直接指定</b>。权重在扇区里，扇区在包里，
 *       所以客户端重算角度就能得到和服务端完全一样的概率。</li>
 *   <li><b>高尔顿板</b>：概率<b>根本不在配置里</b>。它是
 *       {@code Binomial(rows, pegRightChance)} 这个数学后果 ——
 *       配置里只有倍率。若只传槽位不传 {@code p}，客户端就会拿<b>自己本地配置的 p</b>
 *       去算概率，联机时服主改过 p 就会得到另一条分布：
 *       服务端算出「中奖率 21.875%」，客户端 HUD 却显示别的数字。</li>
 * </ul>
 * 也就是说，转盘的包漏传字段只会导致画错，高尔顿板的包漏传 {@code p} 会让
 * <b>同一个表在两台机器上算出不同的概率</b>。所以 {@code p} 是这张表的必要组成部分，
 * 不是可选的装饰。
 *
 * <h2>为什么不传 rows</h2>
 * {@code rows = slots.size() - 1}，是<b>推导量</b>。传它只会多出一个可能对不上的字段
 * （包里的 rows 和槽位数不一致时该信谁？）。推导量一律不传，两边各自推。
 *
 * <h2>线格式</h2>
 * <pre>
 *   Float   pegRightChance
 *   VarInt  slots.size
 *     { VarInt multiplier, Int color, String labelKey } * slots.size
 * </pre>
 * 注意<b>没有 weight 字段</b>：{@link GaltonSlot} 不像 {@code WheelSector} 那样带权重，
 * 概率由几何决定（见 {@link GaltonSlot} 的类注释）。
 */
public record GaltonTableSyncPayload(List<GaltonSlot> slots, float pegRightChance)
		implements FabricPacket {

	/** 网络频道 id：{@code minecraft_to_fish:galton_table} */
	public static final Identifier CHANNEL = new Identifier(MyMod.MOD_ID, "galton_table");

	/** 包类型（客户端注册接收器、服务端发送都用它） */
	public static final PacketType<GaltonTableSyncPayload> TYPE =
			PacketType.create(CHANNEL, GaltonTableSyncPayload::read);

	/** 槽位数量上限；超过直接判定为畸形包（与 {@link GaltonBoard#MAX_SLOTS} 一致） */
	public static final int MAX_SLOTS = GaltonBoard.MAX_SLOTS;

	/** labelKey 最大长度，防止畸形包把内存撑爆 */
	public static final int MAX_LABEL_LENGTH = 64;

	public GaltonTableSyncPayload {
		slots = slots == null ? List.of() : List.copyOf(slots);
	}

	/** 由当前生效的槽位表构建快照（服务端调用） */
	public static GaltonTableSyncPayload of(GaltonBoard board) {
		return board == null ? new GaltonTableSyncPayload(List.of(), 0.5f)
				: new GaltonTableSyncPayload(board.slots(), board.pegRightChance());
	}

	// ---------------------------------------------------------------- 编解码

	/** 从缓冲区读出一个包（由 {@link PacketType} 调用） */
	private static GaltonTableSyncPayload read(PacketByteBuf buf) {
		float pegRightChance = buf.readFloat();

		int size = buf.readVarInt();
		if (size < 0 || size > MAX_SLOTS) {
			throw new DecoderException("minecraft_to_fish: 高尔顿板槽位数量非法：" + size);
		}

		List<GaltonSlot> slots = new ArrayList<>(Math.max(4, size));
		for (int i = 0; i < size; i++) {
			int multiplier = buf.readVarInt();
			int color = buf.readInt();
			String labelKey = buf.readString(MAX_LABEL_LENGTH);

			// 构造 GaltonSlot 会校验并抛异常（负倍率）。
			// 那是畸形包或版本不一致，包在 DecoderException 里让原版断开连接，
			// 比让一个坏槽位混进表里、之后在渲染线程上炸掉要好得多。
			// 与转盘一致的做法：坏数据在<b>边界</b>处拦掉，不让它进入系统内部。
			try {
				slots.add(new GaltonSlot(multiplier, color, labelKey));
			} catch (IllegalArgumentException e) {
				throw new DecoderException(
						"minecraft_to_fish: 高尔顿板槽位数据非法（第 " + i + " 项）：" + e.getMessage());
			}
		}
		return new GaltonTableSyncPayload(slots, pegRightChance);
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeFloat(pegRightChance);
		buf.writeVarInt(slots.size());
		for (GaltonSlot slot : slots) {
			buf.writeVarInt(slot.multiplier());
			buf.writeInt(slot.color());
			buf.writeString(slot.labelKey(), MAX_LABEL_LENGTH);
		}
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
