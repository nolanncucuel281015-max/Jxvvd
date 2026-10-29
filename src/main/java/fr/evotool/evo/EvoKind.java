package fr.evotool.evo;

import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;

/**
 * Les outils et pièces d'armure évolutifs, et leurs profils d'évolution.
 * Tous changent de forme tous les 5 niveaux (21 formes).
 */
public enum EvoKind {
	/** Mine : auto-smelt, Fortune I à VI, 3x3 → 5x5x5, minage instantané. */
	PICKAXE(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.CUBE_3X3X3, 30).mode(EvoMode.AREA_5X5, 40).mode(EvoMode.CUBE_5X5X5, 50)
			.power(Power.AUTOSMELT, 10).power(Power.INSTAMINE, 100)
			.enchant(Enchantments.FORTUNE, "fortune", 15, 20, 25, 30, 35, 40)),

	/** Comme la pioche, mais pour la terre, le sable, le gravier… (sable → verre avec l'auto-smelt). */
	SHOVEL(new EvoProfile()
			.blockXp().withAreaMining()
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.CUBE_3X3X3, 30).mode(EvoMode.AREA_5X5, 40).mode(EvoMode.CUBE_5X5X5, 50)
			.power(Power.AUTOSMELT, 10).power(Power.INSTAMINE, 100)
			.enchant(Enchantments.FORTUNE, "fortune", 15, 20, 25, 30, 35, 40)),

	/** Bûcheronne et se bat : abattage d'arbres, replantation, Tranchant I à V. */
	AXE(new EvoProfile()
			.blockXp().killXp()
			.power(Power.TREE_FELLING, 10).power(Power.FELL_LEAVES, 20).power(Power.AUTO_REPLANT, 30)
			.power(Power.LIFESTEAL, 40).power(Power.SHOCKWAVE, 60)
			.power(Power.INSTAMINE, 100).power(Power.EXECUTION, 100)
			.enchant(Enchantments.SHARPNESS, "sharpness", 15, 25, 35, 45, 55)),

	/** Labour et récolte en zone, replantation, Fortune pour les récoltes, pousse instantanée. */
	HOE(new EvoProfile()
			.blockXp().counter(EvoProfile.Counter.FARMING)
			.mode(EvoMode.AREA_3X3, 20).mode(EvoMode.AREA_5X5, 40).mode(EvoMode.AREA_7X7, 60).mode(EvoMode.AREA_9X9, 80)
			.power(Power.AUTO_REPLANT, 10).power(Power.INSTANT_GROWTH, 100)
			.enchant(Enchantments.FORTUNE, "fortune", 15, 20, 25, 30, 35, 40)),

	/** Évolue en tuant des monstres : Tranchant, Butin, Affilage, Aura de feu, onde de choc, foudre, exécution. */
	SWORD(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 10).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75).power(Power.EXECUTION, 100)
			.enchant(Enchantments.SHARPNESS, "sharpness", 15, 25, 35, 45, 55)
			.enchant(Enchantments.LOOTING, "looting", 20, 30, 40, 50, 60)
			.enchant(Enchantments.SWEEPING_EDGE, "sweeping_edge", 35, 55, 70)
			.enchant(Enchantments.FIRE_ASPECT, "fire_aspect", 65, 85)),

	/** Lance (1.21.11) : évolue en tuant des monstres. Tranchant, Élan, Butin, onde de choc, foudre, exécution. */
	SPEAR(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 10).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75).power(Power.EXECUTION, 100)
			.enchant(Enchantments.SHARPNESS, "sharpness", 15, 25, 35, 45, 55)
			.enchant(Enchantments.LUNGE, "lunge", 20, 40, 60)
			.enchant(Enchantments.LOOTING, "looting", 30, 50, 70)),

	/** Évolue en tuant des monstres : Densité, Brèche, Charge de vent, onde de choc, foudre, exécution. */
	MACE(new EvoProfile()
			.curve(XpCurve.COMBAT).counter(EvoProfile.Counter.KILLS).killXp()
			.power(Power.LIFESTEAL, 10).power(Power.SHOCKWAVE, 30).power(Power.LIGHTNING, 50)
			.power(Power.WIDE_SHOCKWAVE, 75).power(Power.EXECUTION, 100)
			.enchant(Enchantments.DENSITY, "density", 15, 25, 35, 45, 55)
			.enchant(Enchantments.BREACH, "breach", 20, 30, 40, 50)
			.enchant(Enchantments.WIND_BURST, "wind_burst", 60, 70, 80)),

	/**
	 * Armure : chaque pièce portée gagne de l'XP en encaissant des dégâts.
	 * Les effets (Résistance, Régénération, Vitesse, cœurs) dépendent du niveau de l'ensemble,
	 * voir {@link fr.evotool.armor.ArmorSet}.
	 */
	HELMET(new EvoProfile().armor(EquipmentSlot.HEAD)),
	CHESTPLATE(new EvoProfile().armor(EquipmentSlot.CHEST)),
	LEGGINGS(new EvoProfile().armor(EquipmentSlot.LEGS)),
	BOOTS(new EvoProfile().armor(EquipmentSlot.FEET));

	private final EvoProfile profile;

	EvoKind(EvoProfile profile) {
		this.profile = profile;
	}

	public EvoProfile profile() {
		return profile;
	}
}
