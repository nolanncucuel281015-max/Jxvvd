package fr.evotool.plugin.block;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.NoteBlock;

/**
 * Les blocs du mod. Un plugin ne peut pas ajouter de blocs : chacun est un bloc musical dans un état précis
 * (instrument + note, alimenté), que le pack de ressources dessine avec le modèle du mod. Les blocs musicaux
 * normaux, eux, ne sont jamais alimentés (mise à jour des blocs musicaux coupée, voir {@link Blocks}).
 */
public enum CustomBlock {
	DEEPSLATE_EVOLIUM_ORE("deepslate_evolium_ore", Drop.ORE, 4, 5, 9),
	EVOLIUM_BLOCK("evolium_block", Drop.SELF, 3, 0, 0),
	RUBY_ORE("ruby_ore", Drop.ORE, 3, 3, 7),
	DEEPSLATE_RUBY_ORE("deepslate_ruby_ore", Drop.ORE, 3, 3, 7),
	RUBY_BLOCK("ruby_block", Drop.SELF, 3, 0, 0),
	SAPPHIRE_ORE("sapphire_ore", Drop.ORE, 3, 3, 7),
	DEEPSLATE_SAPPHIRE_ORE("deepslate_sapphire_ore", Drop.ORE, 3, 3, 7),
	SAPPHIRE_BLOCK("sapphire_block", Drop.SELF, 3, 0, 0),
	EVOLITE_ORE("evolite_ore", Drop.SELF, 4, 0, 0),
	EVOLITE_BLOCK("evolite_block", Drop.SELF, 4, 0, 0),
	ABYSSITE_ORE("abyssite_ore", Drop.ORE, 4, 6, 11),
	SANCTUARY_ALTAR("sanctuary_altar", Drop.SELF, 1, 0, 0),
	SHIELD_GENERATOR("shield_generator", Drop.MACHINE, 4, 0, 0),
	ELEMENTAL_COMBINER("elemental_combiner", Drop.MACHINE, 4, 0, 0),
	EVOLVING_STONE("evolving_stone", Drop.SELF, 1, 0, 0, 6),
	EVOLVING_DIRT("evolving_dirt", Drop.SELF, 0, 0, 0, 6),
	EVOLVING_GRASS("evolving_grass", Drop.EVOLVING_GRASS, 0, 0, 0, 6),
	EVOLVING_LEAVES("evolving_leaves", Drop.NONE_UNLESS_SHEARS, 0, 0, 0, 6),
	EVOLVING_CRYSTAL("evolving_crystal", Drop.SELF, 1, 0, 0, 6),
	EVOLVING_ORE("evolving_ore", Drop.EVOLVING_ORE, 3, 2, 6, 6),
	/** Bûche : 6 évolutions x 3 orientations (y, z, x). */
	EVOLVING_LOG("evolving_log", Drop.SELF, 0, 0, 0, 18);

	/** Ce que le bloc lâche quand on le casse. */
	public enum Drop {
		/** Lui-même. */
		SELF,
		/** La gemme (Fortune) ; au toucher de soie, le bloc. */
		ORE,
		/** Lui-même et ce qu'il contient (générateur, combineur). */
		MACHINE,
		/** Terre évolutive (bloc d'herbe sans toucher de soie). */
		EVOLVING_GRASS,
		/** De l'évolium, d'autant plus qu'il a évolué. */
		EVOLVING_ORE,
		/** Rien (sauf cisailles ou toucher de soie). */
		NONE_UNLESS_SHEARS
	}

	public static final int MAX_EVOLUTION = 5;
	/** Noms des instruments dans les états de bloc, dans l'ordre de {@link Instrument}. */
	public static final String[] INSTRUMENTS = {"harp", "basedrum", "snare", "hat", "bass", "flute", "bell", "guitar", "chime",
			"xylophone", "iron_xylophone", "cow_bell", "didgeridoo", "bit", "banjo", "pling", "zombie", "skeleton", "creeper",
			"dragon", "wither_skeleton", "piglin", "custom_head"};

	private static final Map<Integer, CustomBlock> BY_INDEX = new HashMap<>();
	private static final Map<String, CustomBlock> BY_ID = new HashMap<>();

	static {
		int index = 0;
		for (CustomBlock block : values()) {
			block.base = index;
			for (int v = 0; v < block.variants; v++) {
				BY_INDEX.put(index++, block);
			}
			BY_ID.put(block.id, block);
		}
	}

