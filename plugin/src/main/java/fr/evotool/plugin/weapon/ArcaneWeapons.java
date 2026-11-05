package fr.evotool.plugin.weapon;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Les sorts des armes magiques ({@link MagicKind}) : charges qui se rechargent avec le temps, missiles à tête
 * chercheuse, éclairs en chaîne, étoiles en orbite, trous noirs, disques boomerang, feux follets et rayons.
 * Au corps à corps, chaque coup a une chance de rendre une charge.
 */
public final class ArcaneWeapons {
	private static final double SIPHON_CHANCE = 0.2;
	private static double power = 1.0;

	private enum MissileType { ARCANE, STAR, WISP, CHAKRAM }

	private static final class Missile {
		final World world;
		final Player owner;
		final MissileType type;
		final double speed;
		final int maxAge;
		final Set<LivingEntity> hit = new HashSet<>();
		final double power = ArcaneWeapons.power;
		Vector pos;
		Vector velocity;
		LivingEntity target;
		int age;
		double travelled;
		boolean returning;

		Missile(Player owner, MissileType type, Vector pos, Vector direction, double speed, int maxAge) {
			this.world = owner.getWorld();
			this.owner = owner;
			this.type = type;
			this.pos = pos;
			this.velocity = direction.clone().normalize().multiply(speed);
			this.speed = speed;
			this.maxAge = maxAge;
		}
	}

	private static final class Orbit {
		final World world;
		final Player owner;
		final Map<LivingEntity, Integer> lastHit = new HashMap<>();
		double power = ArcaneWeapons.power;
		int shards;
		long end;

		Orbit(Player owner, int shards, long end) {
			this.world = owner.getWorld();
			this.owner = owner;
			this.shards = shards;
			this.end = end;
		}
	}

	private static final class Singularity {
		final World world;
		final Player owner;
		final Vector center;
		final double power = ArcaneWeapons.power;
		int age;

		Singularity(Player owner, Vector center) {
			this.world = owner.getWorld();
			this.owner = owner;
			this.center = center;
		}
	}

	private static final List<Missile> MISSILES = new ArrayList<>();
	private static final Map<UUID, Orbit> ORBITS = new HashMap<>();
	private static final List<Singularity> SINGULARITIES = new ArrayList<>();

	private ArcaneWeapons() {
	}

	public static void clear() {
		MISSILES.clear();
		ORBITS.clear();
		SINGULARITIES.clear();
	}

	private static long now() {
		return Bukkit.getWorlds().get(0).getFullTime();
	}

	// ------------------------------------------------------------------ charges

	/** Charges maximum : + 1 tous les 50 niveaux d'évolution. */
	public static int maxCharges(ItemStack stack, MagicKind kind) {
		return kind.maxCharges() + EvoData.get(stack).level() / 50;
	}

