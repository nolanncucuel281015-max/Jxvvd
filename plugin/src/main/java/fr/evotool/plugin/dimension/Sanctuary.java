package fr.evotool.plugin.dimension;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.BlockListener;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.boss.BellBoss;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Lang;
import java.util.Random;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Sanctuaires de la dimension évolutive : un par région de 20 x 20 tronçons (320 blocs), à une place tirée au hasard
 * d'après la graine du monde. Un temple circulaire en quartz, or et améthyste, entouré de 8 colonnes, avec au centre
 * l'autel qui réveille le Slime Cloche (clic droit).
 */
public final class Sanctuary extends BlockPopulator implements Listener {
	public static final int REGION_CHUNKS = 20;
	public static final int RADIUS = 12;
	public static final int BOSS_RANGE = 96;

	/** Une façon de poser des blocs : pendant la génération (région limitée) ou dans le monde. */
	private interface Placer {
		BlockData get(int x, int y, int z);

		void set(int x, int y, int z, BlockData data);

		int top(int x, int z);

		int minY();
	}

	public static void enable() {
		Sanctuary populator = new Sanctuary();
		EvoToolPlugin.listen(populator);
		World world = EvoDimension.world();
		if (world != null) {
			populator.attach(world);
		}
		BlockListener.register(CustomBlock.SANCTUARY_ALTAR, new BlockListener.Handler() {
			@Override
			public boolean used(Block block, Player player) {
				summon(block, player);
				return true;
			}
		});
		DimensionCommand.add("spawn", (player, args) -> {
			int form = 0;
			try {
				form = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
			} catch (NumberFormatException e) {
				form = 0;
			}
			Ravager boss = BellBoss.spawn(player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(4)), form);
			player.sendMessage(Lang.tr("command.evotool.dimension.spawned", Lang.tr(BellBoss.formKey(form)), EvoDimension.minutes())
					.color(NamedTextColor.GOLD));
			return boss != null;
		});
		DimensionCommand.add("evolve", (player, args) -> {
			var bosses = BellBoss.near(player.getLocation(), 64);
			if (bosses.isEmpty()) {
				player.sendMessage(Lang.tr("command.evotool.dimension.no_boss").color(NamedTextColor.RED));
			} else if (!BellBoss.evolve(bosses.get(0))) {
				player.sendMessage(Lang.tr("command.evotool.dimension.max_form").color(NamedTextColor.GRAY));
			}
			return true;
		});
		DimensionCommand.add("locate", (player, args) -> {
			World dimension = EvoDimension.world();
			if (dimension == null) {
				player.sendMessage(Lang.tr("command.evotool.dimension.missing").color(NamedTextColor.RED));
				return true;
			}
			int[] c = nearest(dimension.getSeed(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
			boolean inside = EvoDimension.isDimension(player.getWorld());
			int distance = (int) Math.hypot(c[0] - player.getLocation().getX(), c[1] - player.getLocation().getZ());
			Component coordinates = Component.text("[" + c[0] + ", ~, " + c[1] + "]", NamedTextColor.GREEN).decorate(TextDecoration.UNDERLINED)
					.clickEvent(ClickEvent.runCommand("/dimension tp sanctuaire"));
			player.sendMessage(Lang.tr(inside ? "command.evotool.dimension.locate" : "command.evotool.dimension.locate_outside", coordinates, distance)
					.color(NamedTextColor.LIGHT_PURPLE));
			return true;
		});
		DimensionCommand.add("tp_sanctuaire", (player, args) -> toSanctuary(player));
		DimensionCommand.add("tp_sanctuary", (player, args) -> toSanctuary(player));
	}

	private static boolean toSanctuary(Player player) {
		World dimension = EvoDimension.world();
		if (dimension == null) {
			player.sendMessage(Lang.tr("command.evotool.dimension.missing").color(NamedTextColor.RED));
			return true;
		}
		int[] c = nearest(dimension.getSeed(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
		dimension.getChunkAt(c[0] >> 4, c[1] >> 4);
		Block altar = findAltar(dimension, c[0], c[1]);
		int y = altar != null ? altar.getY() - 1 : dimension.getHighestBlockYAt(c[0], c[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
		player.teleport(new Location(dimension, c[0] + 0.5, y, c[1] + 5.5, 180, 0));
		player.sendMessage(Lang.tr(altar != null ? "command.evotool.dimension.tp_sanctuary" : "command.evotool.dimension.tp_sanctuary_missing")
				.color(NamedTextColor.LIGHT_PURPLE));
		return true;
	}

	private static Block findAltar(World world, int x, int z) {
		for (int y = world.getMaxHeight() - 1; y > world.getMinHeight(); y--) {
			Block block = world.getBlockAt(x, y, z);
			if (Blocks.at(block) == CustomBlock.SANCTUARY_ALTAR) {
				return block;
			}
		}
		return null;
	}

	@EventHandler
	public void onWorldInit(WorldInitEvent event) {
		if (EvoDimension.isDimension(event.getWorld())) {
			attach(event.getWorld());
		}
	}

	private void attach(World world) {
		if (!world.getPopulators().contains(this)) {
			world.getPopulators().add(this);
		}
	}

	// ------------------------------------------------------------------ emplacement

	public static int[] chunkOf(long seed, int rx, int rz) {
		Random random = new Random(seed ^ (rx * 341873128712L + 0x5DEECE66DL) ^ (rz * 132897987541L));
		int ox = 3 + random.nextInt(REGION_CHUNKS - 6);
		int oz = 3 + random.nextInt(REGION_CHUNKS - 6);
		return new int[] {rx * REGION_CHUNKS + ox, rz * REGION_CHUNKS + oz};
	}

	public static boolean isSanctuaryChunk(long seed, int cx, int cz) {
		int[] c = chunkOf(seed, Math.floorDiv(cx, REGION_CHUNKS), Math.floorDiv(cz, REGION_CHUNKS));
		return c[0] == cx && c[1] == cz;
	}

	/** Centre (x, z) du sanctuaire le plus proche. */
	public static int[] nearest(long seed, int x, int z) {
		int rx = Math.floorDiv(x >> 4, REGION_CHUNKS);
		int rz = Math.floorDiv(z >> 4, REGION_CHUNKS);
		int[] best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				int[] chunk = chunkOf(seed, rx + dx, rz + dz);
				int cx = (chunk[0] << 4) + 8;
				int cz = (chunk[1] << 4) + 8;
				double d = Math.pow(cx - x, 2) + Math.pow(cz - z, 2);
				if (d < bestDistance) {
					bestDistance = d;
					best = new int[] {cx, cz};
				}
			}
		}
		return best;
	}

	@Override
	public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
		if (!isSanctuaryChunk(info.getSeed(), chunkX, chunkZ)) {
			return;
		}
		build(new Placer() {
			@Override
			public BlockData get(int x, int y, int z) {
				return region.isInRegion(x, y, z) ? region.getBlockData(x, y, z) : Material.STONE.createBlockData();
			}

			@Override
			public void set(int x, int y, int z, BlockData data) {
				if (region.isInRegion(x, y, z)) {
					region.setBlockData(x, y, z, data);
				}
			}

			@Override
			public int top(int x, int z) {
				return region.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE_WG);
			}

			@Override
			public int minY() {
				return info.getMinHeight();
			}
		}, (chunkX << 4) + 8, (chunkZ << 4) + 8);
	}

	private static boolean natural(BlockData data) {
		Material type = data.getMaterial();
		if (type.isAir() || type == Material.WATER || type == Material.LAVA || !type.isSolid()) {
			return false;
		}
		CustomBlock custom = CustomBlock.of(data);
		return custom != CustomBlock.EVOLVING_LOG && custom != CustomBlock.EVOLVING_LEAVES && custom != CustomBlock.EVOLVING_CRYSTAL;
	}

	private static int ground(Placer placer, int x, int z) {
		int y = placer.top(x, z);
		while (y > placer.minY() + 1) {
			if (natural(placer.get(x, y, z))) {
				return y;
			}
			y--;
		}
		return 64;
	}

	private static BlockData data(Material material) {
		return material.createBlockData();
	}

	/** Construit le sanctuaire centré sur (cx, cz) ; renvoie la hauteur de l'autel. */
	private static int build(Placer world, int cx, int cz) {
		int[] samples = {ground(world, cx, cz), ground(world, cx + 7, cz), ground(world, cx - 7, cz), ground(world, cx, cz + 7), ground(world, cx, cz - 7)};
		java.util.Arrays.sort(samples);
		int y0 = samples[2];
		BlockData stone = CustomBlock.EVOLVING_STONE.data(0);
		BlockData crystal = CustomBlock.EVOLVING_CRYSTAL.data(0);
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > RADIUS + 0.5) {
					continue;
				}
				for (int dy = 1; dy <= 16; dy++) {
					if (!world.get(cx + dx, y0 + dy, cz + dz).getMaterial().isAir()) {
						world.set(cx + dx, y0 + dy, cz + dz, data(Material.AIR));
					}
				}
				for (int dy = -1; dy >= -12; dy--) {
					BlockData below = world.get(cx + dx, y0 + dy, cz + dz);
					if (natural(below) && below.getMaterial().isSolid() && dy < -1) {
						break;
					}
					world.set(cx + dx, y0 + dy, cz + dz, dy == -1 ? data(Material.QUARTZ_BRICKS) : stone);
					if (natural(below)) {
						break;
					}
				}
				world.set(cx + dx, y0, cz + dz, floor(dx, dz, r));
			}
		}
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				world.set(cx + dx, y0 + 1, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? data(Material.CHISELED_QUARTZ_BLOCK) : data(Material.QUARTZ_BRICKS));
			}
		}
		world.set(cx, y0 + 1, cz, data(Material.GOLD_BLOCK));
		world.set(cx, y0 + 2, cz, CustomBlock.SANCTUARY_ALTAR.data(0));
		for (int sx = -2; sx <= 2; sx += 4) {
			for (int sz = -2; sz <= 2; sz += 4) {
				world.set(cx + sx, y0 + 2, cz + sz, data(Material.QUARTZ_PILLAR));
				world.set(cx + sx, y0 + 3, cz + sz, crystal);
				world.set(cx + sx, y0 + 4, cz + sz, data(Material.END_ROD));
			}
		}
		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8;
			int px = cx + (int) Math.round(Math.cos(a) * 10);
			int pz = cz + (int) Math.round(Math.sin(a) * 10);
			world.set(px, y0 + 1, pz, data(Material.CHISELED_QUARTZ_BLOCK));
			for (int dy = 2; dy <= 7; dy++) {
				world.set(px, y0 + dy, pz, data(Material.QUARTZ_PILLAR));
			}
			world.set(px, y0 + 8, pz, data(Material.CHISELED_QUARTZ_BLOCK));
			world.set(px, y0 + 9, pz, data(Material.GOLD_BLOCK));
			world.set(px, y0 + 10, pz, crystal);
			world.set(px, y0 + 11, pz, data(Material.END_ROD));
		}
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 9.5 && r <= 10.5 && world.get(cx + dx, y0 + 8, cz + dz).getMaterial().isAir()) {
					world.set(cx + dx, y0 + 8, cz + dz, data(Material.SMOOTH_QUARTZ));
				}
				if (r > 7.6 && r <= 8.4 && (dx + 2 * dz) % 3 == 0) {
					world.set(cx + dx, y0 + 1, cz + dz, data(Material.AMETHYST_CLUSTER));
				}
			}
		}
		return y0 + 2;
	}

	private static BlockData floor(int dx, int dz, double r) {
		boolean ray = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
		if (r <= 2.5) {
			return data(Material.GOLD_BLOCK);
		}
		if (r <= 3.5) {
			return data(Material.CHISELED_QUARTZ_BLOCK);
		}
		if (r > 5.5 && r <= 6.5) {
			return ray ? data(Material.SEA_LANTERN) : data(Material.AMETHYST_BLOCK);
		}
		if (r <= 8.5) {
			return ray ? data(Material.PURPUR_BLOCK) : data(Material.QUARTZ_BRICKS);
		}
		if (r <= 9.5) {
			return ray ? data(Material.GOLD_BLOCK) : data(Material.CHISELED_QUARTZ_BLOCK);
		}
		return ray ? data(Material.PURPUR_BLOCK) : data(Material.SMOOTH_QUARTZ);
	}

	/** Construit un sanctuaire dans un monde déjà généré (auto-test) ; renvoie l'autel. */
	public static Block buildAt(World world, int cx, int cz) {
		int y = build(new Placer() {
			@Override
			public BlockData get(int x, int yy, int z) {
				return world.getBlockAt(x, yy, z).getBlockData();
			}

			@Override
			public void set(int x, int yy, int z, BlockData data) {
				world.getBlockAt(x, yy, z).setBlockData(data, false);
			}

			@Override
			public int top(int x, int z) {
				return world.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE);
			}

			@Override
			public int minY() {
				return world.getMinHeight();
			}
		}, cx, cz);
		return world.getBlockAt(cx, y, cz);
	}

	// ------------------------------------------------------------------ l'autel

	/** Réveille le boss devant l'autel ; null si un boss rôde déjà dans les parages. */
	public static Ravager summon(Block altar, Player player) {
		Location center = altar.getLocation().add(0.5, 0.5, 0.5);
		if (!BellBoss.near(center, BOSS_RANGE).isEmpty()) {
			if (player != null) {
				player.sendActionBar(Lang.tr("message.evotool.altar_busy").color(NamedTextColor.RED));
			}
			return null;
		}
		Block spawn = altar.getRelative(0, -1, 5);
		if (!spawn.getType().isAir() || !spawn.getRelative(0, 1, 0).getType().isAir() || !spawn.getRelative(0, 2, 0).getType().isAir()) {
			spawn = altar.getRelative(0, 1, 0);
		}
		Ravager boss = BellBoss.spawn(spawn.getLocation().add(0.5, 0, 0.5), 0);
		World world = altar.getWorld();
		Fx.sound(world, center.toVector(), Sound.BLOCK_END_PORTAL_SPAWN, 2.0F, 1.2F);
		Fx.sound(world, center.toVector(), Sound.BLOCK_BELL_RESONATE, 4.0F, 0.6F);
		for (double dy = 0; dy < 24; dy += 0.5) {
			Fx.particle(world, Particle.END_ROD, center.getX(), center.getY() + 0.5 + dy, center.getZ(), 2, 0.15, 0.1, 0.15, 0.01);
		}
		Fx.particle(world, Particle.TOTEM_OF_UNDYING, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.4);
		for (Player other : world.getPlayers()) {
			if (other.getLocation().distanceSquared(center) < BOSS_RANGE * BOSS_RANGE) {
				other.sendMessage(Lang.tr("message.evotool.boss_awakens", Lang.tr(BellBoss.formKey(0)), EvoDimension.minutes())
						.color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
			}
		}
		return boss;
	}

	static World dimension() {
		return Bukkit.getWorld(EvoDimension.WORLD);
	}
}
