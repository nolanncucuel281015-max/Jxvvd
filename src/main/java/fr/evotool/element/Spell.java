package fr.evotool.element;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorts du grimoire élémentaire : chaque élément en apprend quatre. Un trait (projectile qui file droit devant),
 * une explosion (zone là où l'on vise), un souffle (cône devant soi) et un déplacement (vol de feu, saut éclair,
 * bond du vent…). Les attaques appliquent l'effet de l'élément à ce qu'elles touchent.
 */
public enum Spell {
	FROST_LANCE(Element.ICE, Kind.BOLT, 8.0F, 16, 1.8),
	BLIZZARD(Element.ICE, Kind.BURST, 9.0F, 70, 4.5),
	FROST_BREATH(Element.ICE, Kind.BREATH, 6.0F, 40, 8.0),
	FROST_DASH(Element.ICE, Kind.MOVE, 6.0F, 50, 8),
	FIREBALL(Element.FIRE, Kind.BOLT, 9.0F, 16, 1.5),
	INFERNO(Element.FIRE, Kind.BURST, 11.0F, 70, 4.0),
	DRAGON_BREATH(Element.FIRE, Kind.BREATH, 7.0F, 40, 8.0),
	FIRE_FLIGHT(Element.FIRE, Kind.MOVE, 0.0F, 120, 60),
	THUNDERBOLT(Element.LIGHTNING, Kind.BOLT, 10.0F, 18, 2.6),
	STORM(Element.LIGHTNING, Kind.BURST, 12.0F, 80, 3.5),
	ARC_LIGHTNING(Element.LIGHTNING, Kind.BREATH, 7.0F, 40, 9.0),
	LIGHTNING_BLINK(Element.LIGHTNING, Kind.MOVE, 4.0F, 40, 2),
	WATER_JET(Element.WATER, Kind.BOLT, 7.0F, 12, 1.8),
	TIDAL_WAVE(Element.WATER, Kind.BURST, 8.0F, 60, 5.0),
	TORRENT(Element.WATER, Kind.BREATH, 6.0F, 35, 8.0),
	TIDE_RIDE(Element.WATER, Kind.MOVE, 4.0F, 80, 30),
	WIND_BLADE(Element.WIND, Kind.BOLT, 7.0F, 10, 2.2),
	TORNADO(Element.WIND, Kind.BURST, 8.0F, 60, 4.5),
	GUST(Element.WIND, Kind.BREATH, 5.0F, 30, 9.0),
	WIND_LEAP(Element.WIND, Kind.MOVE, 0.0F, 40, 40),
	STONE_SPIKE(Element.EARTH, Kind.BOLT, 12.0F, 20, 1.3),
	EARTHQUAKE(Element.EARTH, Kind.BURST, 11.0F, 80, 5.0),
	ROCKSLIDE(Element.EARTH, Kind.BREATH, 8.0F, 45, 7.0),
	EARTH_SLAM(Element.EARTH, Kind.MOVE, 10.0F, 90, 60),
	SHADOW_BOLT(Element.SHADOW, Kind.BOLT, 9.0F, 16, 1.8),
	DARK_NOVA(Element.SHADOW, Kind.BURST, 10.0F, 70, 4.0),
	SHADOW_VEIL(Element.SHADOW, Kind.BREATH, 6.0F, 40, 8.0),
	SHADOW_STEP(Element.SHADOW, Kind.MOVE, 6.0F, 60, 2),
	HOLY_RAY(Element.LIGHT, Kind.BOLT, 9.0F, 14, 3.0),
	JUDGEMENT(Element.LIGHT, Kind.BURST, 11.0F, 70, 4.0),
	BLINDING_FLASH(Element.LIGHT, Kind.BREATH, 6.0F, 40, 8.0),
	LIGHT_WINGS(Element.LIGHT, Kind.MOVE, 0.0F, 200, 200),
	ACID_SPIT(Element.POISON, Kind.BOLT, 7.0F, 12, 1.6),
	TOXIC_CLOUD(Element.POISON, Kind.BURST, 6.0F, 70, 5.0),
	PUTRID_BREATH(Element.POISON, Kind.BREATH, 5.0F, 35, 8.0),
	TOXIC_MIST(Element.POISON, Kind.MOVE, 3.0F, 160, 100);

	/** Trait, explosion, souffle ou déplacement. */
	public enum Kind {
		BOLT, BURST, BREATH, MOVE;

		public String translationKey() {
			return "tooltip.evotool.spell." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final Element element;
	private final Kind kind;
	private final float damage;
	private final int cooldown;
	/** Trait : vitesse (blocs par tick). Explosion : rayon. Souffle : portée. Déplacement : durée (ticks). */
	private final double power;

	Spell(Element element, Kind kind, float damage, int cooldown, double power) {
		this.element = element;
		this.kind = kind;
		this.damage = damage;
		this.cooldown = cooldown;
		this.power = power;
	}

	public Element element() {
		return element;
	}

	public Kind kind() {
		return kind;
	}

	public float damage() {
		return damage;
	}

	public int cooldown() {
		return cooldown;
	}

	public double speed() {
		return power;
	}

	public double radius() {
		return power;
	}

	public double range() {
		return power;
	}

	/** Durée d'un déplacement (ticks). */
	public int duration() {
		return (int) power;
	}

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public String translationKey() {
		return "spell.evotool." + id();
	}

	/** Les quatre sorts d'un élément (trait, explosion, souffle, déplacement). */
	public static List<Spell> of(Element element) {
		List<Spell> spells = new ArrayList<>(4);
		for (Spell spell : values()) {
			if (spell.element == element) {
				spells.add(spell);
			}
		}
		return spells;
	}
}
