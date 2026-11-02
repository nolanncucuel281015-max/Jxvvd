package fr.evotool.network;

import fr.evotool.EvoTool;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/** Clic droit à main nue (tentacules) : attraper le monstre visé, ou projeter celui qu'on tient. */
public record TentacleGrabPayload() implements CustomPayload {
	public static final TentacleGrabPayload INSTANCE = new TentacleGrabPayload();
	public static final Id<TentacleGrabPayload> ID = new Id<>(EvoTool.id("tentacle_grab"));
	public static final PacketCodec<ByteBuf, TentacleGrabPayload> CODEC = PacketCodec.unit(INSTANCE);

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
