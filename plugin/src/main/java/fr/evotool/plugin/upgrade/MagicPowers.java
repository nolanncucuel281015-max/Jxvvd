package fr.evotool.plugin.upgrade;

import fr.evotool.plugin.armor.ArmorSet;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.EvoliteUpgrade.Upgrade;
import fr.evotool.plugin.util.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** Les pouvoirs des objets améliorés à l'évolite, et l'explosion céleste de l'épée. */
public final class MagicPowers implements Listener {
	public static final int SLASH_COOLDOWN = 20;
	private static final double SLASH_RANGE = 8.0;
	private static final double SLASH_WIDTH = 1.6;
	public static final int SOAR_COOLDOWN = 60;
	private static final int SOAR_FALL_IMMUNITY_TICKS = 200;
	private static final double KNIGHT_SIGHT_RANGE = 24.0;
	private static final double EXPLOSION_RADIUS = 15.0;
	private static final double EXPLOSION_WAVE_SPEED = 2.0;
	public static final int EXPLOSION_COOLDOWN = 300;

	private record ExplosionWave(World world, Player player, Vector center, int startTick, double damage) {
	}

	private static final List<ExplosionWave> WAVES = new ArrayList<>();
	private static final Map<UUID, Integer> SOAR_IMMUNITY_UNTIL = new HashMap<>();

