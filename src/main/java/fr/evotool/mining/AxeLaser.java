package fr.evotool.mining;

import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import fr.evotool.upgrade.EvoliteUpgrade;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Hache Évolutive, clic droit (dans le vide ou sur un bloc qui ne s'écorce pas) :
 * un rayon laser fait le tour du joueur en 2 secondes et coupe tous les arbres à 50 blocs.
 * Le bois et les feuilles reviennent au joueur (dans l'inventaire avec la télékinésie).
 */
public final class AxeLaser {
	private static final int RADIUS = 50;
	private static final int SWEEP_TICKS = 40;
	private static final int COOLDOWN = 200;
	private static final int BELOW = 16;
	private static final int ABOVE = 48;
	private static final int MAX_TREE_LOGS = 400;

	/** Colonnes (dx, dz) dans le disque de 50 blocs, rangées par part d'angle (une part par tick). */
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
		final ServerWorld world;
		final ServerPlayerEntity player;
		final ItemStack axe;
		final BlockPos center;
		final int startTick;
		final boolean telekinesis;
		final boolean replant;
		int blocks;
		int xp;
		/** Bûches reconnues comme faisant partie d'un arbre (tout l'arbre est marqué d'un coup). */
		final Set<Long> treeLogs = new HashSet<>();

		Sweep(ServerWorld world, ServerPlayerEntity player, ItemStack axe, BlockPos center, int startTick,
				boolean telekinesis, boolean replant) {
			this.world = world;
			this.player = player;
			this.axe = axe;
			this.center = center;
			this.startTick = startTick;
			this.telekinesis = telekinesis;
			this.replant = replant;
		}
	}

	private static final List<Sweep> SWEEPS = new ArrayList<>();

	private AxeLaser() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(AxeLaser::onUse);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getTicks()));
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		ItemStack axe = player.getStackInHand(hand);
		if (EvoProgression.kindOf(axe) != EvoKind.AXE) {
			return ActionResult.PASS;
		}
		if (player.getItemCooldownManager().isCoolingDown(axe)) {
			return ActionResult.FAIL;
		}
		player.getItemCooldownManager().set(axe, COOLDOWN);
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			EvoProfile profile = EvoKind.AXE.profile();
			EvoData data = EvoProgression.get(axe);
			SWEEPS.add(new Sweep(serverWorld, serverPlayer, axe, serverPlayer.getBlockPos(), serverWorld.getServer().getTicks(),
					EvoliteUpgrade.has(axe, EvoliteUpgrade.Upgrade.TELEKINESIS),
					EvoProgression.isActive(profile, data, Power.AUTO_REPLANT)));
			serverWorld.playSound(null, serverPlayer.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 1.4F);
		}
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	private static void tick(int tick) {
		for (Iterator<Sweep> it = SWEEPS.iterator(); it.hasNext(); ) {
			Sweep sweep = it.next();
			int step = tick - sweep.startTick;
			if (step >= SWEEP_TICKS || sweep.player.isRemoved()) {
				if (sweep.xp > 0 && !sweep.player.isCreative()) {
					EvoProgression.addXp(sweep.axe, sweep.xp, sweep.blocks, sweep.player, sweep.world);
				}
				it.remove();
				continue;
			}
			beam(sweep, step);
			List<ItemStack> drops = new ArrayList<>();
			for (int[] column : SECTORS.get(step)) {
				cutColumn(sweep, sweep.center.getX() + column[0], sweep.center.getZ() + column[1], drops);
			}
			deliver(sweep, drops);
		}
	}

	/** Le rayon laser : une ligne de particules qui tourne autour du joueur. */
	private static void beam(Sweep sweep, int step) {
		double angle = (step + 0.5) / SWEEP_TICKS * 2 * Math.PI;
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		double y = sweep.player.getY() + 1.2;
		for (double d = 1.5; d <= RADIUS; d += 1.0) {
			double x = sweep.player.getX() + cos * d;
			double z = sweep.player.getZ() + sin * d;
			sweep.world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
			if (((int) d) % 4 == 0) {
				sweep.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 2, 0.2, 0.2, 0.2, 0.05);
			}
		}
		if (step % 4 == 0) {
			sweep.world.playSound(null, sweep.player.getBlockPos(), SoundEvents.ENTITY_GUARDIAN_ATTACK, SoundCategory.PLAYERS, 0.7F, 1.6F);
		}
	}

	private static void cutColumn(Sweep sweep, int x, int z, List<ItemStack> drops) {
		ServerWorld world = sweep.world;
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		int top = sweep.center.getY() + ABOVE;
		int bottom = sweep.center.getY() - BELOW;
		// 1er passage, sans rien casser : on repère les arbres tant que leurs feuilles sont encore là.
		for (int y = top; y >= bottom; y--) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!world.isOutOfHeightLimit(y) && !sweep.treeLogs.contains(pos.asLong())
					&& world.getBlockState(pos).isIn(BlockTags.LOGS) && isTreeLog(world, pos)) {
				markTree(world, pos, sweep.treeLogs);
			}
		}
		// 2e passage : on coupe.
		for (int y = top; y >= bottom; y--) {
			if (world.isOutOfHeightLimit(y)) {
				continue;
			}
			BlockPos pos = new BlockPos(x, y, z);
			BlockState state = world.getBlockState(pos);
			boolean log = state.isIn(BlockTags.LOGS) && sweep.treeLogs.contains(pos.asLong());
			if (!log && !EvoMiningHandler.isNaturalLeaves(state)) {
				continue;
			}
			if (sweep.player.isBlockBreakingRestricted(world, pos, sweep.player.interactionManager.getGameMode())) {
				continue;
			}
			boolean onGround = log && world.getBlockState(pos.down()).isIn(BlockTags.DIRT);
			if (EvoMiningHandler.breakBlock(world, sweep.player, pos, state, sweep.axe, drops)) {
				sweep.blocks++;
				if (log) {
					sweep.xp++;
				}
				if (onGround && sweep.replant) {
					replant(world, pos, state);
				}
			}
		}
	}

	/**
	 * Un tronc d'arbre (et pas une maison en bûches) : il y a des feuilles naturelles juste autour ou au-dessus.
	 */
	private static boolean isTreeLog(ServerWorld world, BlockPos pos) {
		for (int dy = -1; dy <= 14; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					if (EvoMiningHandler.isNaturalLeaves(world.getBlockState(pos.add(dx, dy, dz)))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Marque toutes les bûches reliées (diagonales comprises) comme faisant partie du même arbre. */
	private static void markTree(ServerWorld world, BlockPos start, Set<Long> treeLogs) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		treeLogs.add(start.asLong());
		int marked = 1;
		while (!queue.isEmpty() && marked < MAX_TREE_LOGS) {
			BlockPos current = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = current.add(dx, dy, dz);
						if (!treeLogs.contains(next.asLong()) && world.getBlockState(next).isIn(BlockTags.LOGS)) {
							treeLogs.add(next.asLong());
							queue.add(next);
							marked++;
						}
					}
				}
			}
		}
	}

	/** Replantation (pouvoir de la hache, niveau 30) : la pousse qui correspond au bois coupé. */
	private static void replant(ServerWorld world, BlockPos pos, BlockState log) {
		Identifier id = Registries.BLOCK.getId(log.getBlock());
		String path = id.getPath();
		String sapling = path.startsWith("mangrove") ? "mangrove_propagule"
				: path.replace("stripped_", "").replace("_log", "_sapling").replace("_wood", "_sapling");
		Optional<Block> block = Registries.BLOCK.getOptionalValue(Identifier.of(id.getNamespace(), sapling));
		if (block.isPresent() && world.getBlockState(pos).isAir() && block.get().getDefaultState().canPlaceAt(world, pos)) {
			world.setBlockState(pos, block.get().getDefaultState());
		}
	}

	/** Ramène les objets au joueur : dans l'inventaire avec la télékinésie, sinon à ses pieds. */
	private static void deliver(Sweep sweep, List<ItemStack> drops) {
		List<ItemStack> merged = new ArrayList<>();
		for (ItemStack drop : drops) {
			for (ItemStack existing : merged) {
				if (ItemStack.areItemsAndComponentsEqual(existing, drop) && existing.getCount() < existing.getMaxCount()) {
					int moved = Math.min(drop.getCount(), existing.getMaxCount() - existing.getCount());
					existing.increment(moved);
					drop.decrement(moved);
				}
			}
			if (!drop.isEmpty()) {
				merged.add(drop);
			}
		}
		ServerPlayerEntity player = sweep.player;
		for (ItemStack stack : merged) {
			if (sweep.telekinesis) {
				player.getInventory().insertStack(stack);
			}
			if (!stack.isEmpty()) {
				ItemEntity item = new ItemEntity(sweep.world, player.getX(), player.getY() + 0.5, player.getZ(), stack);
				item.setPickupDelay(0);
				sweep.world.spawnEntity(item);
			}
		}
	}
}
