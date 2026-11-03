package fr.evotool.component;

import com.mojang.serialization.Codec;
import fr.evotool.EvoTool;
import fr.evotool.element.Element;
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

	/** Pièce d'armure améliorée à l'abyssite : elle monte jusqu'au niveau 150 et devient noire. */
	public static final ComponentType<Unit> ABYSSAL = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("abyssal"),
			ComponentType.<Unit>builder().codec(Unit.CODEC).packetCodec(PacketCodec.unit(Unit.INSTANCE)).build()
	);

	/** Évolium stocké dans un générateur de bouclier ramassé (il le garde quand on le casse puis le repose). */
	public static final ComponentType<Long> SHIELD_FUEL = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("shield_fuel"),
			ComponentType.<Long>builder().codec(Codec.LONG).packetCodec(PacketCodecs.VAR_LONG).build()
	);

	/** Rayon réglé sur un générateur de bouclier ramassé. */
	public static final ComponentType<Integer> SHIELD_RADIUS = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("shield_radius"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build()
	);

	/** Élément donné par le combineur élémentaire (glace, feu, électricité, eau, vent). */
	public static final ComponentType<Element> ELEMENT = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("element"),
			ComponentType.<Element>builder().codec(Element.CODEC).packetCodec(Element.PACKET_CODEC).build()
	);

	/** Contenu d'un grimoire élémentaire : ses éléments et le sort choisi. */
	public static final ComponentType<fr.evotool.element.Grimoire> GRIMOIRE = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("grimoire"),
			ComponentType.<fr.evotool.element.Grimoire>builder().codec(fr.evotool.element.Grimoire.CODEC)
					.packetCodec(fr.evotool.element.Grimoire.PACKET_CODEC).build()
	);

	/** Gant de Spider-Man éveillé par le rituel (la foudre tombe la première fois qu'on le tient). */
	public static final ComponentType<Unit> AWAKENED = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("awakened"),
			ComponentType.<Unit>builder().codec(Unit.CODEC).packetCodec(PacketCodec.unit(Unit.INSTANCE)).build()
	);

	/** Pouvoir de toile choisi sur le gant de Spider-Man (touche G), voir {@link fr.evotool.spider.WebPower}. */
	public static final ComponentType<Integer> WEB_POWER = Registry.register(
			Registries.DATA_COMPONENT_TYPE,
			EvoTool.id("web_power"),
			ComponentType.<Integer>builder().codec(Codec.INT).packetCodec(PacketCodecs.VAR_INT).build()
	);

	private ModComponents() {
	}

	public static void init() {
	}
}
