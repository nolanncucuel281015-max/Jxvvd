package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.ToolMaterial;

/** Houe évolutive : le labour en zone est géré par {@link fr.evotool.mining.FarmingHandler}. */
public class EvolvingHoeItem extends HoeItem implements EvolvingItem {
	public EvolvingHoeItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public EvoKind evoKind() {
		return EvoKind.HOE;
	}
}
