package fr.evotool.dimension;

import fr.evotool.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;

/**
 * Sanctuaires de la dimension évolutive : un par région de 20 x 20 chunks (320 blocs), à une place
 * tirée au hasard d'après la graine du monde. C'est un temple circulaire en quartz, or et améthyste,
 * entouré de 8 colonnes, avec au centre l'autel qui réveille le Slime Cloche.
 */
public final class Sanctuary {
	public static final int REGION_CHUNKS = 20;
	/** Rayon du parvis (le temple tient dans le chunk et ses voisins, comme l'exige la génération du monde). */
	public static final int RADIUS = 12;

	private Sanctuary() {
	}

	/** Chunk du sanctuaire de la région (rx, rz). */
	public static ChunkPos chunkOf(long seed, int rx, int rz) {
		java.util.Random random = new java.util.Random(seed ^ (rx * 341873128712L + 0x5DEECE66DL) ^ (rz * 132897987541L));
		int ox = 3 + random.nextInt(REGION_CHUNKS - 6);
		int oz = 3 + random.nextInt(REGION_CHUNKS - 6);
		return new ChunkPos(rx * REGION_CHUNKS + ox, rz * REGION_CHUNKS + oz);
	}

	public static boolean isSanctuaryChunk(long seed, ChunkPos chunk) {
		return chunkOf(seed, Math.floorDiv(chunk.x, REGION_CHUNKS), Math.floorDiv(chunk.z, REGION_CHUNKS)).equals(chunk);
	}

