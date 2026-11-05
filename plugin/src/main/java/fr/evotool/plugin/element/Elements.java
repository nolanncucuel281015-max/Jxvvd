package fr.evotool.plugin.element;

import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.util.Keys;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

/** L'élément porté par un objet, et ce qui peut en recevoir un. */
public final class Elements {
	public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Elements() {
	}

	/** L'élément de cet objet (null s'il n'en a pas). */
	public static Element get(ItemStack stack) {
		if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
			return null;
		}
		String id = stack.getItemMeta().getPersistentDataContainer().get(Keys.ELEMENT, PersistentDataType.STRING);
		return id == null ? null : Element.byId(id);
	}

	/** La case d'armure de cet objet (null si ce n'est pas une pièce d'armure). */
	public static EquipmentSlot armorSlot(ItemStack stack) {
		if (stack == null || stack.getType().isAir()) {
			return null;
		}
		EquipmentSlot slot = stack.getType().getEquipmentSlot();
		if (stack.hasItemMeta() && stack.getItemMeta().hasEquippable()) {
			slot = stack.getItemMeta().getEquippable().getSlot();
		}
		return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET
				? slot : null;
	}

	/** Un livre (ou un grimoire) : le combineur en fait un grimoire de sorts. */
	public static boolean isBook(ItemStack stack) {
		return (stack.getType() == Material.BOOK && Items.of(stack) == null) || Items.is(stack, EvoItem.ELEMENTAL_GRIMOIRE);
	}

	/** Armures, armes et outils (un seul objet, qui s'use ou qui évolue) peuvent recevoir un élément. */
	public static boolean canCombine(ItemStack stack) {
		if (stack == null || stack.getType().isAir() || stack.getAmount() != 1) {
			return false;
		}
		return isBook(stack) || armorSlot(stack) != null || stack.getType().getMaxDurability() > 0
				|| (stack.hasItemMeta() && stack.getItemMeta() instanceof Damageable d && d.hasMaxDamage())
				|| Items.kindOf(stack) != null;
	}

	/** Cet objet peut-il recevoir cet élément ? (Un grimoire qui le connaît déjà, non.) */
	public static boolean canAccept(ItemStack stack, Element element) {
		if (!canCombine(stack) || element == null) {
			return false;
		}
		Grimoire grimoire = Grimoire.get(stack);
		return grimoire == null || !grimoire.elements().contains(element);
	}

	/** L'objet avec son nouvel élément ; un livre devient un grimoire, qui en apprend un de plus. */
	public static ItemStack combine(ItemStack equipment, Element element) {
		if (isBook(equipment)) {
			Grimoire grimoire = Grimoire.get(equipment);
			ItemStack book = Items.is(equipment, EvoItem.ELEMENTAL_GRIMOIRE) ? equipment.asOne() : Items.create(EvoItem.ELEMENTAL_GRIMOIRE);
			Grimoire next = grimoire == null ? new Grimoire(List.of(element), 0) : grimoire.withElement(element);
			book.editMeta(meta -> {
				next.write(meta);
				meta.setEnchantmentGlintOverride(true);
			});
			Lore.update(book);
			return book;
		}
		ItemStack result = equipment.asOne();
		result.editMeta(meta -> {
			meta.getPersistentDataContainer().set(Keys.ELEMENT, PersistentDataType.STRING, element.id());
			meta.setEnchantmentGlintOverride(true);
		});
		Lore.update(result);
		return result;
	}

	/** Un grimoire qui connaît ces éléments (catalogue, auto-test). */
	public static ItemStack grimoire(List<Element> elements, int selected) {
		ItemStack book = Items.create(EvoItem.ELEMENTAL_GRIMOIRE);
		book.editMeta(meta -> {
			new Grimoire(elements, selected).write(meta);
			meta.setEnchantmentGlintOverride(true);
		});
		Lore.update(book);
		return book;
	}

	public static String slotName(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> "head";
			case CHEST -> "chest";
			case LEGS -> "legs";
			case FEET -> "feet";
			default -> "mainhand";
		};
	}

	/** Clé du pouvoir propre à cette pièce d'armure (bottes de glace, plastron de feu…), null si elle n'en a pas. */
	public static String specialKey(Element element, EquipmentSlot slot) {
		boolean special = switch (element) {
			case ICE, WIND -> slot == EquipmentSlot.FEET;
			case FIRE -> slot == EquipmentSlot.CHEST;
			case LIGHTNING -> slot == EquipmentSlot.LEGS;
			case WATER -> slot == EquipmentSlot.CHEST || slot == EquipmentSlot.FEET;
			case EARTH -> slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
			case SHADOW, LIGHT -> slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST;
			case POISON -> slot == EquipmentSlot.CHEST;
		};
		return special ? "tooltip.evotool.element." + slotName(slot) + "." + element.id() : null;
	}

	/** Clé du pouvoir principal que donnerait cet élément à cet objet. */
	public static String abilityKey(Element element, ItemStack stack) {
		EquipmentSlot slot = armorSlot(stack);
		if (slot == null) {
			return "tooltip.evotool.element.weapon." + element.id();
		}
		String special = specialKey(element, slot);
		return special != null ? special : "tooltip.evotool.element.armor." + element.id();
	}

	/** La pièce d'armure portée dans cette case a-t-elle cet élément ? */
	public static boolean wears(Player player, EquipmentSlot slot, Element element) {
		return get(player.getInventory().getItem(slot)) == element;
	}

	/** Les éléments de l'armure portée (chacun une fois). */
	public static Set<Element> armorElements(Player player) {
		Set<Element> elements = EnumSet.noneOf(Element.class);
		for (EquipmentSlot slot : ARMOR) {
			Element element = get(player.getInventory().getItem(slot));
			if (element != null) {
				elements.add(element);
			}
		}
		return elements;
	}
}
