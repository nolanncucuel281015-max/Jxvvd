package fr.evotool.component;

import fr.evotool.EvoTool;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Unit;

public final class ModComponents {
	public static final ComponentType<EvoData> EVO_DATA = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("evo_data"),
			ComponentType.<EvoData>builder().codec(EvoData.CODEC).packetCodec(EvoData.PACKET_CODEC).build()
	);

	/** Présent sur un objet évolutif amélioré à l'enclume avec un lingot d'évolite. */
	public static final ComponentType<Unit> UPGRADED = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("evolite_upgraded"),
			ComponentType.<Unit>builder().codec(Unit.CODEC).packetCodec(PacketCodec.unit(Unit.INSTANCE)).build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