	/** Centre (y = 0) du sanctuaire le plus proche de (x, z). */
	public static BlockPos nearest(long seed, int x, int z) {
		int rx = Math.floorDiv(x >> 4, REGION_CHUNKS);
		int rz = Math.floorDiv(z >> 4, REGION_CHUNKS);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				ChunkPos chunk = chunkOf(seed, rx + dx, rz + dz);
				BlockPos center = new BlockPos(chunk.getCenterX(), 0, chunk.getCenterZ());
				double d = Math.pow(center.getX() - x, 2) + Math.pow(center.getZ() - z, 2);
				if (d < bestDistance) {
					bestDistance = d;
					best = center;
				}
			}
		}
		return best;
	}

	/** Sol naturel sous (x, z) : on ignore les arbres, les feuilles, les cristaux et l'eau. */
	private static int ground(StructureWorldAccess world, int x, int z, Heightmap.Type heightmap) {
		BlockPos.Mutable p = new BlockPos.Mutable(x, world.getTopY(heightmap, x, z), z);
		while (p.getY() > world.getBottomY() + 1) {
			BlockState state = world.getBlockState(p);
			boolean natural = !state.isAir() && state.getFluidState().isEmpty() && !state.isReplaceable()
					&& !state.isOf(ModBlocks.EVOLVING_LOG) && !state.isOf(ModBlocks.EVOLVING_LEAVES) && !state.isOf(ModBlocks.EVOLVING_CRYSTAL);
			if (natural) {
				return p.getY();
			}
			p.move(0, -1, 0);
		}
		return 64;
	}

	/** Construit le sanctuaire centré sur (cx, cz) ; renvoie la position de l'autel. */
	public static BlockPos build(StructureWorldAccess world, int cx, int cz, Heightmap.Type heightmap) {
		int[] samples = {
				ground(world, cx, cz, heightmap), ground(world, cx + 7, cz, heightmap), ground(world, cx - 7, cz, heightmap),
				ground(world, cx, cz + 7, heightmap), ground(world, cx, cz - 7, heightmap)};
		java.util.Arrays.sort(samples);
		int y0 = samples[2];
		BlockPos.Mutable p = new BlockPos.Mutable();
		for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++) {
			for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > RADIUS + 0.5) {
					continue;
				}
				// on dégage le ciel au-dessus du parvis
				for (int dy = 1; dy <= 16; dy++) {
					p.set(cx + dx, y0 + dy, cz + dz);
					if (!world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// fondations jusqu'au sol
				for (int dy = -1; dy >= -12; dy--) {
					p.set(cx + dx, y0 + dy, cz + dz);
					BlockState below = world.getBlockState(p);
					if (!below.isAir() && below.getFluidState().isEmpty() && !below.isReplaceable()) {
						break;
					}
					world.setBlockState(p, dy == -1 ? Blocks.QUARTZ_BRICKS.getDefaultState() : ModBlocks.EVOLVING_STONE.getDefaultState(),
							Block.NOTIFY_LISTENERS);
				}
				p.set(cx + dx, y0, cz + dz);
				world.setBlockState(p, floor(dx, dz, r), Block.NOTIFY_LISTENERS);
			}
		}
		// estrade et autel
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockState step = Math.abs(dx) == 2 || Math.abs(dz) == 2 ? Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState()
						: Blocks.QUARTZ_BRICKS.getDefaultState();
				world.setBlockState(new BlockPos(cx + dx, y0 + 1, cz + dz), step, Block.NOTIFY_LISTENERS);
			}
		}
		world.setBlockState(new BlockPos(cx, y0 + 1, cz), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
		BlockPos altar = new BlockPos(cx, y0 + 2, cz);
		world.setBlockState(altar, ModBlocks.SANCTUARY_ALTAR.getDefaultState(), Block.NOTIFY_LISTENERS);
		for (int sx = -2; sx <= 2; sx += 4) {
			for (int sz = -2; sz <= 2; sz += 4) {
				world.setBlockState(new BlockPos(cx + sx, y0 + 2, cz + sz), Blocks.QUARTZ_PILLAR.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(new BlockPos(cx + sx, y0 + 3, cz + sz), ModBlocks.EVOLVING_CRYSTAL.getDefaultState(), Block.NOTIFY_LISTENERS);
				world.setBlockState(new BlockPos(cx + sx, y0 + 4, cz + sz), Blocks.END_ROD.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		// 8 colonnes reliées par un anneau de quartz, couronnées d'or et de cristal
		for (int k = 0; k < 8; k++) {
			double a = Math.PI * 2 * k / 8;
			int px = cx + (int) Math.round(Math.cos(a) * 10);
			int pz = cz + (int) Math.round(Math.sin(a) * 10);
			world.setBlockState(new BlockPos(px, y0 + 1, pz), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			for (int dy = 2; dy <= 7; dy++) {
				world.setBlockState(new BlockPos(px, y0 + dy, pz), Blocks.QUARTZ_PILLAR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
			world.setBlockState(new BlockPos(px, y0 + 8, pz), Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(new BlockPos(px, y0 + 9, pz), Blocks.GOLD_BLOCK.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(new BlockPos(px, y0 + 10, pz), ModBlocks.EVOLVING_CRYSTAL.getDefaultState(), Block.NOTIFY_LISTENERS);
			world.setBlockState(new BlockPos(px, y0 + 11, pz), Blocks.END_ROD.getDefaultState(), Block.NOTIFY_LISTENERS);
		}
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				double r = Math.sqrt(dx * dx + dz * dz);
				if (r > 9.5 && r <= 10.5) {
					p.set(cx + dx, y0 + 8, cz + dz);
					if (world.getBlockState(p).isAir()) {
						world.setBlockState(p, Blocks.SMOOTH_QUARTZ.getDefaultState(), Block.NOTIFY_LISTENERS);
					}
				}
				// grappes d'améthyste au pied des colonnes
				if (r > 7.6 && r <= 8.4 && (dx + 2 * dz) % 3 == 0) {
					world.setBlockState(new BlockPos(cx + dx, y0 + 1, cz + dz), Blocks.AMETHYST_CLUSTER.getDefaultState(), Block.NOTIFY_LISTENERS);
				}
			}
		}
		return altar;
	}

	/** Dallage : cœur doré, rosace de quartz et de purpur, anneau d'améthyste, lanternes marines. */
	private static BlockState floor(int dx, int dz, double r) {
		boolean ray = dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz);
		if (r <= 2.5) {
			return Blocks.GOLD_BLOCK.getDefaultState();
		}
		if (r <= 3.5) {
			return Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
		}
		if (r > 5.5 && r <= 6.5) {
			return ray ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.AMETHYST_BLOCK.getDefaultState();
		}
		if (r <= 8.5) {
			return ray ? Blocks.PURPUR_BLOCK.getDefaultState() : Blocks.QUARTZ_BRICKS.getDefaultState();
		}
		if (r <= 9.5) {
			return ray ? Blocks.GOLD_BLOCK.getDefaultState() : Blocks.CHISELED_QUARTZ_BLOCK.getDefaultState();
		}
		return ray ? Blocks.PURPUR_BLOCK.getDefaultState() : Blocks.SMOOTH_QUARTZ.getDefaultState();
	}
}
