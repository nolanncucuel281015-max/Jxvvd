package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Envoyé par le client depuis le menu de la masse (touche Y).
 *
 * @param shockwave 1 pour activer l'onde de choc, 0 pour la couper
 * @param lightning 1 pour activer la foudre, 0 pour la couper
 */
public record MaceSettingsPayload(int shockwave, int lightning) implements CustomPayload {
	public static final Id<MaceSettingsPayload> ID = new Id<>(EvoTool.id("mace_settings"));
	public static final PacketCodec<RegistryByteBuf, MaceSettingsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, MaceSettingsPayload::shockwave,
			PacketCodecs.VAR_INT, MaceSettingsPayload::lightning,
			MaceSettingsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
