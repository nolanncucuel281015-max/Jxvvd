package fr.evotool.plugin.craft;

import fr.evotool.plugin.armor.AbyssalArmor;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Lang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

/**
 * L'enclume : objet évolutif + lingot d'évolite = amélioration (10 niveaux) ; pièce d'armure niveau 100 + abyssite
 * (ou son minerai) = armure abyssale (30 niveaux). Un seul lingot est consommé. Si l'abyssite ne prend pas, on dit
 * pourquoi dans la barre d'action. Les objets du mod ne se combinent pas autrement (pas de réparation entre eux).
 */
public final class AnvilListener implements Listener {
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepare(PrepareAnvilEvent event) {
		AnvilView view = event.getView();
		AnvilInventory inventory = event.getInventory();
		ItemStack tool = inventory.getFirstItem();
		ItemStack ingot = inventory.getSecondItem();
		if (tool == null || tool.isEmpty() || ingot == null || ingot.isEmpty()) {
			return;
		}
		ItemStack result;
		int cost;
		if (AbyssalArmor.isAbyssite(ingot) && AbyssalArmor.canUpgrade(tool)) {
			result = tool.asOne();
			AbyssalArmor.apply(result);
			cost = AbyssalArmor.LEVEL_COST;
		} else if (Items.is(ingot, EvoItem.EVOLITE_INGOT) && EvoliteUpgrade.canUpgrade(tool)) {
			result = tool.asOne();
			EvoliteUpgrade.apply(result);
			cost = EvoliteUpgrade.LEVEL_COST;
		} else {
			if (Items.of(ingot) != null || Items.of(tool) != null && Items.of(tool).kind() == null) {
				event.setResult(null);   // pas de réparation ni d'enchantement avec les matériaux du mod
			}
			explain(view, tool, ingot);
			return;
		}
		String name = view.getRenameText();
		if (name != null && !name.isBlank()) {
			result.editMeta(meta -> meta.customName(Component.text(name)));
		}
		event.setResult(result);
		view.setMaximumRepairCost(Math.max(40, cost + 1));
		view.setRepairCost(cost);
		view.setRepairItemCountCost(1);
	}

	private static void explain(AnvilView view, ItemStack left, ItemStack right) {
		if (!(view.getPlayer() instanceof Player player)) {
			return;
		}
		Component problem = null;
		if (AbyssalArmor.isAbyssite(left)) {
			problem = Lang.tr("message.evotool.abyssite.swap");
		} else if (AbyssalArmor.isAbyssite(right)) {
			problem = AbyssalArmor.problem(left);
		}
		if (problem != null) {
			player.sendActionBar(problem.color(NamedTextColor.RED));
		}
	}
}
