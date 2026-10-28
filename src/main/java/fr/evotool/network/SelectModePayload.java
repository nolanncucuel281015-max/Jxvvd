package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Envoyé par le client depuis l'interface (touche Y).
 *
 * @param mode      index du mode choisi
 * @param autosmelt 1 pour activer l'auto-smelt, 0 pour le couper
 */
public record SelectModePayload(int mode, int autosmelt) implements CustomPayload {
	public static final Id<SelectModePayload> ID = new Id<>(EvoTool.id("select_mode"));
	public static final PacketCodec<RegistryByteBuf, SelectModePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, SelectModePayload::mode,
			PacketCodecs.VAR_INT, SelectModePayload::autosmelt,
			SelectModePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
