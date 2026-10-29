package fr.evotool.dimension;

import fr.evotool.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Portal;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.tick.ScheduledTickView;

/** Portail évolutif : apparaît dans un cadre de blocs de rubis allumé au briquet d'évolium. */
public class EvolutionPortalBlock extends Block implements Portal {
	public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
	private static final VoxelShape X_SHAPE = Block.createCuboidShape(0, 0, 6, 16, 16, 10);
	private static final VoxelShape Z_SHAPE = Block.createCuboidShape(6, 0, 0, 10, 16, 16);

	public EvolutionPortalBlock(Settings settings) {
		super(settings);
		setDefaultState(getDefaultState().with(AXIS, Direction.Axis.X));
	}

	@Override
	protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
		builder.add(AXIS);
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return state.get(AXIS) == Direction.Axis.Z ? Z_SHAPE : X_SHAPE;
	}

	@Override
	protected BlockState getStateForNeighborUpdate(BlockState state, WorldView world, ScheduledTickView tickView, BlockPos pos,
			Direction direction, BlockPos neighborPos, BlockState neighborState, Random random) {
		Direction.Axis axis = direction.getAxis();
		boolean inPlane = axis == state.get(AXIS) || axis == Direction.Axis.Y;
		if (inPlane && !neighborState.isOf(this) && !neighborState.isOf(ModBlocks.RUBY_BLOCK)) {
			return Blocks.AIR.getDefaultState();   // le cadre est cassé : le portail s'éteint
		}
		return super.getStateForNeighborUpdate(state, world, tickView, pos, direction, neighborPos, neighborState, random);
	}

	@Override
	protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean bl) {
		if (entity.canUsePortals(false)) {
			entity.tryUsePortal(this, pos);
		}
	}

	@Override
	public int getPortalDelay(ServerWorld world, Entity entity) {
		return entity instanceof PlayerEntity player && !player.getAbilities().invulnerable ? 60 : 1;
	}

	@Override
	public TeleportTarget createTeleportTarget(ServerWorld world, Entity entity, BlockPos pos) {
		return RubyPortal.teleportTarget(world, entity, pos);
	}

	@Override
	public Portal.Effect getPortalEffect() {
		return Portal.Effect.CONFUSION;
	}

	@Override
	protected ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state, boolean includeData) {
		return ItemStack.EMPTY;
	}
}
