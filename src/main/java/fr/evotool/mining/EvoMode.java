package fr.evotool.mining;

/**
 * Modes de minage sélectionnables avec la touche Y.
 *
 * @param size          taille du carré (1, 3 ou 5)
 * @param depth         profondeur (1 = plan, 3 ou 5 = cube)
 * @param requiredLevel niveau requis pour débloquer le mode
 */
public enum EvoMode {
	SINGLE("single", 1, 1, 0),
	AREA_3X3("3x3", 3, 1, 20),
	CUBE_3X3X3("3x3x3", 3, 3, 30),
	AREA_5X5("5x5", 5, 1, 40),
	CUBE_5X5X5("5x5x5", 5, 5, 50);

	private final String id;
	private final int size;
	private final int depth;
	private final int requiredLevel;

	EvoMode(String id, int size, int depth, int requiredLevel) {
		this.id = id;
		this.size = size;
		this.depth = depth;
		this.requiredLevel = requiredLevel;
	}

	public String id() {
		return id;
	}

	public int size() {
		return size;
	}

	public int radius() {
		return (size - 1) / 2;
	}

	public int depth() {
		return depth;
	}

	public int requiredLevel() {
		return requiredLevel;
	}

	public boolean isUnlocked(int level) {
		return level >= requiredLevel;
	}

	public String translationKey() {
		return "mode.evotool." + id;
	}

	public static EvoMode byIndex(int index) {
		EvoMode[] values = values();
		return index >= 0 && index < values.length ? values[index] : SINGLE;
	}
}