	/** Pas de dégâts de chute après un bond de la masse. */
	@EventHandler(ignoreCancelled = true)
	public void onFall(EntityDamageEvent event) {
		if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
			Integer until = SOAR_IMMUNITY_UNTIL.get(player.getUniqueId());
			if (until != null && Bukkit.getCurrentTick() <= until) {
				event.setCancelled(true);
			}
		}
	}

	public static void tick(int tick) {
		tickExplosionWaves(tick);
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (tick % 5 == 0) {
				lightningCharge(player);
			}
			if (tick % 20 == 0) {
				knightSight(player);
			}
		}
	}

	/** Épée améliorée : vague tranchante. Masse améliorée : bond. Renvoie le temps de recharge (0 : rien fait). */
	public static int use(Player player, ItemStack stack) {
		if (EvoliteUpgrade.has(stack, Upgrade.MAGIC_SLASH)) {
			magicSlash(player);
			return SLASH_COOLDOWN;
		}
		if (EvoliteUpgrade.has(stack, Upgrade.SOAR)) {
			Vector look = Fx.look(player);
			player.setVelocity(player.getVelocity().add(new Vector(look.getX() * 0.6, 1.4, look.getZ() * 0.6)));
			SOAR_IMMUNITY_UNTIL.put(player.getUniqueId(), Bukkit.getCurrentTick() + SOAR_FALL_IMMUNITY_TICKS);
			Location l = player.getLocation();
			Fx.particle(player.getWorld(), Particle.GUST, l.getX(), l.getY(), l.getZ(), 1, 0, 0, 0, 0);
			Fx.sound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0F, 0.7F);
			return SOAR_COOLDOWN;
		}
		return 0;
	}

	/** Vague tranchante : touche tous les monstres dans un couloir de 8 blocs devant le joueur. */
	private static void magicSlash(Player player) {
		World world = player.getWorld();
		Vector start = Fx.eye(player);
		Vector dir = Fx.look(player);
		double damage = player.getAttribute(Attribute.ATTACK_DAMAGE).getValue();
		for (double d = 1.0; d <= SLASH_RANGE; d += 0.8) {
			Vector p = start.clone().add(dir.clone().multiply(d));
			Fx.particle(world, Particle.SWEEP_ATTACK, p.getX(), p.getY() - 0.3, p.getZ(), 1, 0, 0, 0, 0);
			Fx.particle(world, Particle.END_ROD, p.getX(), p.getY() - 0.3, p.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
		}
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
		Vector end = start.clone().add(dir.clone().multiply(SLASH_RANGE));
		BoundingBox area = BoundingBox.of(start, end).expand(SLASH_WIDTH);
		for (Entity e : world.getNearbyEntities(area, e -> e != player && e instanceof LivingEntity l && !l.isDead() && !(e instanceof Player))) {
			LivingEntity target = (LivingEntity) e;
			Vector center = Fx.center(target);
			double along = center.clone().subtract(start).dot(dir);
			if (along < 0 || along > SLASH_RANGE + 1) {
				continue;
			}
			if (start.clone().add(dir.clone().multiply(along)).distance(center) <= SLASH_WIDTH + Fx.width(target) / 2) {
				Fx.hurt(player, target, damage);
				Fx.particle(world, Particle.ENCHANTED_HIT, center, 8, 0.3, 0.3, 0.3, 0.1);
			}
		}
	}

	/** Épée évolutive, accroupi + clic droit : explosion céleste de 15 blocs. */
	public static void celestialExplosion(Player player, int level) {
		World world = player.getWorld();
		Vector center = Fx.feet(player).add(new Vector(0, 0.5, 0));
		Fx.particle(world, Particle.EXPLOSION_EMITTER, center, 3, 1.0, 0.5, 1.0, 0.0);
		Fx.particle(world, Particle.SONIC_BOOM, center.getX(), center.getY() + 1, center.getZ(), 1, 0, 0, 0, 0);
		Fx.sound(player, Sound.ENTITY_GENERIC_EXPLODE, 4.0F, 0.6F);
		Fx.sound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.0F, 0.8F);
		WAVES.add(new ExplosionWave(world, player, center, Bukkit.getCurrentTick(), 14.0 + level * 0.2));
	}

	private static void tickExplosionWaves(int tick) {
		for (Iterator<ExplosionWave> it = WAVES.iterator(); it.hasNext(); ) {
			ExplosionWave wave = it.next();
			int age = tick - wave.startTick();
			double outer = Math.min(EXPLOSION_RADIUS, (age + 1) * EXPLOSION_WAVE_SPEED);
			double inner = age * EXPLOSION_WAVE_SPEED;
			if (inner >= EXPLOSION_RADIUS || !wave.player().isOnline()) {
				it.remove();
				continue;
			}
			World world = wave.world();
			Vector c = wave.center();
			int points = (int) (outer * 5);
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				double x = c.getX() + Math.cos(angle) * outer;
				double z = c.getZ() + Math.sin(angle) * outer;
				Fx.particle(world, i % 3 == 0 ? Particle.EXPLOSION : Particle.CLOUD, x, c.getY(), z, 1, 0.2, 0.3, 0.2, 0.02);
			}
			BoundingBox band = new BoundingBox(c.getX() - outer, c.getY() - 6, c.getZ() - outer, c.getX() + outer, c.getY() + 6, c.getZ() + outer);
			for (Entity e : world.getNearbyEntities(band, e -> e instanceof Enemy && e instanceof LivingEntity l && !l.isDead())) {
				LivingEntity target = (LivingEntity) e;
				Location tl = target.getLocation();
				double dist = Math.hypot(tl.getX() - c.getX(), tl.getZ() - c.getZ());
				if (dist < inner || dist > outer) {
					continue;
				}
				double falloff = 1.0 - dist / (EXPLOSION_RADIUS + 4.0);
				Fx.hurt(wave.player(), target, wave.damage() * falloff);
				Vector away = new Vector(tl.getX() - c.getX(), 0, tl.getZ() - c.getZ());
				if (away.lengthSquared() > 1.0E-4) {
					away.normalize().multiply(1.2 * falloff);
				}
				Fx.push(target, away.getX(), 0.4 * falloff, away.getZ());
			}
		}
	}

	/** Lance améliorée : pendant la charge, on court beaucoup plus vite. */
	private static void lightningCharge(Player player) {
		if (player.isHandRaised() && EvoliteUpgrade.has(player.getActiveItem(), Upgrade.LIGHTNING_CHARGE)) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10, 2, true, false, false));
			Location l = player.getLocation();
			Fx.particle(player.getWorld(), Particle.ELECTRIC_SPARK, l.getX(), l.getY() + 1, l.getZ(), 6, 0.4, 0.5, 0.4, 0.1);
		}
	}

	/** Casque amélioré : les monstres proches brillent à travers les murs. */
	private static void knightSight(Player player) {
		ItemStack helmet = ArmorSet.worn(player, EquipmentSlot.HEAD);
		if (helmet == null || !EvoliteUpgrade.has(helmet, Upgrade.KNIGHT_SIGHT)) {
			return;
		}
		for (Entity e : player.getWorld().getNearbyEntities(player.getBoundingBox().clone().expand(KNIGHT_SIGHT_RANGE),
				e -> e instanceof Enemy && e instanceof LivingEntity l && !l.isDead())) {
			((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false, false));
		}
	}

	public static boolean isSword(ItemStack stack) {
		return Items.kindOf(stack) == EvoKind.SWORD;
	}

	public static int level(ItemStack stack) {
		return EvoData.get(stack).level();
	}

	public static void forget(Player player) {
		SOAR_IMMUNITY_UNTIL.remove(player.getUniqueId());
	}
}
