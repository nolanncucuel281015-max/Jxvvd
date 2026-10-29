package fr.evotool.dimension;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

/**
 * Bloc de la dimension évolutive : il a 6 niveaux d'évolution (0 à 5). Autour d'un joueur, il rattrape peu à peu
 * l'évolution de la dimension (une de plus toutes les 10 minutes passées dans la dimension).
 */
public class EvolvingBlock extends Block {
	public static final IntProperty EVOLUTION = IntProperty.of("evolution", 0, EvoDimension.MAX_PHASE);

	public EvolvingBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(EVOLUTION, 0));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(EVOLUTION);
	}

	@Override
	protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
		EvoDimension.evolveNearPlayers(world, pos, state);
	}
}
