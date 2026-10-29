package fr.evotool.upgrade;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pioche Évolutive niveau 100 améliorée à l'évolite, clic droit : la pioche du joueur apparaît en géant,
 * se lève au-dessus de lui puis s'abat vers l'avant (slash). Une onde tranchante creuse alors une tranchée
 * de 100 blocs de long et 7 de large droit devant, de 7 blocs au-dessus des pieds jusqu'à la bedrock.
 * Seuls les minerais rapportent quelque chose.
 */
public final class PickaxeSlam {
	public static final int LENGTH = 100;
	public static final int WIDTH = 7;
	/** Hauteur creusée au-dessus des pieds du joueur (en plus de tout ce qui est en dessous, jusqu'à la bedrock). */
	public static final int HEIGHT_ABOVE = 7;
	private static final int START_DISTANCE = 2;
	/** Longueur creusée par tick (chaque bloc de longueur = 7 colonnes jusqu'à la bedrock). */
	private static final int BLOCKS_PER_TICK = 2;

	private static final int COOLDOWN = 300;
	private static final int GROW_TICKS = 8;
	private static final int SWING_TICK = 12;
	private static final int SWING_TICKS = 4;
	private static final int IMPACT_TICK = SWING_TICK + SWING_TICKS;
	private static final int VANISH_TICK = IMPACT_TICK + 24;
	private static final float SCALE = 5.0F;
	/** Distance du centre du modèle de pioche au bas du manche, avant mise à l'échelle (en blocs). */
	private static final float HANDLE_BOTTOM = 0.8F;

	private static final class Slash {
		final ServerWorld world;
		final ServerPlayerEntity player;
		final ItemStack pickaxe;
		final BlockPos origin;
		final Vec3d forward;
		final Vec3d right;
		final int startTick;
		final DisplayEntity.ItemDisplayEntity giant;
		int carved;
		int xp;

		Slash(ServerWorld world, ServerPlayerEntity player, ItemStack pickaxe, int startTick, DisplayEntity.ItemDisplayEntity giant) {
			this.world = world;
			this.player = player;
			this.pickaxe = pickaxe;
			this.origin = player.getBlockPos();
			this.forward = horizontal(player.getYaw());
			this.right = new Vec3d(-forward.z, 0, forward.x);
			this.startTick = startTick;
			this.giant = giant;
		}
	}

	private static final List<Slash> SLASHES = new ArrayList<>();

