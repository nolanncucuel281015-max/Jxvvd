package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Notre joueur accroche (ou lâche) sa toile de Spider-Man : le serveur la montre aux autres. */
public record WebPayload(boolean attached, double x, double y, double z) implements CustomPayload {
	public static final Id<WebPayload> ID = new Id<>(EvoTool.id("web"));
	public static final PacketCodec<ByteBuf, WebPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, WebPayload::attached,
			PacketCodecs.DOUBLE, WebPayload::x,
			PacketCodecs.DOUBLE, WebPayload::y,
			PacketCodecs.DOUBLE, WebPayload::z,
			WebPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
