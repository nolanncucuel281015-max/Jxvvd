package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** La toile d'un joueur (id d'entité) : accrochée à ce point, ou lâchée. */
public record WebStatePayload(int entityId, boolean attached, double x, double y, double z) implements CustomPayload {
	public static final Id<WebStatePayload> ID = new Id<>(EvoTool.id("web_state"));
	public static final PacketCodec<ByteBuf, WebStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, WebStatePayload::entityId,
			PacketCodecs.BOOLEAN, WebStatePayload::attached,
			PacketCodecs.DOUBLE, WebStatePayload::x,
			PacketCodecs.DOUBLE, WebStatePayload::y,
			PacketCodecs.DOUBLE, WebStatePayload::z,
			WebStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
