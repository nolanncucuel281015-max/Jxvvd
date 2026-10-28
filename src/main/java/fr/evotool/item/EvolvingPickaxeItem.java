package fr.evotool.item;

import net.minecraft.item.Item;

/**
 * La pioche évolutive. Toute la logique (XP, niveaux, minage de zone, auto-smelt)
 * vit dans {@link fr.evotool.mining.EvoMiningHandler} et {@link fr.evotool.mining.EvoProgression} ;
 * cette classe sert surtout à identifier l'item.
 */
public class EvolvingPickaxeItem extends Item {
	public EvolvingPickaxeItem(Settings settings) {
		super(settings);
	}
}
