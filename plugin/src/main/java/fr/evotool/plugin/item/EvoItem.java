package fr.evotool.plugin.item;

import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.evo.EvoKind;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;

/**
 * Les objets du mod. Chacun est un objet de Minecraft (sa « base ») qui porte le modèle 3D du mod (pack de
 * ressources, composant item_model) et son identifiant dans ses données (clé evotool:item).
 */
public enum EvoItem {
	// --- outils et armure évolutifs
	EVOLVING_PICKAXE(Material.NETHERITE_PICKAXE, ItemRarity.EPIC, EvoKind.PICKAXE),
	EVOLVING_SHOVEL(Material.NETHERITE_SHOVEL, ItemRarity.EPIC, EvoKind.SHOVEL),
	EVOLVING_AXE(Material.NETHERITE_AXE, ItemRarity.EPIC, EvoKind.AXE),
	EVOLVING_HOE(Material.NETHERITE_HOE, ItemRarity.EPIC, EvoKind.HOE),
	EVOLVING_SWORD(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.SWORD),
	EVOLVING_SPEAR(Material.NETHERITE_SPEAR, ItemRarity.EPIC, EvoKind.SPEAR),
	EVOLVING_BOW(Material.BOW, ItemRarity.EPIC, EvoKind.BOW),
	EVOLIUM_MACE(Material.MACE, ItemRarity.EPIC, EvoKind.MACE),
	EVOLVING_HELMET(Material.NETHERITE_HELMET, ItemRarity.EPIC, EvoKind.HELMET),
	EVOLVING_CHESTPLATE(Material.NETHERITE_CHESTPLATE, ItemRarity.EPIC, EvoKind.CHESTPLATE),
	EVOLVING_LEGGINGS(Material.NETHERITE_LEGGINGS, ItemRarity.EPIC, EvoKind.LEGGINGS),
	EVOLVING_BOOTS(Material.NETHERITE_BOOTS, ItemRarity.EPIC, EvoKind.BOOTS),
	// --- armes moddées (évolutives aussi)
	EVOLIUM_SCYTHE(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.SCYTHE),
	RUBY_KATANA(Material.NETHERITE_SWORD, ItemRarity.RARE, EvoKind.KATANA),
	SAPPHIRE_WARHAMMER(Material.NETHERITE_SWORD, ItemRarity.RARE, EvoKind.WARHAMMER),
	EVOLITE_GREATSWORD(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.GREATSWORD),
	SHADOW_DAGGER(Material.NETHERITE_SWORD, ItemRarity.RARE, EvoKind.DAGGER),
	ROYAL_HALBERD(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.HALBERD),
	CRYSTAL_STAFF(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.STAFF),
	BLAST_BLADE(Material.NETHERITE_SWORD, ItemRarity.EPIC, EvoKind.BLAST_BLADE),
	// --- armes magiques à charges
	ARCANE_WAND(Material.STICK, ItemRarity.EPIC, EvoKind.ARCANE_WAND),
	THUNDER_GAUNTLET(Material.STICK, ItemRarity.EPIC, EvoKind.THUNDER_GAUNTLET),
	CELESTIAL_ORB(Material.STICK, ItemRarity.EPIC, EvoKind.CELESTIAL_ORB),
	VOID_GRIMOIRE(Material.STICK, ItemRarity.EPIC, EvoKind.VOID_GRIMOIRE),
	SOLAR_CHAKRAM(Material.STICK, ItemRarity.EPIC, EvoKind.SOLAR_CHAKRAM),
	SPIRIT_LANTERN(Material.STICK, ItemRarity.EPIC, EvoKind.SPIRIT_LANTERN),
	EVOLIUM_BLASTER(Material.STICK, ItemRarity.EPIC, EvoKind.EVOLIUM_BLASTER),
	// --- matériaux
	EVOLIUM(Material.PAPER, ItemRarity.RARE),
	RUBY(Material.PAPER, ItemRarity.COMMON),
	SAPPHIRE(Material.PAPER, ItemRarity.COMMON),
	EVOLITE_INGOT(Material.PAPER, ItemRarity.EPIC),
	ABYSSITE(Material.PAPER, ItemRarity.EPIC),
	BOSS_HEART(Material.PAPER, ItemRarity.EPIC),
	// --- objets spéciaux
	EVOLIUM_LIGHTER(Material.FLINT_AND_STEEL, ItemRarity.RARE),
	ELEMENTAL_GRIMOIRE(Material.BOOK, ItemRarity.EPIC),
	SPIDER_GLOVE(Material.STICK, ItemRarity.EPIC),
	EVOTOOL_MANUAL(Material.BOOK, ItemRarity.UNCOMMON),
	// --- blocs
	SANCTUARY_ALTAR(CustomBlock.SANCTUARY_ALTAR, ItemRarity.EPIC),
	SHIELD_GENERATOR(CustomBlock.SHIELD_GENERATOR, ItemRarity.EPIC),
	ELEMENTAL_COMBINER(CustomBlock.ELEMENTAL_COMBINER, ItemRarity.EPIC),
	ABYSSITE_ORE(CustomBlock.ABYSSITE_ORE, ItemRarity.EPIC),
	EVOLITE_BLOCK(CustomBlock.EVOLITE_BLOCK, ItemRarity.EPIC),
	EVOLITE_ORE(CustomBlock.EVOLITE_ORE, ItemRarity.RARE),
	EVOLIUM_BLOCK(CustomBlock.EVOLIUM_BLOCK, ItemRarity.RARE),
	DEEPSLATE_EVOLIUM_ORE(CustomBlock.DEEPSLATE_EVOLIUM_ORE, ItemRarity.RARE),
	RUBY_BLOCK(CustomBlock.RUBY_BLOCK, ItemRarity.COMMON),
	RUBY_ORE(CustomBlock.RUBY_ORE, ItemRarity.COMMON),
	DEEPSLATE_RUBY_ORE(CustomBlock.DEEPSLATE_RUBY_ORE, ItemRarity.COMMON),
	SAPPHIRE_BLOCK(CustomBlock.SAPPHIRE_BLOCK, ItemRarity.COMMON),
	SAPPHIRE_ORE(CustomBlock.SAPPHIRE_ORE, ItemRarity.COMMON),
	DEEPSLATE_SAPPHIRE_ORE(CustomBlock.DEEPSLATE_SAPPHIRE_ORE, ItemRarity.COMMON),
	EVOLVING_STONE(CustomBlock.EVOLVING_STONE, ItemRarity.COMMON),
	EVOLVING_DIRT(CustomBlock.EVOLVING_DIRT, ItemRarity.COMMON),
	EVOLVING_GRASS(CustomBlock.EVOLVING_GRASS, ItemRarity.COMMON),
	EVOLVING_LOG(CustomBlock.EVOLVING_LOG, ItemRarity.COMMON),
	EVOLVING_LEAVES(CustomBlock.EVOLVING_LEAVES, ItemRarity.COMMON),
	EVOLVING_CRYSTAL(CustomBlock.EVOLVING_CRYSTAL, ItemRarity.UNCOMMON),
	EVOLVING_ORE(CustomBlock.EVOLVING_ORE, ItemRarity.RARE);

