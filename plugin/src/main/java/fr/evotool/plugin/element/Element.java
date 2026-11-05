package fr.evotool.plugin.element;

import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.inventory.ItemStack;

/**
 * Les éléments du combineur élémentaire : chacun s'obtient avec un objet (catalyseur) et donne des pouvoirs à
 * l'armure, aux armes et aux outils (voir {@link ElementalPowers}).
 */
public enum Element {
	ICE("ice", "❄", NamedTextColor.AQUA, 0x8FF4FF, List.of(Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE)),
	FIRE("fire", "✹", NamedTextColor.GOLD, 0xFF7A1A, List.of(Material.FLINT_AND_STEEL, Material.FIRE_CHARGE)),
	LIGHTNING("lightning", "⚡", NamedTextColor.YELLOW, 0xFFE94A, List.of(Material.GUNPOWDER)),
	WATER("water", "≈", NamedTextColor.BLUE, 0x2F7BFF, List.of(Material.WATER_BUCKET, Material.HEART_OF_THE_SEA)),
	WIND("wind", "➶", NamedTextColor.WHITE, 0xE8F6FF, List.of(Material.WIND_CHARGE, Material.BREEZE_ROD)),
	EARTH("earth", "▲", NamedTextColor.DARK_GREEN, 0x9A6A3A, List.of(Material.DIRT, Material.COARSE_DIRT, Material.MOSS_BLOCK)),
	SHADOW("shadow", "☾", NamedTextColor.DARK_PURPLE, 0x8A4AD8, List.of(Material.INK_SAC, Material.ECHO_SHARD)),
	LIGHT("light", "☀", NamedTextColor.YELLOW, 0xFFF3A0, List.of(Material.GLOWSTONE_DUST, Material.GLOWSTONE)),
	POISON("poison", "☠", NamedTextColor.GREEN, 0x6ACD3A, List.of(Material.SPIDER_EYE, Material.POISONOUS_POTATO, Material.PUFFERFISH));

	private final String id;
	private final String symbol;
	private final NamedTextColor color;
	private final int rgb;
	private final List<Material> catalysts;

	Element(String id, String symbol, NamedTextColor color, int rgb, List<Material> catalysts) {
		this.id = id;
		this.symbol = symbol;
		this.color = color;
		this.rgb = rgb;
		this.catalysts = catalysts;
	}

	public String id() {
		return id;
	}

	public String symbol() {
		return symbol;
	}

	public NamedTextColor color() {
		return color;
	}

	public int rgb() {
		return rgb;
	}

	public Color bukkitColor() {
		return Color.fromRGB(rgb);
	}

	public List<Material> catalysts() {
		return catalysts;
	}

	public String translationKey() {
		return "element.evotool." + id;
	}

	/** Particule de l'élément (animation du combineur, traînées, coups). */
	public Particle particle() {
		return switch (this) {
			case ICE -> Particle.SNOWFLAKE;
			case FIRE -> Particle.FLAME;
			case LIGHTNING -> Particle.ELECTRIC_SPARK;
			case WATER -> Particle.SPLASH;
			case WIND -> Particle.CLOUD;
			case EARTH -> Particle.BLOCK;
			case SHADOW -> Particle.SQUID_INK;
			case LIGHT -> Particle.END_ROD;
			case POISON -> Particle.ITEM_SLIME;
		};
	}

	/** Donnée de la particule (bloc de terre pour la terre), sinon null. */
	public Object particleData() {
		return this == EARTH ? Material.ROOTED_DIRT.createBlockData() : null;
	}

	public static Element byId(String id) {
		for (Element element : values()) {
			if (element.id.equals(id)) {
				return element;
			}
		}
		return null;
	}

	/** L'élément que donne cet objet dans le combineur (null s'il n'en donne pas). */
	public static Element fromCatalyst(ItemStack stack) {
		if (stack == null || stack.getType().isAir()) {
			return null;
		}
		for (Element element : values()) {
			if (element.catalysts.contains(stack.getType())) {
				return element;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
