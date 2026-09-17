package com.leafyezi233.minecrafttofish.economy.net;

import java.util.LinkedHashMap;
import java.util.Map;

import com.leafyezi233.minecrafttofish.MyMod;

import io.netty.handler.codec.DecoderException;
import net.fabricmc.fabric.api.networking.v1.FabricPacket;
import net.fabricmc.fabric.api.networking.v1.PacketType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

/**
 * 服务端 → 客户端：把整张价值表推给客户端。
 *
 * <p>价值表原本只存在服务端（数据包 {@code fish_values/*.json}），
 * 联机时客户端拿不到，HUD 也就无从显示。本包把解析所需的最小信息一次性送过去：
 * <ul>
 *   <li>{@link #items()} —— 精确物品价值（已合并运行时覆盖，覆盖优先）</li>
 *   <li>{@link #tags()} —— 标签价值</li>
 *   <li>{@link #currencyName()} —— 服务端配置里的货币名，保证客户端显示的货币跟服务端一致</li>
 * </ul>
 *
 * <p>客户端只做只读镜像（见 {@code economy/client/ClientValueCache}），
 * <b>不会</b>用网络数据覆盖本地的服务端权威价值表。
 *
 * <h2>线格式</h2>
 * <pre>
 *   VarInt  items.size
 *     { Identifier itemId, VarLong value } * items.size
 *   VarInt  tags.size
 *     { Identifier tagId,  VarLong value } * tags.size
 *   String  currencyName
 * </pre>
 */
public record ValueTableSyncPayload(Map<Identifier, Long> items, Map<Identifier, Long> tags, String currencyName)
		implements FabricPacket {

	/** 网络频道 id：{@code minecraft_to_fish:value_table} */
	public static final Identifier CHANNEL = new Identifier(MyMod.MOD_ID, "value_table");

	/** 包类型（客户端注册接收器、服务端发送都用它） */
	public static final PacketType<ValueTableSyncPayload> TYPE = PacketType.create(CHANNEL, ValueTableSyncPayload::read);

	/** 货币名最大长度，防止畸形包把内存撑爆 */
	private static final int MAX_CURRENCY_LENGTH = 64;

	/** 单张表最大条目数；超过直接判定为畸形包 */
	private static final int MAX_ENTRIES = 32_768;

	public ValueTableSyncPayload {
		items = items == null ? Map.of() : Map.copyOf(items);
		tags = tags == null ? Map.of() : Map.copyOf(tags);
		currencyName = currencyName == null ? "" : currencyName;
	}

	// ---------------------------------------------------------------- 编解码

	/** 从缓冲区读出一个包（由 {@link PacketType} 调用） */
	private static ValueTableSyncPayload read(PacketByteBuf buf) {
		Map<Identifier, Long> items = readMap(buf);
		Map<Identifier, Long> tags = readMap(buf);
		String currencyName = buf.readString(MAX_CURRENCY_LENGTH);
		return new ValueTableSyncPayload(items, tags, currencyName);
	}

	private static Map<Identifier, Long> readMap(PacketByteBuf buf) {
		int size = buf.readVarInt();
		if (size < 0 || size > MAX_ENTRIES) {
			throw new DecoderException("minecraft_to_fish: 价值表条目数非法：" + size);
		}
		Map<Identifier, Long> map = new LinkedHashMap<>(Math.max(4, size));
		for (int i = 0; i < size; i++) {
			map.put(buf.readIdentifier(), buf.readVarLong());
		}
		return map;
	}

	private static void writeMap(PacketByteBuf buf, Map<Identifier, Long> map) {
		buf.writeVarInt(map.size());
		for (Map.Entry<Identifier, Long> entry : map.entrySet()) {
			buf.writeIdentifier(entry.getKey());
			buf.writeVarLong(entry.getValue());
		}
	}

	@Override
	public void write(PacketByteBuf buf) {
		writeMap(buf, items);
		writeMap(buf, tags);
		buf.writeString(currencyName, MAX_CURRENCY_LENGTH);
	}

	@Override
	public PacketType<?> getType() {
		return TYPE;
	}

	/** 条目总数，仅用于日志 */
	public int size() {
		return items.size() + tags.size();
	}
}
