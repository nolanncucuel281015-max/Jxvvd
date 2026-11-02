package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Double saut des bottes de vent : le client l'a fait, le serveur ajoute le souffle et oublie la chute. */
public record WindJumpPayload() implements CustomPayload {
	public static final WindJumpPayload INSTANCE = new WindJumpPayload();
	public static final Id<WindJumpPayload> ID = new Id<>(EvoTool.id("wind_jump"));
	public static final PacketCodec<ByteBuf, WindJumpPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
