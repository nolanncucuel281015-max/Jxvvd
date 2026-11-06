package fr.evotool.plugin.dimension;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Dimension évolutive : plus un joueur y reste longtemps, plus tout évolue autour de lui. Toutes les 5 minutes
 * (réglable avec /dimension speed) passées dans la dimension sans en sortir, la dimension passe une phase (0 à 5) :
 * une onde d'évolution part du joueur et les blocs évolutifs proches rattrapent cette phase.
 */
public final class EvoDimension implements Listener {
	public static final NamespacedKey WORLD = new NamespacedKey("evotool", "evolutive");
	public static final int MAX_PHASE = 5;
	public static final int DEFAULT_MINUTES = 5;
	public static final int MAX_MINUTES = 120;
	private static final int WAVE_RADIUS = 40;
	private static final int WAVE_HALF_HEIGHT = 20;
	/** Blocs tirés au hasard autour de chaque joueur, chaque seconde, qui rattrapent sa phase (comme les ticks aléatoires). */
	private static final int RANDOM_BLOCKS = 600;

	private record Wave(World world, int x, int y, int z, int phase, int[] radius) {
	}

	private static final Map<UUID, BossBar> BARS = new HashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();

	public static void enable() {
		EvoToolPlugin.listen(new EvoDimension());
		EvoToolPlugin.ticker(EvoDimension::tick);
		World world = world();
		EvoToolPlugin.instance().getLogger().info(world != null ? "Dimension évolutive chargée : " + world.getName()
				: "Dimension évolutive absente (le pack de données n'a pas été chargé)");
	}

	public static World world() {
		return Bukkit.getWorld(WORLD);
	}

	public static boolean isDimension(World world) {
		return world != null && world.getKey().equals(WORLD);
	}

	public static int minutes() {
		return Math.max(1, Math.min(MAX_MINUTES, EvoToolPlugin.instance().getConfig().getInt("dimension.minutes", DEFAULT_MINUTES)));
	}

	public static void setMinutes(int value) {
		EvoToolPlugin.instance().getConfig().set("dimension.minutes", Math.max(1, Math.min(MAX_MINUTES, value)));
		EvoToolPlugin.instance().saveConfig();
	}

	public static int intervalTicks() {
		return minutes() * 60 * 20;
	}

	public static int time(Player player) {
		return player.getPersistentDataContainer().getOrDefault(Keys.DIMENSION_TIME, PersistentDataType.INTEGER, 0);
	}

	private static void setTime(Player player, int ticks) {
		player.getPersistentDataContainer().set(Keys.DIMENSION_TIME, PersistentDataType.INTEGER, ticks);
	}

	public static int phaseOf(int ticks) {
		return Math.min(MAX_PHASE, ticks / intervalTicks());
	}

	public static int phase(Player player) {
		return phaseOf(time(player));
	}

	/** Place le joueur au début de la phase voulue (commande /dimension phase). */
	public static void setPhase(Player player, int phase) {
		setTime(player, Math.max(0, Math.min(MAX_PHASE, phase)) * intervalTicks() + 1);
		if (isDimension(player.getWorld())) {
			startWave(player.getWorld(), player.getLocation().getBlock(), phase(player));
		}
	}

	private static void tick(int tick) {
		World world = world();
		if (world != null) {
			for (Player player : world.getPlayers()) {
				if (player.getGameMode() == GameMode.SPECTATOR && !fr.evotool.plugin.upgrade.Burrow.isBurrowing(player)) {
					continue;
				}
				int before = time(player);
				int after = before + 1;
				setTime(player, after);
				if (phaseOf(after) > phaseOf(before)) {
					phaseUp(player, phaseOf(after));
				}
				updateBar(player, after);
				if (tick % 20 == 0) {
					evolveAround(player);
				}
			}
		}
		tickWaves();
		if (tick % 20 == 0) {
			BARS.entrySet().removeIf(entry -> {
				Player player = Bukkit.getPlayer(entry.getKey());
				if (player == null || !isDimension(player.getWorld())) {
					if (player != null) {
						player.hideBossBar(entry.getValue());
					}
					return true;
				}
				return false;
			});
		}
	}

	private static void phaseUp(Player player, int phase) {
		player.sendMessage(Lang.tr("message.evotool.phase_up", phase, MAX_PHASE).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
		Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 2.0F, 0.5F + phase * 0.1F);
		Fx.sound(player, Sound.BLOCK_BELL_RESONATE, 2.0F, 0.8F);
		Fx.particle(player.getWorld(), Particle.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 120, 3, 2, 3, 0.15);
		startWave(player.getWorld(), player.getLocation().getBlock(), phase);
	}

	public static void startWave(World world, Block center, int phase) {
		WAVES.add(new Wave(world, center.getX(), center.getY(), center.getZ(), phase, new int[] {0}));
	}

