package fr.evotool.plugin.spider;

import fr.evotool.plugin.util.Keys;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;

/** Les pouvoirs de toile du gant de Spider-Man : accroupi + clic droit lance celui choisi, la touche G en change. */
public enum WebPower {
	/** Tir de toile : englue ce qu'il touche. */
	SHOT("shot", 20),
	/** Traction : une créature visée est tirée vers soi ; un bloc visé, c'est nous qui filons vers lui. */
	PULL("pull", 15),
	/** Bombe de toile : elle éclate là où l'on vise, toiles partout autour, ceux qui sont pris sont englués. */
	BOMB("bomb", 60),
	/** Mur de toile : un mur de toiles (5 x 3) se dresse devant soi, les monstres ne passent plus. */
	WALL("wall", 100);

	private final String id;
	private final int cooldown;

	WebPower(String id, int cooldown) {
		this.id = id;
		this.cooldown = cooldown;
	}

	public String translationKey() {
		return "web_power.evotool." + id;
	}

	/** Recharge après usage (ticks). */
	public int cooldown() {
		return cooldown;
	}

	public WebPower next() {
		return values()[(ordinal() + 1) % values().length];
	}

	/** Le pouvoir choisi sur ce gant (tir de toile par défaut). */
	public static WebPower of(ItemStack stack) {
		Integer index = stack == null || !stack.hasItemMeta() ? null
				: stack.getItemMeta().getPersistentDataContainer().get(Keys.WEB_POWER, PersistentDataType.INTEGER);
		return index == null || index < 0 || index >= values().length ? SHOT : values()[index];
	}
}
