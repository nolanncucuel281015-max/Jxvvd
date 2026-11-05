package fr.evotool.plugin.upgrade;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Arc Évolutif niveau 100 amélioré à l'évolite : clic droit = l'arc du joueur apparaît en géant devant lui,
 * bandé, et tire une flèche toutes les demi-secondes droit sur les monstres proches.
 */
public final class CelestialBow {
	private static final int ARROW_COUNT = 20;
	private static final int ARROW_INTERVAL = 10;
	private static final int APPEAR_TICKS = 8;
	private static final int VOLLEY_TICKS = APPEAR_TICKS + ARROW_COUNT * ARROW_INTERVAL;
	public static final int COOLDOWN = VOLLEY_TICKS + 40;
	private static final double RANGE = 48.0;
	private static final float ARROW_SPEED = 3.0F;
	private static final int ARROW_LIFETIME = 100;
	private static final float GIANT_SCALE = 6.0F;
	private static final double DISTANCE_IN_FRONT = 4.5;
	/** Tourné de 45° pour que la corde soit verticale. */
	private static final Quaternionf ROTATION = new Quaternionf().rotationZ((float) Math.toRadians(-45));

	private static final class Volley {
		final World world;
		final Player player;
		final ItemStack bow;
		final int startTick;
		final ItemDisplay giant;
		int fired;

		Volley(Player player, ItemStack bow, int startTick, ItemDisplay giant) {
			this.world = player.getWorld();
			this.player = player;
			this.bow = bow;
			this.startTick = startTick;
			this.giant = giant;
		}
	}

	private record Flying(Arrow arrow, int spawnTick) {
	}

	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static final List<Flying> ARROWS = new ArrayList<>();

	private CelestialBow() {
	}

	public static boolean isReady(ItemStack stack) {
		return Items.kindOf(stack) == EvoKind.BOW && EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.CELESTIAL_BOW)
				&& EvoData.get(stack).level() >= Progression.MAX_LEVEL;
	}

	public static void start(Player player, ItemStack bow) {
		ItemStack shown = bow.asOne();
		shown.editMeta(meta -> {
			CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
			cmd.setFloats(List.of((float) Progression.stage(EvoData.get(bow).level())));
			cmd.setFlags(List.of(true));   // forcer le modèle bandé (voir items/evolving_bow.json)
			meta.setCustomModelDataComponent(cmd);
			meta.setEnchantmentGlintOverride(false);
		});
		ItemDisplay giant = Giant.spawn(player.getWorld(), shown, ItemDisplay.ItemDisplayTransform.FIXED, giantPosition(player),
				player.getLocation().getYaw(), ROTATION, GIANT_SCALE, APPEAR_TICKS);
		Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.5F, 1.2F);
		VOLLEYS.add(new Volley(player, bow, Bukkit.getCurrentTick(), giant));
	}

	private static Vector giantPosition(Player player) {
		return Fx.eye(player).add(Fx.look(player).multiply(DISTANCE_IN_FRONT)).add(new Vector(0, 0.5, 0));
	}

	public static void tick(int tick) {
		for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext(); ) {
			Volley volley = it.next();
			int step = tick - volley.startTick;
			if (step >= VOLLEY_TICKS || !volley.player.isOnline() || volley.player.getWorld() != volley.world) {
				Giant.remove(volley.giant, GIANT_SCALE);
				it.remove();
				continue;
			}
			Vector pos = giantPosition(volley.player);
			volley.giant.teleport(new Location(volley.world, pos.getX(), pos.getY(), pos.getZ(), volley.player.getLocation().getYaw(), 0.0F));
			if (step >= APPEAR_TICKS && (step - APPEAR_TICKS) % ARROW_INTERVAL == 0 && volley.fired < ARROW_COUNT) {
				fire(volley, pos, tick);
				volley.fired++;
			}
		}
		ARROWS.removeIf(f -> {
			if (!f.arrow().isValid()) {
				return true;
			}
			if (tick - f.spawnTick() > ARROW_LIFETIME) {
				f.arrow().remove();
				return true;
			}
			return false;
		});
	}

	private static void fire(Volley volley, Vector origin, int tick) {
		Player player = volley.player;
		List<LivingEntity> targets = new ArrayList<>();
		for (Entity e : volley.world.getNearbyEntities(player.getBoundingBox().clone().expand(RANGE),
				e -> e instanceof Enemy && e instanceof LivingEntity l && !l.isDead() && e.getLocation().distanceSquared(player.getLocation()) <= RANGE * RANGE)) {
			targets.add((LivingEntity) e);
		}
		targets.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())));
		Vector direction;
		if (targets.isEmpty()) {
			direction = Fx.look(player);
		} else {
			LivingEntity target = targets.get(volley.fired % Math.min(targets.size(), 3));
			Vector aim = Fx.center(target);
			double travel = aim.distance(origin) / ARROW_SPEED;
			direction = aim.add(target.getVelocity().multiply(travel)).subtract(origin);
		}
		Arrow arrow = volley.world.spawnArrow(Fx.loc(volley.world, origin), direction, ARROW_SPEED, 0.0F);
		arrow.setShooter(player);
		arrow.setWeapon(volley.bow.clone());
		arrow.setGravity(false);
		arrow.setCritical(true);
		arrow.setDamage(6.0);
		arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
		ARROWS.add(new Flying(arrow, tick));
		Fx.particle(volley.world, Particle.END_ROD, origin, 12, 0.3, 0.3, 0.3, 0.08);
		Fx.sound(volley.world, origin, Sound.ENTITY_ARROW_SHOOT, 1.5F, 0.8F);
	}

	public static void clear() {
		VOLLEYS.forEach(v -> v.giant.remove());
		VOLLEYS.clear();
		ARROWS.clear();
	}
}
