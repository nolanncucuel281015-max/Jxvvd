package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Envoyé par le serveur au joueur qui plonge sous terre avec les bottes améliorées (ou qui en ressort) :
 * son client doit alors traverser les blocs comme le serveur.
 */
public record BurrowPayload(boolean active) implements CustomPayload {
	public static final Id<BurrowPayload> ID = new Id<>(EvoTool.id("burrow"));
	public static final PacketCodec<RegistryByteBuf, BurrowPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.BOOLEAN, BurrowPayload::active,
			BurrowPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
