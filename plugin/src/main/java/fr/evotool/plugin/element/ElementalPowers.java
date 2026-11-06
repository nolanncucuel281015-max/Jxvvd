package fr.evotool.plugin.element;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.upgrade.Burrow;
import fr.evotool.plugin.upgrade.InfinityWings;
import fr.evotool.plugin.util.Fx;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Pouvoirs des objets élémentaires (voir le mod) : l'arme ou l'outil frappe avec son élément, l'armure punit ceux qui
 * la frappent et donne ses pouvoirs permanents (vol de feu, nage, sprint électrique, vision nocturne, aura…). Les
 * bottes de vent font un double saut (saut en l'air) et celles d'eau marchent sur l'eau.
 */
public final class ElementalPowers implements Listener {
	/** Vitesse du vol de feu (Bukkit ; le mod : 0,07 interne). */
	public static final float FIRE_FLY_SPEED = 0.14F;
	private static final int EFFECT_TICKS = 60;
	private static final Map<UUID, Double> FALLING = new HashMap<>();
	/** Le double saut des bottes de vent a déjà servi pendant ce saut. */
	private static final Set<UUID> WIND_JUMPED = new HashSet<>();

	public static void enable() {
		EvoToolPlugin.listen(new ElementalPowers());
		EvoToolPlugin.ticker(tick -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				tick(player, tick);
			}
		});
		InfinityWings.otherFlight(player -> fireFlight(player) ? FIRE_FLY_SPEED : null);
	}

	private static boolean hasPiece(Player player, Element element) {
		return Elements.armorElements(player).contains(element);
	}

	public static boolean fireFlight(Player player) {
		return Elements.wears(player, EquipmentSlot.CHEST, Element.FIRE)
				&& (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) && !Burrow.isBurrowing(player);
	}

	private static boolean undead(LivingEntity e) {
		return Tag.ENTITY_TYPES_UNDEAD != null && Tag.ENTITY_TYPES_UNDEAD.isTagged(e.getType());
	}

	// ------------------------------------------------------------------ coups

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageByEntityEvent event) {
		if (Fx.applyingBonus() || event.getFinalDamage() <= 0 || !(event.getEntity() instanceof LivingEntity entity)) {
			return;
		}
		Entity damager = event.getDamager();
		Player attacker = damager instanceof Player p ? p
				: damager instanceof Projectile proj && proj.getShooter() instanceof Player p ? p : null;
		if (attacker != null && entity != attacker) {
			ItemStack weapon = attacker.getInventory().getItemInMainHand();
			Element element = Elements.get(weapon);
			if (element != null && Elements.armorSlot(weapon) == null) {
				Fx.applyingBonusRun(() -> onHit(attacker, entity, element));
			}
		}
		LivingEntity source = damager instanceof LivingEntity l ? l
				: damager instanceof Projectile proj && proj.getShooter() instanceof LivingEntity l ? l : null;
		if (entity instanceof Player player && source != null && source != player) {
			Fx.applyingBonusRun(() -> {
				for (Element element : Elements.armorElements(player)) {
					onHurt(player, source, element);
				}
			});
		}
	}

	/** L'effet de l'élément sur une cible (sorts du grimoire), sans relancer les pouvoirs en cascade. */
	public static void hitEffect(Player player, LivingEntity target, Element element) {
		if (target.isDead()) {
			return;
		}
		Fx.applyingBonusRun(() -> onHit(player, target, element));
	}

	private static void onHit(Player player, LivingEntity target, Element element) {
		World world = target.getWorld();
		Vector c = Fx.center(target);
		switch (element) {
			case ICE -> {
				target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3));
				target.setFreezeTicks(Math.max(target.getFreezeTicks(), target.getMaxFreezeTicks() + 60));
				Fx.particle(world, Particle.SNOWFLAKE, c, 25, 0.4, 0.5, 0.4, 0.05);
				Fx.sound(target, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0F, 1.2F);
			}
			case FIRE -> {
				target.setFireTicks(Math.max(target.getFireTicks(), 100));
				Fx.particle(world, Particle.FLAME, c, 25, 0.4, 0.5, 0.4, 0.05);
				Fx.sound(target, Sound.ITEM_FIRECHARGE_USE, 0.8F, 1.2F);
			}
			case LIGHTNING -> {
				List<LivingEntity> near = Fx.living(world, target.getBoundingBox().clone().expand(5.0), player);
				near.removeIf(e -> e == target || !(e instanceof Enemy));
				near.sort((a, b) -> Double.compare(a.getLocation().distanceSquared(target.getLocation()), b.getLocation().distanceSquared(target.getLocation())));
				for (LivingEntity other : near.subList(0, Math.min(3, near.size()))) {
					arc(world, c, Fx.center(other));
					Fx.hurt(player, other, 4.0);
				}
				Fx.particle(world, Particle.ELECTRIC_SPARK, c, 30, 0.4, 0.5, 0.4, 0.3);
				Fx.sound(target, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 1.6F);
			}
			case WATER -> {
				target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
				fr.evotool.plugin.weapon.ArcaneWeapons.heal(player, 2.0);
				Fx.particle(world, Particle.SPLASH, c, 40, 0.5, 0.5, 0.5, 0.2);
				Fx.particle(world, Particle.BUBBLE_POP, c, 15, 0.4, 0.4, 0.4, 0.05);
				Fx.sound(target, Sound.ENTITY_GENERIC_SPLASH, 1.0F, 1.0F);
			}
			case WIND -> {
				target.setVelocity(target.getVelocity().add(new Vector(0, 0.9, 0)));
				Fx.particle(world, Particle.CLOUD, c.getX(), target.getY(), c.getZ(), 20, 0.5, 0.1, 0.5, 0.1);
				Fx.sound(target, Sound.ENTITY_PHANTOM_FLAP, 1.2F, 0.7F);
			}
			case EARTH -> {
				target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 4));
				quake(player, Fx.feet(target), 3.0, 3.0, target);
			}
			case SHADOW -> {
				target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
				target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
				fr.evotool.plugin.weapon.ArcaneWeapons.heal(player, 1.0);
				Fx.particle(world, Particle.SQUID_INK, c, 20, 0.4, 0.5, 0.4, 0.05);
				Fx.particle(world, Particle.SCULK_SOUL, c, 6, 0.3, 0.4, 0.3, 0.03);
				Fx.sound(target, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0F, 1.4F);
			}
			case LIGHT -> {
				target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
				if (undead(target)) {
					target.setFireTicks(Math.max(target.getFireTicks(), 100));
					target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 0));
				}
				Fx.particle(world, Particle.END_ROD, c, 20, 0.4, 0.5, 0.4, 0.08);
				Fx.sound(target, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 1.8F);
			}
			case POISON -> {
				target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1));
				Fx.particle(world, Particle.ITEM_SLIME, c, 20, 0.4, 0.5, 0.4, 0.05);
				Fx.sound(target, Sound.ENTITY_SLIME_SQUISH, 1.0F, 1.4F);
			}
		}
	}

	/** Le sol tremble : les monstres autour (sauf {@code spared}) prennent des dégâts et décollent. */
	public static void quake(Player player, Vector at, double radius, double damage, LivingEntity spared) {
		World world = player.getWorld();
		Block below = world.getBlockAt(at.getBlockX(), (int) Math.floor(at.getY() - 0.5), at.getBlockZ());
		BlockData ground = below.getType().isAir() ? Material.DIRT.createBlockData() : below.getBlockData();
		Fx.particle(world, Particle.BLOCK, at.getX(), at.getY() + 0.1, at.getZ(), (int) (radius * 25), radius * 0.5, 0.1, radius * 0.5, 0.2, ground);
		Fx.sound(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 0.6F);
		for (LivingEntity other : Fx.living(world, Fx.box(at, radius).expand(0, 2.0 - radius, 0), player)) {
			if (other != spared && other instanceof Enemy) {
				Fx.hurt(player, other, damage);
				other.setVelocity(other.getVelocity().add(new Vector(0, 0.45, 0)));
			}
		}
	}

	/** Un arc électrique entre deux points. */
	public static void arc(World world, Vector from, Vector to) {
		Vector step = to.clone().subtract(from);
		int count = Math.max(3, (int) (step.length() * 3));
		for (int i = 0; i <= count; i++) {
			Vector p = from.clone().add(step.clone().multiply(i / (double) count));
			Fx.particle(world, Particle.ELECTRIC_SPARK, p, 2, 0.08, 0.08, 0.08, 0.02);
		}
	}

	// ------------------------------------------------------------------ armure frappée

	private static void onHurt(Player player, LivingEntity attacker, Element element) {
		World world = attacker.getWorld();
		Vector c = Fx.center(attacker);
		switch (element) {
			case ICE -> {
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
				attacker.setFreezeTicks(Math.max(attacker.getFreezeTicks(), attacker.getMaxFreezeTicks()));
				Fx.particle(world, Particle.SNOWFLAKE, c, 12, 0.3, 0.4, 0.3, 0.03);
			}
			case FIRE -> {
				attacker.setFireTicks(Math.max(attacker.getFireTicks(), 80));
				Fx.particle(world, Particle.FLAME, c, 12, 0.3, 0.4, 0.3, 0.03);
			}
			case LIGHT -> {
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 60, 0));
				if (undead(attacker)) {
					attacker.setFireTicks(Math.max(attacker.getFireTicks(), 80));
				}
				Fx.particle(world, Particle.END_ROD, c, 10, 0.3, 0.4, 0.3, 0.05);
			}
			case POISON -> {
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
				Fx.particle(world, Particle.ITEM_SLIME, c, 10, 0.3, 0.4, 0.3, 0.03);
			}
			case SHADOW -> {
				attacker.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 40, 0));
				Fx.particle(world, Particle.SQUID_INK, c, 10, 0.3, 0.4, 0.3, 0.03);
			}
			case LIGHTNING -> {
				arc(world, Fx.center(player), c);
				Fx.hurt(player, attacker, 3.0);
				Fx.sound(attacker, Sound.BLOCK_BEEHIVE_SHEAR, 0.4F, 2.0F);
			}
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEnvironment(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player)) {
			return;
		}
		if (event.getCause() == EntityDamageEvent.DamageCause.FALL
				&& (hasPiece(player, Element.WIND) || Elements.wears(player, EquipmentSlot.FEET, Element.EARTH) || MoveSafety.safe(player))) {
			event.setCancelled(true);   // le vent amortit les chutes ; la terre les encaisse (atterrissage sismique)
		} else if (event.getCause() == EntityDamageEvent.DamageCause.FREEZE && hasPiece(player, Element.ICE)) {
			event.setCancelled(true);
		}
	}

	// ------------------------------------------------------------------ double saut (bottes de vent)

	@EventHandler
	public void onInput(PlayerInputEvent event) {
		Player player = event.getPlayer();
		if (!event.getInput().isJump() || player.getCurrentInput().isJump() || ((Entity) player).isOnGround() || player.isFlying()
				|| player.isGliding() || !Elements.wears(player, EquipmentSlot.FEET, Element.WIND)
				|| WIND_JUMPED.contains(player.getUniqueId()) || player.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		WIND_JUMPED.add(player.getUniqueId());
		Vector v = player.getVelocity();
		Vector look = Fx.flatLook(player);
		player.setVelocity(new Vector(v.getX() + look.getX() * 0.2, 0.62, v.getZ() + look.getZ() * 0.2));
		player.setFallDistance(0);
		Fx.particle(player.getWorld(), Particle.CLOUD, player.getX(), player.getY(), player.getZ(), 15, 0.4, 0.05, 0.4, 0.05);
		Fx.particle(player.getWorld(), Particle.POOF, player.getX(), player.getY(), player.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		Fx.sound(player, Sound.ENTITY_PHANTOM_FLAP, 1.0F, 1.4F);
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		FALLING.remove(event.getPlayer().getUniqueId());
		WIND_JUMPED.remove(event.getPlayer().getUniqueId());
	}

	// ------------------------------------------------------------------ chaque tick

	private static void effect(Player player, PotionEffectType type, int amplifier) {
		player.addPotionEffect(new PotionEffect(type, EFFECT_TICKS, amplifier, true, false, true));
	}

	public static void tick(Player player, int tick) {
		if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		World world = player.getWorld();
		boolean onGround = ((Entity) player).isOnGround();
		if (onGround) {
			WIND_JUMPED.remove(player.getUniqueId());
		}
		if (tick % 20 == 0) {
			if (hasPiece(player, Element.FIRE)) {
				effect(player, PotionEffectType.FIRE_RESISTANCE, 0);
			}
			if (hasPiece(player, Element.WATER)) {
				effect(player, PotionEffectType.WATER_BREATHING, 0);
				if (Elements.wears(player, EquipmentSlot.CHEST, Element.WATER) && player.isInWater()) {
					effect(player, PotionEffectType.DOLPHINS_GRACE, 0);
					effect(player, PotionEffectType.CONDUIT_POWER, 0);
				}
			}
			if (Elements.wears(player, EquipmentSlot.LEGS, Element.LIGHTNING)) {
				effect(player, PotionEffectType.SPEED, 1);
			}
			if (hasPiece(player, Element.EARTH)) {
				effect(player, PotionEffectType.RESISTANCE, 0);
			}
			if (Elements.wears(player, EquipmentSlot.LEGS, Element.EARTH)) {
				effect(player, PotionEffectType.HASTE, 1);
			}
			if (Elements.wears(player, EquipmentSlot.HEAD, Element.LIGHT)) {
				for (Entity e : world.getNearbyEntities(player.getBoundingBox().clone().expand(24.0), e -> e instanceof Enemy && e instanceof LivingEntity)) {
					((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, true, false));
				}
			}
			if (Elements.wears(player, EquipmentSlot.CHEST, Element.LIGHT)) {
				PotionEffect regen = player.getPotionEffect(PotionEffectType.REGENERATION);
				if (regen == null || regen.getDuration() < 40) {
					player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 0, true, false, true));
				}
			}
			if (Elements.wears(player, EquipmentSlot.CHEST, Element.POISON) && tick % 40 == 0) {
				for (Entity e : world.getNearbyEntities(player.getBoundingBox().clone().expand(4.0), e -> e instanceof Enemy && e instanceof LivingEntity)) {
					((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
				}
				Fx.particle(world, Particle.ITEM_SLIME, player.getX(), player.getY() + 1.0, player.getZ(), 25, 2.0, 0.6, 2.0, 0.02);
			}
			if (Elements.wears(player, EquipmentSlot.HEAD, Element.SHADOW)) {
				player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 260, 0, true, false, true));
			}
		}
		if (hasPiece(player, Element.POISON) && player.hasPotionEffect(PotionEffectType.POISON)) {
			player.removePotionEffect(PotionEffectType.POISON);
		}
		if (Elements.wears(player, EquipmentSlot.CHEST, Element.SHADOW) && player.isSneaking()) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 10, 0, true, false, false));
			if (tick % 4 == 0) {
				Fx.particle(world, Particle.SQUID_INK, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
			}
		}
		if (Elements.wears(player, EquipmentSlot.FEET, Element.EARTH)) {
			double fall = player.getFallDistance();
			if (!onGround) {
				if (fall > 0) {
					FALLING.merge(player.getUniqueId(), fall, Math::max);
				}
			} else {
				Double fell = FALLING.remove(player.getUniqueId());
				if (fell != null && fell >= 4.0) {
					quake(player, Fx.feet(player), Math.min(6.0, 2.0 + fell / 3.0), Math.min(12.0, fell), null);
				}
			}
		} else {
			FALLING.remove(player.getUniqueId());
		}
		if (fireFlight(player) && player.isFlying() && tick % 2 == 0) {
			Fx.particle(world, Particle.FLAME, player.getX(), player.getY() - 0.1, player.getZ(), 4, 0.2, 0.05, 0.2, 0.02);
			Fx.particle(world, Particle.SMOKE, player.getX(), player.getY() - 0.2, player.getZ(), 1, 0.15, 0.05, 0.15, 0.01);
		}
		if (Elements.wears(player, EquipmentSlot.FEET, Element.ICE)) {
			if (onGround && !player.isSneaking()) {
				freezeWater(player);
			}
			if (player.isSprinting() && tick % 2 == 0) {
				Fx.particle(world, Particle.SNOWFLAKE, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
			}
		}
		if (Elements.wears(player, EquipmentSlot.FEET, Element.WATER) && !player.isSneaking()) {
			walkOnWater(player);
		}
		if (Elements.wears(player, EquipmentSlot.LEGS, Element.LIGHTNING) && player.isSprinting() && tick % 2 == 0) {
			Fx.particle(world, Particle.ELECTRIC_SPARK, player.getX(), player.getY() + 0.6, player.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
		}
	}

	/** Comme Semelles givrantes : l'eau calme autour des pieds devient de la glace qui fond toute seule. */
	static void freezeWater(Player player) {
		Block feet = player.getLocation().getBlock();
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				if (dx * dx + dz * dz > 6.5) {
					continue;
				}
				Block block = feet.getRelative(dx, -1, dz);
				if (!block.getRelative(0, 1, 0).getType().isAir()) {
					continue;
				}
				if (block.getType() == Material.WATER && block.getBlockData() instanceof Levelled water && water.getLevel() == 0) {
					block.setType(Material.FROSTED_ICE);
				}
			}
		}
	}

	/** Bottes d'eau : on marche à la surface de l'eau (accroupi pour plonger). */
	private static void walkOnWater(Player player) {
		Block feet = player.getLocation().getBlock();
		Block below = player.getLocation().subtract(0, 0.05, 0).getBlock();
		if (feet.getType().isAir() && below.getType() == Material.WATER) {
			Vector v = player.getVelocity();
			if (v.getY() < 0) {
				player.setVelocity(new Vector(v.getX(), 0.0, v.getZ()));
			}
			player.setFallDistance(0);
			if (ThreadLocalRandom.current().nextInt(4) == 0) {
				Fx.particle(player.getWorld(), Particle.SPLASH, player.getX(), player.getY(), player.getZ(), 3, 0.3, 0.0, 0.3, 0.0);
			}
		} else if (feet.getType() == Material.WATER && feet.getRelative(0, 1, 0).getType().isAir() && !player.isSwimming()) {
			Vector v = player.getVelocity();
			player.setVelocity(new Vector(v.getX(), Math.max(v.getY(), 0.12), v.getZ()));   // remonte à la surface
		}
	}

	/** Les déplacements du grimoire protègent aussi des chutes (voir MoveSpells). */
	public static final class MoveSafety {
		private static final Map<UUID, Integer> SAFE_UNTIL = new HashMap<>();

		private MoveSafety() {
		}

		public static void protect(Player player, int ticks) {
			SAFE_UNTIL.merge(player.getUniqueId(), Bukkit.getCurrentTick() + ticks, Math::max);
		}

		public static boolean safe(Player player) {
			Integer until = SAFE_UNTIL.get(player.getUniqueId());
			return until != null && Bukkit.getCurrentTick() <= until;
		}
	}
}
