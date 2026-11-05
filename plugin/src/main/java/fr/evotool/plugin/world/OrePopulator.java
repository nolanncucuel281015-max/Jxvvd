package fr.evotool.plugin.world;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.CustomBlock;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.LimitedRegion;
import org.bukkit.generator.WorldInfo;

/**
 * Les minerais du mod dans les nouveaux tronçons, comme dans le mod : évolium (sous y = -16, dans l'ardoise),
 * rubis et saphir dans le monde normal, évolite dans la pierre de l'End.
 */
public final class OrePopulator extends BlockPopulator implements Listener {
	private record Ore(CustomBlock stone, CustomBlock deepslate, int veins, int size, int min, int max, boolean trapezoid,
			double airDiscard) {
	}

	private static final Ore[] OVERWORLD = {
			new Ore(null, CustomBlock.DEEPSLATE_EVOLIUM_ORE, 5, 4, -64, -16, true, 0.5),
			new Ore(CustomBlock.RUBY_ORE, CustomBlock.DEEPSLATE_RUBY_ORE, 4, 6, -48, 48, true, 0.3),
			new Ore(CustomBlock.SAPPHIRE_ORE, CustomBlock.DEEPSLATE_SAPPHIRE_ORE, 4, 6, -32, 64, false, 0.3),
	};
	private static final Ore END = new Ore(CustomBlock.EVOLITE_ORE, null, 10, 3, 10, 90, false, 0.5);

	public static void enable() {
		if (!EvoToolPlugin.instance().getConfig().getBoolean("ores", true)) {
			return;
		}
		OrePopulator populator = new OrePopulator();
		EvoToolPlugin.listen(populator);
		for (World world : Bukkit.getWorlds()) {
			populator.attach(world);
		}
	}

	@EventHandler
	public void onWorldInit(WorldInitEvent event) {
		attach(event.getWorld());
	}

	private void attach(World world) {
		if ((world.getEnvironment() == World.Environment.NORMAL && !world.getKey().getNamespace().equals("evotool")
				|| world.getEnvironment() == World.Environment.THE_END) && !world.getPopulators().contains(this)) {
			world.getPopulators().add(this);
		}
	}

	@Override
	public void populate(WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
		if (info.getEnvironment() == World.Environment.THE_END) {
			place(END, info, random, chunkX, chunkZ, region);
		} else {
			for (Ore ore : OVERWORLD) {
				place(ore, info, random, chunkX, chunkZ, region);
			}
		}
	}

	private static void place(Ore ore, WorldInfo info, Random random, int chunkX, int chunkZ, LimitedRegion region) {
		for (int v = 0; v < ore.veins; v++) {
			int x = (chunkX << 4) + random.nextInt(16);
			int z = (chunkZ << 4) + random.nextInt(16);
			int range = ore.max - ore.min;
			int y = ore.trapezoid ? ore.min + random.nextInt(range / 2 + 1) + random.nextInt(range - range / 2 + 1) : ore.min + random.nextInt(range + 1);
			if (y < info.getMinHeight() || y >= info.getMaxHeight()) {
				continue;
			}
			vein(ore, random, x, y, z, region);
		}
	}

	/** Un filon : quelques blocs groupés autour du centre, seulement dans la roche qui convient. */
	private static void vein(Ore ore, Random random, int cx, int cy, int cz, LimitedRegion region) {
		int x = cx;
		int y = cy;
		int z = cz;
		for (int i = 0; i < ore.size; i++) {
			if (region.isInRegion(x, y, z)) {
				Material type = region.getType(x, y, z);
				CustomBlock block = null;
				if (ore == END) {
					block = type == Material.END_STONE ? ore.stone : null;
				} else if (Tag.DEEPSLATE_ORE_REPLACEABLES.isTagged(type)) {
					block = ore.deepslate;
				} else if (Tag.STONE_ORE_REPLACEABLES.isTagged(type)) {
					block = ore.stone;
				}
				if (block != null && !(exposed(region, x, y, z) && random.nextDouble() < ore.airDiscard)) {
					BlockData data = block.data(0);
					region.setBlockData(x, y, z, data);
				}
			}
			switch (random.nextInt(6)) {
				case 0 -> x++;
				case 1 -> x--;
				case 2 -> y++;
				case 3 -> y--;
				case 4 -> z++;
				default -> z--;
			}
		}
	}

	private static boolean exposed(LimitedRegion region, int x, int y, int z) {
		int[][] sides = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		for (int[] s : sides) {
			int nx = x + s[0];
			int ny = y + s[1];
			int nz = z + s[2];
			if (region.isInRegion(nx, ny, nz) && region.getType(nx, ny, nz).isAir()) {
				return true;
			}
		}
		return false;
	}
}
