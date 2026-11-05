package fr.evotool.plugin.element;

import fr.evotool.plugin.util.Keys;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** Contenu d'un grimoire élémentaire : ses éléments (dans l'ordre où on les a ajoutés) et le sort choisi. */
public record Grimoire(List<Element> elements, int selected) {
	public Grimoire {
		elements = List.copyOf(elements);
	}

	/** Tous les sorts appris : quatre par élément. */
	public List<Spell> spells() {
		List<Spell> spells = new ArrayList<>();
		for (Element element : elements) {
			spells.addAll(Spell.of(element));
		}
		return spells;
	}

	/** Le sort choisi (null si le grimoire est vide). */
	public Spell selectedSpell() {
		List<Spell> spells = spells();
		return spells.isEmpty() ? null : spells.get(Math.floorMod(selected, spells.size()));
	}

	public Grimoire withElement(Element element) {
		if (elements.contains(element)) {
			return this;
		}
		List<Element> more = new ArrayList<>(elements);
		more.add(element);
		return new Grimoire(more, selected);
	}

	public Grimoire next() {
		int count = Math.max(1, spells().size());
		return new Grimoire(elements, Math.floorMod(selected + 1, count));
	}

	/** Le grimoire rangé sur cet objet (null s'il n'en a pas). */
	public static Grimoire get(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta()) {
			return null;
		}
		PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
		String ids = pdc.get(Keys.GRIMOIRE_ELEMENTS, PersistentDataType.STRING);
		if (ids == null) {
			return null;
		}
		List<Element> elements = new ArrayList<>();
		for (String id : ids.split(",")) {
			Element element = Element.byId(id);
			if (element != null) {
				elements.add(element);
			}
		}
		return new Grimoire(elements, pdc.getOrDefault(Keys.GRIMOIRE_SELECTED, PersistentDataType.INTEGER, 0));
	}

	public void write(ItemMeta meta) {
		List<String> ids = new ArrayList<>();
		for (Element element : elements) {
			ids.add(element.id());
		}
		meta.getPersistentDataContainer().set(Keys.GRIMOIRE_ELEMENTS, PersistentDataType.STRING, String.join(",", ids));
		meta.getPersistentDataContainer().set(Keys.GRIMOIRE_SELECTED, PersistentDataType.INTEGER, selected);
	}
}
