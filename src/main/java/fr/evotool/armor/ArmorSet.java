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
 * Bonus de l'ensemble d'armure évolutive : un nouveau bonus <b>tous les 5 niveaux</b>.
 *
 * <p>Le niveau d'ensemble = somme des niveaux des 4 pièces portées / 4 (une pièce manquante compte 0),
 * il faut donc porter l'armure complète pour tout avoir. Les effets sont permanents tant qu'on la porte.
 * Maximum : Résistance V, Vitesse III, Régénération III, Hâte II et +10 cœurs.
 */
public final class ArmorSet {
	public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Points de vie par palier de cœurs (4 points = 2 cœurs). */
	private static final double HEALTH_PER_STEP = 4.0;

	/**
	 * Un palier : soit un effet (amplificateur 0 = niveau I), soit des cœurs en plus (effect == null).
	 */
	public record Step(int level, RegistryEntry<StatusEffect> effect, int amplifier) {
		public boolean isHearts() {
			return effect == null;
		}
	}

	public static final List<Step> STEPS = List.of(
			new Step(5, StatusEffects.RESISTANCE, 0),
			new Step(10, StatusEffects.SPEED, 0),
			new Step(15, StatusEffects.REGENERATION, 0),
			new Step(20, StatusEffects.NIGHT_VISION, 0),
			new Step(25, StatusEffects.RESISTANCE, 1),
			new Step(30, StatusEffects.SPEED, 1),
			new Step(35, StatusEffects.FIRE_RESISTANCE, 0),
			new Step(40, StatusEffects.REGENERATION, 1),
			new Step(45, StatusEffects.RESISTANCE, 2),
			new Step(50, StatusEffects.WATER_BREATHING, 0),
			new Step(55, StatusEffects.HASTE, 0),
			new Step(60, StatusEffects.SPEED, 2),
			new Step(65, StatusEffects.RESISTANCE, 3),
			new Step(70, StatusEffects.REGENERATION, 2),
			new Step(75, StatusEffects.HASTE, 1),
			// Les cœurs : les paliers les plus durs (niveaux 80 à 100).
			new Step(80, null, 0),
			new Step(85, null, 0),
			new Step(90, null, 0),
			new Step(95, null, 0),
			new Step(100, null, 0),
			new Step(100, StatusEffects.RESISTANCE, 4));

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

	/** Effets voulus à ce niveau d'ensemble : effet → amplificateur le plus haut atteint. */
	public static Map<RegistryEntry<StatusEffect>, Integer> effects(int setLevel) {
		Map<RegistryEntry<StatusEffect>, Integer> result = new LinkedHashMap<>();
		for (Step step : STEPS) {
			if (!step.isHearts() && setLevel >= step.level()) {
				result.merge(step.effect(), step.amplifier(), Math::max);
			}
		}
		return result;
	}

	/** Points de vie en plus (2 points = 1 cœur) : +2 cœurs aux niveaux 80, 85, 90, 95 et 100. */
	public static double bonusHealth(int setLevel) {
		double health = 0;
		for (Step step : STEPS) {
			if (step.isHearts() && setLevel >= step.level()) {
				health += HEALTH_PER_STEP;
			}
		}
		return health;
	}

	/** Lignes « bonus actifs » pour le menu, plus le prochain palier. */
	public static List<Text> describe(int setLevel) {
		List<Text> lines = new ArrayList<>();
		effects(setLevel).forEach((effect, amplifier) -> lines.add(effectText(effect, amplifier)));
		double health = bonusHealth(setLevel);
		if (health > 0) {
			lines.add(heartsText(health));
		}
		for (Step step : STEPS) {
			if (step.level() > setLevel) {
				lines.add(Text.translatable("armor.evotool.next", step.level(), stepText(step)));
				break;
			}
		}
		return lines;
	}

	/** Ce qui se débloque exactement à ce niveau d'ensemble. */
	public static List<Text> unlocksAt(int setLevel) {
		List<Text> lines = new ArrayList<>();
		for (Step step : STEPS) {
			if (step.level() == setLevel) {
				lines.add(stepText(step));
			}
		}
		return lines;
	}

	private static Text stepText(Step step) {
		return step.isHearts()
				? Text.translatable("armor.evotool.more_hearts", (int) (HEALTH_PER_STEP / 2), (int) (bonusHealth(step.level()) / 2))
				: effectText(step.effect(), step.amplifier());
	}

	private static Text heartsText(double health) {
		return Text.translatable("armor.evotool.hearts", (int) (health / 2));
	}

	private static MutableText effectText(RegistryEntry<StatusEffect> effect, int amplifier) {
		return Text.translatable(effect.value().getTranslationKey()).append(" ")
				.append(Text.translatable("enchantment.level." + (amplifier + 1)));
	}
}
