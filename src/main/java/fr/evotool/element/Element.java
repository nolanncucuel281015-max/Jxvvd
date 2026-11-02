package fr.evotool.element;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

/**
 * Les éléments du combineur élémentaire : chacun s'obtient avec un objet (catalyseur) et donne des pouvoirs à
 * l'armure, aux armes et aux outils (voir {@link ElementalPowers}).
 */
public enum Element implements StringIdentifiable {
	ICE("ice", "❄", Formatting.AQUA, 0x8FF4FF, List.of(Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE)),
	FIRE("fire", "✹", Formatting.GOLD, 0xFF7A1A, List.of(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE)),
	LIGHTNING("lightning", "⚡", Formatting.YELLOW, 0xFFE94A, List.of(Items.GUNPOWDER)),
	WATER("water", "≈", Formatting.BLUE, 0x2F7BFF, List.of(Items.WATER_BUCKET, Items.HEART_OF_THE_SEA)),
	WIND("wind", "➶", Formatting.WHITE, 0xE8F6FF, List.of(Items.WIND_CHARGE, Items.BREEZE_ROD)),
	EARTH("earth", "▲", Formatting.DARK_GREEN, 0x9A6A3A, List.of(Items.DIRT, Items.COARSE_DIRT, Items.MOSS_BLOCK)),
	SHADOW("shadow", "☾", Formatting.DARK_PURPLE, 0x8A4AD8, List.of(Items.INK_SAC, Items.ECHO_SHARD));

	public static final Codec<Element> CODEC = StringIdentifiable.createCodec(Element::values);
	public static final PacketCodec<ByteBuf, Element> PACKET_CODEC = PacketCodecs.indexed(i -> values()[i], Element::ordinal);

	private final String id;
	private final String symbol;
	private final Formatting color;
	private final int rgb;
	private final List<Item> catalysts;

	Element(String id, String symbol, Formatting color, int rgb, List<Item> catalysts) {
		this.id = id;
		this.symbol = symbol;
		this.color = color;
		this.rgb = rgb;
		this.catalysts = catalysts;
	}

	@Override
	public String asString() {
		return id;
	}

	public String id() {
		return id;
	}

	public String symbol() {
		return symbol;
	}

	public Formatting color() {
		return color;
	}

	public int rgb() {
		return rgb;
	}

	public List<Item> catalysts() {
		return catalysts;
	}

	public String translationKey() {
		return "element.evotool." + id;
	}

	/** Particule de l'élément (animation du combineur, traînées, coups). */
	public ParticleEffect particle() {
		return switch (this) {
			case ICE -> ParticleTypes.SNOWFLAKE;
			case FIRE -> ParticleTypes.FLAME;
			case LIGHTNING -> ParticleTypes.ELECTRIC_SPARK;
			case WATER -> ParticleTypes.SPLASH;
			case WIND -> ParticleTypes.CLOUD;
			case EARTH -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.ROOTED_DIRT.getDefaultState());
			case SHADOW -> ParticleTypes.SQUID_INK;
		};
	}

	/** L'élément que donne cet objet dans le combineur (null s'il n'en donne pas). */
	public static Element fromCatalyst(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		for (Element element : values()) {
			if (element.catalysts.contains(stack.getItem())) {
				return element;
			}
		}
		return null;
	}
}
