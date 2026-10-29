package fr.evotool.evo;

import fr.evotool.EvoTool;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

/** Enchantements du mod (définis en JSON dans data/evotool/enchantment/). */
public final class ModEnchantments {
	/** Élan de la Lance Évolutive : même propulsion que l'Élan vanilla, sans faim ni usure. */
	public static final RegistryKey<Enchantment> EVOLVING_LUNGE =
			RegistryKey.of(RegistryKeys.ENCHANTMENT, EvoTool.id("evolving_lunge"));

	private ModEnchantments() {
	}
}
