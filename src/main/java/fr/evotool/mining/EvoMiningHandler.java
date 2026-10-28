package fr.evotool.mining;

import fr.evotool.component.EvoData;
import fr.evotool.item.EvolvingPickaxeItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.WorldEventS2CPacket;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldEvents;

/**
 * Prend en charge le cassage des blocs avec la pioche évolutive :
 * minage de zone, auto-smelt et gain d'XP.
 */
public final class EvoMiningHandler {
	private static final int MAX_BREAK_EFFECTS = 26;

	private EvoMiningHandler() {
	}

	public static void init() {
		PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
			if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
				return true;
			}
			ItemStack tool = player.getMainHandStack();
			if (!(tool.getItem() instanceof EvolvingPickaxeItem)) {
				return true;
			}
			mine(serverWorld, serverPlayer, pos, state, tool);
			// On a déjà cassé le bloc nous-mêmes : on annule le cassage vanilla.
			return false;
		});
	}

	private static void mine(ServerWorld world, ServerPlayerEntity player, BlockPos origin, BlockState originState, ItemStack tool) {
		EvoData data = EvoProgression.get(tool);
		// Accroupi = toujours un seul bloc, pratique pour les travaux de précision.
		EvoMode mode = player.isSneaking() ? EvoMode.SINGLE : EvoMode.byIndex(data.mode());
		if (!mode.isUnlocked(data.level())) {
			mode = EvoMode.SINGLE;
		}
		boolean smelt = data.autosmelt() && EvoProgression.autosmeltUnlocked(data.level());

		List<ItemStack> drops = new ArrayList<>();
		int xp = 0;
		int blocks = 0;
		int effects = 0;

		for (BlockPos pos : areaPositions(player, origin, mode)) {
			BlockState state = pos.equals(origin) ? originState : world.getBlockState(pos);
			if (!pos.equals(origin) && !canAreaMine(world, player, pos, state, tool)) {
				continue;
			}
			if (breakBlock(world, player, pos, state, tool, drops)) {
				blocks++;
				// Le client n'affiche les particules que du bloc visé : on envoie celles de la zone (avec une limite).
				if (!pos.equals(origin) && effects++ < MAX_BREAK_EFFECTS) {
					player.networkHandler.sendPacket(new WorldEventS2CPacket(WorldEvents.BLOCK_BROKEN, pos,
							Block.getRawIdFromState(state), false));
				}
				if (tool.isSuitableFor(state) && state.getHardness(world, pos) > 0) {
					xp += EvoProgression.xpFor(state);
				}
			}
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

	private static boolean canAreaMine(ServerWorld world, ServerPlayerEntity player, BlockPos pos, BlockState state, ItemStack tool) {
		if (state.isAir() || world.getBlockEntity(pos) != null) {
			return false;
		}
		float hardness = state.getHardness(world, pos);
		if (hardness < 0) {
			return false;
		}
		// En zone, on ne casse que ce qui se mine à la pioche (pas de terre, pas de fluides...).
		return tool.isSuitableFor(state) && !player.isBlockBreakingRestricted(world, pos, player.interactionManager.getGameMode());
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
}
