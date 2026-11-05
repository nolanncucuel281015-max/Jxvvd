package fr.evotool.plugin.upgrade;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Keys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Plastron amélioré : les ailes de l'infini. Le joueur vole (double saut, comme en créatif), plus vite qu'en créatif,
 * sans dégâts de chute. Les ailes (les vrais modèles 3D du mod) sont des objets d'affichage portés sur le dos du
 * joueur : repliées au sol, elles se déploient et battent en vol ; le symbole de l'infini brille entre les deux.
 */
public final class InfinityWings implements Listener {
	/** Vitesse de vol avec les ailes (en vitesse Bukkit : créatif = 0,1 ; le mod : 0,11 interne = 0,22 ici). */
	public static final float SPEED = 0.22F;
	public static final float DEFAULT_SPEED = 0.1F;
	private static final org.bukkit.NamespacedKey TAG = Keys.key("infinity_wings");

	/** Autres sources de vol (vol de feu du plastron élémentaire…) : vitesse, ou null si elles ne volent pas. */
	private static final List<java.util.function.Function<Player, Float>> OTHER_FLIGHT = new ArrayList<>();

	private static final float WING_ROOT_X = 0.6F / 16;
	private static final float HINGE_X = 0.1F;
	private static final float HINGE_Y = -0.16F;
	private static final float HINGE_Z = -0.2F;
	private static final float EMBLEM_SCALE = 0.44F;
	/** Le haut de la tête (où se pose un passager) est à 1,8 ; le cou du modèle du joueur, vers 1,4. */
	private static final float NECK_BELOW_TOP = 0.4F;
	private static final float MODEL_SCALE = 0.9375F;

	private static final class Wings {
		final ItemDisplay left;
		final ItemDisplay right;
		final ItemDisplay emblem;
		final boolean abyss;
		float open;

		Wings(ItemDisplay left, ItemDisplay right, ItemDisplay emblem, boolean abyss) {
			this.left = left;
			this.right = right;
			this.emblem = emblem;
			this.abyss = abyss;
		}

		void remove() {
			left.remove();
			right.remove();
			emblem.remove();
		}

		boolean valid() {
			return left.isValid() && right.isValid() && emblem.isValid();
		}
	}

	private static final Map<UUID, Wings> WINGS = new HashMap<>();
	/** Le joueur ne doit pas montrer ses ailes (sous terre…). */
	private static final List<Predicate<Player>> HIDDEN = new ArrayList<>();

	public static void enable() {
		EvoToolPlugin.listen(new InfinityWings());
		EvoToolPlugin.ticker(InfinityWings::tick);
	}

	public static void otherFlight(java.util.function.Function<Player, Float> source) {
		OTHER_FLIGHT.add(source);
	}

	public static void hideWhen(Predicate<Player> condition) {
		HIDDEN.add(condition);
	}

