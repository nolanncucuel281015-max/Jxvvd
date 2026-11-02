package fr.evotool.element;

import java.util.ArrayList;
import java.util.List;

/**
 * Sorts offensifs du grimoire élémentaire : chaque élément en apprend deux, un trait (projectile qui file droit
 * devant) et une explosion (zone là où l'on vise). Les deux appliquent l'effet de l'élément à ce qu'ils touchent.
 */
public enum Spell {
	FROST_LANCE(Element.ICE, true, 8.0F, 16, 1.8),
	BLIZZARD(Element.ICE, false, 9.0F, 70, 4.5),
	FIREBALL(Element.FIRE, true, 9.0F, 16, 1.5),
	INFERNO(Element.FIRE, false, 11.0F, 70, 4.0),
	THUNDERBOLT(Element.LIGHTNING, true, 10.0F, 18, 2.6),
	STORM(Element.LIGHTNING, false, 12.0F, 80, 3.5),
	WATER_JET(Element.WATER, true, 7.0F, 12, 1.8),
	TIDAL_WAVE(Element.WATER, false, 8.0F, 60, 5.0),
	WIND_BLADE(Element.WIND, true, 7.0F, 10, 2.2),
	TORNADO(Element.WIND, false, 8.0F, 60, 4.5),
	STONE_SPIKE(Element.EARTH, true, 12.0F, 20, 1.3),
	EARTHQUAKE(Element.EARTH, false, 11.0F, 80, 5.0),
	SHADOW_BOLT(Element.SHADOW, true, 9.0F, 16, 1.8),
	DARK_NOVA(Element.SHADOW, false, 10.0F, 70, 4.0),
	HOLY_RAY(Element.LIGHT, true, 9.0F, 14, 3.0),
	JUDGEMENT(Element.LIGHT, false, 11.0F, 70, 4.0),
	ACID_SPIT(Element.POISON, true, 7.0F, 12, 1.6),
	TOXIC_CLOUD(Element.POISON, false, 6.0F, 70, 5.0);

	private final Element element;
	private final boolean bolt;
	private final float damage;
	private final int cooldown;
	/** Trait : vitesse (blocs par tick). Explosion : rayon (blocs). */
	private final double power;

	Spell(Element element, boolean bolt, float damage, int cooldown, double power) {
		this.element = element;
		this.bolt = bolt;
		this.damage = damage;
		this.cooldown = cooldown;
		this.power = power;
	}

	public Element element() {
		return element;
	}

	/** Vrai pour un trait (projectile), faux pour une explosion de zone. */
	public boolean bolt() {
		return bolt;
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

	public String id() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	public String translationKey() {
		return "spell.evotool." + id();
	}

	/** Les deux sorts d'un élément (le trait, puis l'explosion). */
	public static List<Spell> of(Element element) {
		List<Spell> spells = new ArrayList<>(2);
		for (Spell spell : values()) {
			if (spell.element == element) {
				spells.add(spell);
			}
		}
		return spells;
	}
}
