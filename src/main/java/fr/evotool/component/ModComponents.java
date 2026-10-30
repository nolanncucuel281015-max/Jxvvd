package fr.evotool.component;

import com.mojang.serialization.Codec;
import fr.evotool.EvoTool;
import net.minecraft.component.ComponentType;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
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

	/** Charges d'une arme magique (elles se rechargent avec le temps). */
	public static final ComponentType<Integer> CHARGES = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("charges"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build()
	);

	/** Heure du monde (en ticks) à partir de laquelle la prochaine charge se recharge. */
	public static final ComponentType<Long> CHARGE_CLOCK = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("charge_clock"),
			ComponentType.<Long>builder().codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
