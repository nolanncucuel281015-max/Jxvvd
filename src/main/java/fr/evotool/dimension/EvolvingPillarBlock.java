package fr.evotool.dimension;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.PillarBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/** Bûche évolutive : comme {@link EvolvingBlock}, avec un axe. */
public class EvolvingPillarBlock extends PillarBlock {
	public EvolvingPillarBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(EvolvingBlock.EVOLUTION, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		super.appendProperties(builder);
		builder.add(EvolvingBlock.EVOLUTION);
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		EvoDimension.evolveNearPlayers(world, pos, state);
	}
}
