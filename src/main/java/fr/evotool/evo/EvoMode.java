package fr.evotool.evo;

/**
 * Modes de zone sélectionnables avec la touche Y. Le niveau requis dépend de l'outil
 * (voir {@link EvoProfile#modes()}).
 *
 * @param size  taille du carré
 * @param depth profondeur (1 = plan, sinon cube)
 */
public enum EvoMode {
	SINGLE("single", 1, 1),
	AREA_3X3("3x3", 3, 1),
	CUBE_3X3X3("3x3x3", 3, 3),
	AREA_5X5("5x5", 5, 1),
	CUBE_5X5X5("5x5x5", 5, 5),
	AREA_7X7("7x7", 7, 1),
	AREA_9X9("9x9", 9, 1);

	private final String id;
	private final int size;
	private final int depth;

	EvoMode(String id, int size, int depth) {
		this.id = id;
		this.size = size;
		this.depth = depth;
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

	public String translationKey() {
		return "mode.evotool." + id;
	}

	public static EvoMode byIndex(int index) {
		EvoMode[] values = values();
		return index >= 0 && index < values.length ? values[index] : SINGLE;
	}
}
