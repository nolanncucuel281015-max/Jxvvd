package fr.evotool.upgrade;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/**
 * Pioche Évolutive, clic droit : la pioche du joueur apparaît en géant au-dessus de l'endroit visé,
 * s'abat dans le sol et creuse un puits jusqu'à la bedrock (1x1, puis 3x3, 5x5 et 7x7 en évoluant).
 * Seuls les minerais rapportent quelque chose (dans l'inventaire avec la télékinésie).
 */
public final class PickaxeSlam {
	private static final int COOLDOWN = 300;
	private static final int GROW_TICKS = 8;
	private static final int SLAM_TICK = 14;
	private static final int FALL_TICKS = 4;
	private static final int IMPACT_TICK = SLAM_TICK + FALL_TICKS;
	private static final int VANISH_TICK = IMPACT_TICK + 30;
	private static final float SCALE = 5.0F;
	private static final double RAISED = 14.0;
	/** Distance du centre de la pioche géante à la pointe de son fer (pioche à l'envers), en blocs. */
	private static final double HEAD_OFFSET = 2.9;
	private static final int LAYERS_PER_TICK = 6;
	private static final double REACH = 32.0;
	/** Pioche à l'envers : le fer vers le sol. */
	private static final Quaternionf UPSIDE_DOWN = new Quaternionf().rotationZ((float) Math.PI);

	private static final class Slam {
		final ServerWorld world;
		final ServerPlayerEntity player;
		final ItemStack pickaxe;
		final BlockPos target;
		final int radius;
		final int startTick;
		final boolean telekinesis;
		final DisplayEntity.ItemDisplayEntity giant;
		int digY;
		boolean digging;
		boolean done;
		int xp;

		Slam(ServerWorld world, ServerPlayerEntity player, ItemStack pickaxe, BlockPos target, int radius, int startTick,
				DisplayEntity.ItemDisplayEntity giant) {
			this.world = world;
			this.player = player;
			this.pickaxe = pickaxe;
			this.target = target;
			this.radius = radius;
			this.startTick = startTick;
			this.telekinesis = EvoliteUpgrade.has(pickaxe, EvoliteUpgrade.Upgrade.TELEKINESIS);
			this.giant = giant;
			this.digY = target.getY();
		}
	}

	private static final List<Slam> SLAMS = new ArrayList<>();

