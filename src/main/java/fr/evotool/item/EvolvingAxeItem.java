package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ToolMaterial;

/** Hache évolutive : garde l'écorçage vanilla (clic droit). */
public class EvolvingAxeItem extends AxeItem implements EvolvingItem {
	public EvolvingAxeItem(ToolMaterial material, float attackDamage, float attackSpeed, Settings settings) {
		super(material, attackDamage, attackSpeed, settings);
	}

	@Override
	public EvoKind evoKind() {
		return EvoKind.AXE;
	}
}
