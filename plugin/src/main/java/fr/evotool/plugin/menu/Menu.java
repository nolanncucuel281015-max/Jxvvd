package fr.evotool.plugin.menu;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Un menu en coffre (les écrans du mod deviennent des menus : évolution, générateur de bouclier, combineur,
 * catalogue). Les cases « bouton » réagissent au clic ; les cases « libres » se remplissent comme un coffre.
 */
public class Menu implements InventoryHolder {
	protected final Inventory inventory;
	private final Map<Integer, Consumer<InventoryClickEvent>> buttons = new HashMap<>();
	private final java.util.Set<Integer> free = new java.util.HashSet<>();

	public Menu(int rows, Component title) {
		this.inventory = Bukkit.createInventory(this, rows * 9, title);
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public void button(int slot, ItemStack icon, Consumer<InventoryClickEvent> action) {
		inventory.setItem(slot, icon);
		buttons.put(slot, action);
	}

	public void freeSlot(int slot) {
		free.add(slot);
	}

	public boolean isFree(int slot) {
		return free.contains(slot);
	}

	public void open(Player player) {
		player.openInventory(inventory);
	}

	/** Le contenu a changé (case libre) : à redéfinir. */
	protected void changed() {
	}

	/** Le menu se ferme : à redéfinir (rendre les objets…). */
	protected void closed(Player player) {
	}

	/** Une icône : un objet avec un nom et des lignes, sans italique. */
	public static ItemStack icon(Material material, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(material);
		stack.editMeta(meta -> {
			meta.itemName(name.decoration(TextDecoration.ITALIC, false));
			if (lore != null && !lore.isEmpty()) {
				meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
			}
		});
		return stack;
	}

	public static ItemStack filler() {
		ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
		pane.editMeta(meta -> meta.setHideTooltip(true));
		return pane;
	}

	public void fill() {
		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) == null && !free.contains(i)) {
				inventory.setItem(i, filler());
			}
		}
	}

	/** L'écouteur commun à tous les menus. */
	public static final class Events implements Listener {
		@EventHandler
		public void onClick(InventoryClickEvent event) {
			if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
				return;
			}
			boolean top = event.getClickedInventory() == menu.inventory;
			if (!top) {
				// clic dans l'inventaire du joueur : un maj-clic enverrait l'objet n'importe où dans le menu
				if (event.isShiftClick()) {
					event.setCancelled(true);
					menu.shiftIn(event);
				}
				return;
			}
			int slot = event.getRawSlot();
			if (menu.isFree(slot)) {
				Bukkit.getScheduler().runTask(fr.evotool.plugin.EvoToolPlugin.instance(), menu::changed);
				return;
			}
			event.setCancelled(true);
			Consumer<InventoryClickEvent> action = menu.buttons.get(slot);
			if (action != null) {
				action.accept(event);
			}
		}

		@EventHandler
		public void onDrag(InventoryDragEvent event) {
			if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) {
				return;
			}
			for (int slot : event.getRawSlots()) {
				if (slot < menu.inventory.getSize() && !menu.isFree(slot)) {
					event.setCancelled(true);
					return;
				}
			}
			Bukkit.getScheduler().runTask(fr.evotool.plugin.EvoToolPlugin.instance(), menu::changed);
		}

		@EventHandler
		public void onClose(InventoryCloseEvent event) {
			if (event.getInventory().getHolder() instanceof Menu menu && event.getPlayer() instanceof Player player) {
				menu.closed(player);
			}
		}
	}

	/** Maj-clic depuis l'inventaire du joueur : dans la première case libre qui accepte l'objet. */
	protected void shiftIn(InventoryClickEvent event) {
		ItemStack moving = event.getCurrentItem();
		if (moving == null || moving.getType().isAir()) {
			return;
		}
		for (int slot : free) {
			ItemStack there = inventory.getItem(slot);
			if ((there == null || there.getType().isAir()) && accepts(slot, moving)) {
				inventory.setItem(slot, moving.clone());
				event.setCurrentItem(null);
				changed();
				return;
			}
		}
	}

	/** Cette case libre accepte-t-elle cet objet ? */
	protected boolean accepts(int slot, ItemStack stack) {
		return true;
	}
}
