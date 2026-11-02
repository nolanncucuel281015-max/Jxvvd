package fr.evotool.combiner;

import com.mojang.serialization.MapCodec;
import fr.evotool.block.ModBlocks;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Combineur élémentaire : on y pose une armure, une arme ou un outil et un élément (glace, poudre à canon,
 * briquet, seau d'eau, charge de vent…), et il les fusionne dans une petite animation.
 */
public class ElementalCombinerBlock extends BlockWithEntity {
	public static final MapCodec<ElementalCombinerBlock> CODEC = createCodec(ElementalCombinerBlock::new);
	private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 12, 16);

	public ElementalCombinerBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ElementalCombinerBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
		return SHAPE;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, ModBlocks.ELEMENTAL_COMBINER_ENTITY, ElementalCombinerBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof ElementalCombinerBlockEntity combiner) {
			player.openHandledScreen(combiner);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		// ce qu'il contient (et l'objet en pleine fusion) tombe par terre
		if (!world.isClient() && world.getBlockEntity(pos) instanceof ElementalCombinerBlockEntity combiner) {
			for (ItemStack drop : combiner.drops()) {
				dropStack(world, pos, drop);
			}
		}
		return super.onBreak(world, pos, state, player);
	}
}
