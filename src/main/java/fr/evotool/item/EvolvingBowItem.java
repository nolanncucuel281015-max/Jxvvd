package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.BowItem;

/** Arc Évolutif : un arc vanilla qui évolue en tuant des monstres avec ses flèches. */
public class EvolvingBowItem extends BowItem implements EvolvingItem {
	public EvolvingBowItem(Settings settings) {
		super(settings);
	}

	@Override
	public EvoKind evoKind() {
		return EvoKind.BOW;
	}
}
