package fr.evotool.plugin.input;

import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Keys;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Temps de recharge des pouvoirs : par objet du mod (groupe evotool:<objet>, affiché sur l'objet comme une perle de
 * l'Ender), sans toucher aux autres objets de même base.
 */
public final class Cooldowns {
	private Cooldowns() {
	}

	public static NamespacedKey group(EvoItem item) {
		return Keys.key(item.id());
	}

	public static boolean active(Player player, ItemStack stack) {
		EvoItem item = Items.of(stack);
		return item != null && player.getCooldown(group(item)) > 0;
	}

	public static void set(Player player, ItemStack stack, int ticks) {
		EvoItem item = Items.of(stack);
		if (item != null && ticks > 0) {
			player.setCooldown(group(item), ticks);
		}
	}
}
