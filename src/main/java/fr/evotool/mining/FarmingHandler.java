package fr.evotool.mining;

import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoMode;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/** Labour en zone avec la houe évolutive (le bloc visé reste labouré par le code vanilla). */
public final class FarmingHandler {
	private FarmingHandler() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			if (!(world instanceof ServerWorld serverWorld) || hand != Hand.MAIN_HAND || hit.getSide() == Direction.DOWN) {
				return ActionResult.PASS;
			}
			ItemStack hoe = player.getStackInHand(hand);
			if (EvoProgression.kindOf(hoe) != EvoKind.HOE) {
				return ActionResult.PASS;
			}
			EvoProfile profile = EvoKind.HOE.profile();
			EvoData data = EvoProgression.get(hoe);
			EvoMode mode = player.isSneaking() ? EvoMode.SINGLE : EvoProgression.selectedMode(profile, data);

			BlockPos center = hit.getBlockPos();
			int tilled = tilledState(serverWorld, center) != null ? 1 : 0;
			int r = mode.radius();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					BlockPos pos = center.add(dx, 0, dz);
					if (pos.equals(center)) {
						continue;
					}
					BlockState result = tilledState(serverWorld, pos);
					if (result != null && canModify(player, serverWorld, pos)) {
						serverWorld.setBlockState(pos, result, Block.NOTIFY_ALL_AND_REDRAW);
						tilled++;
					}
				}
			}
			if (tilled > 1) {
				serverWorld.playSound(null, center, SoundEvents.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 0.8F);
			}
			if (tilled > 0 && !player.isCreative()) {
				EvoProgression.addXp(hoe, tilled, tilled, player, serverWorld);
			}
			return ActionResult.PASS;
		});
	}

	private static boolean canModify(PlayerEntity player, ServerWorld world, BlockPos pos) {
		return player instanceof ServerPlayerEntity serverPlayer
				&& !serverPlayer.isBlockBreakingRestricted(world, pos, serverPlayer.interactionManager.getGameMode());
	}

	/** Le bloc labouré correspondant, ou null si on ne peut pas labourer ici. */
	private static BlockState tilledState(ServerWorld world, BlockPos pos) {
		if (!world.getBlockState(pos.up()).isAir()) {
			return null;
		}
		BlockState state = world.getBlockState(pos);
		if (state.isOf(Blocks.DIRT) || state.isOf(Blocks.GRASS_BLOCK) || state.isOf(Blocks.DIRT_PATH)) {
			return Blocks.FARMLAND.getDefaultState();
		}
		if (state.isOf(Blocks.COARSE_DIRT)) {
			return Blocks.DIRT.getDefaultState();
		}
		return null;
	}
}
