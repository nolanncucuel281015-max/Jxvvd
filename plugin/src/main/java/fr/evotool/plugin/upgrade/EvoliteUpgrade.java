package fr.evotool.plugin.upgrade;

import com.google.common.collect.Multimap;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Keys;
import java.util.Locale;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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
		/** Lance : pendant la charge, on court beaucoup plus vite et chaque coup foudroie la cible. */
		LIGHTNING_CHARGE,
		/** Arc (niveau 100) : clic droit = arc géant qui tire une pluie de flèches droit sur les monstres. */
		CELESTIAL_BOW,
		/** Masse : clic droit = bond dans les airs pour préparer une attaque écrasante, sans dégâts de chute. */
		SOAR,
		/** Casque : les monstres proches brillent à travers les murs. */
		KNIGHT_SIGHT,
		/** Plastron : ailes de l'infini ; double saut pour voler, plus vite qu'en créatif. */
		INFINITY_WINGS,
		/** Jambières : monte les blocs sans sauter et plus aucun dégât de chute. */
		CELESTIAL_LEGS,
		/** Bottes : rester accroupi sur le sol pour plonger dedans et filer sous terre à travers la roche. */
		BURROW,
		/** Armes moddées et magiques : attaque spéciale 30 % plus forte et recharge 30 % plus courte. */
		EMPOWERED;

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
			case SPEAR -> Upgrade.LIGHTNING_CHARGE;
			case BOW -> Upgrade.CELESTIAL_BOW;
			case MACE -> Upgrade.SOAR;
			case HELMET -> Upgrade.KNIGHT_SIGHT;
			case CHESTPLATE -> Upgrade.INFINITY_WINGS;
			case LEGGINGS -> Upgrade.CELESTIAL_LEGS;
			case BOOTS -> Upgrade.BURROW;
			default -> Upgrade.EMPOWERED;
		};
	}

	public static boolean isUpgraded(ItemStack stack) {
		return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(Keys.UPGRADED);
	}

	/** Vrai si l'objet a ce pouvoir d'amélioration. */
	public static boolean has(ItemStack stack, Upgrade upgrade) {
		EvoKind kind = Items.kindOf(stack);
		return kind != null && isUpgraded(stack) && upgradeFor(kind) == upgrade;
	}

	public static boolean canUpgrade(ItemStack stack) {
		return Items.kindOf(stack) != null && !isUpgraded(stack);
	}

	/** Applique l'amélioration (le stack est modifié). */
	public static void apply(ItemStack stack) {
		if (Items.kindOf(stack) == null) {
			return;
		}
		stack.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.UPGRADED, PersistentDataType.BYTE, (byte) 1));
		Progression.refresh(stack, false);
	}

	/** Attributs de l'amélioration (jambières célestes : marches sans sauter, pas de dégâts de chute). */
	public static void addModifiers(EvoKind kind, Multimap<Attribute, AttributeModifier> modifiers) {
		if (upgradeFor(kind) == Upgrade.CELESTIAL_LEGS) {
			modifiers.put(Attribute.STEP_HEIGHT, Items.modifier("evolite_step", 0.5, EquipmentSlotGroup.LEGS));
			modifiers.put(Attribute.FALL_DAMAGE_MULTIPLIER, new AttributeModifier(Keys.key("evolite_fall"), -1.0,
					AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.LEGS));
		}
	}
}
