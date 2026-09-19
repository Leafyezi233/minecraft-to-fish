package com.leafyezi233.minecrafttofish.wheel.net;

import java.util.ArrayList;
import java.util.List;

import com.leafyezi233.minecrafttofish.MyMod;
import com.leafyezi233.minecrafttofish.wheel.WheelSector;
import com.leafyezi233.minecrafttofish.wheel.WheelTable;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端：把转盘扇区表推给客户端。
 *
 * <h2>为什么必须同步</h2>
 * 扇区表来自服务端的 {@code config/minecraft_to_fish.json}，客户端本地没有。
 * 客户端要画盘面、要算指针落点，就必须知道服务端到底配了哪些扇区。
 * 不同步的话，服主一改配置，联机客户端画出来的就是另一个盘 ——
 * 而转盘的「所有人看到同一个结果」正是靠客户端能复现服务端的几何。
 *
 * <h2>为什么连颜色和角度一起传，而不是只传权重让客户端算</h2>
 * 角度确实可以由权重推出，但颜色是配置里的任意 ARGB 值。
 * 更关键的是：<b>让客户端「自己算一遍」就是把同一份几何逻辑实现两次</b>，
 * 两边一旦有差异（取整、回退规则），盘面就会错位。
 * 这里直接把「服务端校验后的最终扇区」整张发过去，
 * 客户端只做 {@link WheelTable#fromSectors} 重算角度，不重新解读配置。
 *
 * <h2>线格式</h2>
 * <pre>
 *   VarInt  sectors.size
 *     { VarInt multiplier, VarInt weight, Int color, String labelKey } * sectors.size
 * </pre>
 */
public record WheelTableSyncPayload(List<WheelSector> sectors) implements FabricPacket {

	/** 网络频道 id：{@code minecraft_to_fish:wheel_table} */
	public static final Identifier CHANNEL = new Identifier(MyMod.MOD_ID, "wheel_table");

	/** 包类型（客户端注册接收器、服务端发送都用它） */
	public static final PacketType<WheelTableSyncPayload> TYPE =
			PacketType.create(CHANNEL, WheelTableSyncPayload::read);

	/** 扇区数量上限；超过直接判定为畸形包（与 {@link WheelTable#MAX_SECTORS} 一致） */
	public static final int MAX_SECTORS = WheelTable.MAX_SECTORS;

	/** labelKey 最大长度，防止畸形包把内存撑爆 */
	public static final int MAX_LABEL_LENGTH = 64;

	public WheelTableSyncPayload {
		sectors = sectors == null ? List.of() : List.copyOf(sectors);
	}

	/** 由当前生效的扇区表构建快照（服务端调用） */
	public static WheelTableSyncPayload of(WheelTable table) {
		return table == null ? new WheelTableSyncPayload(List.of())
				: new WheelTableSyncPayload(table.sectors());
	}

	// ---------------------------------------------------------------- 编解码

	/** 从缓冲区读出一个包（由 {@link PacketType} 调用） */
	private static WheelTableSyncPayload read(PacketByteBuf buf) {
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_SECTORS) {
			throw new DecoderException("minecraft_to_fish: 扇区数量非法：" + size);
		}

		List<WheelSector> sectors = new ArrayList<>(Math.max(4, size));
		for (int i = 0; i < size; i++) {
			int multiplier = buf.readVarInt();
			int weight = buf.readVarInt();
			int color = buf.readInt();
			String labelKey = buf.readString(MAX_LABEL_LENGTH);

			// 构造 WheelSector 会校验并抛异常（负倍率 / 非正权重）。
			// 那是畸形包或版本不一致，包在 DecoderException 里让原版断开连接，
			// 比让一个坏扇区混进表里、之后在渲染线程上炸掉要好得多。
			try {
				sectors.add(new WheelSector(multiplier, weight, color, labelKey));
			} catch (IllegalArgumentException e) {
				throw new DecoderException("minecraft_to_fish: 扇区数据非法（第 " + i + " 项）：" + e.getMessage());
			}
		}
		return new WheelTableSyncPayload(sectors);
	}

	@Override
	public void write(PacketByteBuf buf) {
		buf.writeVarInt(sectors.size());
		for (WheelSector sector : sectors) {
			buf.writeVarInt(sector.multiplier());
			buf.writeVarInt(sector.weight());
			buf.writeInt(sector.color());
			buf.writeString(sector.labelKey(), MAX_LABEL_LENGTH);
		}
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}
}