	public static boolean isWingChestplate(ItemStack stack) {
		return EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.INFINITY_WINGS);
	}

	public static boolean hasWings(Player player) {
		return isWingChestplate(player.getInventory().getChestplate());
	}

	private static boolean survivalLike(Player player) {
		return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
	}

	private static void tick(int tick) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (tick % 5 == 0) {
				abilities(player);
			}
			visuals(player, tick);
		}
		for (Iterator<Map.Entry<UUID, Wings>> it = WINGS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Wings> e = it.next();
			Player player = Bukkit.getPlayer(e.getKey());
			if (player == null || !player.isOnline()) {
				e.getValue().remove();
				it.remove();
			}
		}
	}

	/** Donne (ou retire) le vol rapide selon que le joueur porte les ailes. */
	private static void abilities(Player player) {
		if (!survivalLike(player) || Burrow.isBurrowing(player)) {
			return;
		}
		boolean tagged = player.getPersistentDataContainer().has(TAG);
		if (hasWings(player)) {
			if (!tagged) {
				Fx.particle(player.getWorld(), Particle.END_ROD, Fx.feet(player).add(new org.bukkit.util.Vector(0, 1.3, 0)), 30, 0.5, 0.5, 0.5, 0.08);
				Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
			}
			if (!player.getAllowFlight() || player.getFlySpeed() != SPEED || !tagged) {
				restore(player);
			}
		} else if (tagged) {
			restore(player);
		} else {
			for (var source : OTHER_FLIGHT) {
				Float speed = source.apply(player);
				if (speed != null && (!player.getAllowFlight() || player.getFlySpeed() != speed)) {
					restore(player);
					break;
				}
			}
		}
	}

	/** Remet le vol du joueur dans l'état normal : celui des ailes s'il les porte, sinon celui de son mode de jeu. */
	public static void restore(Player player) {
		if (!survivalLike(player)) {
			if (player.getGameMode() != GameMode.SPECTATOR) {
				player.setFlySpeed(DEFAULT_SPEED);
			}
			player.getPersistentDataContainer().remove(TAG);
			return;
		}
		if (hasWings(player)) {
			player.setAllowFlight(true);
			player.setFlySpeed(SPEED);
			player.getPersistentDataContainer().set(TAG, PersistentDataType.BYTE, (byte) 1);
			return;
		}
		player.getPersistentDataContainer().remove(TAG);
		for (var source : OTHER_FLIGHT) {
			Float speed = source.apply(player);
			if (speed != null) {
				player.setAllowFlight(true);
				player.setFlySpeed(speed);
				return;
			}
		}
		player.setFlying(false);
		player.setAllowFlight(false);
		player.setFlySpeed(DEFAULT_SPEED);
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onFall(EntityDamageEvent event) {
		if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player && hasWings(player)) {
			event.setCancelled(true);   // des ailes : on plane jusqu'au sol
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Wings wings = WINGS.remove(event.getPlayer().getUniqueId());
		if (wings != null) {
			wings.remove();
		}
	}

	// ------------------------------------------------------------------ les ailes, sur le dos

	private static boolean shown(Player player) {
		if (!hasWings(player) || player.isDead() || player.getGameMode() == GameMode.SPECTATOR || player.isInvisible()
				|| player.isInsideVehicle()) {
			return false;
		}
		for (Predicate<Player> hidden : HIDDEN) {
			if (hidden.test(player)) {
				return false;
			}
		}
		return true;
	}

	private static void visuals(Player player, int tick) {
		Wings wings = WINGS.get(player.getUniqueId());
		boolean show = shown(player);
		boolean abyss = show && Progression.isAbyssal(player.getInventory().getChestplate());
		if (wings != null && (!show || !wings.valid() || wings.abyss != abyss || !player.getPassengers().contains(wings.left))) {
			wings.remove();
			WINGS.remove(player.getUniqueId());
			wings = null;
		}
		if (!show) {
			return;
		}
		if (wings == null) {
			String suffix = abyss ? "_abyss" : "";
			wings = new Wings(part(player, "wings/infinity_wing" + suffix), part(player, "wings/infinity_wing_right" + suffix),
					part(player, "wings/infinity_emblem" + suffix), abyss);
			WINGS.put(player.getUniqueId(), wings);
		}
		boolean flying = player.isFlying() || player.isGliding();
		wings.open += ((flying ? 1.0F : 0.0F) - wings.open) * 0.26F;
		pose(player, wings, tick);
	}

	private static ItemDisplay part(Player player, String model) {
		ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
			d.setItemStack(Items.display(model));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setPersistent(false);
			d.getPersistentDataContainer().set(Keys.TEMPORARY, PersistentDataType.BYTE, (byte) 1);
			d.setBillboard(Display.Billboard.FIXED);
			d.setInterpolationDuration(2);
			d.setTeleportDuration(0);
			d.setViewRange(0.6F);
			d.setShadowRadius(0);
			d.setRotation(0, 0);
		});
		player.addPassenger(display);
		return display;
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	/** Les mêmes gestes que dans le mod (InfinityWingsFeature), tournés comme le buste du joueur. */
	private static void pose(Player player, Wings wings, int tick) {
		float open = wings.open;
		float t = tick;
		float flap = open * 24.0F * (float) Math.sin(t * 0.33);
		float breathe = (1.0F - open) * 2.5F * (float) Math.sin(t * 0.08);
		float spread = lerp(open, -35.0F, 8.0F) + flap + breathe;
		float sweep = lerp(open, 78.0F, 20.0F) - flap * 0.25F;
		float tilt = lerp(open, 22.0F, 0.0F);
		// repère du buste : +y en haut, +z devant, +x à gauche ; tourné comme le corps du joueur
		Matrix4f body = new Matrix4f().rotateY(rad(-player.getBodyYaw()))
				.translate(0, -NECK_BELOW_TOP - (player.isSneaking() ? 0.25F : 0.0F), 0)
				.scale(MODEL_SCALE);
		if (player.isSneaking()) {
			body.rotateX(rad(28.6));
		}
		for (int side = -1; side <= 1; side += 2) {
			Matrix4f m = new Matrix4f(body).translate(side * HINGE_X, HINGE_Y, HINGE_Z)
					.rotate(new Quaternionf().rotationX(rad(tilt)).rotateY(rad(side * sweep)).rotateZ(rad(side * spread)))
					.translate(-side * WING_ROOT_X, 0.0F, 0.0F)
					.rotateY((float) Math.PI);   // l'objet d'affichage tourne son modèle d'un demi-tour : on le compense
			apply(side > 0 ? wings.left : wings.right, m);
		}
		float pulse = EMBLEM_SCALE * (1.0F + 0.06F * (float) Math.sin(t * 0.2));
		Matrix4f emblem = new Matrix4f(body).translate(0.0F, HINGE_Y + 0.02F, HINGE_Z - 0.06F)
				.rotate(new Quaternionf().rotationX(rad(tilt)).rotateZ(rad(6 * Math.sin(t * 0.05))))
				.scale(pulse).rotateY((float) Math.PI);
		apply(wings.emblem, emblem);
	}

	private static void apply(ItemDisplay display, Matrix4f matrix) {
		display.setInterpolationDelay(0);
		display.setInterpolationDuration(2);
		display.setTransformationMatrix(matrix);
	}

	public static void clear() {
		WINGS.values().forEach(Wings::remove);
		WINGS.clear();
	}

	static boolean isTemporaryWing(org.bukkit.entity.Entity e) {
		return Giant.isTemporary(e);
	}
}
