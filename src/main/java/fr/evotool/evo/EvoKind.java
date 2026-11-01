package fr.evotool.evo;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;

/**
 * Les outils et pièces d'armure évolutifs, et leurs profils d'évolution.
 * Tous changent de forme tous les 5 niveaux (21 formes) : il faut alors les faire évoluer avec la touche R.
 * Équilibrage : outils un peu au-dessus de Minecraft (Fortune IV), armes plus sages (la force vient de l'armure),
 * pas de minage instantané ni d'exécution.
 */
public enum EvoKind {
	/** Mine : auto-smelt, Fortune I à IV, zones 3x3, 3x3x3, 5x5 puis 5x5x5. */
	PICKAXE(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.CUBE_3X3X3, 40).mode(EvoMode.AREA_5X5, 60).mode(EvoMode.CUBE_5X5X5, 90)
			.power(Power.AUTOSMELT, 10)
			.enchant(Enchantments.FORTUNE, "fortune", 20, 40, 60, 80)),

	/** Comme la pioche, mais pour la terre, le sable, le gravier… (sable → verre avec l'auto-smelt). */
	SHOVEL(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.CUBE_3X3X3, 40).mode(EvoMode.AREA_5X5, 60).mode(EvoMode.CUBE_5X5X5, 90)
			.power(Power.AUTOSMELT, 10)
			.enchant(Enchantments.FORTUNE, "fortune", 20, 40, 60, 80)),

	/** Bûcheronne et se bat : abattage d'arbres, replantation, Tranchant I à V. */
	AXE(new EvoProfile()
			.blockXp().killXp()
			.power(Power.TREE_FELLING, 10).power(Power.FELL_LEAVES, 20).power(Power.AUTO_REPLANT, 30)
			.power(Power.LIFESTEAL, 40).power(Power.SHOCKWAVE, 60)
			.enchant(Enchantments.SHARPNESS, "sharpness", 20, 35, 50, 65, 80)),

	/** Labour et récolte en zone 3x3, 5x5 puis 7x7, replantation, Fortune I à IV pour les récoltes. */
	HOE(new EvoProfile()
			.blockXp().counter(EvoProfile.Counter.FARMING)
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.AREA_5X5, 40).mode(EvoMode.AREA_7X7, 70)
			.power(Power.AUTO_REPLANT, 10)
			.enchant(Enchantments.FORTUNE, "fortune", 20, 40, 60, 80)),

	/** Évolue en tuant des monstres : Tranchant, Butin, Affilage, Aura de feu, onde de choc, foudre. */
	SWORD(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 15).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75)
			.enchant(Enchantments.SHARPNESS, "sharpness", 20, 35, 50, 65, 80)
			.enchant(Enchantments.LOOTING, "looting", 30, 60, 90)
			.enchant(Enchantments.SWEEPING_EDGE, "sweeping_edge", 35, 60, 85)
			.enchant(Enchantments.FIRE_ASPECT, "fire_aspect", 55, 90)),

	/** Lance (1.21.11) : évolue en tuant des monstres. Tranchant, Élan, Butin, onde de choc, foudre. */
	SPEAR(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 15).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75)
			.enchant(Enchantments.SHARPNESS, "sharpness", 20, 35, 50, 65, 80)
			// Élan évolutif : comme Élan, mais sans faim (voir data/evotool/enchantment/evolving_lunge.json).
			.enchant(ModEnchantments.EVOLVING_LUNGE, "enchantment.evotool.evolving_lunge", 20, 45, 70)
			.enchant(Enchantments.LOOTING, "looting", 30, 60, 90)),

	/** Arc : évolue en tuant des monstres avec ses flèches. Puissance, Frappe, Flamme, Infinité, foudre. */
	BOW(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 15).power(Power.LIGHTNING, 50)
			.enchant(Enchantments.POWER, "power", 20, 35, 50, 65, 80)
			.enchant(Enchantments.PUNCH, "punch", 30, 60)
			.enchant(Enchantments.FLAME, "flame", 40)
			.enchant(Enchantments.INFINITY, "infinity", 70)),

	/** Évolue en tuant des monstres : Densité, Brèche, Charge de vent, onde de choc, foudre. */
	MACE(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 15).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75)
			.enchant(Enchantments.DENSITY, "density", 20, 35, 50, 65, 80)
			.enchant(Enchantments.BREACH, "breach", 30, 60, 90)
			.enchant(Enchantments.WIND_BURST, "wind_burst", 50, 85)),

	/**
	 * Armure : chaque pièce portée gagne de l'XP en encaissant des dégâts.
	 * Les effets (Résistance, Régénération, Vitesse, cœurs) dépendent du niveau de l'ensemble,
	 * voir {@link fr.evotool.armor.ArmorSet}.
	 */
	HELMET(new EvoProfile().armor(EquipmentSlot.HEAD)),
	CHESTPLATE(new EvoProfile().armor(EquipmentSlot.CHEST)),
	LEGGINGS(new EvoProfile().armor(EquipmentSlot.LEGS)),
	BOOTS(new EvoProfile().armor(EquipmentSlot.FEET)),

	// --- armes moddées (voir fr.evotool.weapon.WeaponKind) : leur attaque spéciale grandit aussi avec le niveau
	SCYTHE(weapon().power(Power.LIFESTEAL, 10).power(Power.SHOCKWAVE, 30).power(Power.WIDE_SHOCKWAVE, 75)),
	KATANA(weapon().power(Power.LIFESTEAL, 10).power(Power.LIGHTNING, 50)),
	WARHAMMER(weapon().power(Power.SHOCKWAVE, 10).power(Power.WIDE_SHOCKWAVE, 50)),
	GREATSWORD(weapon().power(Power.SHOCKWAVE, 20).power(Power.LIGHTNING, 50).power(Power.WIDE_SHOCKWAVE, 75)),
	DAGGER(weapon().power(Power.LIFESTEAL, 10).power(Power.LIGHTNING, 60)),
	HALBERD(weapon().power(Power.SHOCKWAVE, 20).power(Power.LIGHTNING, 50)),
	STAFF(weapon().power(Power.LIGHTNING, 20).power(Power.LIFESTEAL, 40)),
	BLAST_BLADE(weapon().power(Power.SHOCKWAVE, 20).power(Power.WIDE_SHOCKWAVE, 50).power(Power.LIGHTNING, 70)),

	// --- armes magiques à charges (voir fr.evotool.weapon.MagicKind) : + 1 charge tous les 25 niveaux
	ARCANE_WAND(magic()),
	THUNDER_GAUNTLET(magic().power(Power.LIGHTNING, 30)),
	CELESTIAL_ORB(magic()),
	VOID_GRIMOIRE(magic()),
	SOLAR_CHAKRAM(magic()),
	SPIRIT_LANTERN(magic()),
	EVOLIUM_BLASTER(magic());

	private final EvoProfile profile;

	/** Arme moddée : évolue en tuant des monstres, Tranchant I à III, Butin I et II, Aura de feu. */
	private static EvoProfile weapon() {
		return new EvoProfile()
				.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
				.enchant(Enchantments.SHARPNESS, "sharpness", 25, 50, 75)
				.enchant(Enchantments.LOOTING, "looting", 40, 80)
				.enchant(Enchantments.FIRE_ASPECT, "fire_aspect", 70);
	}

	/** Arme magique : évolue en tuant des monstres (au corps à corps ou avec ses sorts). */
	private static EvoProfile magic() {
		return new EvoProfile()
				.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
				.power(Power.LIFESTEAL, 20);
	}

	EvoKind(EvoProfile profile) {
		this.profile = profile;
	}

	public EvoProfile profile() {
		return profile;
	}
}
