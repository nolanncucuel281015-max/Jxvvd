package fr.evotool.plugin.evo;

import fr.evotool.plugin.util.Keys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Données d'évolution rangées sur l'objet (données persistantes evotool:level, xp, mode, flags, count).
 *
 * @param level niveau (0 à 100, 150 pour l'armure abyssale)
 * @param xp    XP dans le niveau actuel
 * @param mode  index du {@link EvoMode} choisi
 * @param flags pouvoirs activés (un bit par {@link Power}), tout est activé par défaut
 * @param count compteur affiché (blocs, monstres, cultures, dégâts)
 */
public record EvoData(int level, int xp, int mode, int flags, long count) {
	public static final EvoData DEFAULT = new EvoData(0, 0, 0, -1, 0L);

	public EvoData withProgress(int newLevel, int newXp, long newCount) {
		return new EvoData(newLevel, newXp, mode, flags, newCount);
	}

	public EvoData withSettings(int newMode, int newFlags) {
		return new EvoData(level, xp, newMode, newFlags, count);
	}

	public static EvoData get(ItemStack stack) {
		if (stack == null || !stack.hasItemMeta()) {
			return DEFAULT;
		}
		return read(stack.getItemMeta().getPersistentDataContainer());
	}

	public static EvoData read(PersistentDataContainer pdc) {
		return new EvoData(
				pdc.getOrDefault(Keys.LEVEL, PersistentDataType.INTEGER, 0),
				pdc.getOrDefault(Keys.XP, PersistentDataType.INTEGER, 0),
				pdc.getOrDefault(Keys.MODE, PersistentDataType.INTEGER, 0),
				pdc.getOrDefault(Keys.FLAGS, PersistentDataType.INTEGER, -1),
				pdc.getOrDefault(Keys.COUNT, PersistentDataType.LONG, 0L));
	}

	public void write(ItemMeta meta) {
		PersistentDataContainer pdc = meta.getPersistentDataContainer();
		pdc.set(Keys.LEVEL, PersistentDataType.INTEGER, level);
		pdc.set(Keys.XP, PersistentDataType.INTEGER, xp);
		pdc.set(Keys.MODE, PersistentDataType.INTEGER, mode);
		pdc.set(Keys.FLAGS, PersistentDataType.INTEGER, flags);
		pdc.set(Keys.COUNT, PersistentDataType.LONG, count);
	}

	public void write(ItemStack stack) {
		stack.editMeta(this::write);
	}
}
