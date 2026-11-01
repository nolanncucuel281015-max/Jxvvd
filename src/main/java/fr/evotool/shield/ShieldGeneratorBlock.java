package fr.evotool.shield;

import com.mojang.serialization.MapCodec;
import fr.evotool.block.ModBlocks;
import fr.evotool.component.ModComponents;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Générateur de bouclier : clic droit pour ouvrir son menu (case à évolium, rayon, marche / arrêt).
 * Cassé à la pioche, il se ramasse avec son évolium et son rayon, et les retrouve une fois reposé.
 */
public class ShieldGeneratorBlock extends BlockWithEntity {
	public static final MapCodec<ShieldGeneratorBlock> CODEC = createCodec(ShieldGeneratorBlock::new);

	public ShieldGeneratorBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BlockWithEntity> getCodec() {
		return CODEC;
	}

	@Override
	public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
		return new ShieldGeneratorBlockEntity(pos, state);
	}

	@Override
	protected BlockRenderType getRenderType(BlockState state) {
		return BlockRenderType.MODEL;
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
		return world.isClient() ? null : validateTicker(type, ModBlocks.SHIELD_GENERATOR_ENTITY, ShieldGeneratorBlockEntity::tick);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (!world.isClient() && world.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
			player.openHandledScreen(generator);
		}
		return ActionResult.SUCCESS;
	}

	@Override
	public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
		super.onPlaced(world, pos, state, placer, stack);
		if (!world.isClient() && world.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
			generator.load(stack.getOrDefault(ModComponents.SHIELD_FUEL, 0L),
					stack.getOrDefault(ModComponents.SHIELD_RADIUS, ShieldGeneratorBlockEntity.DEFAULT_RADIUS));
		}
	}

	@Override
	public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
		// pas de butin classique : on lâche le générateur avec son évolium (jusqu'à 1 milliard, sans en lâcher autant au sol)
		if (!world.isClient() && !player.isCreative() && world.getBlockEntity(pos) instanceof ShieldGeneratorBlockEntity generator) {
			for (ItemStack drop : generator.drops()) {
				dropStack(world, pos, drop);
			}
		}
		return super.onBreak(world, pos, state, player);
	}
}