	/** Ticks pour regagner une charge : 40 % plus rapide au niveau 100, encore 30 % de mieux amélioré. */
	public static int rechargeTicks(ItemStack stack, MagicKind kind) {
		double t = kind.rechargeTicks() * (1.0 - EvoData.get(stack).level() / 250.0);
		if (EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED)) {
			t *= 0.7;
		}
		return Math.max(5, (int) Math.round(t));
	}

	public static int charges(ItemStack stack, MagicKind kind) {
		int max = maxCharges(stack, kind);
		if (stack == null || !stack.hasItemMeta()) {
			return max;
		}
		Integer charges = stack.getItemMeta().getPersistentDataContainer().get(Keys.CHARGES, PersistentDataType.INTEGER);
		return charges == null ? max : Math.max(0, Math.min(max, charges));
	}

	private static void setCharges(ItemStack stack, int charges, Long clock) {
		stack.editMeta(meta -> {
			PersistentDataContainer pdc = meta.getPersistentDataContainer();
			pdc.set(Keys.CHARGES, PersistentDataType.INTEGER, charges);
			if (clock != null) {
				pdc.set(Keys.CHARGE_CLOCK, PersistentDataType.LONG, clock);
			}
		});
		Lore.update(stack);
	}

	/** Recharge selon le temps écoulé depuis la dernière charge ; renvoie le nombre de charges regagnées. */
	public static int recharge(ItemStack stack, MagicKind kind, long now) {
		int charges = charges(stack, kind);
		int max = maxCharges(stack, kind);
		int ticks = rechargeTicks(stack, kind);
		if (charges >= max) {
			return 0;
		}
		Long clock = stack.getItemMeta().getPersistentDataContainer().get(Keys.CHARGE_CLOCK, PersistentDataType.LONG);
		if (clock == null || clock > now) {
			setCharges(stack, charges, now);
			return 0;
		}
		long gained = (now - clock) / ticks;
		if (gained <= 0) {
			return 0;
		}
		int after = (int) Math.min(max, charges + gained);
		setCharges(stack, after, clock + gained * ticks);
		return after - charges;
	}

	private static void consume(ItemStack stack, MagicKind kind, int used, long now) {
		int charges = charges(stack, kind);
		setCharges(stack, Math.max(0, charges - used), charges >= maxCharges(stack, kind) ? Long.valueOf(now) : null);
	}

	// ------------------------------------------------------------------ sorts

	/** Lance le sort de l'arme ; renvoie false s'il n'y a plus de charge. */
	public static boolean cast(MagicKind kind, ItemStack stack, Player player) {
		World world = player.getWorld();
		long now = now();
		recharge(stack, kind, now);
		int charges = charges(stack, kind);
		if (charges <= 0) {
			player.sendActionBar(Lang.tr("message.evotool.no_charges").color(NamedTextColor.GRAY));
			Fx.sound(player, Sound.BLOCK_DISPENSER_FAIL, 0.6F, 1.4F);
			return false;
		}
		boolean sneak = player.isSneaking();
		power = WeaponPowers.powerOf(stack);
		Vector eye = Fx.eye(player);
		Vector look = Fx.look(player);
		int used = switch (kind) {
			case ARCANE_WAND -> {
				int n = sneak ? charges : 1;
				for (int i = 0; i < n; i++) {
					Vector dir = look.clone().rotateAroundY(Math.toRadians(n == 1 ? 0 : (i - (n - 1) / 2.0) * 12.0));
					MISSILES.add(new Missile(player, MissileType.ARCANE, eye.clone().add(dir.clone().multiply(0.8)).add(new Vector(0, -0.2, 0)),
							dir, 1.1, 70));
				}
				Fx.sound(player, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0F, sneak ? 0.8F : 1.4F);
				yield n;
			}
			case THUNDER_GAUNTLET -> {
				if (sneak) {
					thunderWave(player);
				} else {
					chainLightning(player);
				}
				yield 1;
			}
			case CELESTIAL_ORB -> {
				Orbit orbit = ORBITS.get(player.getUniqueId());
				if (sneak) {
					int shards = orbit != null && orbit.world == world ? orbit.shards : 5;
					ORBITS.remove(player.getUniqueId());
					for (int i = 0; i < shards; i++) {
						Vector dir = look.clone().rotateAroundY(Math.toRadians((i - (shards - 1) / 2.0) * 10.0));
						MISSILES.add(new Missile(player, MissileType.STAR, eye.clone().add(dir), dir, 1.4, 50));
					}
					Fx.sound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 1.6F);
				} else {
					if (orbit == null || orbit.world != world) {
						ORBITS.put(player.getUniqueId(), new Orbit(player, 4, now + 200));
					} else {
						orbit.shards = Math.min(8, orbit.shards + 4);
						orbit.end = now + 200;
						orbit.power = power;
					}
					Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5F, 1.8F);
				}
				yield 1;
			}
			case VOID_GRIMOIRE -> {
				Vector center = sneak ? Fx.feet(player).add(new Vector(0, 1.0, 0)) : targetPoint(player, 24.0);
				SINGULARITIES.add(new Singularity(player, center));
				Fx.sound(world, center, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.5F, 0.5F);
				Fx.sound(world, center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.4F);
				yield 1;
			}
			case SOLAR_CHAKRAM -> {
				int n = sneak && charges >= 2 ? 3 : 1;
				for (int i = 0; i < n; i++) {
					Vector dir = look.clone().rotateAroundY(Math.toRadians(n == 1 ? 0 : (i - 1) * 20.0));
					MISSILES.add(new Missile(player, MissileType.CHAKRAM, eye.clone().add(dir).add(new Vector(0, -0.3, 0)), dir, 1.3, 70));
				}
				Fx.sound(player, Sound.ITEM_FIRECHARGE_USE, 0.8F, 1.4F);
				Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 1.6F);
				yield n == 3 ? 2 : 1;
			}
			case SPIRIT_LANTERN -> {
				Location l = player.getLocation();
				if (sneak) {
					heal(player, 6.0);
					player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
					Fx.particle(world, Particle.SOUL, l.getX(), l.getY() + 1.0, l.getZ(), 30, 0.6, 0.8, 0.6, 0.02);
					Fx.particle(world, Particle.HEART, l.getX(), l.getY() + 2.0, l.getZ(), 5, 0.5, 0.3, 0.5, 0);
					Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.8F);
				} else {
					for (int i = -1; i <= 1; i++) {
						Vector dir = look.clone().rotateAroundY(Math.toRadians(i * 30.0)).add(new Vector(0, 0.25, 0));
						MISSILES.add(new Missile(player, MissileType.WISP, eye.clone().add(dir.clone().multiply(0.6)), dir, 0.55, 120));
					}
					Fx.sound(player, Sound.ENTITY_VEX_CHARGE, 1.0F, 0.8F);
				}
				yield 1;
			}
			case EVOLIUM_BLASTER -> {
				int n = sneak ? charges : 1;
				beam(player, sneak ? 5.0 * n : 6.0, sneak ? 1.6 : 0.6, sneak);
				yield n;
			}
		};
		consume(stack, kind, used, now);
		power = 1.0;
		return true;
	}

	public static void heal(Player player, double amount) {
		double max = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
		player.setHealth(Math.min(max, player.getHealth() + amount));
	}

	private static Vector targetPoint(Player player, double range) {
		Location eye = player.getEyeLocation();
		Vector dir = eye.getDirection();
		RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
		Vector end = wall != null ? wall.getHitPosition().subtract(dir.clone().multiply(0.5)) : eye.toVector().add(dir.clone().multiply(range));
		LivingEntity aimed = aimed(player, eye.toVector().distance(end));
		return aimed != null ? Fx.center(aimed) : end;
	}

	public static LivingEntity aimed(Player player, double range) {
		Location eye = player.getEyeLocation();
		Vector dir = eye.getDirection();
		RayTraceResult wall = player.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
		double max = wall != null ? wall.getHitPosition().distance(eye.toVector()) : range;
		RayTraceResult hit = player.getWorld().rayTraceEntities(eye, dir, max, 1.0, e -> Fx.isFoe(player, e));
		return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
	}

	public static LivingEntity nearestFoe(Player player, Vector from, double range, Set<LivingEntity> except) {
		LivingEntity best = null;
		double bestDistance = range * range;
		for (LivingEntity foe : Fx.foes(player, Fx.box(from, range))) {
			double d = Fx.center(foe).distanceSquared(from);
			if (d < bestDistance && (except == null || !except.contains(foe))) {
				bestDistance = d;
				best = foe;
			}
		}
		return best;
	}

	private static void spell(Player player, LivingEntity target, double amount) {
		Fx.magic(player, target, amount * power);
	}

	public static void sparkLine(World world, Vector from, Vector to) {
		double length = from.distance(to);
		if (length < 1.0E-3) {
			return;
		}
		Vector dir = to.clone().subtract(from).normalize();
		for (double d = 0; d <= length; d += 0.35) {
			Vector p = from.clone().add(dir.clone().multiply(d));
			double jitter = Math.sin(d * 7.0) * 0.12;
			Fx.particle(world, Particle.ELECTRIC_SPARK, p.getX() + jitter, p.getY() - jitter, p.getZ(), 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	/** Gantelet : l'éclair frappe l'ennemi visé puis saute jusqu'à 4 ennemis proches. */
	private static void chainLightning(Player player) {
		World world = player.getWorld();
		Vector look = Fx.look(player);
		Vector from = Fx.eye(player).add(look.clone().multiply(0.6)).add(new Vector(0, -0.4, 0));
		LivingEntity current = aimed(player, 18.0);
		if (current == null) {
			current = nearestFoe(player, Fx.eye(player).add(look.clone().multiply(5.0)), 6.0, null);
		}
		Fx.sound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6F, 1.7F);
		if (current == null) {
			sparkLine(world, from, from.clone().add(look.clone().multiply(8.0)));
			return;
		}
		Set<LivingEntity> struck = new HashSet<>();
		double damage = 10.0;
		for (int i = 0; i < 5 && current != null; i++) {
			Vector c = Fx.center(current);
			sparkLine(world, from, c);
			if (i == 0) {
				world.strikeLightningEffect(current.getLocation());
			}
			spell(player, current, damage);
			Fx.particle(world, Particle.ELECTRIC_SPARK, c, 15, 0.3, 0.4, 0.3, 0.2);
			struck.add(current);
			from = c;
			damage *= 0.8;
			current = nearestFoe(player, c, 7.0, struck);
		}
	}

	/** Gantelet, accroupi : onde de tonnerre de 5 blocs qui repousse et ralentit. */
	private static void thunderWave(Player player) {
		World world = player.getWorld();
		Location p = player.getLocation();
		for (int i = 0; i < 40; i++) {
			double a = Math.PI * 2 * i / 40;
			Fx.particle(world, Particle.ELECTRIC_SPARK, p.getX() + Math.cos(a) * 4.5, p.getY() + 0.3, p.getZ() + Math.sin(a) * 4.5, 2, 0.1, 0.2,
					0.1, 0.1);
		}
		Fx.sound(player, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5F, 0.8F);
		for (LivingEntity foe : Fx.foes(player, player.getBoundingBox().clone().expand(5.0, 2.0, 5.0))) {
			Vector away = new Vector(foe.getLocation().getX() - p.getX(), 0, foe.getLocation().getZ() - p.getZ());
			if (away.length() > 5.5) {
				continue;
			}
			spell(player, foe, 6.0);
			away = away.lengthSquared() < 1.0E-4 ? Fx.look(player) : away.normalize();
			Fx.push(foe, away.getX() * 1.1, 0.45, away.getZ() * 1.1);
			foe.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
		}
	}

	/** Canon : rayon perforant (les blocs l'arrêtent). */
	private static void beam(Player player, double damage, double width, boolean overcharge) {
		World world = player.getWorld();
		Location eyeLoc = player.getEyeLocation();
		Vector start = eyeLoc.toVector();
		Vector dir = eyeLoc.getDirection();
		RayTraceResult wall = world.rayTraceBlocks(eyeLoc, dir, 40.0, FluidCollisionMode.NEVER, true);
		Vector end = wall != null ? wall.getHitPosition() : start.clone().add(dir.clone().multiply(40.0));
		double length = start.distance(end);
		for (double d = 0.8; d <= length; d += 0.4) {
			Vector p = start.clone().add(dir.clone().multiply(d)).add(new Vector(0, -0.15, 0));
			Fx.particle(world, Particle.END_ROD, p, overcharge ? 3 : 1, 0.03 * width, 0.03 * width, 0.03 * width, 0.0);
			if (overcharge && ((int) (d * 2.5)) % 8 == 0) {
				Fx.particle(world, Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0);
			}
		}
		Fx.particle(world, Particle.ELECTRIC_SPARK, end, 20, 0.3, 0.3, 0.3, 0.2);
		if (overcharge) {
			Fx.particle(world, Particle.EXPLOSION, end, 3, 0.5, 0.5, 0.5, 0);
			Fx.sound(player, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2F, 1.3F);
		} else {
			Fx.sound(player, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0F, 1.8F);
		}
		BoundingBox box = BoundingBox.of(start, end).expand(width + 1.0);
		for (LivingEntity foe : Fx.foes(player, box)) {
			Vector c = Fx.center(foe);
			double along = c.clone().subtract(start).dot(dir);
			if (along < 0 || along > length + 1.0) {
				continue;
			}
			if (start.clone().add(dir.clone().multiply(along)).distance(c) <= width + Fx.width(foe) / 2) {
				spell(player, foe, damage);
				Fx.push(foe, dir.getX() * (overcharge ? 1.2 : 0.3), overcharge ? 0.4 : 0.1, dir.getZ() * (overcharge ? 1.2 : 0.3));
				Fx.particle(world, Particle.ENCHANTED_HIT, c, 10, 0.3, 0.3, 0.3, 0.2);
			}
		}
	}

	// ------------------------------------------------------------------ corps à corps

	/** Après un coup au corps à corps avec une arme magique (appelé par l'écouteur de combat). */
	public static void afterHit(Player player, MagicKind kind, ItemStack stack, LivingEntity entity) {
		if (ThreadLocalRandom.current().nextDouble() < SIPHON_CHANCE && charges(stack, kind) < maxCharges(stack, kind)) {
			setCharges(stack, charges(stack, kind) + 1, null);
			Location l = player.getLocation();
			Fx.particle(player.getWorld(), Particle.ENCHANT, l.getX(), l.getY() + 1.2, l.getZ(), 20, 0.4, 0.5, 0.4, 0.5);
			Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 2.0F);
		}
		if (kind == MagicKind.THUNDER_GAUNTLET) {
			Set<LivingEntity> except = new HashSet<>();
			except.add(entity);
			LivingEntity next = nearestFoe(player, Fx.center(entity), 4.0, except);
			if (next != null) {
				sparkLine(player.getWorld(), Fx.center(entity), Fx.center(next));
				Fx.magic(player, next, 3.0);
			}
		}
	}

	// ------------------------------------------------------------------ ticks

	public static void tick(int tick) {
		Iterator<Missile> missiles = MISSILES.iterator();
		while (missiles.hasNext()) {
			Missile missile = missiles.next();
			power = missile.power;
			if (!step(missile)) {
				missiles.remove();
			}
		}
		Iterator<Map.Entry<UUID, Orbit>> orbits = ORBITS.entrySet().iterator();
		while (orbits.hasNext()) {
			Orbit orbit = orbits.next().getValue();
			power = orbit.power;
			if (!step(orbit, tick)) {
				orbits.remove();
			}
		}
		Iterator<Singularity> holes = SINGULARITIES.iterator();
		while (holes.hasNext()) {
			Singularity hole = holes.next();
			power = hole.power;
			if (!step(hole)) {
				holes.remove();
			}
		}
		power = 1.0;
		if (tick % 10 == 0) {
			long now = now();
			for (Player player : Bukkit.getOnlinePlayers()) {
				ItemStack[] contents = player.getInventory().getContents();
				for (ItemStack stack : contents) {
					if (stack == null) {
						continue;
					}
					MagicKind kind = Items.kindOf(stack) != null ? Items.magicKind(Items.kindOf(stack)) : null;
					if (kind != null && recharge(stack, kind, now) > 0 && stack.equals(player.getInventory().getItemInMainHand())) {
						Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3F, 1.6F);
					}
				}
			}
		}
	}

	private static boolean solid(World world, Vector pos) {
		Block block = world.getBlockAt(pos.getBlockX(), pos.getBlockY(), pos.getBlockZ());
		return block.getType().isSolid() && block.getType().isOccluding();
	}

	private static boolean step(Missile m) {
		if (!m.owner.isOnline() || m.owner.getWorld() != m.world || ++m.age > m.maxAge) {
			burst(m);
			return false;
		}
		switch (m.type) {
			case ARCANE, STAR, WISP -> {
				if (m.target == null || m.target.isDead() || Fx.center(m.target).distanceSquared(m.pos) > 24 * 24) {
					m.target = nearestFoe(m.owner, m.pos.clone().add(m.velocity.clone().normalize().multiply(4.0)), 12.0, m.hit);
				}
				if (m.target != null && m.age > 2) {
					double turn = m.type == MissileType.WISP ? 0.18 : m.type == MissileType.STAR ? 0.12 : 0.3;
					Vector desired = Fx.center(m.target).subtract(m.pos);
					if (desired.lengthSquared() > 1.0E-6) {
						desired.normalize().multiply(m.speed);
						m.velocity = m.velocity.clone().multiply(1 - turn).add(desired.multiply(turn)).normalize().multiply(m.speed);
					}
				}
			}
			case CHAKRAM -> {
				if (!m.returning && (m.travelled >= 14.0 || m.age > 16)) {
					m.returning = true;
					m.hit.clear();
				}
				if (m.returning) {
					Vector home = Fx.eye(m.owner).add(new Vector(0, -0.4, 0));
					if (home.distanceSquared(m.pos) < 1.6 * 1.6) {
						Fx.sound(m.owner, Sound.ITEM_ARMOR_EQUIP_GOLD, 0.6F, 1.6F);
						return false;
					}
					m.velocity = home.subtract(m.pos).normalize().multiply(m.speed * 1.2);
				}
			}
		}
		int steps = (int) Math.ceil(m.velocity.length() / 0.4);
		for (int i = 0; i < steps; i++) {
			m.pos.add(m.velocity.clone().multiply(1.0 / steps));
			m.travelled += m.velocity.length() / steps;
			if (!m.world.isChunkLoaded(m.pos.getBlockX() >> 4, m.pos.getBlockZ() >> 4)) {
				return false;
			}
			if (solid(m.world, m.pos)) {
				if (m.type == MissileType.CHAKRAM && !m.returning) {
					m.returning = true;
					m.hit.clear();
					break;
				}
				if (m.type != MissileType.CHAKRAM) {
					burst(m);
					return false;
				}
			}
			double reach = m.type == MissileType.CHAKRAM ? 1.0 : 0.6;
			for (LivingEntity foe : Fx.foes(m.owner, Fx.box(m.pos, reach))) {
				if (!m.hit.add(foe)) {
					continue;
				}
				Vector c = Fx.center(foe);
				switch (m.type) {
					case ARCANE -> spell(m.owner, foe, 7.0);
					case STAR -> {
						spell(m.owner, foe, 6.0);
						foe.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0));
					}
					case WISP -> {
						spell(m.owner, foe, 6.0);
						heal(m.owner, 2.0);
						foe.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
						Fx.particle(m.world, Particle.SOUL, c, 8, 0.3, 0.3, 0.3, 0.03);
					}
					case CHAKRAM -> {
						Fx.hurt(m.owner, foe, 8.0 * power);
						foe.setFireTicks(Math.max(foe.getFireTicks(), 80));
						Fx.particle(m.world, Particle.SWEEP_ATTACK, c, 1, 0, 0, 0, 0);
					}
				}
				if (m.type != MissileType.CHAKRAM) {
					burst(m);
					return false;
				}
			}
		}
		trail(m);
		return true;
	}

	private static void trail(Missile m) {
		Vector p = m.pos;
		switch (m.type) {
			case ARCANE -> {
				Fx.particle(m.world, Particle.WITCH, p, 2, 0.05, 0.05, 0.05, 0.0);
				Fx.particle(m.world, Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.0);
			}
			case STAR -> {
				Fx.particle(m.world, Particle.END_ROD, p, 2, 0.05, 0.05, 0.05, 0.0);
				Fx.particle(m.world, Particle.FIREWORK, p, 1, 0.02, 0.02, 0.02, 0.0);
			}
			case WISP -> {
				Fx.particle(m.world, Particle.SOUL_FIRE_FLAME, p, 2, 0.06, 0.06, 0.06, 0.0);
				Fx.particle(m.world, Particle.SOUL, p, 1, 0.05, 0.05, 0.05, 0.0);
			}
			case CHAKRAM -> {
				double a = m.age * 1.3;
				for (int i = 0; i < 4; i++) {
					double b = a + i * Math.PI / 2;
					Fx.particle(m.world, Particle.FLAME, p.getX() + Math.cos(b) * 0.5, p.getY(), p.getZ() + Math.sin(b) * 0.5, 1, 0, 0, 0, 0.0);
				}
				Fx.particle(m.world, Particle.WAX_ON, p, 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
	}

	private static void burst(Missile m) {
		Vector p = m.pos;
		switch (m.type) {
			case ARCANE -> Fx.particle(m.world, Particle.WITCH, p, 15, 0.25, 0.25, 0.25, 0.1);
			case STAR -> Fx.particle(m.world, Particle.FIREWORK, p, 12, 0.2, 0.2, 0.2, 0.1);
			case WISP -> Fx.particle(m.world, Particle.SOUL_FIRE_FLAME, p, 10, 0.2, 0.2, 0.2, 0.05);
			case CHAKRAM -> Fx.particle(m.world, Particle.FLAME, p, 10, 0.2, 0.2, 0.2, 0.05);
		}
	}

	private static boolean step(Orbit orbit, int tick) {
		Player player = orbit.owner;
		if (!player.isOnline() || player.getWorld() != orbit.world || now() > orbit.end) {
			if (player.isOnline()) {
				Location l = player.getLocation();
				Fx.particle(orbit.world, Particle.FIREWORK, l.getX(), l.getY() + 1.2, l.getZ(), 20, 1.0, 0.5, 1.0, 0.1);
			}
			return false;
		}
		Location l = player.getLocation();
		double base = tick * 0.22;
		for (int i = 0; i < orbit.shards; i++) {
			double a = base + Math.PI * 2 * i / orbit.shards;
			double radius = 2.2 + 0.3 * (i % 2);
			Vector p = new Vector(l.getX() + Math.cos(a) * radius, l.getY() + 1.1 + 0.35 * Math.sin(base * 1.7 + i), l.getZ() + Math.sin(a) * radius);
			Fx.particle(orbit.world, Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.0);
			if (tick % 2 == 0) {
				Fx.particle(orbit.world, Particle.FIREWORK, p, 1, 0.02, 0.02, 0.02, 0.0);
			}
			for (LivingEntity foe : Fx.foes(player, Fx.box(p, 0.9))) {
				Integer last = orbit.lastHit.get(foe);
				if (last != null && tick - last < 10) {
					continue;
				}
				orbit.lastHit.put(foe, tick);
				spell(player, foe, 5.0);
				Vector away = new Vector(foe.getLocation().getX() - l.getX(), 0, foe.getLocation().getZ() - l.getZ());
				if (away.lengthSquared() > 1.0E-4) {
					away.normalize();
					Fx.push(foe, away.getX() * 0.5, 0.2, away.getZ() * 0.5);
				}
			}
		}
		return true;
	}

	private static boolean step(Singularity hole) {
		hole.age++;
		World world = hole.world;
		Vector c = hole.center;
		if (!hole.owner.isOnline()) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			double a = hole.age * 0.5 + i * Math.PI * 2 / 3;
			double r = 3.0 - (hole.age % 20) * 0.13;
			Fx.particle(world, Particle.SQUID_INK, c.getX() + Math.cos(a) * r, c.getY() + Math.sin(a * 0.7) * 0.6, c.getZ() + Math.sin(a) * r, 1,
					0, 0, 0, 0.0);
		}
		Fx.particle(world, Particle.PORTAL, c, 12, 1.5, 1.5, 1.5, 1.0);
		Fx.particle(world, Particle.REVERSE_PORTAL, c, 3, 0.2, 0.2, 0.2, 0.02);
		if (hole.age < 60) {
			for (LivingEntity foe : Fx.foes(hole.owner, Fx.box(c, 8.0))) {
				Vector toCenter = c.clone().subtract(Fx.center(foe));
				double d = toCenter.length();
				if (d > 8.0 || d < 0.3) {
					continue;
				}
				Vector pull = toCenter.normalize().multiply(0.16 * (1.0 - d / 9.0) + 0.04);
				Fx.push(foe, pull.getX(), pull.getY() * 0.5 + 0.02, pull.getZ());
				if (hole.age % 10 == 0) {
					spell(hole.owner, foe, 2.0);
				}
			}
			if (hole.age % 20 == 0) {
				Fx.sound(world, c, Sound.BLOCK_PORTAL_AMBIENT, 0.6F, 1.6F);
			}
			return true;
		}
		Fx.particle(world, Particle.EXPLOSION, c, 4, 0.8, 0.8, 0.8, 0);
		Fx.particle(world, Particle.REVERSE_PORTAL, c, 80, 0.3, 0.3, 0.3, 0.6);
		Fx.sound(world, c, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.5F);
		for (LivingEntity foe : Fx.foes(hole.owner, Fx.box(c, 4.5))) {
			Vector away = Fx.center(foe).subtract(c);
			if (away.length() > 4.5) {
				continue;
			}
			spell(hole.owner, foe, 12.0);
			away = away.lengthSquared() < 1.0E-4 ? new Vector(0, 1, 0) : away.normalize();
			Fx.push(foe, away.getX() * 1.2, 0.5, away.getZ() * 1.2);
		}
		return false;
	}
}
