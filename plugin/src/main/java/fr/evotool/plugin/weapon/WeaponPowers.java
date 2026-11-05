package fr.evotool.plugin.weapon;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Pouvoirs des armes moddées : un passif à chaque coup porté, et une attaque spéciale au clic droit. Les attaques
 * ne touchent ni les joueurs, ni les animaux apprivoisés, ni les villageois, ni les golems construits par les joueurs.
 */
public final class WeaponPowers {
	private static final double SCYTHE_LIFESTEAL = 0.15;
	private static final double CLEAVE_RATIO = 0.4;
	private static final double PIERCE_RATIO = 0.7;
	private static final int BLAST_INTERVAL = 10;

	/** Multiplicateur de l'attaque spéciale en cours (niveau d'évolution, amélioration à l'évolite). */
	private static double power = 1.0;
	private static final Map<UUID, Integer> LAST_BLAST = new HashMap<>();

	/** Projectile en vol : vague de l'espadon, éclat du sceptre, détonations de la lame. */
	private static final class Shot {
		final World world;
		final Player player;
		final WeaponKind kind;
		final Vector dir;
		final double speed;
		final double range;
		final double power;
		final Set<LivingEntity> hit = new HashSet<>();
		Vector pos;
		double travelled;

		Shot(Player player, WeaponKind kind, Vector pos, Vector dir, double speed, double range) {
			this.world = player.getWorld();
			this.player = player;
			this.kind = kind;
			this.pos = pos;
			this.dir = dir;
			this.speed = speed;
			this.range = range;
			this.power = WeaponPowers.power;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	private WeaponPowers() {
	}

	/** Dégâts d'une attaque spéciale : x0,75 au niveau 0, x1,5 au niveau 100, x1,3 de plus avec l'évolite. */
	public static double powerOf(ItemStack stack) {
		double p = 0.75 * (1.0 + EvoData.get(stack).level() / 100.0);
		return EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED) ? p * 1.3 : p;
	}

	/** Temps de recharge de l'attaque spéciale : 40 % plus court au niveau 100, encore 30 % de moins amélioré. */
	public static int cooldown(WeaponKind kind, ItemStack stack) {
		double c = kind.cooldown() * (1.0 - EvoData.get(stack).level() / 250.0);
		if (EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED)) {
			c *= 0.7;
		}
		return Math.max(6, (int) Math.round(c));
	}

	private static void strike(Player player, LivingEntity target, double amount) {
		Fx.hurt(player, target, amount * power);
	}

	// ------------------------------------------------------------------ passifs (chaque coup à l'arme)

