package fr.evotool.network;

import fr.evotool.EvoTool;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Envoyé au joueur au moment où son objet évolue : son écran fait jaillir la nouvelle forme, comme un totem. */
public record EvolvedPayload(ItemStack stack) implements CustomPayload {
	public static final Id<EvolvedPayload> ID = new Id<>(EvoTool.id("evolved"));
	public static final PacketCodec<RegistryByteBuf, EvolvedPayload> CODEC = PacketCodec.tuple(
			ItemStack.PACKET_CODEC, EvolvedPayload::stack,
			EvolvedPayload::new);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
