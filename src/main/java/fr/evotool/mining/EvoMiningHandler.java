package fr.evotool.mining;

import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoMode;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldEvents;

/**
 * Prend en charge le cassage des blocs avec les outils évolutifs :
 * minage de zone, abattage d'arbres, récolte en zone, replantation, auto-smelt et gain d'XP.
 */
public final class EvoMiningHandler {
	private static final int MAX_BREAK_EFFECTS = 26;
	private static final int MAX_TREE_BLOCKS = 320;
	private static final int CROP_XP = 3;

	private EvoMiningHandler() {
	}

	public static void init() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return true;
			}
			ItemStack tool = player.getMainHandStack();
			EvoKind kind = EvoProgression.kindOf(tool);
			if (kind == null || !kind.profile().gainsBlockXp()) {
				return true;
			}
			mine(serverWorld, serverPlayer, pos, state, tool, kind);
			// On a déjà cassé le(s) bloc(s) nous-mêmes : on annule le cassage vanilla.
			return false;
		});
	}

	private static void mine(ServerWorld world, ServerPlayerEntity player, BlockPos origin, BlockState originState,
			ItemStack tool, EvoKind kind) {
		EvoProfile profile = kind.profile();
		EvoData data = EvoProgression.get(tool);
		// Accroupi = toujours un seul bloc, pratique pour les travaux de précision.
		boolean precise = player.isSneaking();
		boolean smelt = EvoProgression.isActive(profile, data, Power.AUTOSMELT);
		boolean replant = EvoProgression.isActive(profile, data, Power.AUTO_REPLANT);

		List<BlockPos> positions;
		boolean felling = false;
		if (precise) {
			positions = List.of(origin);
		} else if (profile.areaMining()) {
			positions = areaPositions(player, origin, EvoProgression.selectedMode(profile, data));
		} else if (kind == EvoKind.AXE && originState.isIn(BlockTags.LOGS)
				&& EvoProgression.isActive(profile, data, Power.TREE_FELLING)) {
			positions = treePositions(world, origin, EvoProgression.isActive(profile, data, Power.FELL_LEAVES));
			felling = true;
		} else if (kind == EvoKind.HOE && isMatureCrop(originState)) {
			positions = cropPositions(world, origin, EvoProgression.selectedMode(profile, data));
		} else {
			positions = List.of(origin);
		}

		List<ItemStack> drops = new ArrayList<>();
		List<BlockPos> harvestedCrops = new ArrayList<>();
		List<BlockState> harvestedStates = new ArrayList<>();
		int xp = 0;
		int blocks = 0;
		int effects = 0;

		for (BlockPos pos : positions) {
			boolean isOrigin = pos.equals(origin);
			BlockState state = isOrigin ? originState : world.getBlockState(pos);
			if (!isOrigin && !canBreakExtra(world, player, pos, state, tool, kind)) {
				continue;
			}
			if (!breakBlock(world, player, pos, state, tool, drops)) {
				continue;
			}
			blocks++;
			if (!isOrigin && effects++ < MAX_BREAK_EFFECTS) {
				// Le client n'affiche les particules que du bloc visé : on envoie celles de la zone (avec une limite).
				player.networkHandler.sendPacket(new WorldEventS2CPacket(WorldEvents.BLOCK_BROKEN, pos,
						Block.getRawIdFromState(state), false));
			}
			if (isMatureCrop(state)) {
				xp += CROP_XP;
				harvestedCrops.add(pos);
				harvestedStates.add(state);
			} else if (tool.isSuitableFor(state) && state.getHardness(world, pos) > 0) {
				xp += EvoProgression.xpForBlock(state);
			}
		}

		if (replant && kind == EvoKind.HOE) {
			boolean grown = EvoProgression.isActive(profile, data, Power.INSTANT_GROWTH);
			for (int i = 0; i < harvestedCrops.size(); i++) {
				replantCrop(world, harvestedCrops.get(i), harvestedStates.get(i), grown);
			}
		}
		if (replant && felling) {
			replantSapling(world, origin, drops);
		}

		for (ItemStack drop : drops) {
			ItemStack result = smelt ? smelt(world, drop) : drop;
			// Fortune VI + auto-smelt peut dépasser la taille max d'une pile : on découpe.
			while (!result.isEmpty()) {
				Block.dropStack(world, origin, result.split(result.getMaxCount()));
			}
		}

		if (xp > 0 && !player.isCreative()) {
			EvoProgression.addXp(tool, xp, blocks, player, world);
		}
	}

	private static boolean canBreakExtra(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state,
			ItemStack tool, EvoKind kind) {
		if (state.isAir() || world.getBlockEntity(pos) != null || state.getHardness(world, pos) < 0) {
			return false;
		}
		if (player.isBlockBreakingRestricted(world, pos, player.interactionManager.getGameMode())) {
			return false;
		}
		return switch (kind) {
			case AXE -> state.isIn(BlockTags.LOGS) || isNaturalLeaves(state);
			case HOE -> isMatureCrop(state);
			// En zone, on ne casse que ce qui se mine avec cet outil (pas de terre à la pioche, pas de pierre à la pelle).
			default -> tool.isSuitableFor(state);
		};
	}

	/** Reprend ServerPlayerInteractionManager#tryBreakBlock, mais en récupérant les drops. */
	private static boolean breakBlock(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state,
			ItemStack tool, List<ItemStack> drops) {
		BlockEntity blockEntity = world.getBlockEntity(pos);
		Block block = state.getBlock();
		BlockState brokenState = block.onBreak(world, pos, state, player);
		if (!world.removeBlock(pos, false)) {
			return false;
		}
		block.onBroken(world, pos, brokenState);
		if (player.isCreative()) {
			return true;
		}

		player.incrementStat(Stats.MINED.getOrCreateStat(block));
		player.addExhaustion(0.005F);
		if (player.canHarvest(brokenState)) {
			drops.addAll(Block.getDroppedStacks(brokenState, world, pos, blockEntity, player, tool));
			brokenState.onStacksDropped(world, pos, tool, true);
		}
		return true;
	}

	private static ItemStack smelt(ServerWorld world, ItemStack stack) {
		SingleStackRecipeInput input = new SingleStackRecipeInput(stack);
		Optional<RecipeEntry<SmeltingRecipe>> recipe = world.getRecipeManager().getFirstMatch(RecipeType.SMELTING, input, world);
		if (recipe.isEmpty()) {
			return stack;
		}
		ItemStack result = recipe.get().value().craft(input, world.getRegistryManager());
		if (result.isEmpty()) {
			return stack;
		}
		result.setCount(result.getCount() * stack.getCount());
		return result;
	}

	// ------------------------------------------------------------------ pioche / pelle : zone

	private static List<BlockPos> areaPositions(PlayerEntity player, BlockPos origin, EvoMode mode) {
		List<BlockPos> positions = new ArrayList<>();
		positions.add(origin);
		if (mode == EvoMode.SINGLE) {
			return positions;
		}

		Direction face = hitFace(player, origin);
		Direction.Axis u;
		Direction.Axis v;
		switch (face.getAxis()) {
			case Y -> { u = Direction.Axis.X; v = Direction.Axis.Z; }
			case X -> { u = Direction.Axis.Z; v = Direction.Axis.Y; }
			default -> { u = Direction.Axis.X; v = Direction.Axis.Y; }
		}
		Direction inward = face.getOpposite();
		int r = mode.radius();

		for (int d = 0; d < mode.depth(); d++) {
			BlockPos layer = origin.offset(inward, d);
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					BlockPos pos = layer.offset(u, a).offset(v, b);
					if (!pos.equals(origin)) {
						positions.add(pos);
					}
				}
			}
		}
		return positions;
	}

	private static Direction hitFace(PlayerEntity player, BlockPos origin) {
		HitResult hit = player.raycast(player.getBlockInteractionRange() + 1.0, 0.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(origin)) {
			return blockHit.getSide();
		}
		float pitch = player.getPitch();
		if (pitch > 45) {
			return Direction.UP;
		}
		if (pitch < -45) {
			return Direction.DOWN;
		}
		return player.getHorizontalFacing().getOpposite();
	}

	// ------------------------------------------------------------------ hache : abattage

	/** Tous les troncs reliés au bloc cassé (diagonales comprises), plus les feuilles naturelles si débloqué. */
	private static List<BlockPos> treePositions(ServerWorld world, BlockPos origin, boolean withLeaves) {
		Set<BlockPos> found = new LinkedHashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		found.add(origin);
		queue.add(origin);
		while (!queue.isEmpty() && found.size() < MAX_TREE_BLOCKS) {
			BlockPos current = queue.poll();
			boolean currentIsLog = world.getBlockState(current).isIn(BlockTags.LOGS) || current.equals(origin);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos next = current.add(dx, dy, dz);
						if (found.contains(next) || next.getY() < origin.getY()) {
							continue;
						}
						BlockState state = world.getBlockState(next);
						// On ne remonte que par les troncs ; les feuilles sont prises autour des troncs.
						boolean log = state.isIn(BlockTags.LOGS);
						boolean leaves = withLeaves && currentIsLog && isNaturalLeaves(state);
						if (log || leaves) {
							found.add(next);
							if (log) {
								queue.add(next);
							} else {
								addLeafCluster(world, next, found, origin);
							}
						}
					}
				}
			}
		}
		return new ArrayList<>(found);
	}

	private static void addLeafCluster(ServerWorld world, BlockPos start, Set<BlockPos> found, BlockPos origin) {
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		queue.add(start);
		while (!queue.isEmpty() && found.size() < MAX_TREE_BLOCKS) {
			BlockPos current = queue.poll();
			for (Direction direction : Direction.values()) {
				BlockPos next = current.offset(direction);
				if (!found.contains(next) && next.getY() >= origin.getY() && next.isWithinDistance(start, 4.0)
						&& isNaturalLeaves(world.getBlockState(next))) {
					found.add(next);
					queue.add(next);
				}
			}
		}
	}

	private static boolean isNaturalLeaves(BlockState state) {
		return state.isIn(BlockTags.LEAVES) && state.contains(LeavesBlock.PERSISTENT) && !state.get(LeavesBlock.PERSISTENT);
	}

	private static void replantSapling(ServerWorld world, BlockPos origin, List<ItemStack> drops) {
		if (!world.getBlockState(origin).isAir() || !world.getBlockState(origin.down()).isIn(BlockTags.DIRT)) {
			return;
		}
		for (Iterator<ItemStack> it = drops.iterator(); it.hasNext(); ) {
			ItemStack stack = it.next();
			if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof SaplingBlock sapling) {
				world.setBlockState(origin, sapling.getDefaultState());
				stack.decrement(1);
				if (stack.isEmpty()) {
					it.remove();
				}
				return;
			}
		}
	}

	// ------------------------------------------------------------------ houe : récolte

	private static boolean isMatureCrop(BlockState state) {
		return state.getBlock() instanceof CropBlock crop && crop.isMature(state);
	}

	private static List<BlockPos> cropPositions(ServerWorld world, BlockPos origin, EvoMode mode) {
		List<BlockPos> positions = new ArrayList<>();
		positions.add(origin);
		int r = mode.radius();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx != 0 || dz != 0) {
					positions.add(origin.add(dx, 0, dz));
				}
			}
		}
		return positions;
	}

	private static void replantCrop(ServerWorld world, BlockPos pos, BlockState harvested, boolean grown) {
		if (!(harvested.getBlock() instanceof CropBlock crop) || !world.getBlockState(pos).isAir()) {
			return;
		}
		BlockState planted = grown ? crop.withAge(crop.getMaxAge()) : crop.withAge(0);
		if (planted.canPlaceAt(world, pos)) {
			world.setBlockState(pos, planted);
		}
	}
}
