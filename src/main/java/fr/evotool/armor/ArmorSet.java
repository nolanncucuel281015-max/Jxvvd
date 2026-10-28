package fr.evotool.armor;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Bonus de l'ensemble d'armure évolutive.
 *
 * <p>Le niveau d'ensemble = somme des niveaux des 4 pièces portées / 4 (une pièce manquante compte 0),
 * il faut donc porter l'armure complète pour tout avoir. Les effets sont permanents tant qu'on la porte :
 * <ul>
 *   <li>Résistance I à V : niveaux 10 / 30 / 50 / 75 / 100</li>
 *   <li>Vitesse I à III : niveaux 15 / 45 / 70</li>
 *   <li>Régénération I à III : niveaux 20 / 55 / 90</li>
 *   <li>Cœurs en plus : +4 au niveau 80, +10 au niveau 100</li>
 * </ul>
 */
public final class ArmorSet {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static final Map<RegistryEntry<StatusEffect>, int[]> EFFECTS = new LinkedHashMap<>();

	static {
		EFFECTS.put(StatusEffects.RESISTANCE, new int[] {10, 30, 50, 75, 100});
		EFFECTS.put(StatusEffects.SPEED, new int[] {15, 45, 70});
		EFFECTS.put(StatusEffects.REGENERATION, new int[] {20, 55, 90});
	}

	public static final int HEARTS_LEVEL_1 = 80;
	public static final int HEARTS_LEVEL_2 = 100;

	private ArmorSet() {
	}

	public static boolean isEvolvingArmor(ItemStack stack, EquipmentSlot slot) {
		EvoKind kind = EvoProgression.kindOf(stack);
		return kind != null && kind.profile().armorSlot() == slot;
	}

	public static int setLevel(PlayerEntity player) {
		int sum = 0;
		for (EquipmentSlot slot : SLOTS) {
			ItemStack stack = player.getEquippedStack(slot);
			if (isEvolvingArmor(stack, slot)) {
				sum += EvoProgression.get(stack).level();
			}
		}
		return sum / SLOTS.length;
	}

	/** Effets voulus à ce niveau d'ensemble : effet → amplificateur (0 = niveau I). */
	public static Map<RegistryEntry<StatusEffect>, Integer> effects(int setLevel) {
		Map<RegistryEntry<StatusEffect>, Integer> result = new LinkedHashMap<>();
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
		if (setLevel >= HEARTS_LEVEL_2) {
			return 20.0;
		}
		return setLevel >= HEARTS_LEVEL_1 ? 8.0 : 0.0;
	}

	/** Lignes « bonus actifs » pour le menu. */
	public static List<Text> describe(int setLevel) {
		List<Text> lines = new ArrayList<>();
		effects(setLevel).forEach((effect, amplifier) -> lines.add(effectText(effect, amplifier + 1)));
		double health = bonusHealth(setLevel);
		if (health > 0) {
			lines.add(Text.translatable("armor.evotool.hearts", (int) (health / 2)));
		}
		return lines;
	}

	/** Ce qui se débloque exactement à ce niveau d'ensemble. */
	public static List<Text> unlocksAt(int setLevel) {
		List<Text> lines = new ArrayList<>();
		EFFECTS.forEach((effect, thresholds) -> {
			for (int i = 0; i < thresholds.length; i++) {
				if (thresholds[i] == setLevel) {
					lines.add(effectText(effect, i + 1));
				}
			}
		});
		if (setLevel == HEARTS_LEVEL_1 || setLevel == HEARTS_LEVEL_2) {
			lines.add(Text.translatable("armor.evotool.hearts", (int) (bonusHealth(setLevel) / 2)));
		}
		return lines;
	}

	private static MutableText effectText(RegistryEntry<StatusEffect> effect, int level) {
		return Text.translatable(effect.value().getTranslationKey()).append(" ")
				.append(Text.translatable("enchantment.level." + level));
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
