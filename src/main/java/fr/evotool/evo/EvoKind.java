package fr.evotool.evo;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;

/**
 * Les outils et pièces d'armure évolutifs, et leurs profils d'évolution.
 * Tous changent de forme tous les 5 niveaux (21 formes) : il faut alors les faire évoluer avec la touche R.
 * Équilibrage : enchantements au plus au niveau normal de Minecraft, pas de minage instantané ni d'exécution.
 */
public enum EvoKind {
	/** Mine : auto-smelt, Fortune I à III, zones 3x3, 3x3x3 puis 5x5. */
	PICKAXE(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 25).mode(EvoMode.CUBE_3X3X3, 50).mode(EvoMode.AREA_5X5, 75)
			.power(Power.AUTOSMELT, 15)
			.enchant(Enchantments.FORTUNE, "fortune", 30, 55, 80)),

	/** Comme la pioche, mais pour la terre, le sable, le gravier… (sable → verre avec l'auto-smelt). */
	SHOVEL(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 25).mode(EvoMode.CUBE_3X3X3, 50).mode(EvoMode.AREA_5X5, 75)
			.power(Power.AUTOSMELT, 15)
			.enchant(Enchantments.FORTUNE, "fortune", 30, 55, 80)),

	/** Bûcheronne et se bat : abattage d'arbres, replantation, Tranchant I à III. */
	AXE(new EvoProfile()
			.blockXp().killXp()
			.power(Power.TREE_FELLING, 10).power(Power.FELL_LEAVES, 25).power(Power.AUTO_REPLANT, 35)
			.power(Power.LIFESTEAL, 50).power(Power.SHOCKWAVE, 75)
			.enchant(Enchantments.SHARPNESS, "sharpness", 25, 50, 75)),

	/** Labour et récolte en zone 3x3 puis 5x5, replantation, Fortune I à III pour les récoltes. */
	HOE(new EvoProfile()
			.blockXp().counter(EvoProfile.Counter.FARMING)
			.mode(EvoMode.AREA_3X3, 25).mode(EvoMode.AREA_5X5, 60)
			.power(Power.AUTO_REPLANT, 10)
			.enchant(Enchantments.FORTUNE, "fortune", 30, 55, 80)),

	/** Évolue en tuant des monstres : Tranchant, Butin, Affilage, Aura de feu, onde de choc, foudre. */
	SWORD(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 20).power(Power.SHOCKWAVE, 40).power(Power.LIGHTNING, 65)
			.power(Power.WIDE_SHOCKWAVE, 85)
			.enchant(Enchantments.SHARPNESS, "sharpness", 25, 50, 75)
			.enchant(Enchantments.LOOTING, "looting", 30, 60, 90)
			.enchant(Enchantments.SWEEPING_EDGE, "sweeping_edge", 35, 60, 85)
			.enchant(Enchantments.FIRE_ASPECT, "fire_aspect", 55, 90)),

	/** Lance (1.21.11) : évolue en tuant des monstres. Tranchant, Élan, Butin, onde de choc, foudre. */
	SPEAR(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 20).power(Power.SHOCKWAVE, 40).power(Power.LIGHTNING, 65)
			.power(Power.WIDE_SHOCKWAVE, 85)
			.enchant(Enchantments.SHARPNESS, "sharpness", 25, 50, 75)
			// Élan évolutif : comme Élan, mais sans faim (voir data/evotool/enchantment/evolving_lunge.json).
			.enchant(ModEnchantments.EVOLVING_LUNGE, "enchantment.evotool.evolving_lunge", 20, 45, 70)
			.enchant(Enchantments.LOOTING, "looting", 30, 60, 90)),

	/** Arc : évolue en tuant des monstres avec ses flèches. Puissance, Frappe, Flamme, Infinité, foudre. */
	BOW(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 20).power(Power.LIGHTNING, 65)
			.enchant(Enchantments.POWER, "power", 25, 50, 75)
			.enchant(Enchantments.PUNCH, "punch", 40)
			.enchant(Enchantments.FLAME, "flame", 55)
			.enchant(Enchantments.INFINITY, "infinity", 90)),

	/** Évolue en tuant des monstres : Densité, Brèche, Charge de vent, onde de choc, foudre. */
	MACE(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 20).power(Power.SHOCKWAVE, 40).power(Power.LIGHTNING, 65)
			.power(Power.WIDE_SHOCKWAVE, 85)
			.enchant(Enchantments.DENSITY, "density", 20, 40, 60, 80)
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
