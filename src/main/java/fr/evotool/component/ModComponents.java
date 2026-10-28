package fr.evotool.component;

import fr.evotool.EvoTool;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModComponents {
	public static final ComponentType<EvoData> EVO_DATA = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("evo_data"),
			ComponentType.<EvoData>builder().codec(EvoData.CODEC).packetCodec(EvoData.PACKET_CODEC).build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
