package fr.evotool.armor;

import fr.evotool.block.ModBlocks;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Unit;

/**
 * Armure abyssale : une pièce d'armure évolutive niveau 100 + une abyssite (minerai de la dimension évolutive)
 * à l'enclume. Elle devient noire et continue d'évoluer jusqu'au niveau 150 (une forme tous les 5 niveaux, avec
 * la touche R). L'ensemble complet donne alors des bonus bien plus forts (voir {@link ArmorSet}) et, au niveau 150,
 * les tentacules.
 */
public final class AbyssalArmor {
	public static final int LEVEL_COST = 30;

	private AbyssalArmor() {
	}

	/** L'abyssite ou son minerai (le bloc, au toucher de soie ou dans l'onglet créatif) : les deux marchent. */
	public static boolean isAbyssite(ItemStack stack) {
		return stack.isOf(ModItems.ABYSSITE) || stack.isOf(ModBlocks.ABYSSITE_ORE.asItem());
	}

	/** Pourquoi cet objet ne peut pas recevoir l'abyssite (null : il peut). */
	public static Text problem(ItemStack stack) {
		EvoKind kind = EvoProgression.kindOf(stack);
		if (kind == null || !kind.profile().isArmor()) {
			return Text.translatable("message.evotool.abyssite.armor_only");
		}
		if (EvoProgression.isAbyssal(stack)) {
			return Text.translatable("message.evotool.abyssite.already");
		}
		int level = EvoProgression.get(stack).level();
		if (level < EvoProgression.MAX_LEVEL) {
			return Text.translatable("message.evotool.abyssite.level", EvoProgression.MAX_LEVEL, level);
		}
		return null;
	}

	public static boolean canUpgrade(ItemStack stack) {
		EvoKind kind = EvoProgression.kindOf(stack);
		return kind != null && kind.profile().isArmor() && !EvoProgression.isAbyssal(stack)
				&& EvoProgression.get(stack).level() >= EvoProgression.MAX_LEVEL;
	}

	public static void apply(ItemStack stack) {
		stack.set(ModComponents.ABYSSAL, Unit.INSTANCE);
		EvoProgression.applyForm(stack);
	}
}
