package fr.evotool.plugin.armor;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Armure abyssale : une pièce d'armure évolutive niveau 100 + une abyssite (ou son minerai) à l'enclume.
 * Elle devient noire et continue d'évoluer jusqu'au niveau 150 ; au niveau 150, l'ensemble donne les tentacules.
 */
public final class AbyssalArmor {
	public static final int LEVEL_COST = 30;

	private AbyssalArmor() {
	}

	/** L'abyssite ou son minerai : les deux marchent. */
	public static boolean isAbyssite(ItemStack stack) {
		return Items.is(stack, EvoItem.ABYSSITE) || Items.is(stack, EvoItem.ABYSSITE_ORE);
	}

	/** Pourquoi cet objet ne peut pas recevoir l'abyssite (null : il peut). */
	public static Component problem(ItemStack stack) {
		EvoKind kind = Items.kindOf(stack);
		if (kind == null || !kind.profile().isArmor()) {
			return Lang.tr("message.evotool.abyssite.armor_only");
		}
		if (Progression.isAbyssal(stack)) {
			return Lang.tr("message.evotool.abyssite.already");
		}
		int level = EvoData.get(stack).level();
		if (level < Progression.MAX_LEVEL) {
			return Lang.tr("message.evotool.abyssite.level", Progression.MAX_LEVEL, level);
		}
		return null;
	}

	public static boolean canUpgrade(ItemStack stack) {
		return problem(stack) == null;
	}

	public static void apply(ItemStack stack) {
		stack.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ABYSSAL, PersistentDataType.BYTE, (byte) 1));
		Progression.refresh(stack, false);
	}
}
