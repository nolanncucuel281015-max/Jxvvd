package fr.evotool.plugin.util;

import org.bukkit.NamespacedKey;

/** Clés des données rangées sur les objets, les entités et les mondes (espace « evotool »). */
public final class Keys {
	public static final String NAMESPACE = "evotool";

	/** Quel objet du mod c'est (« evolving_pickaxe »…). */
	public static final NamespacedKey ITEM = key("item");
	// données d'évolution (voir EvoData)
	public static final NamespacedKey LEVEL = key("level");
	public static final NamespacedKey XP = key("xp");
	public static final NamespacedKey MODE = key("mode");
	public static final NamespacedKey FLAGS = key("flags");
	public static final NamespacedKey COUNT = key("count");
	/** Amélioré à l'évolite. */
	public static final NamespacedKey UPGRADED = key("evolite_upgraded");
	/** Armure abyssale (jusqu'au niveau 150). */
	public static final NamespacedKey ABYSSAL = key("abyssal");
	/** Charges d'une arme magique, et l'heure de la prochaine. */
	public static final NamespacedKey CHARGES = key("charges");
	public static final NamespacedKey CHARGE_CLOCK = key("charge_clock");
	/** Élément donné par le combineur. */
	public static final NamespacedKey ELEMENT = key("element");
	/** Grimoire : éléments connus (« fire,ice ») et sort choisi. */
	public static final NamespacedKey GRIMOIRE_ELEMENTS = key("grimoire_elements");
	public static final NamespacedKey GRIMOIRE_SELECTED = key("grimoire_selected");
	/** Gant de Spider-Man éveillé, et son pouvoir de toile choisi. */
	public static final NamespacedKey AWAKENED = key("awakened");
	public static final NamespacedKey WEB_POWER = key("web_power");
	/** Générateur de bouclier ramassé : son évolium et son rayon. */
	public static final NamespacedKey SHIELD_FUEL = key("shield_fuel");
	public static final NamespacedKey SHIELD_RADIUS = key("shield_radius");
	/** Entités du plugin (morceaux du boss, tentacules, dômes…) : à retirer au redémarrage. */
	public static final NamespacedKey TEMPORARY = key("temporary");
	/** Le boss. */
	public static final NamespacedKey BOSS = key("bell_boss");
	public static final NamespacedKey BOSS_FORM = key("boss_form");
	/** Manuel déjà offert à ce joueur. */
	public static final NamespacedKey MANUAL_GIVEN = key("manual_given");
	/** Temps passé dans la dimension évolutive (ticks). */
	public static final NamespacedKey DIMENSION_TIME = key("evolutive_time");

	private Keys() {
	}

	public static NamespacedKey key(String path) {
		return new NamespacedKey(NAMESPACE, path);
	}
}