	/** Après un coup de l'arme (appelé par l'écouteur de combat). */
	public static void afterHit(Player player, WeaponKind kind, LivingEntity entity, double damage) {
		World world = entity.getWorld();
		Vector c = Fx.center(entity);
		switch (kind) {
			case SCYTHE -> {
				player.heal(Math.min(damage * SCYTHE_LIFESTEAL, player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
						- player.getHealth()));
				Fx.particle(world, Particle.WITCH, c, 6, 0.3, 0.3, 0.3, 0.02);
			}
			case KATANA -> {
				entity.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
				Fx.particle(world, Particle.CRIMSON_SPORE, c, 10, 0.3, 0.4, 0.3, 0.02);
			}
			case WARHAMMER -> {
				Fx.push(entity, 0, 0.45, 0);
				entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
				Location l = entity.getLocation();
				Fx.particle(world, Particle.CLOUD, l.getX(), l.getY() + 0.1, l.getZ(), 8, 0.4, 0.05, 0.4, 0.05);
			}
			case GREATSWORD -> {
				for (LivingEntity other : Fx.foes(player, entity.getBoundingBox().clone().expand(3.0, 1.0, 3.0))) {
					if (other != entity) {
						Fx.hurt(player, other, damage * CLEAVE_RATIO);
						Fx.particle(world, Particle.SWEEP_ATTACK, Fx.center(other), 1, 0, 0, 0, 0);
					}
				}
			}
			case DAGGER -> {
				Vector facing = Fx.flatLook(entity);
				Vector toTarget = new Vector(entity.getLocation().getX() - player.getLocation().getX(), 0,
						entity.getLocation().getZ() - player.getLocation().getZ());
				if (!entity.isDead() && toTarget.lengthSquared() > 1.0E-4 && facing.dot(toTarget.normalize()) > 0.3) {
					// coup dans le dos : les dégâts sont doublés
					entity.setNoDamageTicks(0);
					Fx.hurt(player, entity, damage);
					Fx.particle(world, Particle.CRIT, c, 20, 0.3, 0.4, 0.3, 0.3);
					Fx.particle(world, Particle.LARGE_SMOKE, c, 6, 0.2, 0.3, 0.2, 0.01);
					Fx.sound(entity, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0F, 0.6F);
				}
			}
			case HALBERD -> {
				Vector eye = Fx.eye(player);
				Vector look = Fx.look(player);
				double reach = c.clone().subtract(eye).dot(look);
				for (LivingEntity other : Fx.foes(player, entity.getBoundingBox().clone().expand(3.5, 1.5, 3.5))) {
					Vector o = Fx.center(other);
					double along = o.clone().subtract(eye).dot(look);
					if (other != entity && along > reach && along < reach + 3.5
							&& o.distance(eye.clone().add(look.clone().multiply(along))) < 1.2 + Fx.width(other) / 2) {
						Fx.hurt(player, other, damage * PIERCE_RATIO);
						Fx.particle(world, Particle.ENCHANTED_HIT, o, 10, 0.2, 0.3, 0.2, 0.1);
					}
				}
			}
			case STAFF -> {
				entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
				Fx.particle(world, Particle.END_ROD, c, 6, 0.3, 0.3, 0.3, 0.03);
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ attaques spéciales (clic droit)

	public static void activate(WeaponKind kind, Player player, ItemStack stack) {
		power = powerOf(stack);
		Vector feet = Fx.feet(player);
		switch (kind) {
			case SCYTHE -> reap(player);
			case KATANA -> iaijutsu(player);
			case WARHAMMER -> earthquake(player);
			case GREATSWORD -> {
				Vector dir = Fx.flatLook(player);
				SHOTS.add(new Shot(player, kind, feet.clone().add(new Vector(0, 1.0, 0)).add(dir), dir, 1.5, 16.0));
				Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.5F);
				Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 0.8F, 1.6F);
			}
			case DAGGER -> shadowStep(player);
			case HALBERD -> royalSweep(player);
			case STAFF -> {
				Vector dir = Fx.look(player);
				SHOTS.add(new Shot(player, kind, Fx.eye(player).add(dir.clone().multiply(0.8)), dir, 2.5, 32.0));
				Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 1.4F);
				Fx.sound(player, Sound.ENTITY_EVOKER_CAST_SPELL, 0.6F, 1.8F);
			}
			case BLAST_BLADE -> {
				Vector dir = Fx.flatLook(player);
				SHOTS.add(new Shot(player, kind, feet.clone().add(new Vector(0, 0.6, 0)).add(dir.clone().multiply(2.0)), dir, 2.0, 14.0));
				Fx.sound(player, Sound.ENTITY_TNT_PRIMED, 1.0F, 1.4F);
			}
		}
		power = 1.0;
	}

