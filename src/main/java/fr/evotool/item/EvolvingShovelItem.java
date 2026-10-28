package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.ShovelItem;
import net.minecraft.item.ToolMaterial;

/** Pelle évolutive : garde la création de chemins vanilla (clic droit). */
public class EvolvingShovelItem extends ShovelItem implements EvolvingItem {
	public EvolvingShovelItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public EvoKind evoKind() {
		return EvoKind.SHOVEL;
	}
}
