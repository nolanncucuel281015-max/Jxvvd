package fr.evotool.dimension;

import fr.evotool.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;

/**
 * Portail en blocs de rubis, comme le portail du Nether : un cadre rectangulaire (intérieur de 2x3 à 21x21),
 * allumé avec le briquet d'évolium. Il relie le monde normal et la dimension évolutive (coordonnées 1:1).
 */
public final class RubyPortal {
	public static final int MIN_WIDTH = 2;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_SIZE = 21;
	/** Rayon de recherche d'un portail existant à l'arrivée. */
	private static final int SEARCH_RADIUS = 16;

	private RubyPortal() {
	}

	/** Intérieur d'un cadre valide : coin en bas « à gauche », axe, largeur et hauteur. */
	public record Frame(BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
		public Direction right() {
			return axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		}

		public void fill(World world) {
			BlockState portal = ModBlocks.EVOLUTION_PORTAL.getDefaultState().with(EvolutionPortalBlock.AXIS, axis);
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					world.setBlockState(bottomLeft.offset(right(), x).up(y), portal, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				}
			}
		}
	}

	/** Cherche un cadre de rubis dont {@code inside} est une case intérieure ; l'allume si {@code light}. */
	public static boolean tryLight(World world, BlockPos inside, boolean light) {
		for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
			Frame frame = findFrame(world, inside, axis);
			if (frame != null) {
				if (light) {
					frame.fill(world);
				}
				return true;
			}
		}
		return false;
	}

	public static Frame findFrame(World world, BlockPos start, Direction.Axis axis) {
		if (!isInside(world.getBlockState(start))) {
			return null;
		}
		Direction right = axis == Direction.Axis.X ? Direction.EAST : Direction.SOUTH;
		Direction left = right.getOpposite();
		BlockPos p = start;
		for (int n = 0; n < MAX_SIZE && isInside(world.getBlockState(p.down())); n++) {
			p = p.down();
		}
		if (!isFrame(world.getBlockState(p.down()))) {
			return null;
		}
		for (int n = 0; n < MAX_SIZE && isInside(world.getBlockState(p.offset(left))); n++) {
			p = p.offset(left);
		}
		if (!isFrame(world.getBlockState(p.offset(left)))) {
			return null;
		}
		BlockPos bottomLeft = p;
		int width = 0;
		while (width <= MAX_SIZE && isInside(world.getBlockState(bottomLeft.offset(right, width)))) {
			width++;
		}
		if (width < MIN_WIDTH || width > MAX_SIZE || !isFrame(world.getBlockState(bottomLeft.offset(right, width)))) {
			return null;
		}
		for (int x = 0; x < width; x++) {
			if (!isFrame(world.getBlockState(bottomLeft.offset(right, x).down()))) {
				return null;
			}
		}
		int height = 0;
		rows:
		while (height <= MAX_SIZE) {
			for (int x = 0; x < width; x++) {
				if (!isInside(world.getBlockState(bottomLeft.offset(right, x).up(height)))) {
					break rows;
				}
			}
			if (!isFrame(world.getBlockState(bottomLeft.offset(left).up(height)))
					|| !isFrame(world.getBlockState(bottomLeft.offset(right, width).up(height)))) {
				return null;
			}
			height++;
		}
		if (height < MIN_HEIGHT || height > MAX_SIZE) {
			return null;
		}
		for (int x = 0; x < width; x++) {
			if (!isFrame(world.getBlockState(bottomLeft.offset(right, x).up(height)))) {
				return null;
			}
		}
		return new Frame(bottomLeft, axis, width, height);
	}

	private static boolean isInside(BlockState state) {
		return state.isAir() || state.isOf(Blocks.FIRE) || state.isOf(ModBlocks.EVOLUTION_PORTAL);
	}

	private static boolean isFrame(BlockState state) {
		return state.isOf(ModBlocks.RUBY_BLOCK);
	}

	/** Destination d'une entité qui traverse le portail : l'autre monde, devant un portail (trouvé ou construit). */
	public static TeleportTarget teleportTarget(ServerWorld from, Entity entity, BlockPos pos) {
		MinecraftServer server = from.getServer();
		boolean leaving = from.getRegistryKey() == EvoDimension.WORLD;
		ServerWorld destination = leaving ? server.getOverworld() : server.getWorld(EvoDimension.WORLD);
		if (destination == null) {
			return null;
		}
		BlockPos portal = findOrBuild(destination, pos);
		BlockState state = destination.getBlockState(portal);
		Direction.Axis axis = state.contains(EvolutionPortalBlock.AXIS) ? state.get(EvolutionPortalBlock.AXIS) : Direction.Axis.X;
		// on arrive juste devant le portail (et pas dedans, pour ne pas repartir aussitôt)
		BlockPos front = axis == Direction.Axis.X ? portal.south() : portal.east();
		Vec3d arrival = Vec3d.ofBottomCenter(front);
		return new TeleportTarget(destination, arrival, Vec3d.ZERO, entity.getYaw(), entity.getPitch(),
				TeleportTarget.SEND_TRAVEL_THROUGH_PORTAL_PACKET.then(TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
	}

	/** Bas d'un portail évolutif près de {@code near} dans {@code world} ; en construit un s'il n'y en a pas. */
	public static BlockPos findOrBuild(ServerWorld world, BlockPos near) {
		int x0 = near.getX();
		int z0 = near.getZ();
		world.getChunk(x0 >> 4, z0 >> 4);   // la hauteur du sol n'est connue qu'une fois le chunk chargé
		int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x0, z0);
		int minY = Math.max(world.getBottomY() + 1, Math.min(near.getY(), surface) - 24);
		int maxY = Math.min(world.getTopYInclusive() - 1, Math.max(near.getY(), surface) + 24);
		BlockPos.Mutable p = new BlockPos.Mutable();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
			for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
				for (int y = minY; y <= maxY; y++) {
					p.set(x0 + dx, y, z0 + dz);
					if (world.getBlockState(p).isOf(ModBlocks.EVOLUTION_PORTAL) && !world.getBlockState(p.down()).isOf(ModBlocks.EVOLUTION_PORTAL)) {
						double d = p.getSquaredDistance(near);
						if (d < bestDistance) {
							bestDistance = d;
							best = p.toImmutable();
						}
					}
				}
			}
		}
		return best != null ? best : build(world, x0, z0);
	}

	/** Construit un cadre de rubis 4x5 allumé (axe X), sur une petite plateforme si le sol manque. */
	public static BlockPos build(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		boolean noGround = y <= world.getBottomY() + 2;
		if (noGround || y > world.getTopYInclusive() - 10) {
			y = 70;
		}
		BlockState floor = world.getRegistryKey() == EvoDimension.WORLD
				? ModBlocks.EVOLVING_STONE.getDefaultState() : Blocks.STONE.getDefaultState();
		BlockState ruby = ModBlocks.RUBY_BLOCK.getDefaultState();
		BlockPos base = new BlockPos(x, y, z);
		for (int dx = -2; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				BlockPos ground = base.add(dx, -1, dz);
				if (!world.getBlockState(ground).isSolidBlock(world, ground)) {
					world.setBlockState(ground, floor);
				}
				for (int dy = 0; dy <= 4; dy++) {
					world.setBlockState(base.add(dx, dy, dz), Blocks.AIR.getDefaultState());
				}
			}
		}
		for (int dx = -1; dx <= 2; dx++) {
			world.setBlockState(base.add(dx, -1, 0), ruby);
			world.setBlockState(base.add(dx, 3, 0), ruby);
		}
		for (int dy = 0; dy < 3; dy++) {
			world.setBlockState(base.add(-1, dy, 0), ruby);
			world.setBlockState(base.add(2, dy, 0), ruby);
		}
		new Frame(base, Direction.Axis.X, 2, 3).fill(world);
		return base;
	}
}