	private static final Map<String, EvoItem> BY_ID = new HashMap<>();
	private static final Map<EvoKind, EvoItem> BY_KIND = new HashMap<>();
	private static final Map<CustomBlock, EvoItem> BY_BLOCK = new HashMap<>();

	static {
		for (EvoItem item : values()) {
			BY_ID.put(item.id, item);
			if (item.kind != null) {
				BY_KIND.put(item.kind, item);
			}
			if (item.block != null) {
				BY_BLOCK.put(item.block, item);
			}
		}
	}

	private final String id;
	private final Material base;
	private final ItemRarity rarity;
	private final EvoKind kind;
	private final CustomBlock block;

	EvoItem(Material base, ItemRarity rarity) {
		this(base, rarity, null, null);
	}

	EvoItem(Material base, ItemRarity rarity, EvoKind kind) {
		this(base, rarity, kind, null);
	}

	EvoItem(CustomBlock block, ItemRarity rarity) {
		this(Material.PAPER, rarity, null, block);
	}

	EvoItem(Material base, ItemRarity rarity, EvoKind kind, CustomBlock block) {
		this.id = name().toLowerCase(Locale.ROOT);
		this.base = base;
		this.rarity = rarity;
		this.kind = kind;
		this.block = block;
	}

	public String id() {
		return id;
	}

	public Material base() {
		return base;
	}

	public ItemRarity rarity() {
		return rarity;
	}

	/** Profil d'évolution (null : l'objet n'évolue pas). */
	public EvoKind kind() {
		return kind;
	}

	/** Le bloc que pose cet objet (null : pas un bloc). */
	public CustomBlock block() {
		return block;
	}

	public boolean stackable() {
		return kind == null && this != ELEMENTAL_GRIMOIRE && this != SPIDER_GLOVE && this != EVOTOOL_MANUAL
				&& this != EVOLIUM_LIGHTER;
	}

	public String translationKey() {
		return block != null ? "block.evotool." + id : "item.evotool." + id;
	}

	public static EvoItem byId(String id) {
		return BY_ID.get(id);
	}

	public static EvoItem of(EvoKind kind) {
		return BY_KIND.get(kind);
	}

	public static EvoItem of(CustomBlock block) {
		return BY_BLOCK.get(block);
	}
}
