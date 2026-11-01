package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Envoyé par le client quand le joueur appuie sur la touche d'évolution (R par défaut). */
public record EvolvePayload() implements CustomPayload {
	public static final EvolvePayload INSTANCE = new EvolvePayload();
	public static final Id<EvolvePayload> ID = new Id<>(EvoTool.id("evolve"));
	public static final PacketCodec<ByteBuf, EvolvePayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
