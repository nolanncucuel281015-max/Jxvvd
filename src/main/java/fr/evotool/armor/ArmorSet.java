package fr.evotool.armor;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 *   <li>Résistance I à IV : niveaux 20 / 45 / 70 / 95</li>
 *   <li>Force I et II : niveaux 35 / 75 (c'est l'armure qui rend fort, pas les armes)</li>
 *   <li>Vitesse I et II : niveaux 25 / 65</li>
 *   <li>Célérité I : niveau 55</li>
 *   <li>Régénération I et II : niveaux 40 / 85</li>
 *   <li>Cœurs en plus : +4 au niveau 80, +6 au niveau 100</li>
 * </ul>
 * Au-delà de 100 (armure abyssale, voir {@link AbyssalArmor}) : Célérité II (105), Résistance au feu (105),
 * Apnée (110), Force III (110), Vitesse III (115), Régénération III (120), <b>Résistance V</b> (125),
 * Force IV (140), +10 cœurs (130) puis +15 cœurs (150) ; au niveau 150, les tentacules.
 */
public final class ArmorSet {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private static final Map<RegistryEntry<StatusEffect>, int[]> EFFECTS = new LinkedHashMap<>();

	static {
		EFFECTS.put(StatusEffects.RESISTANCE, new int[] {20, 45, 70, 95, 125});
		EFFECTS.put(StatusEffects.STRENGTH, new int[] {35, 75, 110, 140});
		EFFECTS.put(StatusEffects.SPEED, new int[] {25, 65, 115});
		EFFECTS.put(StatusEffects.HASTE, new int[] {55, 105});
		EFFECTS.put(StatusEffects.REGENERATION, new int[] {40, 85, 120});
		EFFECTS.put(StatusEffects.FIRE_RESISTANCE, new int[] {105});
		EFFECTS.put(StatusEffects.WATER_BREATHING, new int[] {110});
	}

	public static final int HEARTS_LEVEL_1 = 80;
	public static final int HEARTS_LEVEL_2 = 100;
	public static final int HEARTS_LEVEL_3 = 130;
	public static final int HEARTS_LEVEL_4 = 150;
	/** Ensemble abyssal complet au niveau 150 : les tentacules. */
	public static final int TENTACLE_LEVEL = 150;

	private ArmorSet() {
	}

	public static boolean isEvolvingArmor(ItemStack stack, EquipmentSlot slot) {
		EvoKind kind = EvoProgression.kindOf(stack);
		return kind != null && kind.profile().armorSlot() == slot;
	}

	/** Niveau d'ensemble à partir des 4 pièces (aussi utilisé par le client pour dessiner les tentacules). */
	public static int setLevel(ItemStack head, ItemStack chest, ItemStack legs, ItemStack feet) {
		ItemStack[] stacks = {head, chest, legs, feet};
		int sum = 0;
		for (int i = 0; i < SLOTS.length; i++) {
			if (stacks[i] != null && isEvolvingArmor(stacks[i], SLOTS[i])) {
				sum += EvoProgression.get(stacks[i]).level();
			}
		}
		return sum / SLOTS.length;
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

	/** Tous les effets que l'armure peut donner. */
	public static Set<RegistryEntry<StatusEffect>> managedEffects() {
		return EFFECTS.keySet();
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
	public static List<Text> describe(int setLevel) {
		List<Text> lines = new ArrayList<>();
		effects(setLevel).forEach((effect, amplifier) -> lines.add(effectText(effect, amplifier + 1)));
		double health = bonusHealth(setLevel);
		if (health > 0) {
			lines.add(Text.translatable("armor.evotool.hearts", (int) (health / 2)));
		}
		for (int level = setLevel + 1; level <= EvoProgression.ABYSS_MAX_LEVEL; level++) {
			List<Text> next = unlocksAt(level);
			if (!next.isEmpty()) {
				lines.add(Text.translatable("armor.evotool.next", level, next.get(0)));
				break;
			}
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
		if (setLevel == HEARTS_LEVEL_1 || setLevel == HEARTS_LEVEL_2 || setLevel == HEARTS_LEVEL_3 || setLevel == HEARTS_LEVEL_4) {
			lines.add(Text.translatable("armor.evotool.hearts", (int) (bonusHealth(setLevel) / 2)));
		}
		if (setLevel == TENTACLE_LEVEL) {
			lines.add(Text.translatable("armor.evotool.tentacles"));
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
