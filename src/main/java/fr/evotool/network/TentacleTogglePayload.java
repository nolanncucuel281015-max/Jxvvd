package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Touche J : allume ou éteint le mode déplacement des tentacules. */
public record TentacleTogglePayload() implements CustomPayload {
	public static final TentacleTogglePayload INSTANCE = new TentacleTogglePayload();
	public static final Id<TentacleTogglePayload> ID = new Id<>(EvoTool.id("tentacle_toggle"));
	public static final PacketCodec<ByteBuf, TentacleTogglePayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
