package fr.evotool.dimension;

import fr.evotool.boss.BellBossEntity;
import fr.evotool.boss.ModEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

/** Autel du sanctuaire : un clic droit réveille le Slime Cloche (un seul boss à la fois autour de l'autel). */
public class SanctuaryAltarBlock extends Block {
	public static final int BOSS_RANGE = 96;

	public SanctuaryAltarBlock(Settings settings) {
		super(settings);
	}

	@Override
	protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
		if (world instanceof ServerWorld serverWorld) {
			summon(serverWorld, pos, player);
		}
		return ActionResult.SUCCESS;
	}

	/** Réveille le boss devant l'autel ; renvoie null si un boss rôde déjà dans les parages. */
	public static BellBossEntity summon(ServerWorld world, BlockPos altar, PlayerEntity player) {
		if (!world.getEntitiesByType(ModEntities.BELL_BOSS, new Box(altar).expand(BOSS_RANGE), Entity::isAlive).isEmpty()) {
			if (player != null) {
				player.sendMessage(Text.translatable("message.evotool.altar_busy").formatted(Formatting.RED), true);
			}
			return null;
		}
		// sur le parvis, à 5 blocs au sud de l'autel ; sinon juste au-dessus
		BlockPos spawn = altar.add(0, -1, 5);
		if (!world.getBlockState(spawn).isAir() || !world.getBlockState(spawn.up()).isAir() || !world.getBlockState(spawn.up(2)).isAir()) {
			spawn = altar.up();
		}
		BellBossEntity boss = EvoDimension.spawnBoss(world, spawn, 0);
		if (boss == null) {
			return null;
		}
		world.playSound(null, altar, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 2.0F, 1.2F);
		world.playSound(null, altar, SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.BLOCKS, 4.0F, 0.6F);
		for (double dy = 0; dy < 24; dy += 0.5) {
			world.spawnParticles(ParticleTypes.END_ROD, altar.getX() + 0.5, altar.getY() + 1 + dy, altar.getZ() + 0.5, 2, 0.15, 0.1, 0.15, 0.01);
		}
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, spawn.getX() + 0.5, spawn.getY() + 1, spawn.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.4);
		for (ServerPlayerEntity other : world.getPlayers()) {
			if (other.squaredDistanceTo(altar.getX(), altar.getY(), altar.getZ()) < BOSS_RANGE * BOSS_RANGE) {
				other.sendMessage(Text.translatable("message.evotool.boss_awakens", Text.translatable(BellBossEntity.formKey(0)),
						EvoDimension.minutes()).formatted(Formatting.GOLD, Formatting.BOLD), false);
			}
		}
		return boss;
	}
}
