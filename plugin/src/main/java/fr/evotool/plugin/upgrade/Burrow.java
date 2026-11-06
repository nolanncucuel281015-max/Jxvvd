package fr.evotool.plugin.upgrade;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * Bottes améliorées : plongée souterraine. Rester accroupi un instant sur le sol fait plonger dedans ; sous terre on
 * vole à travers la roche, trois fois plus vite qu'en créatif (espace pour monter, accroupi pour descendre). On
 * ressort en remontant à travers le sol, ou dès qu'on voit le ciel.
 *
 * <p>Sans mod, le jeu du joueur ne sait pas traverser les blocs en survie : sous terre, le joueur passe en mode
 * spectateur (il traverse la roche), limité au sous-sol (ni téléportation, ni caméra sur une entité, ni bedrock).
 */
public final class Burrow implements Listener {
	private static final NamespacedKey TAG = Keys.key("burrowing");
	/** Vitesse sous terre (Bukkit ; le mod : 0,15 interne = 0,3 ici). */
	public static final float SPEED = 0.3F;
	private static final int DIVE_TICKS = 6;
	private static final int START_GRACE = 40;
	private static final int FALL_IMMUNITY_TICKS = 100;
	private static final int NIGHT_VISION_TICKS = 260;

	private static final class State {
		boolean entered;
		boolean wasInside;
		int airTicks;
		double lastY;
		Location safe;
	}

	private static final Map<UUID, Integer> SNEAK = new HashMap<>();
	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Integer> FALL_IMMUNE_UNTIL = new HashMap<>();
	/** Le changement de mode de jeu vient de nous (pas d'une commande). */
	private static boolean switching;

	public static void enable() {
		EvoToolPlugin.listen(new Burrow());
		EvoToolPlugin.ticker(tick -> {
			for (Player player : Bukkit.getOnlinePlayers()) {
				tick(player, tick);
			}
		});
		InfinityWings.hideWhen(Burrow::isBurrowing);
	}

	public static boolean isBurrowing(Player player) {
		return player.getPersistentDataContainer().has(TAG);
	}

	public static boolean hasBoots(Player player) {
		return EvoliteUpgrade.has(player.getInventory().getBoots(), EvoliteUpgrade.Upgrade.BURROW);
	}

	/** D'autres pouvoirs qui creusent (les tentacules en mode déplacement), avec leur vitesse sous terre. */
	private static final java.util.List<java.util.Map.Entry<java.util.function.Predicate<Player>, Float>> DIGGERS = new java.util.ArrayList<>();

	public static void digger(java.util.function.Predicate<Player> condition, float speed) {
		DIGGERS.add(java.util.Map.entry(condition, speed));
	}

	private static Float diggerSpeed(Player player) {
		for (var digger : DIGGERS) {
			if (digger.getKey().test(player)) {
				return digger.getValue();
			}
		}
		return null;
	}

	private static float speed(Player player) {
		Float other = diggerSpeed(player);
		return other != null ? other : SPEED;
	}

	private static boolean canUse(Player player) {
		return (hasBoots(player) || diggerSpeed(player) != null) && !player.isDead() && !player.isInsideVehicle();
	}

	public static void tick(Player player, int tick) {
		UUID id = player.getUniqueId();
		if (isBurrowing(player)) {
			if (canUse(player) && player.getGameMode() == GameMode.SPECTATOR) {
				underground(player, tick);
			} else {
				stop(player, true);
			}
			return;
		}
		Block ground = player.getLocation().subtract(0, 0.2, 0).getBlock();
		boolean survival = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
		if (survival && canUse(player) && player.isSneaking() && ((org.bukkit.entity.Entity) player).isOnGround() && !player.isFlying()
				&& diggable(ground)) {
			int held = SNEAK.merge(id, 1, Integer::sum);
			Fx.particle(player.getWorld(), Particle.BLOCK, player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.1,
					ground.getBlockData());
			if (held >= (diggerSpeed(player) != null ? DIVE_TICKS / 2 : DIVE_TICKS)) {
				SNEAK.remove(id);
				start(player);
			}
		} else {
			SNEAK.remove(id);
		}
	}

