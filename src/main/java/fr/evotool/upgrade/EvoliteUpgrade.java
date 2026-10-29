package fr.evotool.upgrade;

import fr.evotool.EvoTool;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.Locale;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Unit;

/**
 * Amélioration à l'évolite : objet évolutif + lingot d'évolite dans une enclume.
 * Chaque objet gagne un pouvoir magique (voir {@link Upgrade}).
 */
public final class EvoliteUpgrade {
	/** Coût en niveaux d'expérience à l'enclume. */
	public static final int LEVEL_COST = 10;

	/** Le pouvoir que donne l'amélioration selon l'objet. */
	public enum Upgrade {
		/** Pioche, pelle, hache, houe : les objets cassés vont directement dans l'inventaire. */
		TELEKINESIS,
		/** Épée : clic droit = vague tranchante magique devant soi. */
		MAGIC_SLASH,
		/** Masse : clic droit = bond dans les airs pour préparer une attaque écrasante, sans dégâts de chute. */
		SOAR,
		/** Casque : les monstres proches brillent à travers les murs. */
		KNIGHT_SIGHT,
		/** Plastron : bouclier magique permanent (4 cœurs dorés) qui se recharge hors combat. */
		MAGIC_SHIELD,
		/** Jambières : monte les blocs sans sauter et plus aucun dégât de chute. */
		CELESTIAL_LEGS,
		/** Bottes : double saut = s'envoler sur un nuage magique, comme en créatif. */
		MAGIC_CLOUD;

		public String translationKey() {
			return "upgrade.evotool." + name().toLowerCase(Locale.ROOT);
		}

		public String descriptionKey() {
			return translationKey() + ".desc";
		}
	}

	private EvoliteUpgrade() {
	}

	public static Upgrade upgradeFor(EvoKind kind) {
		return switch (kind) {
			case PICKAXE, SHOVEL, AXE, HOE -> Upgrade.TELEKINESIS;
			case SWORD -> Upgrade.MAGIC_SLASH;
			case MACE -> Upgrade.SOAR;
			case HELMET -> Upgrade.KNIGHT_SIGHT;
			case CHESTPLATE -> Upgrade.MAGIC_SHIELD;
			case LEGGINGS -> Upgrade.CELESTIAL_LEGS;
			case BOOTS -> Upgrade.MAGIC_CLOUD;
		};
	}

	public static boolean isUpgraded(ItemStack stack) {
		return stack.contains(ModComponents.UPGRADED);
	}

	/** Vrai si l'objet a ce pouvoir d'amélioration. */
	public static boolean has(ItemStack stack, Upgrade upgrade) {
		EvoKind kind = EvoProgression.kindOf(stack);
		return kind != null && isUpgraded(stack) && upgradeFor(kind) == upgrade;
	}

	public static boolean canUpgrade(ItemStack stack) {
		return EvoProgression.kindOf(stack) != null && !isUpgraded(stack);
	}

	/** Applique l'amélioration (le stack est modifié). */
	public static void apply(ItemStack stack) {
		EvoKind kind = EvoProgression.kindOf(stack);
		if (kind == null) {
			return;
		}
		stack.set(ModComponents.UPGRADED, Unit.INSTANCE);
		stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

		AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.DEFAULT);
		switch (upgradeFor(kind)) {
			case MAGIC_SHIELD -> modifiers = modifiers.with(EntityAttributes.MAX_ABSORPTION,
					new EntityAttributeModifier(EvoTool.id("evolite_shield"), MagicPowers.SHIELD_MAX, EntityAttributeModifier.Operation.ADD_VALUE),
					AttributeModifierSlot.CHEST);
			case CELESTIAL_LEGS -> modifiers = modifiers
					.with(EntityAttributes.STEP_HEIGHT,
							new EntityAttributeModifier(EvoTool.id("evolite_step"), 0.5, EntityAttributeModifier.Operation.ADD_VALUE),
							AttributeModifierSlot.LEGS)
					.with(EntityAttributes.FALL_DAMAGE_MULTIPLIER,
							new EntityAttributeModifier(EvoTool.id("evolite_fall"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
							AttributeModifierSlot.LEGS);
			default -> {
			}
		}
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);
	}
}
