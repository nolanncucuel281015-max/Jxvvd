package fr.evotool.plugin.util;

import fr.evotool.plugin.EvoToolPlugin;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Objets d'affichage du plugin (objets géants, morceaux du boss, tentacules, dômes…) : jamais sauvegardés,
 * marqués « temporaires » pour être nettoyés au redémarrage.
 */
public final class Giant {
	private Giant() {
	}

	/** Un objet d'affichage, immobile, pleine lumière. */
	public static ItemDisplay create(World world, Vector pos, ItemStack stack, ItemDisplay.ItemDisplayTransform context, float yaw,
			Transformation transformation) {
		Location at = new Location(world, pos.getX(), pos.getY(), pos.getZ(), yaw, 0.0F);
		return world.spawn(at, ItemDisplay.class, display -> {
			display.setItemStack(stack);
			display.setItemDisplayTransform(context);
			display.setBillboard(Display.Billboard.FIXED);
			display.setBrightness(new Display.Brightness(15, 15));
			display.setViewRange(4.0F);
			display.setTeleportDuration(3);
			display.setTransformation(transformation);
			display.setPersistent(false);
			display.getPersistentDataContainer().set(Keys.TEMPORARY, PersistentDataType.BYTE, (byte) 1);
		});
	}

	public static Transformation transformation(Quaternionf rotation, float scale) {
		return new Transformation(new Vector3f(), new Quaternionf(rotation), new Vector3f(scale, scale, scale), new Quaternionf());
	}

	/**
	 * Fait apparaître l'objet en géant : il grandit de 0,2 à {@code scale} en {@code growTicks} ticks (avec un « pfff »).
	 */
	public static ItemDisplay spawn(World world, ItemStack stack, ItemDisplay.ItemDisplayTransform context, Vector pos, float yaw,
			Quaternionf rotation, float scale, int growTicks) {
		ItemDisplay giant = create(world, pos, stack, context, yaw, transformation(rotation, 0.2F));
		pose(giant, transformation(rotation, scale), growTicks);
		poof(world, pos, scale);
		return giant;
	}

	/** Change la pose avec une animation (le tick suivant, pour que le client voie le départ). */
	public static void pose(Display display, Transformation transformation, int ticks) {
		EvoToolPlugin.later(1, () -> {
			if (display.isValid()) {
				display.setInterpolationDelay(0);
				display.setInterpolationDuration(ticks);
				display.setTransformation(transformation);
			}
		});
	}

	/** Change la pose tout de suite (déjà affiché depuis au moins un tick). */
	public static void poseNow(Display display, Transformation transformation, int ticks) {
		display.setInterpolationDelay(0);
		display.setInterpolationDuration(ticks);
		display.setTransformation(transformation);
	}

	public static void remove(ItemDisplay giant, float scale) {
		if (giant.isValid()) {
			poof(giant.getWorld(), giant.getLocation().toVector(), scale);
			giant.remove();
		}
	}

	public static void poof(World world, Vector pos, float scale) {
		double spread = Math.max(1.0, scale / 4.0);
		Fx.particle(world, Particle.POOF, pos, 60, spread, spread, spread, 0.05);
		Fx.particle(world, Particle.CLOUD, pos, 30, spread, spread, spread, 0.02);
		Fx.sound(world, pos, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 1.0F);
	}

	/** Entité du plugin à retirer au démarrage (restes d'un arrêt brutal). */
	public static boolean isTemporary(org.bukkit.entity.Entity entity) {
		return entity.getPersistentDataContainer().has(Keys.TEMPORARY);
	}
}
