package fr.evotool.plugin.armor;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * Bonus de l'ensemble d'armure évolutive (niveau d'ensemble = somme des 4 pièces / 4) : Résistance I à V, Force I
 * à IV, Vitesse I à III, Célérité I et II, Régénération I à III, Résistance au feu, Apnée, cœurs en plus et, au
 * niveau 150 (armure abyssale), les tentacules.
 */
public final class ArmorSet {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final Map<PotionEffectType, int[]> EFFECTS = new LinkedHashMap<>();

	static {
		EFFECTS.put(PotionEffectType.RESISTANCE, new int[] {20, 45, 70, 95, 125});
		EFFECTS.put(PotionEffectType.STRENGTH, new int[] {35, 75, 110, 140});
		EFFECTS.put(PotionEffectType.SPEED, new int[] {25, 65, 115});
		EFFECTS.put(PotionEffectType.HASTE, new int[] {55, 105});
		EFFECTS.put(PotionEffectType.REGENERATION, new int[] {40, 85, 120});
		EFFECTS.put(PotionEffectType.FIRE_RESISTANCE, new int[] {105});
		EFFECTS.put(PotionEffectType.WATER_BREATHING, new int[] {110});
	}

	public static final int HEARTS_LEVEL_1 = 80;
	public static final int HEARTS_LEVEL_2 = 100;
	public static final int HEARTS_LEVEL_3 = 130;
	public static final int HEARTS_LEVEL_4 = 150;
	public static final int TENTACLE_LEVEL = 150;

	private ArmorSet() {
	}

	public static boolean isEvolvingArmor(ItemStack stack, EquipmentSlot slot) {
		EvoKind kind = Items.kindOf(stack);
		return kind != null && kind.profile().armorSlot() == slot;
	}

	public static ItemStack worn(Player player, EquipmentSlot slot) {
		ItemStack stack = player.getInventory().getItem(slot);
		return isEvolvingArmor(stack, slot) ? stack : null;
	}

	public static int setLevel(Player player) {
		int sum = 0;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = worn(player, slot);
			if (stack != null) {
				sum += EvoData.get(stack).level();
			}
		}
		return sum / SLOTS.length;
	}

	/** Ensemble abyssal complet au niveau 150 : les tentacules. */
	public static boolean hasTentacles(Player player) {
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = worn(player, slot);
			if (stack == null || !Progression.isAbyssal(stack)) {
				return false;
			}
		}
		return setLevel(player) >= TENTACLE_LEVEL;
	}

	public static Set<PotionEffectType> managedEffects() {
		return EFFECTS.keySet();
	}

	/** Effets voulus à ce niveau d'ensemble : effet → amplificateur (0 = niveau I). */
	public static Map<PotionEffectType, Integer> effects(int setLevel) {
		Map<PotionEffectType, Integer> result = new LinkedHashMap<>();
		EFFECTS.forEach((effect, thresholds) -> {
			int count = countReached(setLevel, thresholds);
			if (count > 0) {
				result.put(effect, count - 1);
			}
		});
		return result;
	}

	/** Points de vie en plus (2 points = 1 cœur). */
	public static double bonusHealth(int setLevel) {
		if (setLevel >= HEARTS_LEVEL_4) {
			return 30.0;
		}
		if (setLevel >= HEARTS_LEVEL_3) {
			return 20.0;
		}
		if (setLevel >= HEARTS_LEVEL_2) {
			return 12.0;
		}
		return setLevel >= HEARTS_LEVEL_1 ? 8.0 : 0.0;
	}

	/** Lignes « bonus actifs » pour le menu, plus le prochain bonus. */
	public static List<Component> describe(int setLevel) {
		List<Component> lines = new ArrayList<>();
		effects(setLevel).forEach((effect, amplifier) -> lines.add(effectText(effect, amplifier + 1)));
		double health = bonusHealth(setLevel);
		if (health > 0) {
			lines.add(Lang.tr("armor.evotool.hearts", (int) (health / 2)));
		}
		for (int level = setLevel + 1; level <= Progression.ABYSS_MAX_LEVEL; level++) {
			List<Component> next = unlocksAt(level);
			if (!next.isEmpty()) {
				lines.add(Lang.tr("armor.evotool.next", level, next.get(0)));
				break;
			}
		}
		return lines;
	}

	public static List<Component> unlocksAt(int setLevel) {
		List<Component> lines = new ArrayList<>();
		EFFECTS.forEach((effect, thresholds) -> {
			for (int i = 0; i < thresholds.length; i++) {
				if (thresholds[i] == setLevel) {
					lines.add(effectText(effect, i + 1));
				}
			}
		});
		if (setLevel == HEARTS_LEVEL_1 || setLevel == HEARTS_LEVEL_2 || setLevel == HEARTS_LEVEL_3 || setLevel == HEARTS_LEVEL_4) {
			lines.add(Lang.tr("armor.evotool.hearts", (int) (bonusHealth(setLevel) / 2)));
		}
		if (setLevel == TENTACLE_LEVEL) {
			lines.add(Lang.tr("armor.evotool.tentacles"));
		}
		return lines;
	}

	private static Component effectText(PotionEffectType effect, int level) {
		return Component.translatable(effect.translationKey()).append(Component.text(" "))
				.append(Component.translatable("enchantment.level." + level));
	}

	private static int countReached(int level, int[] thresholds) {
		int count = 0;
		for (int threshold : thresholds) {
			if (level >= threshold) {
				count++;
			}
		}
		return count;
	}
}
