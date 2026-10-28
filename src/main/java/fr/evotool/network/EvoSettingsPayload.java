package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Envoyé par le client depuis le menu (touche Y) pour l'outil évolutif tenu en main.
 *
 * @param mode  index du mode de zone choisi
 * @param flags pouvoirs activés (un bit par {@link fr.evotool.evo.Power})
 */
public record EvoSettingsPayload(int mode, int flags) implements CustomPayload {
	public static final Id<EvoSettingsPayload> ID = new Id<>(EvoTool.id("settings"));
	public static final PacketCodec<RegistryByteBuf, EvoSettingsPayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, EvoSettingsPayload::mode,
			PacketCodecs.VAR_INT, EvoSettingsPayload::flags,
			EvoSettingsPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
