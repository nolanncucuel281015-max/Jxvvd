package fr.evotool.plugin.armor;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.input.InputListener;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.Burrow;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Les tentacules de l'armure abyssale (ensemble complet au niveau 150) :
 * <ul>
 * <li>clic gauche à main nue : une tentacule noire jaillit du dos jusqu'à 14 blocs et frappe tout ce qu'elle
 * traverse ;</li>
 * <li>accroupi + clic gauche à main nue : elle attrape le monstre visé et le tient devant soi ; recommencer le
 * projette là où l'on regarde (ou le pose doucement si l'on regarde par terre) ;</li>
 * <li>F à main vide : mode déplacement (très rapide, monte les blocs, ni noyade ni chute) ; accroupi sur le sol,
 * elles creusent et l'on traverse la roche.</li>
 * </ul>
 */
public final class Tentacles implements Listener {
	private static final NamespacedKey MOVE_TAG = Keys.key("tentacle_move");
	private static final NamespacedKey SPEED_ID = Keys.key("tentacle_speed");
	private static final NamespacedKey STEP_ID = Keys.key("tentacle_step");
	/** Vitesse sous terre quand elles creusent (Bukkit ; le mod : 0,2 interne). */
	public static final float DIG_SPEED = 0.4F;
	private static final double GROUND_SPEED_BONUS = 2.5;
	private static final double STEP_BONUS = 0.9;
	private static final double STRIKE_RANGE = 14.0;
	private static final int STRIKE_COOLDOWN = 10;
	private static final float[] EXTENSION = {0.12F, 0.26F, 0.42F, 0.58F, 0.74F, 0.88F, 1.0F, 1.0F, 1.0F,
			0.88F, 0.74F, 0.58F, 0.42F, 0.26F, 0.12F};
	private static final int HIT_TICK = 6;
	private static final int GRAB_REACH = 6;
	private static final float BOX_MARGIN = 3.0F;
	private static final float DROP_PITCH = 50.0F;
	private static final int RELEASE_TICKS = 5;
	private static final double HOLD_DISTANCE = 6.0;
	private static final float SEGMENT = 0.5F;
	private static final float MODEL_RADIUS = 3.0F / 16.0F;
	private static final Quaternionf FLIP = new Quaternionf().rotationY((float) Math.PI);

	private static final class Strike {
		final Player player;
		final World world;
		Vector anchor;
		float box;
		Vector origin;
		Vector control;
		Vector end;
		final List<ItemDisplay> segments;
		int age;
		LivingEntity grabbed;
		int releasing = -1;

		Strike(Player player, Vector origin, Vector control, Vector end, List<ItemDisplay> segments, Vector anchor) {
			this.player = player;
			this.world = player.getWorld();
			this.origin = origin;
			this.control = control;
			this.end = end;
			this.segments = segments;
			this.anchor = anchor;
		}
	}

	private record Thrown(Player player, LivingEntity target, int start) {
	}

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static final List<Thrown> THROWN = new ArrayList<>();
	private static final Map<UUID, Strike> GRABS = new HashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
	private static final Map<UUID, Vector> LAST_POS = new HashMap<>();

