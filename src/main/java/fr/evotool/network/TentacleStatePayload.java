package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Dit aux clients qui voient ce joueur si ses tentacules sont en mode déplacement (pour les animer). */
public record TentacleStatePayload(int entityId, boolean moving) implements CustomPayload {
	public static final Id<TentacleStatePayload> ID = new Id<>(EvoTool.id("tentacle_state"));
	public static final PacketCodec<RegistryByteBuf, TentacleStatePayload> CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, TentacleStatePayload::entityId,
			PacketCodecs.BOOLEAN, TentacleStatePayload::moving,
			TentacleStatePayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
