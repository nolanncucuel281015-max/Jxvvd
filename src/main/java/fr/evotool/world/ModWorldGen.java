package fr.evotool.world;

import fr.evotool.EvoTool;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class ModWorldGen {
	private ModWorldGen() {
	}

	public static void init() {
		// Les features elles-mêmes sont en JSON dans data/evotool/worldgen/.
		for (String name : new String[] {"ore_evolium", "ore_ruby", "ore_sapphire"}) {
			RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EvoTool.id(name));
			BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), GenerationStep.Feature.UNDERGROUND_ORES, key);
		}
	}
}
