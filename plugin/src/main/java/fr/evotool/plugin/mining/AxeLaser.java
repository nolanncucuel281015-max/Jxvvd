package fr.evotool.plugin.mining;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Fx;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Hache Évolutive, clic droit (dans le vide ou sur un bloc qui ne s'écorce pas) : un rayon laser fait le tour du
 * joueur en 2 secondes et coupe tous les arbres à 50 blocs. Le bois et les feuilles reviennent au joueur.
 */
public final class AxeLaser {
	private static final int RADIUS = 50;
	private static final int SWEEP_TICKS = 40;
	public static final int COOLDOWN = 200;
	private static final int BELOW = 16;
	private static final int ABOVE = 48;
	private static final int MAX_TREE_LOGS = 400;
	private static final List<List<int[]>> SECTORS = new ArrayList<>();

	static {
		for (int i = 0; i < SWEEP_TICKS; i++) {
			SECTORS.add(new ArrayList<>());
		}
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				double angle = Math.atan2(dz, dx);
				if (angle < 0) {
					angle += 2 * Math.PI;
				}
				int sector = Math.min(SWEEP_TICKS - 1, (int) (angle / (2 * Math.PI) * SWEEP_TICKS));
				SECTORS.get(sector).add(new int[] {dx, dz});
			}
		}
	}

	private static final class Sweep {
		final World world;
		final Player player;
		final ItemStack axe;
		final Location center;
		final int startTick;
		final boolean telekinesis;
		final boolean replant;
		final Set<Block> treeLogs = new HashSet<>();
		int blocks;
		int xp;

		Sweep(Player player, ItemStack axe, int startTick, boolean telekinesis, boolean replant) {
			this.world = player.getWorld();
			this.player = player;
			this.axe = axe;
			this.center = player.getLocation().getBlock().getLocation();
			this.startTick = startTick;
			this.telekinesis = telekinesis;
			this.replant = replant;
		}
	}

	private static final List<Sweep> SWEEPS = new ArrayList<>();

	private AxeLaser() {
	}

	/** La hache doit-elle tirer le laser ? (pas quand on écorce une bûche) */
	public static boolean wouldStrip(Block clicked) {
		return clicked != null && Tag.LOGS.isTagged(clicked.getType()) && !clicked.getType().name().startsWith("STRIPPED_");
	}

	public static void start(Player player, ItemStack axe) {
		EvoData data = EvoData.get(axe);
		SWEEPS.add(new Sweep(player, axe, Bukkit.getCurrentTick(), EvoliteUpgrade.has(axe, EvoliteUpgrade.Upgrade.TELEKINESIS),
				Progression.isActive(EvoKind.AXE.profile(), data, Power.AUTO_REPLANT)));
		Fx.sound(player, Sound.BLOCK_BEACON_ACTIVATE, 1.5F, 1.4F);
	}

	public static void tick(int tick) {
		for (Iterator<Sweep> it = SWEEPS.iterator(); it.hasNext(); ) {
			Sweep sweep = it.next();
			int step = tick - sweep.startTick;
			if (step >= SWEEP_TICKS || !sweep.player.isOnline()) {
				if (sweep.xp > 0 && sweep.player.isOnline() && sweep.player.getGameMode() != GameMode.CREATIVE) {
					Progression.addXp(sweep.axe, sweep.xp, sweep.blocks, sweep.player);
				}
				it.remove();
				continue;
			}
			beam(sweep, step);
			List<ItemStack> drops = new ArrayList<>();
			for (int[] column : SECTORS.get(step)) {
				cutColumn(sweep, sweep.center.getBlockX() + column[0], sweep.center.getBlockZ() + column[1], drops);
			}
			Location at = sweep.player.getLocation();
			MiningListener.deliver(sweep.player, at, drops, false, sweep.telekinesis);
		}
	}

	private static void beam(Sweep sweep, int step) {
		double angle = (step + 0.5) / SWEEP_TICKS * 2 * Math.PI;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		Location p = sweep.player.getLocation();
		double y = p.getY() + 1.2;
		for (double d = 1.5; d <= RADIUS; d += 1.0) {
			double x = p.getX() + cos * d;
			double z = p.getZ() + sin * d;
			Fx.particle(sweep.world, Particle.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
			if (((int) d) % 4 == 0) {
				Fx.particle(sweep.world, Particle.ELECTRIC_SPARK, x, y, z, 2, 0.2, 0.2, 0.2, 0.05);
			}
		}
		if (step % 4 == 0) {
			Fx.sound(sweep.player, Sound.ENTITY_GUARDIAN_ATTACK, 0.7F, 1.6F);
		}
	}

	private static void cutColumn(Sweep sweep, int x, int z, List<ItemStack> drops) {
		World world = sweep.world;
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		int top = Math.min(world.getMaxHeight() - 1, sweep.center.getBlockY() + ABOVE);
		int bottom = Math.max(world.getMinHeight(), sweep.center.getBlockY() - BELOW);
		for (int y = top; y >= bottom; y--) {
			Block block = world.getBlockAt(x, y, z);
			if (!sweep.treeLogs.contains(block) && MiningListener.isLog(block) && isTreeLog(block)) {
				markTree(block, sweep.treeLogs);
			}
		}
		for (int y = top; y >= bottom; y--) {
			Block block = world.getBlockAt(x, y, z);
			boolean log = MiningListener.isLog(block) && sweep.treeLogs.contains(block);
			if (!log && !MiningListener.isNaturalLeaves(block)) {
				continue;
			}
			BlockBreakEvent check = new BlockBreakEvent(block, sweep.player);
			check.setDropItems(false);
			Bukkit.getPluginManager().callEvent(check);
			if (check.isCancelled()) {
				continue;
			}
			boolean onGround = log && Tag.DIRT.isTagged(block.getRelative(BlockFace.DOWN).getType());
			Material type = block.getType();
			if (sweep.player.getGameMode() != GameMode.CREATIVE) {
				drops.addAll(MiningListener.dropsOf(block, sweep.axe, sweep.player));
			}
			block.setType(Material.AIR);
			sweep.blocks++;
			if (log) {
				sweep.xp++;
			}
			if (onGround && sweep.replant) {
				replant(block, type);
			}
		}
	}

	/** Un tronc d'arbre (et pas une maison en bûches) : il y a des feuilles naturelles juste autour ou au-dessus. */
	private static boolean isTreeLog(Block block) {
		for (int dy = -1; dy <= 14; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (MiningListener.isNaturalLeaves(block.getRelative(dx, dy, dz))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static void markTree(Block start, Set<Block> treeLogs) {
		ArrayDeque<Block> queue = new ArrayDeque<>();
		queue.add(start);
		treeLogs.add(start);
		int marked = 1;
		while (!queue.isEmpty() && marked < MAX_TREE_LOGS) {
			Block current = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						Block next = current.getRelative(dx, dy, dz);
						if (!treeLogs.contains(next) && MiningListener.isLog(next)) {
							treeLogs.add(next);
							queue.add(next);
							marked++;
						}
					}
				}
			}
		}
	}

	/** Replantation : la pousse qui correspond au bois coupé. */
	private static void replant(Block block, Material log) {
		String name = log.name();
		String sapling = name.startsWith("MANGROVE") ? "MANGROVE_PROPAGULE"
				: name.replace("STRIPPED_", "").replace("_LOG", "_SAPLING").replace("_WOOD", "_SAPLING");
		Material material = Material.matchMaterial(sapling);
		if (material != null && block.getType().isAir()) {
			block.setType(material);
		}
	}
}
