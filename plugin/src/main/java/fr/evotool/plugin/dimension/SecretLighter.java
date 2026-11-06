package fr.evotool.plugin.dimension;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;

/**
 * Le briquet d'évolium (qui allume le portail) est un secret : sa recette marche mais le livre de recettes ne la
 * montre jamais. On le trouve aussi parfois dans les coffres des portails en ruine, et plus rarement dans ceux des
 * cités antiques, des temples et des forteresses.
 */
public final class SecretLighter implements Listener {
	public static final Map<String, Float> CHANCES = Map.of(
			"chests/ruined_portal", 0.3F,
			"chests/ancient_city", 0.12F,
			"chests/jungle_temple", 0.1F,
			"chests/desert_pyramid", 0.08F,
			"chests/stronghold_corridor", 0.08F);

	public static void enable() {
		EvoToolPlugin.listen(new SecretLighter());
	}

	@EventHandler(ignoreCancelled = true)
	public void onLoot(LootGenerateEvent event) {
		NamespacedKey key = event.getLootTable().getKey();
		Float chance = key.getNamespace().equals("minecraft") ? CHANCES.get(key.getKey()) : null;
		if (chance != null && ThreadLocalRandom.current().nextFloat() < chance) {
			event.getLoot().add(Items.create(EvoItem.EVOLIUM_LIGHTER));
		}
	}
}
