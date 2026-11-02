package fr.evotool.dimension;

import fr.evotool.item.ModItems;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;

/**
 * Le briquet d'évolium (qui allume le portail de la dimension évolutive) est un secret à découvrir : sa recette
 * marche mais le livre de recettes ne la montre jamais. On le trouve aussi parfois dans les coffres des portails en
 * ruine (c'est logique : ils servent aux portails), et plus rarement dans ceux des temples, des forteresses et des
 * cités antiques.
 */
public final class SecretLighter {
	/** Table de butin → chance de trouver un briquet dans le coffre. */
	public static final Map<RegistryKey<LootTable>, Float> CHANCES = Map.of(
			LootTables.RUINED_PORTAL_CHEST, 0.3F,
			LootTables.ANCIENT_CITY_CHEST, 0.12F,
			LootTables.JUNGLE_TEMPLE_CHEST, 0.1F,
			LootTables.DESERT_PYRAMID_CHEST, 0.08F,
			LootTables.STRONGHOLD_CORRIDOR_CHEST, 0.08F);

	private SecretLighter() {
	}

	public static void init() {
		LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
			Float chance = CHANCES.get(key);
			if (chance != null && source.isBuiltin()) {
				tableBuilder.pool(LootPool.builder()
						.rolls(ConstantLootNumberProvider.create(1))
						.conditionally(RandomChanceLootCondition.builder(chance))
						.with(ItemEntry.builder(ModItems.EVOLIUM_LIGHTER)));
			}
		});
	}
}
