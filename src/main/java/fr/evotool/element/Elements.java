package fr.evotool.element;

import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import net.minecraft.item.Items;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;

/** L'élément porté par un objet, et ce qui peut en recevoir un. */
public final class Elements {
	public static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	private Elements() {
	}

	/** L'élément de cet objet (null s'il n'en a pas). */
	public static Element get(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(ModComponents.ELEMENT);
	}

	/** La case d'armure de cet objet (null si ce n'est pas une pièce d'armure). */
	public static EquipmentSlot armorSlot(ItemStack stack) {
		EquippableComponent equippable = stack.get(DataComponentTypes.EQUIPPABLE);
		if (equippable == null) {
			return null;
		}
		EquipmentSlot slot = equippable.slot();
		return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR ? slot : null;
	}

	/** Un livre (ou un grimoire) : le combineur en fait un grimoire de sorts. */
	public static boolean isBook(ItemStack stack) {
		return stack.isOf(Items.BOOK) || stack.isOf(ModItems.ELEMENTAL_GRIMOIRE);
	}

	/**
	 * Armures, armes et outils (un seul objet, qui s'use ou qui évolue) peuvent recevoir un élément ; un livre
	 * devient un grimoire, et un grimoire apprend d'autres éléments.
	 */
	public static boolean canCombine(ItemStack stack) {
		if (stack.isEmpty() || stack.getCount() != 1) {
			return false;
		}
		return isBook(stack) || armorSlot(stack) != null || stack.isDamageable() || EvoProgression.kindOf(stack) != null;
	}

	/** Cet objet peut-il recevoir cet élément ? (Un grimoire qui le connaît déjà, non.) */
	public static boolean canAccept(ItemStack stack, Element element) {
		if (!canCombine(stack) || element == null) {
			return false;
		}
		Grimoire grimoire = stack.get(ModComponents.GRIMOIRE);
		return grimoire == null || !grimoire.elements().contains(element);
	}

	/** L'objet avec son nouvel élément (il remplace l'ancien) ; un livre devient un grimoire, qui en apprend un de plus. */
	public static ItemStack combine(ItemStack equipment, Element element) {
		if (isBook(equipment)) {
			Grimoire grimoire = equipment.get(ModComponents.GRIMOIRE);
			ItemStack book = equipment.isOf(ModItems.ELEMENTAL_GRIMOIRE) ? equipment.copyWithCount(1)
					: new ItemStack(ModItems.ELEMENTAL_GRIMOIRE);
			book.set(ModComponents.GRIMOIRE, grimoire == null ? new Grimoire(java.util.List.of(element), 0) : grimoire.withElement(element));
			book.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
			return book;
		}
		ItemStack result = equipment.copyWithCount(1);
		result.set(ModComponents.ELEMENT, element);
		result.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
		return result;
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
		return special ? "tooltip.evotool.element." + slot.getName() + "." + element.id() : null;
	}

	/** Clé du pouvoir principal que donnerait cet élément à cet objet (armure : celui de la pièce, sinon l'arme). */
	public static String abilityKey(Element element, ItemStack stack) {
		EquipmentSlot slot = armorSlot(stack);
		if (slot == null) {
			return "tooltip.evotool.element.weapon." + element.id();
		}
		String special = specialKey(element, slot);
		return special != null ? special : "tooltip.evotool.element.armor." + element.id();
	}

	/** La pièce d'armure portée dans cette case a-t-elle cet élément ? */
	public static boolean wears(PlayerEntity player, EquipmentSlot slot, Element element) {
		return get(player.getEquippedStack(slot)) == element;
	}

	/** Les éléments de l'armure portée (chacun une fois). */
	public static Set<Element> armorElements(PlayerEntity player) {
		Set<Element> elements = EnumSet.noneOf(Element.class);
		for (EquipmentSlot slot : ARMOR) {
			Element element = get(player.getEquippedStack(slot));
			if (element != null) {
				elements.add(element);
			}
		}
		return elements;
	}
}