	private PickaxeSlam() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(PickaxeSlam::onUse);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getTicks()));
	}

	public static boolean isReady(ItemStack stack) {
		return EvoProgression.kindOf(stack) == EvoKind.PICKAXE
				&& EvoliteUpgrade.isUpgraded(stack)
				&& EvoProgression.get(stack).level() >= EvoProgression.MAX_LEVEL;
	}

	/** Direction horizontale du regard (yaw Minecraft : 0 = sud, +Z). */
	public static Vec3d horizontal(float yaw) {
		double rad = Math.toRadians(yaw);
		return new Vec3d(-Math.sin(rad), 0, Math.cos(rad));
	}

	/** Les colonnes (x, z) de la tranchée situées entre deux distances (en blocs) devant le joueur (y = 0). */
	public static Set<BlockPos> trench(BlockPos origin, Vec3d forward, Vec3d right, double from, double to) {
		Set<BlockPos> columns = new LinkedHashSet<>();
		double half = (WIDTH - 1) / 2.0;
		for (double d = from; d < to; d += 0.5) {
			for (double w = -half - 0.25; w <= half + 0.25; w += 0.5) {
				double x = origin.getX() + 0.5 + forward.x * d + right.x * w;
				double z = origin.getZ() + 0.5 + forward.z * d + right.z * w;
				columns.add(BlockPos.ofFloored(x, 0, z));
			}
		}
		return columns;
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		ItemStack pickaxe = player.getStackInHand(hand);
		if (EvoProgression.kindOf(pickaxe) != EvoKind.PICKAXE) {
			return ActionResult.PASS;
		}
		if (!isReady(pickaxe)) {
			// On explique pourquoi rien ne se passe.
			if (world instanceof ServerWorld) {
				player.sendMessage(Text.translatable("message.evotool.slash_locked").formatted(Formatting.GRAY), true);
			}
			return ActionResult.PASS;
		}
		if (player.getItemCooldownManager().isCoolingDown(pickaxe)) {
			return ActionResult.FAIL;
		}
		player.getItemCooldownManager().set(pickaxe, COOLDOWN);
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			ItemStack shown = pickaxe.copyWithCount(1);
			shown.remove(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE);
			float stage = EvoProgression.stage(EvoProgression.get(pickaxe).level());
			shown.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(stage), List.of(), List.of(), List.of()));
			Vec3d pivot = pivot(serverPlayer);
			DisplayEntity.ItemDisplayEntity giant = GiantItem.spawn(serverWorld, shown, ItemDisplayContext.NONE, pivot,
					serverPlayer.getYaw(), new Quaternionf(), 0.2F, 1);
			// Levée au-dessus du joueur (fer légèrement en arrière), en grandissant.
			pose(giant, -35, SCALE, GROW_TICKS);
			serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.7F);
			SLASHES.add(new Slash(serverWorld, serverPlayer, pickaxe, serverWorld.getServer().getTicks(), giant));
		}
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	/** Le point où l'on « tient » la pioche géante : au-dessus et un peu devant le joueur. */
	private static Vec3d pivot(ServerPlayerEntity player) {
		Vec3d f = horizontal(player.getYaw());
		return new Vec3d(player.getX() + f.x * 1.5, player.getY() + 2.0, player.getZ() + f.z * 1.5);
	}

	/**
	 * Oriente la pioche géante en la faisant pivoter autour du bas de son manche.
	 * {@code angle} : 0 = verticale, positif = le fer bascule vers l'avant.
	 */
	private static void pose(DisplayEntity.ItemDisplayEntity giant, float angle, float scale, int ticks) {
		Quaternionf rotation = new Quaternionf().rotationX((float) Math.toRadians(angle));
		// translation = -R·(S·h) avec h le bas du manche : le bas du manche reste sur la position de l'entité.
		Vector3f handle = new Vector3f(0, -HANDLE_BOTTOM * scale, 0);
		rotation.transform(handle);
		giant.setInterpolationDuration(ticks);
		giant.setStartInterpolation(0);
		giant.setTransformation(new net.minecraft.util.math.AffineTransformation(handle.negate(), rotation,
				new Vector3f(scale, scale, scale), new Quaternionf()));
	}

	private static void tick(int tick) {
		for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
			Slash slash = it.next();
			int step = tick - slash.startTick;
			if (step == SWING_TICK) {
				// Le slash : la pioche s'abat vers l'avant.
				pose(slash.giant, 115, SCALE, SWING_TICKS);
				slash.world.playSound(null, slash.origin, SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 2.0F, 0.5F);
			}
			if (step == IMPACT_TICK) {
				impact(slash);
			}
			if (step >= IMPACT_TICK && slash.carved < LENGTH) {
				carve(slash);
			}
			if (step == VANISH_TICK) {
				GiantItem.remove(slash.world, slash.giant, SCALE);
			}
			if (step >= VANISH_TICK && slash.carved >= LENGTH) {
				if (slash.xp > 0 && !slash.player.isCreative() && !slash.player.isRemoved()) {
					EvoProgression.addXp(slash.pickaxe, slash.xp, 0, slash.player, slash.world);
				}
				it.remove();
			}
		}
	}

	/** POW : explosion et choc à l'endroit où la pioche touche. */
	private static void impact(Slash slash) {
		ServerWorld world = slash.world;
		Vec3d c = Vec3d.ofBottomCenter(slash.origin).add(slash.forward.multiply(5.0));
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 2, 0.5, 0.2, 0.5, 0.0);
		world.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y + 1.5, c.z, 1, 0, 0, 0, 0);
		world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 3.0F, 0.6F);
		world.playSound(null, BlockPos.ofFloored(c), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 2.0F, 0.5F);
	}

	/** L'onde tranchante avance de quelques blocs par tick en creusant la tranchée. */
	private static void carve(Slash slash) {
		ServerWorld world = slash.world;
		double from = START_DISTANCE + slash.carved;
		int advance = Math.min(BLOCKS_PER_TICK, LENGTH - slash.carved);
		double to = from + advance;
		List<ItemStack> drops = new ArrayList<>();
		int top = slash.origin.getY() + HEIGHT_ABOVE - 1;
		int bottom = world.getBottomY();
		for (BlockPos column : trench(slash.origin, slash.forward, slash.right, from, to)) {
			if (!world.isChunkLoaded(column.getX() >> 4, column.getZ() >> 4)) {
				continue;
			}
			// De 7 blocs au-dessus des pieds jusqu'en bas du monde ; la bedrock (incassable) reste.
			for (int y = top; y >= bottom; y--) {
				BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
				BlockState state = world.getBlockState(pos);
				if (state.isAir() || state.getHardness(world, pos) < 0 || !state.getFluidState().isEmpty()
						|| world.getBlockEntity(pos) != null
						|| slash.player.isBlockBreakingRestricted(world, pos, slash.player.interactionManager.getGameMode())) {
					continue;
				}
				if (state.isIn(ConventionalBlockTags.ORES)) {
					drops.addAll(Block.getDroppedStacks(state, world, pos, null, slash.player, slash.pickaxe));
					slash.xp += EvoProgression.xpForBlock(state);
				}
				// Sans mise à jour des voisins : beaucoup plus rapide pour des milliers de blocs.
				world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
			}
		}
		slash.carved += advance;

		// L'onde : un mur de particules qui file vers l'avant.
		Vec3d front = Vec3d.ofBottomCenter(slash.origin).add(slash.forward.multiply(to));
		for (double w = -3.5; w <= 3.5; w += 0.7) {
			Vec3d p = front.add(slash.right.multiply(w));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 3.5, p.z, 1, 0, 2.5, 0, 0);
			world.spawnParticles(ParticleTypes.EXPLOSION, p.x, p.y + 1.0, p.z, 1, 0.2, 1.5, 0.2, 0);
		}
		world.playSound(null, BlockPos.ofFloored(front), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.2F, 1.2F);
		deliver(slash, drops);
	}

	private static void deliver(Slash slash, List<ItemStack> drops) {
		ServerPlayerEntity player = slash.player;
		boolean telekinesis = EvoliteUpgrade.has(slash.pickaxe, EvoliteUpgrade.Upgrade.TELEKINESIS);
		for (ItemStack stack : drops) {
			if (telekinesis && !player.isRemoved()) {
				player.getInventory().insertStack(stack);
			}
			if (!stack.isEmpty() && !player.isRemoved()) {
				ItemEntity item = new ItemEntity(slash.world, player.getX(), player.getY() + 0.5, player.getZ(), stack);
				item.setPickupDelay(0);
				slash.world.spawnEntity(item);
			}
		}
	}
}
