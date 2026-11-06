package fr.evotool.plugin.element;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.input.Cooldowns;
import fr.evotool.plugin.input.InputListener;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.util.Fx;
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
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Les sorts du grimoire élémentaire (clic droit : lancer ; accroupi + clic droit : sort suivant). Un trait est un
 * projectile d'énergie simulé tick par tick ; une explosion frappe autour du point visé ; un souffle frappe en cône ;
 * un déplacement emporte le joueur (glissade de givre, vol de feu, saut éclair, vague, bond du vent, bond sismique,
 * pas de l'ombre, ailes de lumière, brume toxique).
 */
public final class Spells {
	private static final double RANGE = 24.0;
	private static final int BOLT_LIFE = 30;
	private static final double BLINK_RANGE = 16.0;
	private static final double STEP_RANGE = 24.0;
	private static final int SAFE_AFTER = 80;

	private static final class Bolt {
		final Player caster;
		final World world;
		final Spell spell;
		Vector pos;
		final Vector velocity;
		int age;

		Bolt(Player caster, Spell spell, Vector pos, Vector velocity) {
			this.caster = caster;
			this.world = caster.getWorld();
			this.spell = spell;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private record Active(Spell spell, int start) {
	}

	private static final List<Bolt> BOLTS = new ArrayList<>();
	private static final Map<UUID, Active> MOVING = new HashMap<>();
	private static final Map<UUID, Set<UUID>> HIT = new HashMap<>();

	private Spells() {
	}

	public static void enable() {
		EvoToolPlugin.ticker(tick -> {
			tickBolts();
			for (Player player : Bukkit.getOnlinePlayers()) {
				moveTick(player, tick);
			}
		});
		InputListener.onUse((player, stack, clicked, hand) -> {
			if (!Items.is(stack, EvoItem.ELEMENTAL_GRIMOIRE)) {
				return false;
			}
			Grimoire grimoire = Grimoire.get(stack);
			if (grimoire == null || grimoire.selectedSpell() == null) {
				return false;
			}
			if (player.isSneaking()) {
				Grimoire next = grimoire.next();
				stack.editMeta(next::write);
				Lore.update(stack);
				Spell spell = next.selectedSpell();
				player.sendActionBar(Lang.tr("message.evotool.spell_selected", spell.element().symbol(), Lang.tr(spell.translationKey()))
						.color(spell.element().color()));
				Fx.sound(player, Sound.ITEM_BOOK_PAGE_TURN, 1.0F, 1.0F);
				return true;
			}
			if (!Cooldowns.active(player, stack)) {
				Spell spell = grimoire.selectedSpell();
				cast(player, spell);
				Cooldowns.set(player, stack, spell.cooldown());
			}
			return true;
		});
	}

	/** Particules de l'élément (avec la terre qu'il faut pour celles de terre). */
	public static void particle(World world, Element element, double x, double y, double z, int count, double dx, double dy, double dz,
			double speed) {
		Object data = element.particleData();
		if (data != null) {
			Fx.particle(world, element.particle(), x, y, z, count, dx, dy, dz, speed, data);
		} else if (element.particle().getDataType() == Void.class) {
			Fx.particle(world, element.particle(), x, y, z, count, dx, dy, dz, speed);
		} else {
			Fx.particle(world, Particle.ITEM_SLIME, x, y, z, count, dx, dy, dz, speed);
		}
	}

	private static void particle(World world, Element element, Vector at, int count, double d, double speed) {
		particle(world, element, at.getX(), at.getY(), at.getZ(), count, d, d, d, speed);
	}

	static boolean hittable(Player caster, Entity e) {
		return e instanceof LivingEntity target && target != caster && !target.isDead()
				&& !(target instanceof Player p && p.getGameMode() == GameMode.SPECTATOR)
				&& !(target instanceof Tameable tame && caster.equals(tame.getOwner()));
	}

	private static void strike(Player caster, LivingEntity target, Spell spell) {
		Fx.magic(caster, target, spell.damage());
		ElementalPowers.hitEffect(caster, target, spell.element());
	}

	/** Lance ce sort (public pour l'auto-test). */
	public static void cast(Player player, Spell spell) {
		Vector look = Fx.look(player);
		Vector right = new Vector(-look.getZ(), 0, look.getX());
		right = right.lengthSquared() < 1.0E-6 ? new Vector() : right.normalize();
		Vector hand = Fx.eye(player).add(look.clone().multiply(1.4)).add(right.multiply(0.3)).add(new Vector(0, -0.25, 0));
		particle(player.getWorld(), spell.element(), hand, 4, 0.1, 0.02);
		Fx.sound(player, Sound.ENTITY_EVOKER_CAST_SPELL, 0.8F, spell.kind() == Spell.Kind.BOLT ? 1.6F : 1.0F);
		switch (spell.kind()) {
			case BOLT -> BOLTS.add(new Bolt(player, spell, hand, look.clone().multiply(spell.speed())));
			case BURST -> burst(player, spell, aim(player));
			case BREATH -> breath(player, spell);
			case MOVE -> startMove(player, spell);
		}
	}

	// ------------------------------------------------------------------ souffles

	private static void breath(Player player, Spell spell) {
		World world = player.getWorld();
		Vector eye = Fx.eye(player);
		Vector look = Fx.look(player);
		double range = spell.range();
		double cone = Math.cos(Math.toRadians(35.0));
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 70; i++) {
			Vector dir = look.clone().add(new Vector((random.nextDouble() - 0.5) * 0.9, (random.nextDouble() - 0.5) * 0.6,
					(random.nextDouble() - 0.5) * 0.9)).normalize();
			double d = 1.5 + random.nextDouble() * (range - 1.5);
			Vector p = eye.clone().add(dir.clone().multiply(d)).add(new Vector(0, -0.3, 0));
			particle(world, spell.element(), p.getX(), p.getY(), p.getZ(), 0, dir.getX(), dir.getY(), dir.getZ(), 0.35);
		}
		if (spell.element() == Element.FIRE) {
			Fx.sound(player, Sound.ENTITY_BLAZE_SHOOT, 1.0F, 0.8F);
		}
		for (Entity e : world.getNearbyEntities(player.getBoundingBox().clone().expand(range), e -> hittable(player, e))) {
			LivingEntity target = (LivingEntity) e;
			Vector to = Fx.center(target).subtract(eye);
			double dist = to.length();
			if (dist > range + 0.5 || dist < 1.0E-3 || to.multiply(1 / dist).dot(look) < cone) {
				continue;
			}
			strike(player, target, spell);
		}
	}

	private static Vector aim(Player player) {
		Vector eye = Fx.eye(player);
		Vector look = Fx.look(player);
		RayTraceResult block = player.getWorld().rayTraceBlocks(player.getEyeLocation(), look, RANGE, FluidCollisionMode.NEVER, true);
		Vector end = block == null ? eye.clone().add(look.clone().multiply(RANGE)) : block.getHitPosition();
		RayTraceResult entity = player.getWorld().rayTraceEntities(player.getEyeLocation(), look, end.distance(eye), 0.5,
				e -> hittable(player, e));
		return entity != null ? Fx.feet(entity.getHitEntity()) : end;
	}

	// ------------------------------------------------------------------ traits

	private static void tickBolts() {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
			if (stepBolt(it.next())) {
				it.remove();
			}
		}
	}

	public static void tickForTest() {
		tickBolts();
	}

	public static int activeBolts() {
		return BOLTS.size();
	}

	private static boolean stepBolt(Bolt bolt) {
		World world = bolt.world;
		bolt.age++;
		if (bolt.age > BOLT_LIFE || !bolt.caster.isOnline() || !bolt.caster.getWorld().equals(world)) {
			return true;
		}
		Vector from = bolt.pos;
		Vector to = from.clone().add(bolt.velocity);
		double length = bolt.velocity.length();
		RayTraceResult block = world.rayTraceBlocks(new Location(world, from.getX(), from.getY(), from.getZ()), bolt.velocity, length,
				FluidCollisionMode.NEVER, true);
		Vector end = block == null ? to : block.getHitPosition();
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		Vector dir = bolt.velocity.clone().normalize();
		double reach = end.distance(from);
		for (Entity e : world.getNearbyEntities(BoundingBox.of(from, end).expand(1.0), e -> hittable(bolt.caster, e))) {
			RayTraceResult point = e.getBoundingBox().clone().expand(0.3).rayTrace(from, dir, reach);
			if (point != null && point.getHitPosition().distanceSquared(from) < best) {
				best = point.getHitPosition().distanceSquared(from);
				hit = (LivingEntity) e;
			}
		}
		trail(world, bolt.spell, from, end, Fx.eye(bolt.caster));
		if (hit != null) {
			strike(bolt.caster, hit, bolt.spell);
			impact(world, bolt.spell, Fx.center(hit), 0.6);
			return true;
		}
		if (block != null) {
			impact(world, bolt.spell, end, 0.6);
			return true;
		}
		bolt.pos = to;
		return false;
	}

	private static void trail(World world, Spell spell, Vector from, Vector to, Vector eye) {
		Vector step = to.clone().subtract(from);
		int count = Math.max(2, (int) (step.length() * 3));
		for (int i = 0; i < count; i++) {
			Vector p = from.clone().add(step.clone().multiply(i / (double) count));
			if (p.distanceSquared(eye) < 2.5 * 2.5) {
				continue;
			}
			particle(world, spell.element(), p, 1, 0.05, 0.0);
			if (i % 2 == 0) {
				Fx.particle(world, Particle.END_ROD, p, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	private static void impact(World world, Spell spell, Vector at, double size) {
		particle(world, spell.element(), at, (int) (20 * size + 10), size, 0.1);
		Fx.particle(world, Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
		Fx.sound(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.3F + 0.2F * (float) size, 1.6F);
	}

	// ------------------------------------------------------------------ explosions

	private static void burst(Player player, Spell spell, Vector center) {
		World world = player.getWorld();
		double r = spell.radius();
		Element element = spell.element();
		for (int k = 0; k < 48; k++) {
			double a = Math.PI * 2 * k / 48;
			for (double rr = r * 0.5; rr <= r; rr += r * 0.5) {
				particle(world, element, center.getX() + Math.cos(a) * rr, center.getY() + 0.2, center.getZ() + Math.sin(a) * rr,
						1, 0.05, 0.1, 0.05, 0.02);
			}
		}
		particle(world, element, center.getX(), center.getY() + 1.5, center.getZ(), 60, 0.5, 1.5, 0.5, 0.1);
		Fx.particle(world, Particle.EXPLOSION, center.getX(), center.getY() + 0.5, center.getZ(), 3, r * 0.3, 0.3, r * 0.3, 0.0);
		switch (element) {
			case LIGHTNING -> world.strikeLightningEffect(new Location(world, center.getX(), center.getY(), center.getZ()));
			case FIRE -> Fx.particle(world, Particle.LAVA, center.getX(), center.getY() + 0.5, center.getZ(), 30, r * 0.4, 0.5, r * 0.4, 0.1);
			case LIGHT -> Fx.particle(world, Particle.END_ROD, center.getX(), center.getY() + 4, center.getZ(), 80, 0.3, 4.0, 0.3, 0.02);
			default -> {
			}
		}
		Fx.sound(world, center, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.1F);
		BoundingBox area = BoundingBox.of(center, center).expand(r, 2.5, r);
		for (Entity e : world.getNearbyEntities(area, e -> hittable(player, e) && Fx.feet(e).distanceSquared(center) <= (r + 0.5) * (r + 0.5))) {
			strike(player, (LivingEntity) e, spell);
		}
	}

	// ------------------------------------------------------------------ déplacements

	private static Vector flat(Vector look) {
		Vector flat = new Vector(look.getX(), 0, look.getZ());
		return flat.lengthSquared() < 1.0E-6 ? new Vector() : flat.normalize();
	}

	private static void startMove(Player player, Spell spell) {
		MOVING.put(player.getUniqueId(), new Active(spell, Bukkit.getCurrentTick()));
		ElementalPowers.MoveSafety.protect(player, spell.duration() + SAFE_AFTER);
		HIT.remove(player.getUniqueId());
		World world = player.getWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Vector v = player.getVelocity();
		Vector flat = flat(Fx.look(player));
		switch (spell) {
			case LIGHTNING_BLINK -> blink(player, spell);
			case SHADOW_STEP -> shadowStep(player);
			case LIGHT_WINGS -> {
				player.setVelocity(new Vector(v.getX() + flat.getX() * 0.4, 1.1, v.getZ() + flat.getZ() * 0.4));
				player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, spell.duration(), 0, false, false, true));
				Fx.particle(world, Particle.END_ROD, x, y + 1, z, 40, 0.6, 0.6, 0.6, 0.1);
				Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.8F);
			}
			case TOXIC_MIST -> {
				player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, spell.duration(), 0, false, false, true));
				player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, spell.duration(), 2, false, false, true));
				Fx.particle(world, Particle.ITEM_SLIME, x, y + 1, z, 40, 0.6, 0.8, 0.6, 0.05);
				Fx.sound(player, Sound.ENTITY_SLIME_SQUISH, 1.0F, 0.6F);
			}
			case FIRE_FLIGHT -> {
				Fx.particle(world, Particle.FLAME, x, y + 0.2, z, 40, 0.4, 0.2, 0.4, 0.15);
				Fx.sound(player, Sound.ENTITY_BLAZE_SHOOT, 1.2F, 0.6F);
			}
			case WIND_LEAP -> {
				player.setVelocity(new Vector(flat.getX() * 1.3, 1.5, flat.getZ() * 1.3));
				Fx.particle(world, Particle.CLOUD, x, y + 0.1, z, 30, 0.5, 0.1, 0.5, 0.15);
				Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 1.4F, 0.6F);
			}
			case FROST_DASH -> Fx.sound(player, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0F, 0.6F);
			case TIDE_RIDE -> {
				Fx.particle(world, Particle.SPLASH, x, y + 0.2, z, 60, 0.6, 0.2, 0.6, 0.3);
				Fx.sound(player, Sound.ENTITY_GENERIC_SPLASH, 1.2F, 0.8F);
			}
			case EARTH_SLAM -> {
				player.setVelocity(new Vector(v.getX(), 1.3, v.getZ()));
				Fx.particle(world, Particle.BLOCK, x, y + 0.1, z, 40, 0.5, 0.1, 0.5, 0.2, player.getLocation().subtract(0, 1, 0).getBlock().getBlockData());
				Fx.sound(player, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0F, 0.6F);
			}
			default -> {
			}
		}
	}

	private static void moveTick(Player player, int tick) {
		Active active = MOVING.get(player.getUniqueId());
		if (active == null) {
			return;
		}
		int t = tick - active.start();
		Spell spell = active.spell();
		if (t > spell.duration() || player.isDead()) {
			MOVING.remove(player.getUniqueId());
			return;
		}
		World world = player.getWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		Vector look = Fx.look(player);
		Vector flat = flat(look);
		Vector v = player.getVelocity();
		boolean onGround = ((Entity) player).isOnGround();
		switch (spell) {
			case FIRE_FLIGHT -> {
				player.setVelocity(look.clone().multiply(1.1));
				Fx.particle(world, Particle.FLAME, x, y + 0.4, z, 12, 0.3, 0.4, 0.3, 0.04);
				Fx.particle(world, Particle.LAVA, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.0);
				Fx.particle(world, Particle.LARGE_SMOKE, x, y + 0.2, z, 2, 0.2, 0.2, 0.2, 0.01);
				if (t % 6 == 0) {
					Fx.sound(player, Sound.ITEM_FIRECHARGE_USE, 0.4F, 1.4F);
				}
			}
			case FROST_DASH -> {
				player.setVelocity(new Vector(flat.getX() * 1.6, Math.min(v.getY(), 0.1), flat.getZ() * 1.6));
				Fx.particle(world, Particle.SNOWFLAKE, x, y + 0.5, z, 6, 0.3, 0.3, 0.3, 0.02);
				ElementalPowers.freezeWater(player);
				ram(player, spell, 1.8);
			}
			case TIDE_RIDE -> {
				player.setVelocity(new Vector(flat.getX() * 0.95, player.isInWater() ? 0.08 : v.getY(), flat.getZ() * 0.95));
				Fx.particle(world, Particle.SPLASH, x, y + 0.2, z, 12, 0.6, 0.1, 0.6, 0.2);
				Fx.particle(world, Particle.BUBBLE, x, y + 0.3, z, 3, 0.4, 0.2, 0.4, 0.05);
				ram(player, spell, 2.2);
			}
			case WIND_LEAP -> {
				if (t < 12) {
					Fx.particle(world, Particle.CLOUD, x, y, z, 3, 0.2, 0.1, 0.2, 0.02);
				}
			}
			case EARTH_SLAM -> {
				if (t > 5 && onGround) {
					ElementalPowers.quake(player, Fx.feet(player), 5.0, spell.damage(), null);
					Fx.particle(world, Particle.EXPLOSION, x, y + 0.5, z, 3, 1.5, 0.2, 1.5, 0.0);
					MOVING.remove(player.getUniqueId());
				} else if (t >= 12) {
					player.setVelocity(new Vector(v.getX() * 0.5, -2.5, v.getZ() * 0.5));
				}
			}
			case LIGHT_WINGS -> {
				if (!onGround && t % 2 == 0) {
					Fx.particle(world, Particle.END_ROD, x, y + 1.0, z, 2, 0.5, 0.3, 0.5, 0.01);
				}
			}
			case TOXIC_MIST -> {
				if (t % 5 == 0) {
					Fx.particle(world, Particle.ITEM_SLIME, x, y + 0.6, z, 4, 0.4, 0.4, 0.4, 0.02);
					for (Entity e : world.getNearbyEntities(player.getBoundingBox().clone().expand(2.5), e -> e instanceof Enemy && e instanceof LivingEntity)) {
						((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
					}
				}
			}
			default -> {
			}
		}
	}

	private static void ram(Player player, Spell spell, double radius) {
		Set<UUID> hit = HIT.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());
		for (Entity e : player.getWorld().getNearbyEntities(player.getBoundingBox().clone().expand(radius),
				e -> hittable(player, e) && !hit.contains(e.getUniqueId()))) {
			hit.add(e.getUniqueId());
			strike(player, (LivingEntity) e, spell);
		}
	}

	private static boolean free(Player player, Vector feet) {
		BoundingBox box = player.getBoundingBox().clone().shift(feet.clone().subtract(Fx.feet(player)));
		return !player.getWorld().hasCollisionsIn(box);
	}

	private static Vector furthestFree(Player player, Vector look, double distance) {
		RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), look, distance, FluidCollisionMode.NEVER, true);
		double reach = hit == null ? distance : Math.max(0.0, Fx.eye(player).distance(hit.getHitPosition()) - 0.8);
		Vector start = Fx.feet(player);
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vector feet = start.clone().add(look.clone().multiply(d));
			if (free(player, feet)) {
				return feet;
			}
			Vector up = feet.clone().add(new Vector(0, 1, 0));
			if (free(player, up)) {
				return up;
			}
		}
		return null;
	}

	private static void teleport(Player player, Vector to) {
		Location at = new Location(player.getWorld(), to.getX(), to.getY(), to.getZ(), player.getLocation().getYaw(), player.getLocation().getPitch());
		player.teleport(at);
		player.setFallDistance(0);
	}

	private static void blink(Player player, Spell spell) {
		World world = player.getWorld();
		Vector look = Fx.look(player);
		Vector dest = furthestFree(player, look, BLINK_RANGE);
		if (dest == null) {
			return;
		}
		Vector from = Fx.feet(player).add(new Vector(0, 1, 0));
		Vector to = dest.clone().add(new Vector(0, 1, 0));
		ElementalPowers.arc(world, from, to);
		Fx.particle(world, Particle.ELECTRIC_SPARK, from, 30, 0.4, 0.8, 0.4, 0.3);
		Vector dir = to.clone().subtract(from);
		double length = dir.length();
		if (length > 1.0E-3) {
			dir.multiply(1 / length);
			for (Entity e : world.getNearbyEntities(BoundingBox.of(from, to).expand(1.5), e -> hittable(player, e))) {
				if (e.getBoundingBox().clone().expand(1.0).rayTrace(from, dir, length) != null) {
					strike(player, (LivingEntity) e, spell);
				}
			}
		}
		teleport(player, dest);
		Fx.particle(world, Particle.ELECTRIC_SPARK, to, 30, 0.4, 0.8, 0.4, 0.3);
		Fx.sound(world, dest, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8F, 1.6F);
	}

	private static void shadowStep(Player player) {
		World world = player.getWorld();
		Vector look = Fx.look(player);
		RayTraceResult aimed = world.rayTraceEntities(player.getEyeLocation(), look, STEP_RANGE, 0.5, e -> hittable(player, e));
		Vector dest = null;
		if (aimed != null) {
			Vector at = Fx.feet(aimed.getHitEntity());
			Vector back = flat(at.clone().subtract(Fx.feet(player)));
			for (double d = 1.6; d <= 3.0 && dest == null; d += 0.5) {
				Vector feet = at.clone().subtract(back.clone().multiply(d));
				if (free(player, feet)) {
					dest = feet;
				}
			}
		}
		if (dest == null) {
			dest = furthestFree(player, look, 10.0);
		}
		if (dest == null) {
			return;
		}
		Vector from = Fx.feet(player);
		Fx.particle(world, Particle.SQUID_INK, from.getX(), from.getY() + 1, from.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		teleport(player, dest);
		player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false, true));
		Fx.particle(world, Particle.SQUID_INK, dest.getX(), dest.getY() + 1, dest.getZ(), 30, 0.4, 0.8, 0.4, 0.05);
		Fx.sound(world, dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 0.6F);
	}

	public static void forget(UUID id) {
		MOVING.remove(id);
		HIT.remove(id);
	}

	public static void clear() {
		BOLTS.clear();
		MOVING.clear();
		HIT.clear();
	}
}