	public static void enable() {
		EvoToolPlugin.listen(new Tentacles());
		EvoToolPlugin.ticker(tick -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				if (isMoving(player)) {
					moveTick(player, tick);
				}
			}
			tickStrikes();
		});
		InputListener.onSwing(player -> {
			if (!player.getInventory().getItemInMainHand().getType().isAir() || player.getGameMode() == GameMode.SPECTATOR
					|| !ArmorSet.hasTentacles(player)) {
				return;
			}
			if (player.isSneaking()) {
				grabOrThrow(player);
			} else {
				strike(player);
			}
		});
		InputListener.onSwap((player, main) -> {
			if (!main.getType().isAir() || player.isSneaking() || !(ArmorSet.hasTentacles(player) || isMoving(player))) {
				return false;
			}
			toggle(player);
			return true;
		});
		Burrow.digger(Tentacles::isMoving, DIG_SPEED);
	}

	public static boolean isMoving(Player player) {
		return player.getPersistentDataContainer().has(MOVE_TAG);
	}

	// ------------------------------------------------------------------ coup

	public static boolean strike(Player player) {
		int now = Bukkit.getCurrentTick();
		Integer ready = COOLDOWN.get(player.getUniqueId());
		if (ready != null && now < ready) {
			return false;
		}
		COOLDOWN.put(player.getUniqueId(), now + STRIKE_COOLDOWN);
		Vector look = Fx.look(player);
		Vector eye = Fx.eye(player);
		Vector origin = shoulder(player);
		RayTraceResult block = player.getWorld().rayTraceBlocks(player.getEyeLocation(), look, STRIKE_RANGE, FluidCollisionMode.NEVER, true);
		Vector end = block == null ? eye.clone().add(look.clone().multiply(STRIKE_RANGE)) : block.getHitPosition();
		RayTraceResult aimed = player.getWorld().rayTraceEntities(player.getEyeLocation(), look, end.distance(eye), 0.5,
				e -> e instanceof LivingEntity l && e != player && !l.isDead());
		if (aimed != null) {
			end = Fx.center(aimed.getHitEntity());
		}
		STRIKES.add(chain(player, origin, end));
		Fx.sound(player, Sound.ENTITY_SQUID_SQUIRT, 1.2F, 0.6F);
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.5F);
		return true;
	}

	private static Vector shoulder(Player player) {
		Vector look = Fx.look(player);
		Vector right = new Vector(-look.getZ(), 0, look.getX());
		right = right.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : right.normalize();
		return Fx.eye(player).add(right.multiply(0.75)).add(new Vector(0, -0.5, 0)).add(look.multiply(0.35));
	}

	private static Vector curve(Vector origin, Vector end, double wobble) {
		Vector look = end.clone().subtract(origin);
		Vector right = new Vector(-look.getZ(), 0, look.getX());
		right = right.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : right.normalize();
		double length = look.length();
		return origin.clone().add(end).multiply(0.5).add(new Vector(0, 0.3 + length * 0.1, 0)).add(right.multiply(length * 0.12 + wobble));
	}

	private static Strike chain(Player player, Vector origin, Vector end) {
		double length = origin.distance(end);
		int count = Math.max(4, (int) Math.ceil(length / SEGMENT));
		Vector control = curve(origin, end, 0);
		Vector anchor = anchorFor(origin, control, end);
		float box = boxFor(origin, control, end);
		List<ItemDisplay> segments = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ItemDisplay segment = Giant.create(player.getWorld(), anchor, Items.display(i == count - 1 ? "tentacle/tip" : "tentacle/segment"),
					ItemDisplay.ItemDisplayTransform.NONE, 0.0F, Giant.transformation(new Quaternionf(), 0.0F));
			segment.setTeleportDuration(1);
			segment.setDisplayWidth(box);
			segment.setDisplayHeight(box);
			segment.setBrightness(null);
			segments.add(segment);
		}
		Strike strike = new Strike(player, origin, control, end, segments, anchor);
		strike.box = box;
		pose(strike, 0.0F);
		return strike;
	}

	private static Vector anchorFor(Vector a, Vector b, Vector c) {
		double minX = Math.min(a.getX(), Math.min(b.getX(), c.getX()));
		double maxX = Math.max(a.getX(), Math.max(b.getX(), c.getX()));
		double minZ = Math.min(a.getZ(), Math.min(b.getZ(), c.getZ()));
		double maxZ = Math.max(a.getZ(), Math.max(b.getZ(), c.getZ()));
		double minY = Math.min(a.getY(), Math.min(b.getY(), c.getY()));
		return new Vector((minX + maxX) / 2, minY - 0.3, (minZ + maxZ) / 2);
	}

	private static float boxFor(Vector a, Vector b, Vector c) {
		double dx = Math.max(a.getX(), Math.max(b.getX(), c.getX())) - Math.min(a.getX(), Math.min(b.getX(), c.getX()));
		double dy = Math.max(a.getY(), Math.max(b.getY(), c.getY())) - Math.min(a.getY(), Math.min(b.getY(), c.getY()));
		double dz = Math.max(a.getZ(), Math.max(b.getZ(), c.getZ())) - Math.min(a.getZ(), Math.min(b.getZ(), c.getZ()));
		return (float) Math.max(dx, Math.max(dy, dz)) + 2 * BOX_MARGIN;
	}

	private static Vector bezier(Strike strike, double t) {
		double u = 1 - t;
		return strike.origin.clone().multiply(u * u).add(strike.control.clone().multiply(2 * u * t)).add(strike.end.clone().multiply(t * t));
	}

	private static void pose(Strike strike, float extension) {
		int count = strike.segments.size();
		Vector base = strike.anchor;
		for (int i = 0; i < count; i++) {
			float t0 = (float) i / count;
			float t1 = (float) (i + 1) / count;
			ItemDisplay segment = strike.segments.get(i);
			Transformation transformation;
			if (t0 >= extension) {
				transformation = new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0), FLIP);
			} else {
				Vector a = bezier(strike, t0);
				Vector b = bezier(strike, Math.min(t1, extension));
				Vector dir = b.clone().subtract(a);
				float length = (float) dir.length() + 0.04F;
				Vector unit = dir.lengthSquared() < 1.0E-6 ? new Vector(0, 1, 0) : dir.clone().normalize();
				float width = (0.5F - 0.3F * t0) / (2 * MODEL_RADIUS);
				Quaternionf rotation = new Quaternionf().rotationTo(0, 1, 0, (float) unit.getX(), (float) unit.getY(), (float) unit.getZ());
				Vector center = a.clone().add(unit.clone().multiply(length / 2)).subtract(base);
				transformation = new Transformation(new Vector3f((float) center.getX(), (float) center.getY(), (float) center.getZ()),
						rotation, new Vector3f(width, length, width), new Quaternionf(FLIP));
			}
			segment.setInterpolationDelay(0);
			segment.setInterpolationDuration(1);
			segment.setTransformation(transformation);
		}
	}

	private static void tickStrikes() {
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			strike.age++;
			boolean done = strike.grabbed != null ? !tickGrab(strike) : strike.age >= EXTENSION.length;
			if (done || !strike.player.isOnline()) {
				strike.segments.forEach(Entity::remove);
				if (GRABS.get(strike.player.getUniqueId()) == strike) {
					GRABS.remove(strike.player.getUniqueId());
				}
				it.remove();
				continue;
			}
			if (strike.grabbed == null) {
				strike.origin = shoulder(strike.player);
				strike.control = curve(strike.origin, strike.end, 0);
				follow(strike);
				pose(strike, EXTENSION[strike.age]);
				if (strike.age == HIT_TICK) {
					hit(strike);
				}
			}
		}
		tickThrown();
	}

	// ------------------------------------------------------------------ attraper et projeter

	public static boolean grabOrThrow(Player player) {
		Strike held = GRABS.get(player.getUniqueId());
		if (held != null) {
			if (held.releasing < 0 && held.age > GRAB_REACH) {
				if (player.getLocation().getPitch() > DROP_PITCH) {
					dropGrabbed(held);
				} else {
					throwGrabbed(held);
				}
			}
			return true;
		}
		Vector look = Fx.look(player);
		RayTraceResult block = player.getWorld().rayTraceBlocks(player.getEyeLocation(), look, STRIKE_RANGE, FluidCollisionMode.NEVER, true);
		double reach = block == null ? STRIKE_RANGE : block.getHitPosition().distance(Fx.eye(player));
		RayTraceResult aimed = player.getWorld().rayTraceEntities(player.getEyeLocation(), look, reach, 0.5,
				e -> e instanceof LivingEntity l && canGrab(player, l));
		return aimed != null && grabEntity(player, (LivingEntity) aimed.getHitEntity());
	}

	private static boolean canGrab(Player player, LivingEntity target) {
		if (target == player || target.isDead() || Progression.isBoss(target) || isHeld(target)) {
			return false;
		}
		if (target instanceof Player other) {
			Strike theirs = GRABS.get(other.getUniqueId());
			return other.getGameMode() != GameMode.SPECTATOR && (theirs == null || theirs.grabbed != player);
		}
		return true;
	}

	public static boolean grabEntity(Player player, LivingEntity target) {
		if (GRABS.containsKey(player.getUniqueId()) || !canGrab(player, target)) {
			return false;
		}
		Strike strike = chain(player, shoulder(player), Fx.center(target));
		strike.grabbed = target;
		STRIKES.add(strike);
		GRABS.put(player.getUniqueId(), strike);
		Fx.sound(player, Sound.ENTITY_SQUID_SQUIRT, 1.2F, 0.5F);
		return true;
	}

	private static void moveHeld(LivingEntity target, Vector to) {
		Location at = target.getLocation();
		target.teleport(new Location(target.getWorld(), to.getX(), to.getY(), to.getZ(), at.getYaw(), at.getPitch()),
				io.papermc.paper.entity.TeleportFlag.Relative.VELOCITY_ROTATION);
	}

	private static boolean tickGrab(Strike strike) {
		Player player = strike.player;
		LivingEntity target = strike.grabbed;
		if (strike.releasing >= 0) {
			strike.releasing--;
			pose(strike, Math.max(0.0F, strike.releasing / (float) RELEASE_TICKS));
			return strike.releasing > 0;
		}
		if (target.isDead() || !target.isValid() || player.isDead() || !target.getWorld().equals(player.getWorld())) {
			return false;
		}
		strike.origin = shoulder(player);
		if (strike.age < GRAB_REACH) {
			strike.end = Fx.center(target);
			strike.control = curve(strike.origin, strike.end, 0);
			follow(strike);
			pose(strike, strike.age / (float) GRAB_REACH);
			return true;
		}
		World world = strike.world;
		if (strike.age == GRAB_REACH) {
			Fx.particle(world, Particle.SQUID_INK, Fx.center(target), 25, 0.3, 0.4, 0.3, 0.1);
			Fx.sound(target, Sound.ENTITY_SLIME_SQUISH, 1.2F, 0.6F);
			if (target instanceof Player held) {
				held.sendActionBar(Lang.tr("message.evotool.tentacle_grabbed", player.displayName()).color(NamedTextColor.DARK_PURPLE));
			}
		}
		Vector hold = Fx.eye(player).add(Fx.look(player).multiply(HOLD_DISTANCE)).add(new Vector(0, -0.3 - target.getHeight() / 2, 0));
		Vector now = Fx.feet(target);
		for (int i = 0; i < 10 && !free(target, now, hold); i++) {
			hold.add(new Vector(0, 0.5, 0));
		}
		Vector next = strike.age < GRAB_REACH + 6 ? now.clone().add(hold.clone().subtract(now).multiply(0.45)) : hold;
		Vector above = Fx.feet(player).add(new Vector(0, player.getHeight() + 0.2, 0));
		Vector placed = null;
		for (Vector candidate : new Vector[] {next, hold, above, Fx.feet(player)}) {
			if (free(target, now, candidate)) {
				placed = candidate;
				break;
			}
		}
		if (placed == null && now.distanceSquared(Fx.feet(player)) > 16) {
			placed = Fx.feet(player);
		}
		if (placed != null) {
			moveHeld(target, placed);
		}
		target.setVelocity(new Vector());
		target.setFallDistance(0);
		strike.end = Fx.center(target);
		strike.control = curve(strike.origin, strike.end, 0.25 * Math.sin(strike.age * 0.4));
		follow(strike);
		pose(strike, 1.0F);
		return true;
	}

	private static boolean free(LivingEntity target, Vector now, Vector at) {
		BoundingBox box = target.getBoundingBox().clone().shift(at.clone().subtract(now));
		return !target.getWorld().hasCollisionsIn(box);
	}

	private static void follow(Strike strike) {
		Vector anchor = anchorFor(strike.origin, strike.control, strike.end);
		float box = boxFor(strike.origin, strike.control, strike.end);
		boolean moved = anchor.distanceSquared(strike.anchor) > 1.0E-6;
		boolean resized = Math.abs(box - strike.box) > 0.5F;
		strike.anchor = anchor;
		if (resized) {
			strike.box = box;
		}
		for (ItemDisplay segment : strike.segments) {
			if (moved) {
				segment.teleport(new Location(strike.world, anchor.getX(), anchor.getY(), anchor.getZ()));
			}
			if (resized) {
				segment.setDisplayWidth(box);
				segment.setDisplayHeight(box);
			}
		}
	}

	private static void dropGrabbed(Strike strike) {
		Player player = strike.player;
		LivingEntity target = strike.grabbed;
		RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), Fx.look(player), HOLD_DISTANCE + 3.0,
				FluidCollisionMode.NEVER, true);
		if (hit != null) {
			Vector spot = hit.getHitPosition().subtract(Fx.look(player).multiply(0.3));
			if (free(target, Fx.feet(target), spot)) {
				moveHeld(target, spot);
			}
		}
		target.setVelocity(new Vector());
		target.setFallDistance(0);
		Fx.particle(strike.world, Particle.SQUID_INK, Fx.center(target), 12, 0.3, 0.3, 0.3, 0.05);
		Fx.sound(target, Sound.ENTITY_SLIME_SQUISH, 1.0F, 1.2F);
		strike.releasing = RELEASE_TICKS;
		GRABS.remove(player.getUniqueId());
	}

	private static boolean isHeld(Entity entity) {
		for (Strike strike : GRABS.values()) {
			if (strike.grabbed == entity) {
				return true;
			}
		}
		return false;
	}

	private static void throwGrabbed(Strike strike) {
		Player player = strike.player;
		LivingEntity target = strike.grabbed;
		target.setVelocity(Fx.look(player).multiply(2.6).add(new Vector(0, 0.45, 0)));
		THROWN.add(new Thrown(player, target, Bukkit.getCurrentTick()));
		Fx.particle(strike.world, Particle.SQUID_INK, Fx.center(target), 30, 0.3, 0.3, 0.3, 0.2);
		Fx.sound(player, Sound.ENTITY_SQUID_SQUIRT, 1.5F, 0.4F);
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.2F, 0.6F);
		strike.releasing = RELEASE_TICKS;
		GRABS.remove(player.getUniqueId());
	}

	private static void tickThrown() {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			LivingEntity target = thrown.target();
			int age = Bukkit.getCurrentTick() - thrown.start();
			if (target.isDead() || !target.isValid() || age > 60) {
				it.remove();
				continue;
			}
			Vector v = target.getVelocity();
			boolean blocked = target.getWorld().hasCollisionsIn(target.getBoundingBox().clone().shift(v.getX() * 0.6, 0, v.getZ() * 0.6));
			if (age > 3 && (blocked || target.isOnGround())) {
				Vector c = Fx.center(target);
				Fx.hurt(thrown.player(), target, 10.0);
				Fx.particle(target.getWorld(), Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
				Fx.particle(target.getWorld(), Particle.SQUID_INK, c, 20, 0.4, 0.4, 0.4, 0.1);
				Fx.sound(target, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2F, 0.6F);
				it.remove();
			}
		}
	}

	private static void hit(Strike strike) {
		Player player = strike.player;
		World world = strike.world;
		Vector dir = strike.end.clone().subtract(strike.origin).normalize();
		double damage = player.getAttribute(Attribute.ATTACK_DAMAGE).getValue() * 1.5 + 6.0;
		BoundingBox area = BoundingBox.of(strike.origin, strike.end).expand(1.5);
		boolean touched = false;
		for (LivingEntity target : Fx.living(world, area, player)) {
			Vector center = Fx.center(target);
			if (distanceToPath(strike, center) > 0.9 + Fx.width(target) / 2) {
				continue;
			}
			Fx.hurt(player, target, damage);
			target.knockback(1.6, -dir.getX(), -dir.getZ());
			Fx.particle(world, Particle.SQUID_INK, center, 20, 0.3, 0.3, 0.3, 0.1);
			Fx.particle(world, Particle.SWEEP_ATTACK, center, 1, 0, 0, 0, 0);
			touched = true;
		}
		Vector end = strike.end;
		Fx.particle(world, Particle.SQUID_INK, end, 12, 0.2, 0.2, 0.2, 0.05);
		Vector inside = end.clone().add(dir.clone().multiply(0.1));
		Block block = world.getBlockAt(inside.getBlockX(), inside.getBlockY(), inside.getBlockZ());
		if (!block.getType().isAir()) {
			Fx.particle(world, Particle.BLOCK, end.getX(), end.getY(), end.getZ(), 20, 0.2, 0.2, 0.2, 0.15, block.getBlockData());
		}
		Fx.sound(world, end, touched ? Sound.ENTITY_PLAYER_ATTACK_STRONG : Sound.ENTITY_SQUID_SQUIRT, 1.2F, 0.7F);
	}

	private static double distanceToPath(Strike strike, Vector point) {
		double best = Double.MAX_VALUE;
		Vector previous = strike.origin;
		for (int i = 1; i <= 16; i++) {
			Vector next = bezier(strike, i / 16.0);
			Vector segment = next.clone().subtract(previous);
			double len2 = segment.lengthSquared();
			double t = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, point.clone().subtract(previous).dot(segment) / len2));
			best = Math.min(best, previous.clone().add(segment.clone().multiply(t)).distance(point));
			previous = next;
		}
		return best;
	}

	// ------------------------------------------------------------------ mode déplacement (F à main vide)

	public static void toggle(Player player) {
		if (isMoving(player)) {
			stopMoving(player, true);
			return;
		}
		if (!ArmorSet.hasTentacles(player)) {
			player.sendActionBar(Lang.tr("message.evotool.tentacles_locked").color(NamedTextColor.DARK_PURPLE));
			return;
		}
		startMoving(player);
	}

	public static void startMoving(Player player) {
		player.getPersistentDataContainer().set(MOVE_TAG, PersistentDataType.BYTE, (byte) 1);
		LAST_POS.put(player.getUniqueId(), Fx.feet(player));
		groundBoost(player, true);
		Fx.particle(player.getWorld(), Particle.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.1);
		Fx.sound(player, Sound.ENTITY_SQUID_SQUIRT, 1.5F, 0.5F);
		player.sendActionBar(Lang.tr("message.evotool.tentacles_on").color(NamedTextColor.DARK_PURPLE));
	}

	public static void stopMoving(Player player, boolean rescue) {
		player.getPersistentDataContainer().remove(MOVE_TAG);
		groundBoost(player, false);
		if (Burrow.isBurrowing(player) && !Burrow.hasBoots(player)) {
			Burrow.stop(player, rescue);
		}
		PotionEffect vision = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= 260) {
			player.removePotionEffect(PotionEffectType.NIGHT_VISION);
		}
		player.sendActionBar(Lang.tr("message.evotool.tentacles_off").color(NamedTextColor.GRAY));
	}

	private static void groundBoost(Player player, boolean on) {
		modifier(player.getAttribute(Attribute.MOVEMENT_SPEED), SPEED_ID, GROUND_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_SCALAR_1, on);
		modifier(player.getAttribute(Attribute.STEP_HEIGHT), STEP_ID, STEP_BONUS, AttributeModifier.Operation.ADD_NUMBER, on);
	}

	private static void modifier(AttributeInstance attribute, NamespacedKey id, double value, AttributeModifier.Operation operation, boolean on) {
		if (attribute == null) {
			return;
		}
		boolean present = attribute.getModifier(id) != null;
		if (on && !present) {
			attribute.addTransientModifier(new AttributeModifier(id, value, operation, EquipmentSlotGroup.ANY));
		} else if (!on && present) {
			attribute.removeModifier(id);
		}
	}

	private static void moveTick(Player player, int tick) {
		if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR && !Burrow.isBurrowing(player) || !ArmorSet.hasTentacles(player)) {
			stopMoving(player, true);
			return;
		}
		World world = player.getWorld();
		groundBoost(player, true);
		player.setRemainingAir(player.getMaximumAir());
		if (player.getFireTicks() > 0) {
			player.setFireTicks(0);
		}
		if (tick % 20 == 0) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, false));
			if (player.isInWater()) {
				player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false, false));
			}
		}
		UUID id = player.getUniqueId();
		Vector last = LAST_POS.getOrDefault(id, Fx.feet(player));
		Vector now = Fx.feet(player);
		LAST_POS.put(id, now);
		boolean moving = now.distanceSquared(last) > 0.01;
		// les tentacules, sous le joueur : de l'encre qui gicle à chaque pas
		if (moving && tick % 2 == 0) {
			Fx.particle(world, Particle.SQUID_INK, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
		}
		boolean inside = Burrow.isBurrowing(player) && world.hasCollisionsIn(player.getBoundingBox().clone().expand(-0.01)) || player.isUnderWater();
		if (moving && inside && tick % 3 == 0) {
			Vector ahead = Fx.eye(player).add(Fx.look(player).multiply(1.2));
			Block block = world.getBlockAt(ahead.getBlockX(), ahead.getBlockY(), ahead.getBlockZ());
			if (!block.getType().isAir()) {
				Fx.particle(world, Particle.BLOCK, ahead.getX(), ahead.getY(), ahead.getZ(), 8, 0.4, 0.4, 0.4, 0.15, block.getBlockData());
			}
			Fx.particle(world, Particle.SQUID_INK, ahead, 2, 0.3, 0.3, 0.3, 0.02);
			if (tick % 9 == 0) {
				Fx.sound(world, ahead, Sound.ENTITY_SQUID_SQUIRT, 0.8F, 0.7F + (float) Math.random() * 0.3F);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		EntityDamageEvent.DamageCause cause = event.getCause();
		boolean environment = cause == EntityDamageEvent.DamageCause.SUFFOCATION || cause == EntityDamageEvent.DamageCause.DROWNING
				|| cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK
				|| cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.FALL;
		if (event.getEntity() instanceof Player player && isMoving(player) && environment) {
			event.setCancelled(true);
		} else if ((cause == EntityDamageEvent.DamageCause.SUFFOCATION || cause == EntityDamageEvent.DamageCause.FALL) && isHeld(event.getEntity())) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		if (isMoving(event.getPlayer())) {
			groundBoost(event.getPlayer(), true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		COOLDOWN.remove(id);
		LAST_POS.remove(id);
	}

	public static void clear() {
		for (Strike strike : STRIKES) {
			strike.segments.forEach(Entity::remove);
		}
		STRIKES.clear();
		GRABS.clear();
		THROWN.clear();
	}

	public static int activeStrikes() {
		return STRIKES.size();
	}
}
