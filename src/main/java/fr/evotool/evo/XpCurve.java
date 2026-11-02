package fr.evotool.evo;

/** Courbes d'XP : le minage rapporte beaucoup de petits gains, le combat moins souvent. */
public enum XpCurve {
	/** ~1 500 XP pour le niveau 20 (~10 min), ~4,4 millions pour le niveau 100. */
	MINING(10, 3, 0.0006),
	/** ~400 XP pour le niveau 20, ~1,1 million pour le niveau 100. */
	COMBAT(3, 0.75, 0.00015),
	/**
	 * Armure : 1 XP par point de dégât encaissé (20 max par coup). ~2 000 XP pour le niveau 50,
	 * ~22 000 pour le niveau 80, ~70 000 pour le niveau 100, soit environ 2 h 30 de coups en continu.
	 */
	ARMOR(2, 0.2, 0.0000095);

	private final double base;
	private final double linear;
	private final double steep;

	XpCurve(double base, double linear, double steep) {
		this.base = base;
		this.linear = linear;
		this.steep = steep;
	}

	/**
	 * XP pour le niveau suivant d'un objet dont le niveau maximum est {@code maxLevel} : l'armure abyssale
	 * (améliorée à l'abyssite) continue jusqu'à 150, chaque niveau après 100 coûtant de 1 à 2 fois le niveau 75.
	 */
	public int xpToNext(int level, int maxLevel) {
		if (level >= maxLevel) {
			return 0;
		}
		if (level >= EvoProgression.MAX_LEVEL) {
			return (int) (xpToNext(75) * (1.0 + (level - EvoProgression.MAX_LEVEL) / 50.0));
		}
		return xpToNext(level);
	}

	public int xpToNext(int level) {
		if (level >= EvoProgression.MAX_LEVEL) {
			return 0;
		}
		return (int) (base + linear * level + steep * Math.pow(level, 4.3));
	}
}