	/** Faux : tourbillon de 4,5 blocs qui attire les ennemis et rend de la vie pour chacun. */
	private static void reap(Player player) {
		World world = player.getWorld();
		Location p = player.getLocation();
		double radius = 4.5;
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * i / 24;
			Fx.particle(world, Particle.SWEEP_ATTACK, p.getX() + Math.cos(a) * 2.8, p.getY() + 1.0, p.getZ() + Math.sin(a) * 2.8, 1, 0, 0, 0, 0);
			Fx.particle(world, Particle.WITCH, p.getX() + Math.cos(a) * radius, p.getY() + 0.6, p.getZ() + Math.sin(a) * radius, 2, 0.1, 0.2,
					0.1, 0.02);
		}
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
		Fx.sound(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 0.7F);
		double healed = 0;
		for (LivingEntity foe : Fx.foes(player, player.getBoundingBox().clone().expand(radius, 1.5, radius))) {
			if (Fx.flatDistance(player, foe) > radius + Fx.width(foe) / 2) {
				continue;
			}
			strike(player, foe, 10.0);
			Vector pull = new Vector(p.getX() - foe.getLocation().getX(), 0, p.getZ() - foe.getLocation().getZ());
			if (pull.lengthSquared() > 1.0E-4) {
				pull.normalize().multiply(0.35);
			}
			Fx.push(foe, pull.getX(), 0.15, pull.getZ());
			healed += 1.5;
		}
		if (healed > 0) {
			player.heal(Math.min(Math.min(8.0, healed), maxHealth(player) - player.getHealth()));
			Fx.particle(world, Particle.HEART, p.getX(), p.getY() + 2.0, p.getZ(), 3, 0.4, 0.2, 0.4, 0);
		}
	}

	private static double maxHealth(Player player) {
		return player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
	}

	/** Point d'arrivée d'une ruée : on avance tant que le joueur tient sans entrer dans un bloc. */
	public static Vector dashEnd(Player player, Vector dir, double distance) {
		Vector start = Fx.feet(player);
		Vector end = start.clone();
		BoundingBox box = player.getBoundingBox();
		for (double d = 0.5; d <= distance; d += 0.5) {
			Vector next = start.clone().add(dir.clone().multiply(d));
			if (player.getWorld().hasCollisionsIn(box.clone().shift(next.clone().subtract(start)))) {
				break;
			}
			end = next;
		}
		return end;
	}

	public static void moveTo(Player player, Vector at, Float yaw, Float pitch) {
		Location l = player.getLocation();
		player.teleport(new Location(player.getWorld(), at.getX(), at.getY(), at.getZ(), yaw != null ? yaw : l.getYaw(),
				pitch != null ? pitch : l.getPitch()));
		player.setFallDistance(0);
	}

	/** Katana : ruée éclair de 8 blocs, chaque ennemi traversé est tranché et saigne. */
	private static void iaijutsu(Player player) {
		World world = player.getWorld();
		Vector dir = Fx.flatLook(player);
		Vector start = Fx.feet(player);
		Vector end = dashEnd(player, dir, 8.0);
		BoundingBox path = player.getBoundingBox().clone().union(player.getBoundingBox().clone().shift(end.clone().subtract(start)))
				.expand(1.2, 0.5, 1.2);
		for (LivingEntity foe : Fx.foes(player, path)) {
			strike(player, foe, 12.0);
			foe.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
			Fx.particle(world, Particle.DAMAGE_INDICATOR, Fx.center(foe), 6, 0.3, 0.3, 0.3, 0.1);
		}
		double length = end.distance(start);
		for (double d = 0; d <= length; d += 0.8) {
			Vector p = start.clone().add(dir.clone().multiply(d));
			Fx.particle(world, Particle.SWEEP_ATTACK, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0, 0, 0, 0);
			Fx.particle(world, Particle.CRIMSON_SPORE, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.3, 0.4, 0.3, 0.01);
		}
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2F, 1.5F);
		Fx.sound(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0F, 1.4F);
		moveTo(player, end, null, null);
	}

	/** Marteau : le sol tremble sur 6 blocs, les ennemis sont projetés en l'air et ralentis. */
	private static void earthquake(Player player) {
		World world = player.getWorld();
		Location p = player.getLocation();
		double radius = 6.0;
		BlockData ground = p.clone().subtract(0, 1, 0).getBlock().getBlockData();
		if (ground.getMaterial().isAir()) {
			ground = Material.STONE.createBlockData();
		}
		for (int ring = 1; ring <= 3; ring++) {
			double r = radius * ring / 3;
			int n = 12 * ring;
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				double x = p.getX() + Math.cos(a) * r;
				double z = p.getZ() + Math.sin(a) * r;
				Fx.particle(world, Particle.BLOCK, x, p.getY() + 0.1, z, 6, 0.3, 0.1, 0.3, 0.15, ground);
				if (i % 3 == 0) {
					Fx.particle(world, Particle.EXPLOSION, x, p.getY() + 0.3, z, 1, 0, 0, 0, 0);
				}
			}
		}
		Fx.particle(world, Particle.GUST, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0, 0, 0, 0);
		Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 0.7F);
		Fx.sound(player, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.5F, 0.8F);
		for (LivingEntity foe : Fx.foes(player, player.getBoundingBox().clone().expand(radius, 3.0, radius))) {
			double d = Fx.flatDistance(player, foe);
			if (d > radius + Fx.width(foe) / 2) {
				continue;
			}
			strike(player, foe, 14.0 * (1.0 - d / (radius + 2.0)));
			Vector away = new Vector(foe.getLocation().getX() - p.getX(), 0, foe.getLocation().getZ() - p.getZ());
			if (away.lengthSquared() > 1.0E-4) {
				away.normalize().multiply(0.5);
			}
			Fx.push(foe, away.getX(), 0.9, away.getZ());
			foe.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
		}
	}

	/** Dague : téléportation dans le dos de l'ennemi visé (32 blocs), invisible et rapide. */
	private static void shadowStep(Player player) {
		World world = player.getWorld();
		Location eyeLoc = player.getEyeLocation();
		Vector dir = eyeLoc.getDirection();
		double max = 32.0;
		RayTraceResult wall = world.rayTraceBlocks(eyeLoc, dir, max, org.bukkit.FluidCollisionMode.NEVER, true);
		if (wall != null) {
			max = wall.getHitPosition().distance(eyeLoc.toVector());
		}
		RayTraceResult aimed = world.rayTraceEntities(eyeLoc, dir, max, 1.0, e -> Fx.isFoe(player, e));
		Vector from = Fx.feet(player);
		Fx.particle(world, Particle.LARGE_SMOKE, from.getX(), from.getY() + 1.0, from.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
		if (aimed != null && aimed.getHitEntity() instanceof LivingEntity target) {
			Vector facing = Fx.flatLook(target);
			Vector center = Fx.feet(target);
			Vector destination = null;
			double back = Fx.width(target) / 2 + 0.9;
			for (Vector side : new Vector[] {facing.clone().multiply(-1), new Vector(-facing.getZ(), 0, facing.getX()),
					new Vector(facing.getZ(), 0, -facing.getX()), facing}) {
				Vector candidate = center.clone().add(side.clone().multiply(back));
				if (!world.hasCollisionsIn(player.getBoundingBox().clone().shift(candidate.clone().subtract(from)))) {
					destination = candidate;
					break;
				}
			}
			if (destination != null) {
				moveTo(player, destination, Fx.yawTo(destination, Fx.feet(target)), 10.0F);
			}
		} else {
			moveTo(player, dashEnd(player, Fx.flatLook(player), 14.0), null, null);
		}
		player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 400, 0, false, false, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, false, false, true));
		Location l = player.getLocation();
		Fx.particle(world, Particle.REVERSE_PORTAL, l.getX(), l.getY() + 1.0, l.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		Fx.particle(world, Particle.LARGE_SMOKE, l.getX(), l.getY() + 1.0, l.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
		Fx.sound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);
	}

	/** Hallebarde : grand balayage en demi-cercle devant le joueur. */
	private static void royalSweep(Player player) {
		World world = player.getWorld();
		Location p = player.getLocation();
		double radius = 5.5;
		Vector look = Fx.flatLook(player);
		for (int i = -6; i <= 6; i++) {
			double a = Math.toRadians(i * 15);
			double dx = look.getX() * Math.cos(a) - look.getZ() * Math.sin(a);
			double dz = look.getX() * Math.sin(a) + look.getZ() * Math.cos(a);
			for (double r = 2.0; r <= 4.0; r += 2.0) {
				Fx.particle(world, Particle.SWEEP_ATTACK, p.getX() + dx * r, p.getY() + 1.1, p.getZ() + dz * r, 1, 0, 0, 0, 0);
			}
			Fx.particle(world, Particle.TOTEM_OF_UNDYING, p.getX() + dx * radius, p.getY() + 1.0, p.getZ() + dz * radius, 3, 0.1, 0.2, 0.1, 0.05);
		}
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.4F, 0.7F);
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0F, 0.8F);
		for (LivingEntity foe : Fx.foes(player, player.getBoundingBox().clone().expand(radius, 2.0, radius))) {
			Vector v = new Vector(foe.getLocation().getX() - p.getX(), 0, foe.getLocation().getZ() - p.getZ());
			if (v.length() > radius + Fx.width(foe) / 2 || (v.lengthSquared() > 1.0E-4 && v.clone().normalize().dot(look) < -0.1)) {
				continue;
			}
			strike(player, foe, 11.0);
			Vector away = v.lengthSquared() > 1.0E-4 ? v.clone().normalize().multiply(0.9) : look.clone().multiply(0.9);
			Fx.push(foe, away.getX(), 0.3, away.getZ());
		}
	}

	// ------------------------------------------------------------------ projectiles

	public static void tick() {
		Iterator<Shot> it = SHOTS.iterator();
		while (it.hasNext()) {
			if (!step(it.next())) {
				it.remove();
			}
		}
	}

	public static void clear() {
		SHOTS.clear();
	}

	private static boolean solid(World world, Vector pos) {
		if (!world.isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
			return true;
		}
		Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
		return block.getType().isSolid() && block.getType().isOccluding();
	}

	private static boolean step(Shot s) {
		if (!s.player.isOnline() || s.player.getWorld() != s.world) {
			return false;
		}
		if (s.kind == WeaponKind.BLAST_BLADE) {
			return stepBlast(s);
		}
		boolean wave = s.kind == WeaponKind.GREATSWORD;
		int steps = (int) Math.ceil(s.speed / 0.5);
		for (int i = 0; i < steps; i++) {
			s.pos.add(s.dir.clone().multiply(s.speed / steps));
			s.travelled += s.speed / steps;
			if (solid(s.world, s.pos)) {
				if (!wave) {
					burst(s);
				}
				return false;
			}
			for (LivingEntity foe : Fx.foes(s.player, Fx.box(s.pos, wave ? 1.6 : 0.5))) {
				if (!s.hit.add(foe)) {
					continue;
				}
				Vector c = Fx.center(foe);
				if (wave) {
					Fx.hurt(s.player, foe, 14.0 * s.power);
					Fx.push(foe, s.dir.getX() * 0.8, 0.25, s.dir.getZ() * 0.8);
					Fx.particle(s.world, Particle.ENCHANTED_HIT, c, 12, 0.3, 0.3, 0.3, 0.2);
				} else {
					Fx.magic(s.player, foe, 8.0 * s.power);
					foe.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
					burst(s);
					return false;
				}
			}
			if (s.travelled >= s.range) {
				if (!wave) {
					burst(s);
				}
				return false;
			}
		}
		if (wave) {
			Vector right = new Vector(-s.dir.getZ(), 0, s.dir.getX());
			for (double k = -1.5; k <= 1.5; k += 0.75) {
				Vector p = s.pos.clone().add(right.clone().multiply(k)).add(new Vector(0, -Math.abs(k) * 0.25, 0));
				Fx.particle(s.world, Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0.0);
				Fx.particle(s.world, Particle.ELECTRIC_SPARK, p, 1, 0.1, 0.1, 0.1, 0.0);
			}
			Fx.particle(s.world, Particle.SWEEP_ATTACK, s.pos, 1, 0, 0, 0, 0);
		} else {
			Fx.particle(s.world, Particle.END_ROD, s.pos, 3, 0.08, 0.08, 0.08, 0.0);
			Fx.particle(s.world, Particle.GLOW, s.pos, 1, 0.1, 0.1, 0.1, 0.0);
		}
		return true;
	}

	/** Lame Détonante, clic droit : une explosion tous les 2 blocs en ligne droite, sans casser le sol. */
	private static boolean stepBlast(Shot s) {
		s.pos.add(s.dir.clone().multiply(s.speed));
		s.travelled += s.speed;
		if (solid(s.world, s.pos)) {
			// on longe le relief : un bloc plus haut si possible
			if (solid(s.world, s.pos.clone().add(new Vector(0, 1, 0)))) {
				return false;
			}
			s.pos.add(new Vector(0, 1, 0));
		}
		explode(s.world, s.player, s.pos, 3.0, 10.0 * s.power, s.hit, 1.0F);
		return s.travelled < s.range;
	}

	/** Explosion « propre » : particules, son, dégâts et recul aux ennemis, mais aucun bloc cassé. */
	public static void explode(World world, Player player, Vector at, double radius, double damage, Set<LivingEntity> already, float volume) {
		Fx.particle(world, Particle.EXPLOSION, at, 2, radius * 0.25, 0.3, radius * 0.25, 0);
		Fx.particle(world, Particle.FLAME, at, 8, radius * 0.3, 0.3, radius * 0.3, 0.05);
		Fx.particle(world, Particle.LARGE_SMOKE, at.getX(), at.getY() + 0.3, at.getZ(), 4, radius * 0.2, 0.2, radius * 0.2, 0.02);
		if (volume > 0) {
			Fx.sound(world, at, Sound.ENTITY_GENERIC_EXPLODE, volume, 1.1F);
		}
		for (LivingEntity foe : Fx.foes(player, Fx.box(at, radius))) {
			Vector away = Fx.center(foe).subtract(at);
			if (away.length() > radius + Fx.width(foe) / 2 || (already != null && !already.add(foe))) {
				continue;
			}
			Fx.hurt(player, foe, damage);
			away = away.lengthSquared() < 1.0E-4 ? new Vector(0, 1, 0) : away.normalize();
			Fx.push(foe, away.getX() * 0.8, 0.35, away.getZ() * 0.8);
		}
	}

	/** Lame Détonante : chaque coup d'épée (même dans le vide) fait un slash en arc qui explose devant le joueur. */
	public static void onSwing(Player player) {
		ItemStack stack = player.getInventory().getItemInMainHand();
		if (Items.kindOf(stack) != fr.evotool.plugin.evo.EvoKind.BLAST_BLADE || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
			return;
		}
		int now = Bukkit.getCurrentTick();
		Integer last = LAST_BLAST.get(player.getUniqueId());
		if (last != null && now - last >= 0 && now - last < BLAST_INTERVAL) {
			return;
		}
		LAST_BLAST.put(player.getUniqueId(), now);
		blastSlash(player, powerOf(stack));
	}

	public static void blastSlash(Player player, double strength) {
		Vector look = Fx.flatLook(player);
		Vector origin = Fx.feet(player).add(new Vector(0, 1.0, 0));
		Set<LivingEntity> hit = new HashSet<>();
		for (int i = -2; i <= 2; i++) {
			double a = Math.toRadians(i * 22.0);
			Vector dir = new Vector(look.getX() * Math.cos(a) - look.getZ() * Math.sin(a), 0, look.getX() * Math.sin(a) + look.getZ() * Math.cos(a));
			Vector at = origin.clone().add(dir.multiply(2.6 - Math.abs(i) * 0.25));
			Fx.particle(player.getWorld(), Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
			explode(player.getWorld(), player, at, 1.8, 5.0 * strength, hit, i == 0 ? 0.7F : 0.0F);
		}
	}

	/** Éclat du sceptre qui se brise : gerbe d'étincelles de cristal. */
	private static void burst(Shot s) {
		Fx.particle(s.world, Particle.END_ROD, s.pos, 20, 0.2, 0.2, 0.2, 0.15);
		Fx.particle(s.world, Particle.ELECTRIC_SPARK, s.pos, 12, 0.3, 0.3, 0.3, 0.2);
		Fx.sound(s.world, s.pos, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 0.8F);
	}

	public static void forget(Entity player) {
		LAST_BLAST.remove(player.getUniqueId());
	}
}
