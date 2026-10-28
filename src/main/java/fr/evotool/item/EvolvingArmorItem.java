package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.Item;

/** Pièce d'armure évolutive (les réglages d'armure viennent de Item.Settings#armor). */
public class EvolvingArmorItem extends Item implements EvolvingItem {
	private final EvoKind kind;

	public EvolvingArmorItem(EvoKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public EvoKind evoKind() {
		return kind;
	}
}
