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

	public int xpToNext(int level) {
		if (level >= EvoProgression.MAX_LEVEL) {
			return 0;
		}
		return (int) (base + linear * level + steep * Math.pow(level, 4.3));
	}
}