	private static boolean diggable(Block block) {
		return !block.getType().isAir() && block.getType().getHardness() >= 0 && !block.isLiquid();
	}

	public static void start(Player player) {
		player.getPersistentDataContainer().set(TAG, PersistentDataType.STRING, player.getGameMode().name());
		State state = new State();
		state.lastY = player.getY();
		state.safe = player.getLocation();
		STATES.put(player.getUniqueId(), state);
		Block ground = player.getLocation().subtract(0, 0.2, 0).getBlock();
		gameMode(player, GameMode.SPECTATOR);
		player.setFlying(true);
		player.setFlySpeed(speed(player));
		// on s'enfonce d'un bloc dans le sol
		player.teleport(player.getLocation().add(0, -1.2, 0));
		Fx.particle(player.getWorld(), Particle.BLOCK, player.getX(), ground.getY() + 1.2, player.getZ(), 40, 0.4, 0.2, 0.4, 0.2,
				ground.getBlockData());
		Fx.sound(player.getWorld(), ground.getLocation().toVector(), ground.getBlockSoundGroup().getBreakSound(), 1.2F, 0.7F);
		Fx.sound(player.getWorld(), ground.getLocation().toVector(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.4F, 0.6F);
	}

	private static void gameMode(Player player, GameMode mode) {
		switching = true;
		try {
			player.setGameMode(mode);
		} finally {
			switching = false;
		}
	}

	private static void underground(Player player, int tick) {
		State state = STATES.get(player.getUniqueId());
		if (state == null) {
			state = new State();
			state.entered = true;
			state.lastY = player.getY();
			state.safe = player.getLocation();
			STATES.put(player.getUniqueId(), state);
		}
		if (player.getFlySpeed() != speed(player)) {
			player.setFlySpeed(speed(player));
		}
		World world = player.getWorld();
		BoundingBox box = player.getBoundingBox();
		// garde-fous : ni bedrock (ni aucun bloc incassable), ni fond du monde
		if (player.getY() < world.getMinHeight() + 1 || touchesUnbreakable(world, box)) {
			player.teleport(state.safe);
			return;
		}
		state.safe = player.getLocation();
		if (tick % 20 == 0) {
			player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
		}
		boolean inside = world.hasCollisionsIn(box.clone().expand(-0.01));
		double rise = player.getY() - state.lastY;
		state.lastY = player.getY();
		if (inside) {
			state.entered = true;
			state.airTicks = 0;
			if (tick % 3 == 0) {
				Block at = player.getLocation().add(0, 0.5, 0).getBlock();
				if (!at.getType().isAir()) {
					Fx.particle(world, Particle.BLOCK, player.getX(), player.getY() + 0.9, player.getZ(), 4, 0.3, 0.5, 0.3, 0.05, at.getBlockData());
				}
			}
		} else {
			state.airTicks++;
			boolean emerged = state.wasInside && rise > 0.02;
			Block eye = player.getEyeLocation().getBlock();
			boolean sky = eye.getLightFromSky() >= 15 || eye.getY() >= world.getHighestBlockYAt(eye.getX(), eye.getZ(), HeightMap.MOTION_BLOCKING);
			if (state.entered ? emerged || sky : state.airTicks > START_GRACE) {
				surface(player, state.entered);
				return;
			}
		}
		state.wasInside = inside;
	}

	public static boolean touchesUnbreakable(World world, BoundingBox box) {
		for (int x = (int) Math.floor(box.getMinX()); x <= (int) Math.floor(box.getMaxX()); x++) {
			for (int y = (int) Math.floor(box.getMinY()); y <= (int) Math.floor(box.getMaxY()); y++) {
				for (int z = (int) Math.floor(box.getMinZ()); z <= (int) Math.floor(box.getMaxZ()); z++) {
					Block block = world.getBlockAt(x, y, z);
					if (!block.getType().isAir() && block.getType().getHardness() < 0) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Sortie du sol : un bond hors de terre, sans dégâts de chute. */
	private static void surface(Player player, boolean leap) {
		stop(player, false);
		if (!leap) {
			return;
		}
		Block ground = player.getLocation().subtract(0, 0.5, 0).getBlock();
		if (!ground.getType().isAir()) {
			Fx.particle(player.getWorld(), Particle.BLOCK, player.getX(), player.getY(), player.getZ(), 50, 0.5, 0.3, 0.5, 0.3,
					ground.getBlockData());
			Fx.sound(player.getWorld(), ground.getLocation().toVector(), ground.getBlockSoundGroup().getBreakSound(), 1.2F, 0.8F);
		}
		Fx.particle(player.getWorld(), Particle.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.4, 0.2, 0.4, 0.05);
		player.setVelocity(player.getVelocity().add(new Vector(0, 0.55, 0)));
	}

	/**
	 * Fin de la plongée : le joueur retrouve son mode de jeu et son vol normal. Avec {@code rescue}, s'il est encore
	 * dans la roche (bottes enlevées sous terre…), il est remonté à la surface pour ne pas étouffer.
	 */
	public static void stop(Player player, boolean rescue) {
		String previous = player.getPersistentDataContainer().get(TAG, PersistentDataType.STRING);
		player.getPersistentDataContainer().remove(TAG);
		STATES.remove(player.getUniqueId());
		GameMode mode = GameMode.SURVIVAL;
		try {
			if (previous != null) {
				mode = GameMode.valueOf(previous);
			}
		} catch (IllegalArgumentException e) {
			mode = GameMode.SURVIVAL;
		}
		if (player.getGameMode() == GameMode.SPECTATOR) {
			gameMode(player, mode);
		}
		player.setFlySpeed(InfinityWings.DEFAULT_SPEED);
		InfinityWings.restore(player);
		PotionEffect vision = player.getPotionEffect(PotionEffectType.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= NIGHT_VISION_TICKS) {
			player.removePotionEffect(PotionEffectType.NIGHT_VISION);
		}
		FALL_IMMUNE_UNTIL.put(player.getUniqueId(), Bukkit.getCurrentTick() + FALL_IMMUNITY_TICKS);
		if (rescue && player.getWorld().hasCollisionsIn(player.getBoundingBox().clone().expand(-0.01))) {
			int top = player.getWorld().getHighestBlockYAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ(),
					HeightMap.MOTION_BLOCKING);
			Location up = player.getLocation();
			up.setY(top + 1.1);
			player.teleport(up);
		}
	}

	// ------------------------------------------------------------------ garde-fous du mode spectateur

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onTeleport(PlayerTeleportEvent event) {
		if (isBurrowing(event.getPlayer()) && event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onSpectate(PlayerStartSpectatingEntityEvent event) {
		if (isBurrowing(event.getPlayer())) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onGameMode(PlayerGameModeChangeEvent event) {
		if (!switching && isBurrowing(event.getPlayer())) {
			// le mode change autrement (commande…) : la plongée s'arrête, le nouveau mode reste
			event.getPlayer().getPersistentDataContainer().remove(TAG);
			STATES.remove(event.getPlayer().getUniqueId());
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
			return;
		}
		Integer until = FALL_IMMUNE_UNTIL.get(player.getUniqueId());
		if (until != null && Bukkit.getCurrentTick() <= until) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (isBurrowing(player) && (!canUse(player) || player.getGameMode() != GameMode.SPECTATOR)) {
			stop(player, true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		SNEAK.remove(id);
		STATES.remove(id);
		FALL_IMMUNE_UNTIL.remove(id);
	}

	/** Arrêt du serveur : personne ne reste en spectateur. */
	public static void clear() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			if (isBurrowing(player)) {
				stop(player, true);
			}
		}
	}
}
