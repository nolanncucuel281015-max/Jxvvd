package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Touche G : le gant de Spider-Man passe au pouvoir de toile suivant. */
public record GlovePowerPayload() implements CustomPayload {
	public static final GlovePowerPayload INSTANCE = new GlovePowerPayload();
	public static final Id<GlovePowerPayload> ID = new Id<>(EvoTool.id("glove_power"));
	public static final PacketCodec<ByteBuf, GlovePowerPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
