package fr.evotool.plugin.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Petits outils communes à tous les pouvoirs : particules, sons, ennemis, dégâts, vecteurs. */
public final class Fx {
	/** Évite que les dégâts des pouvoirs relancent les passifs en boucle. */
	private static int bonusDepth;

	private Fx() {
	}

	public static void particle(World world, Particle particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed) {
		world.spawnParticle(particle, x, y, z, count, dx, dy, dz, speed, null, true);
	}

	public static <T> void particle(World world, Particle particle, double x, double y, double z, int count, double dx, double dy,
			double dz, double speed, T data) {
		world.spawnParticle(particle, x, y, z, count, dx, dy, dz, speed, data, true);
	}

	public static void particle(World world, Particle particle, Vector at, int count, double dx, double dy, double dz, double speed) {
		particle(world, particle, at.getX(), at.getY(), at.getZ(), count, dx, dy, dz, speed);
	}

	public static void sound(World world, double x, double y, double z, Sound sound, float volume, float pitch) {
		world.playSound(new Location(world, x, y, z), sound, SoundCategory.PLAYERS, volume, pitch);
	}

	public static void sound(World world, Vector at, Sound sound, float volume, float pitch) {
		sound(world, at.getX(), at.getY(), at.getZ(), sound, volume, pitch);
	}

	public static void sound(Entity entity, Sound sound, float volume, float pitch) {
		Location l = entity.getLocation();
		sound(entity.getWorld(), l.getX(), l.getY(), l.getZ(), sound, volume, pitch);
	}

	/** Un ennemi qu'on peut toucher avec les pouvoirs (ni joueur, ni animal apprivoisé, ni villageois, ni golem construit). */
	public static boolean isFoe(Player player, Entity e) {
		return e instanceof LivingEntity living && living != player && !living.isDead() && living.isValid()
				&& !(e instanceof Player) && !(e instanceof ArmorStand) && !(e instanceof AbstractVillager)
				&& !(e instanceof Tameable tame && tame.isTamed())
				&& !(e instanceof IronGolem golem && golem.isPlayerCreated());
	}

	public static List<LivingEntity> foes(Player player, BoundingBox box) {
		List<LivingEntity> list = new ArrayList<>();
		for (Entity e : player.getWorld().getNearbyEntities(box, e -> isFoe(player, e))) {
			list.add((LivingEntity) e);
		}
		return list;
	}

	/** Tous les vivants (sauf le joueur) dans la boîte. */
	public static List<LivingEntity> living(World world, BoundingBox box, Entity except) {
		List<LivingEntity> list = new ArrayList<>();
		for (Entity e : world.getNearbyEntities(box, e -> e instanceof LivingEntity l && e != except && !l.isDead())) {
			list.add((LivingEntity) e);
		}
		return list;
	}

	/** Dégâts portés par le joueur (sans relancer les passifs des armes). */
	public static void hurt(Player player, LivingEntity target, double amount) {
		bonusDepth++;
		try {
			target.damage(amount, player);
		} finally {
			bonusDepth--;
		}
	}

	/** Dégâts magiques indirects (sorts, projectiles magiques). */
	public static void magic(Player player, LivingEntity target, double amount) {
		bonusDepth++;
		try {
			target.damage(amount, DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(player).withDirectEntity(player).build());
		} finally {
			bonusDepth--;
		}
	}

	/** Fait quelque chose (des dégâts) sans relancer les passifs. */
	public static void applyingBonusRun(Runnable action) {
		bonusDepth++;
		try {
			action.run();
		} finally {
			bonusDepth--;
		}
	}

	/** Vrai pendant les dégâts d'un pouvoir (les passifs ne se déclenchent pas). */
	public static boolean applyingBonus() {
		return bonusDepth > 0;
	}

	public static Vector feet(Entity e) {
		return e.getLocation().toVector();
	}

	public static Vector center(Entity e) {
		return e.getBoundingBox().getCenter();
	}

	public static Vector eye(LivingEntity e) {
		return e.getEyeLocation().toVector();
	}

	public static Vector look(LivingEntity e) {
		return e.getEyeLocation().getDirection();
	}

	/** Direction du regard à l'horizontale (jamais nulle). */
	public static Vector flatLook(Entity e) {
		Vector look = e.getLocation().getDirection();
		Vector flat = new Vector(look.getX(), 0, look.getZ());
		if (flat.lengthSquared() < 1.0E-4) {
			double yaw = Math.toRadians(e.getLocation().getYaw());
			return new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
		}
		return flat.normalize();
	}

	/** Direction d'un angle de lacet (degrés, comme Minecraft : 0 = sud). */
	public static Vector fromYaw(float yaw) {
		double r = Math.toRadians(yaw);
		return new Vector(-Math.sin(r), 0, Math.cos(r));
	}

	public static double flatDistance(Entity a, Entity b) {
		double dx = a.getLocation().getX() - b.getLocation().getX();
		double dz = a.getLocation().getZ() - b.getLocation().getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	public static double width(Entity e) {
		return e.getBoundingBox().getWidthX();
	}

	public static void push(Entity e, double x, double y, double z) {
		e.setVelocity(e.getVelocity().add(new Vector(x, y, z)));
	}

	public static BoundingBox box(Vector at, double radius) {
		return BoundingBox.of(at, radius, radius, radius);
	}

	/** Les quatre cases d'armure. */
	public static Location loc(World world, Vector v) {
		return new Location(world, v.getX(), v.getY(), v.getZ());
	}

	/** Lacet (degrés) pour regarder de {@code from} vers {@code to}. */
	public static float yawTo(Vector from, Vector to) {
		return (float) Math.toDegrees(Math.atan2(-(to.getX() - from.getX()), to.getZ() - from.getZ()));
	}
}