	/** Fait évoluer le bloc évolutif jusqu'à cette phase ; vrai s'il a changé. */
	public static boolean evolveTo(Block block, int phase) {
		CustomBlock custom = Blocks.at(block);
		if (custom == null || !custom.evolving()) {
			return false;
		}
		int variant = Blocks.variant(block);
		int evolution = CustomBlock.evolutionOf(custom, variant);
		if (evolution >= phase) {
			return false;
		}
		int axis = custom == CustomBlock.EVOLVING_LOG ? variant % 3 : 0;
		Blocks.place(block, custom, custom.variant(phase, axis));
		return true;
	}

	private static void tickWaves() {
		for (Iterator<Wave> it = WAVES.iterator(); it.hasNext(); ) {
			Wave wave = it.next();
			int r = ++wave.radius()[0];
			if (r > WAVE_RADIUS) {
				it.remove();
				continue;
			}
			World world = wave.world();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r || d <= r - 1) {
						continue;
					}
					int x = wave.x() + dx;
					int z = wave.z() + dz;
					if (!world.isChunkLoaded(x >> 4, z >> 4)) {
						continue;
					}
					boolean shown = false;
					for (int dy = -WAVE_HALF_HEIGHT; dy <= WAVE_HALF_HEIGHT; dy++) {
						int y = wave.y() + dy;
						if (y < world.getMinHeight() || y >= world.getMaxHeight()) {
							continue;
						}
						Block block = world.getBlockAt(x, y, z);
						if (block.getType() == org.bukkit.Material.NOTE_BLOCK && evolveTo(block, wave.phase())) {
							if (!shown && (dx + dz) % 3 == 0 && block.getRelative(0, 1, 0).getType().isAir()) {
								Fx.particle(world, Particle.END_ROD, x + 0.5, y + 1.1, z + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
								shown = true;
							}
						}
					}
				}
			}
		}
	}

	/** Comme les ticks aléatoires du mod : des blocs au hasard autour du joueur gagnent une évolution s'ils sont en retard. */
	private static void evolveAround(Player player) {
		int phase = phase(player);
		if (phase <= 0) {
			return;
		}
		World world = player.getWorld();
		ThreadLocalRandom random = ThreadLocalRandom.current();
		Block feet = player.getLocation().getBlock();
		for (int i = 0; i < RANDOM_BLOCKS; i++) {
			int x = feet.getX() + random.nextInt(-48, 49);
			int y = feet.getY() + random.nextInt(-16, 17);
			int z = feet.getZ() + random.nextInt(-48, 49);
			if (y < world.getMinHeight() || y >= world.getMaxHeight() || !world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			Block block = world.getBlockAt(x, y, z);
			CustomBlock custom = block.getType() == org.bukkit.Material.NOTE_BLOCK ? Blocks.at(block) : null;
			if (custom != null && custom.evolving()) {
				int evolution = CustomBlock.evolutionOf(custom, Blocks.variant(block));
				if (evolution < phase && evolveTo(block, evolution + 1)) {
					Fx.particle(world, Particle.END_ROD, x + 0.5, y + 1.05, z + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
				}
			}
		}
	}

	private static void updateBar(Player player, int ticks) {
		BossBar bar = BARS.computeIfAbsent(player.getUniqueId(), id -> {
			BossBar b = BossBar.bossBar(Component.empty(), 0, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
			player.showBossBar(b);
			return b;
		});
		if (ticks % 20 != 1) {
			return;
		}
		int phase = phaseOf(ticks);
		if (phase >= MAX_PHASE) {
			bar.name(Lang.tr("bar.evotool.dimension_max", MAX_PHASE).color(NamedTextColor.GOLD));
			bar.progress(1.0F);
			bar.color(BossBar.Color.YELLOW);
			return;
		}
		int interval = intervalTicks();
		int left = (interval - ticks % interval) / 20;
		bar.name(Lang.tr("bar.evotool.dimension", phase, MAX_PHASE, String.format("%d:%02d", left / 60, left % 60)).color(NamedTextColor.LIGHT_PURPLE));
		bar.progress(Math.max(0, Math.min(1, (ticks % interval) / (float) interval)));
		bar.color(BossBar.Color.PURPLE);
	}

	@EventHandler
	public void onChangeWorld(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		if (isDimension(event.getFrom()) && !isDimension(player.getWorld())) {
			setTime(player, 0);   // on repart de zéro à la prochaine visite
			BossBar bar = BARS.remove(player.getUniqueId());
			if (bar != null) {
				player.hideBossBar(bar);
			}
		} else if (isDimension(player.getWorld())) {
			player.sendMessage(Lang.tr("message.evotool.enter_dimension").color(NamedTextColor.LIGHT_PURPLE));
			Fx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 0.6F);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		BARS.remove(event.getPlayer().getUniqueId());
	}
}