	private PickaxeSlam() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(PickaxeSlam::onUse);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getTicks()));
	}

	/** Taille du puits selon le niveau : 1x1, 3x3 (niv. 20), 5x5 (niv. 50), 7x7 (niv. 100). */
	public static int radius(int level) {
		if (level >= EvoProgression.MAX_LEVEL) {
			return 3;
		}
		return level >= 50 ? 2 : level >= 20 ? 1 : 0;
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		ItemStack pickaxe = player.getStackInHand(hand);
		if (EvoProgression.kindOf(pickaxe) != EvoKind.PICKAXE) {
			return ActionResult.PASS;
		}
		if (player.getItemCooldownManager().isCoolingDown(pickaxe)) {
			return ActionResult.FAIL;
		}
		if (!(world instanceof ServerWorld serverWorld) || !(player instanceof ServerPlayerEntity serverPlayer)) {
			return ActionResult.SUCCESS;
		}
		BlockPos target = findTarget(serverWorld, serverPlayer);
		if (target == null) {
			return ActionResult.PASS;
		}
		player.getItemCooldownManager().set(pickaxe, COOLDOWN);

		ItemStack shown = pickaxe.copyWithCount(1);
		shown.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
		float stage = EvoProgression.stage(EvoProgression.get(pickaxe).level());
		shown.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(stage), List.of(), List.of(), List.of()));
		Vec3d raised = Vec3d.ofBottomCenter(target).add(0, 1 + HEAD_OFFSET + RAISED, 0);
		DisplayEntity.ItemDisplayEntity giant = GiantItem.spawn(serverWorld, shown, ItemDisplayContext.NONE, raised,
				player.getYaw(), UPSIDE_DOWN, SCALE, GROW_TICKS);
		serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.7F);
		SLAMS.add(new Slam(serverWorld, serverPlayer, pickaxe, target, radius(EvoProgression.get(pickaxe).level()),
				serverWorld.getServer().getTicks(), giant));
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	/** Le bloc visé (jusqu'à 32 blocs), sinon le sol 3 blocs devant le joueur. */
	private static BlockPos findTarget(ServerWorld world, ServerPlayerEntity player) {
		HitResult hit = player.raycast(REACH, 0.0F, false);
		if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
			return blockHit.getBlockPos();
		}
		Vec3d look = player.getRotationVector();
		BlockPos pos = BlockPos.ofFloored(player.getX() + look.x * 3, player.getY() - 1, player.getZ() + look.z * 3);
		for (int i = 0; i < 6 && world.getBlockState(pos).isAir(); i++) {
			pos = pos.down();
		}
		return world.getBlockState(pos).isAir() ? null : pos;
	}

	private static void tick(int tick) {
		for (Iterator<Slam> it = SLAMS.iterator(); it.hasNext(); ) {
			Slam slam = it.next();
			int step = tick - slam.startTick;
			if (slam.player.isRemoved() && !slam.digging) {
				GiantItem.remove(slam.world, slam.giant, SCALE);
				it.remove();
				continue;
			}
			if (step == SLAM_TICK) {
				// La pioche s'abat : déplacement interpolé côté client (durée de téléportation).
				slam.giant.setTeleportDuration(FALL_TICKS);
				Vec3d impact = Vec3d.ofBottomCenter(slam.target).add(0, 1 + HEAD_OFFSET, 0);
				slam.giant.refreshPositionAndAngles(impact.x, impact.y, impact.z, slam.giant.getYaw(), 0.0F);
				slam.world.playSound(null, slam.target, SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 2.0F, 0.5F);
			}
			if (step == IMPACT_TICK) {
				impact(slam);
				slam.digging = true;
			}
			if (slam.digging && !slam.done) {
				dig(slam);
			}
			if (step == VANISH_TICK) {
				GiantItem.remove(slam.world, slam.giant, SCALE);
			}
			if (step >= VANISH_TICK && slam.done) {
				if (slam.xp > 0 && !slam.player.isCreative() && !slam.player.isRemoved()) {
					EvoProgression.addXp(slam.pickaxe, slam.xp, 0, slam.player, slam.world);
				}
				it.remove();
			}
		}
	}

	/** Le choc : explosion, onde de choc au sol et débris. */
	private static void impact(Slam slam) {
		ServerWorld world = slam.world;
		Vec3d c = Vec3d.ofBottomCenter(slam.target).add(0, 1, 0);
		BlockState ground = world.getBlockState(slam.target);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 0.5, 0.2, 0.5, 0.0);
		if (!ground.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), c.x, c.y, c.z, 250, 2.0, 0.6, 2.0, 0.3);
		}
		for (int ring = 1; ring <= 3; ring++) {
			double r = ring * 1.8;
			for (int i = 0; i < 24 * ring; i++) {
				double a = 2 * Math.PI * i / (24 * ring);
				world.spawnParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
			}
		}
		world.playSound(null, slam.target, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0F, 0.6F);
		world.playSound(null, slam.target, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 2.0F, 0.5F);
	}

	/** Creuse quelques couches par tick, de l'impact jusqu'à la bedrock. */
	private static void dig(Slam slam) {
		ServerWorld world = slam.world;
		List<ItemStack> drops = new ArrayList<>();
		for (int layer = 0; layer < LAYERS_PER_TICK; layer++) {
			int y = slam.digY;
			if (y < world.getBottomY()) {
				slam.done = true;
				break;
			}
			boolean bedrockOnly = true;
			for (int dx = -slam.radius; dx <= slam.radius; dx++) {
				for (int dz = -slam.radius; dz <= slam.radius; dz++) {
					BlockPos pos = new BlockPos(slam.target.getX() + dx, y, slam.target.getZ() + dz);
					BlockState state = world.getBlockState(pos);
					float hardness = state.getHardness(world, pos);
					if (hardness >= 0) {
						bedrockOnly = false;
					}
					if (state.isAir() || hardness < 0 || !state.getFluidState().isEmpty() || world.getBlockEntity(pos) != null
							|| slam.player.isBlockBreakingRestricted(world, pos, slam.player.interactionManager.getGameMode())) {
						continue;
					}
					if (state.isIn(ConventionalBlockTags.ORES)) {
						drops.addAll(Block.getDroppedStacks(state, world, pos, null, slam.player, slam.pickaxe));
						slam.xp += EvoProgression.xpForBlock(state);
					}
					world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
				}
			}
			if (layer == 0) {
				Vec3d p = new Vec3d(slam.target.getX() + 0.5, y + 0.5, slam.target.getZ() + 0.5);
				world.spawnParticles(ParticleTypes.EXPLOSION, p.x, p.y, p.z, 2, slam.radius, 0.5, slam.radius, 0.0);
			}
			slam.digY--;
			if (bedrockOnly) {
				slam.done = true;
				break;
			}
		}
		if (world.getServer().getTicks() % 4 == 0) {
			world.playSound(null, slam.target, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1.5F, 0.6F);
		}
		deliver(slam, drops);
	}

	private static void deliver(Slam slam, List<ItemStack> drops) {
		ServerPlayerEntity player = slam.player;
		for (ItemStack stack : drops) {
			if (slam.telekinesis && !player.isRemoved()) {
				player.getInventory().insertStack(stack);
			}
			if (!stack.isEmpty()) {
				Vec3d at = player.isRemoved() ? Vec3d.ofCenter(slam.target) : new Vec3d(player.getX(), player.getY() + 0.5, player.getZ());
				ItemEntity item = new ItemEntity(slam.world, at.x, at.y, at.z, stack);
				item.setPickupDelay(0);
				slam.world.spawnEntity(item);
			}
		}
	}
}