	private final String id;
	private final Drop drop;
	/** Pioche minimale pour qu'il lâche quelque chose : 0 rien, 1 bois, 2 pierre, 3 fer, 4 diamant. */
	private final int tier;
	private final int xpMin;
	private final int xpMax;
	private final int variants;
	private int base;

	CustomBlock(String id, Drop drop, int tier, int xpMin, int xpMax) {
		this(id, drop, tier, xpMin, xpMax, 1);
	}

	CustomBlock(String id, Drop drop, int tier, int xpMin, int xpMax, int variants) {
		this.id = id;
		this.drop = drop;
		this.tier = tier;
		this.xpMin = xpMin;
		this.xpMax = xpMax;
		this.variants = variants;
	}

	public String id() {
		return id;
	}

	public Drop drop() {
		return drop;
	}

	public int tier() {
		return tier;
	}

	public int xpMin() {
		return xpMin;
	}

	public int xpMax() {
		return xpMax;
	}

	public int variants() {
		return variants;
	}

	public boolean evolving() {
		return variants >= 6;
	}

	public static CustomBlock byId(String id) {
		return BY_ID.get(id);
	}

	/** Numéro global de l'état (0, 1, 2…) : instrument = numéro / 25, note = numéro % 25. */
	public int index(int variant) {
		return base + Math.max(0, Math.min(variants - 1, variant));
	}

	/** L'état de bloc musical de cette variante (évolution, et pour la bûche, orientation). */
	public BlockData data(int variant) {
		int index = index(variant);
		NoteBlock note = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
		note.setInstrument(Instrument.values()[index / 25]);
		note.setNote(new Note(index % 25));
		note.setPowered(true);
		return note;
	}

	public BlockData data() {
		return data(0);
	}

	/** Variante d'un bloc évolutif : évolution 0 à 5 (et orientation 0 y, 1 z, 2 x pour la bûche). */
	public int variant(int evolution, int axis) {
		if (this == EVOLVING_LOG) {
			return Math.max(0, Math.min(MAX_EVOLUTION, evolution)) * 3 + axis;
		}
		return evolving() ? Math.max(0, Math.min(MAX_EVOLUTION, evolution)) : 0;
	}

	public static int evolutionOf(CustomBlock block, int variant) {
		if (block == EVOLVING_LOG) {
			return variant / 3;
		}
		return block.evolving() ? variant : 0;
	}

	/** Le bloc du mod dessiné par cet état (null : bloc musical normal). */
	public static CustomBlock of(BlockData data) {
		if (!(data instanceof NoteBlock note) || !note.isPowered()) {
			return null;
		}
		return BY_INDEX.get(note.getInstrument().ordinal() * 25 + note.getNote().getId());
	}

	public static CustomBlock of(Block block) {
		return block.getType() == Material.NOTE_BLOCK ? of(block.getBlockData()) : null;
	}

	/** Variante (évolution, orientation) de l'état. */
	public static int variantOf(BlockData data) {
		CustomBlock block = of(data);
		if (block == null) {
			return 0;
		}
		NoteBlock note = (NoteBlock) data;
		return note.getInstrument().ordinal() * 25 + note.getNote().getId() - block.base;
	}

	/** Le modèle du mod pour une variante, et sa rotation (bûche couchée) : pour le pack de ressources. */
	public String model(int variant) {
		if (this == EVOLVING_LOG) {
			return "evotool:block/evolving/evolving_log_" + variant / 3;
		}
		if (evolving()) {
			return "evotool:block/evolving/" + id + "_" + variant;
		}
		return "evotool:block/" + id;
	}

	/** Rotations x/y du modèle (bûche : orientation z = x90, orientation x = x90 y90). */
	public int[] rotation(int variant) {
		if (this == EVOLVING_LOG) {
			int axis = variant % 3;
			return axis == 1 ? new int[] {90, 0} : axis == 2 ? new int[] {90, 90} : new int[] {0, 0};
		}
		return new int[] {0, 0};
	}

	/** L'état écrit en texte, pour le pack de données : {"Name":"minecraft:note_block","Properties":{…}}. */
	public Map<String, String> properties(int variant) {
		int index = index(variant);
		Map<String, String> properties = new HashMap<>();
		properties.put("instrument", INSTRUMENTS[index / 25]);
		properties.put("note", Integer.toString(index % 25));
		properties.put("powered", "true");
		return properties;
	}

	public String translationKey() {
		return "block.evotool." + id;
	}

	@Override
	public String toString() {
		return name().toLowerCase(Locale.ROOT);
	}
}
