package fr.evotool.evo;

import java.util.Locale;

/** Pouvoirs spéciaux débloqués en montant de niveau. Certains peuvent être coupés dans le menu (touche Y). */
public enum Power {
	AUTOSMELT(true),
	LIFESTEAL(false),
	SHOCKWAVE(true),
	WIDE_SHOCKWAVE(false),
	LIGHTNING(true),
	EXECUTION(false),
	INSTAMINE(false),
	TREE_FELLING(true),
	FELL_LEAVES(false),
	AUTO_REPLANT(true),
	INSTANT_GROWTH(false);

	private final boolean toggleable;

	Power(boolean toggleable) {
		this.toggleable = toggleable;
	}

	public boolean toggleable() {
		return toggleable;
	}

	public int bit() {
		return 1 << ordinal();
	}

	public String translationKey() {
		return "power.evotool." + name().toLowerCase(Locale.ROOT);
	}
}
