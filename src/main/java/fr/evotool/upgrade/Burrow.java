package fr.evotool.upgrade;

import fr.evotool.network.BurrowPayload;
import fr.evotool.upgrade.EvoliteUpgrade.Upgrade;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

/**
 * Bottes améliorées : plongée souterraine. Rester accroupi un instant sur le sol fait plonger dedans ;
 * sous terre on vole à travers la roche comme un spectateur, trois fois plus vite qu'en créatif
 * (espace pour monter, accroupi pour descendre). On ressort en remontant à travers le sol.
 *
 * <p>Le client doit traverser les blocs comme le serveur : le serveur lui envoie {@link BurrowPayload},
 * et {@code PlayerEntityMixin} active le passe-muraille des deux côtés au début de chaque tick.
 */
public final class Burrow {
	/** Tag posé sur le joueur tant qu'il est sous terre (sauvegardé avec lui : il reste sous terre en se reconnectant). */
	public static final String TAG = "evotool_burrowing";
	/** Vitesse de vol sous terre (créatif : 0,05). */
	public static final float SPEED = 0.15F;
	/** Il faut rester accroupi 0,3 s au sol pour plonger. */
	private static final int DIVE_TICKS = 6;
	/** Plongée annulée si on n'est toujours pas entré dans le sol au bout de 2 s (accroupi relâché trop tôt). */
	private static final int START_GRACE = 40;
	private static final int FALL_IMMUNITY_TICKS = 100;
	private static final int NIGHT_VISION_TICKS = 260;

	/** Côté client : le serveur a dit que notre joueur est sous terre. */
	public static volatile boolean clientActive;

	private static final class State {
		boolean entered;
		boolean wasInside;
		int airTicks;
		double lastY;
		Vec3d safe;
	}

	private static final Map<UUID, Integer> SNEAK = new HashMap<>();
	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Map<UUID, Integer> FALL_IMMUNE_UNTIL = new HashMap<>();

	private Burrow() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tick(player, server.getTicks());
			}
		});

		// Sous terre : ni étouffement, ni noyade, ni lave ; et pas de dégâts de chute en ressortant.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			boolean environment = source.isOf(DamageTypes.IN_WALL) || source.isIn(DamageTypeTags.IS_DROWNING)
					|| source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_FALL);
			if (isBurrowing(player) && environment) {
				return false;
			}
			Integer until = FALL_IMMUNE_UNTIL.get(player.getUuid());
			return !(source.isIn(DamageTypeTags.IS_FALL) && until != null && player.getEntityWorld().getServer().getTicks() <= until);
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (isBurrowing(handler.getPlayer())) {
				resume(handler.getPlayer());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUuid()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (isBurrowing(newPlayer)) {
				stop(newPlayer, false);
			}
		});
	}

	/** Vrai si ce joueur traverse les blocs en ce moment (appelé des deux côtés par le mixin). */
	public static boolean isBurrowing(PlayerEntity player) {
		if (player.getEntityWorld().isClient()) {
			return clientActive && player.isMainPlayer();
		}
		return player.getCommandTags().contains(TAG);
	}

	public static boolean hasBoots(PlayerEntity player) {
		return EvoliteUpgrade.has(player.getEquippedStack(EquipmentSlot.FEET), Upgrade.BURROW);
	}

	private static boolean canUse(ServerPlayerEntity player) {
		return hasBoots(player) && !player.isCreative() && !player.isSpectator() && player.isAlive() && !player.hasVehicle();
	}

	/** Un tick de plongée pour ce joueur (public pour l'auto-test, qui utilise un faux joueur). */
	public static void tick(ServerPlayerEntity player, int tick) {
		UUID id = player.getUuid();
		if (isBurrowing(player)) {
			if (canUse(player)) {
				underground(player, tick);
			} else {
				stop(player, true);
			}
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockPos ground = BlockPos.ofFloored(player.getX(), player.getY() - 0.2, player.getZ());
		if (canUse(player) && player.isSneaking() && player.isOnGround() && !player.getAbilities().flying
				&& diggable(world, ground)) {
			// le sol s'effrite sous les pieds pendant qu'on reste accroupi, puis on plonge dedans
			int held = SNEAK.merge(id, 1, Integer::sum);
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(ground)),
					player.getX(), player.getY() + 0.1, player.getZ(), 6, 0.3, 0.05, 0.3, 0.1);
			if (held >= DIVE_TICKS) {
				SNEAK.remove(id);
				start(player);
			}
		} else {
			SNEAK.remove(id);
		}
	}

	private static boolean diggable(ServerWorld world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		return !state.isAir() && state.getHardness(world, pos) >= 0.0F;
	}

	public static void start(ServerPlayerEntity player) {
		player.addCommandTag(TAG);
		State state = new State();
		state.lastY = player.getY();
		state.safe = player.getEntityPos();
		STATES.put(player.getUuid(), state);
		// le client doit savoir qu'il traverse les blocs AVANT de recevoir le vol, sinon il se reposerait au sol
		send(player, true);
		PlayerAbilities abilities = player.getAbilities();
		abilities.allowFlying = true;
		abilities.flying = true;
		abilities.setFlySpeed(SPEED);
		player.sendAbilitiesUpdate();

		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockPos ground = BlockPos.ofFloored(player.getX(), player.getY() - 0.2, player.getZ());
		BlockState block = world.getBlockState(ground);
		world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block),
				player.getX(), player.getY() + 0.2, player.getZ(), 40, 0.4, 0.2, 0.4, 0.2);
		world.playSound(null, ground, block.getSoundGroup().getBreakSound(), SoundCategory.PLAYERS, 1.2F, 0.7F);
		world.playSound(null, ground, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.4F, 0.6F);
	}

	/** Reconnexion sous terre : le client doit de nouveau traverser les blocs. */
	private static void resume(ServerPlayerEntity player) {
		State state = new State();
		state.entered = true;
		state.lastY = player.getY();
		state.safe = player.getEntityPos();
		STATES.put(player.getUuid(), state);
		send(player, true);
		player.sendAbilitiesUpdate();
	}

	private static void underground(ServerPlayerEntity player, int tick) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		State state = STATES.get(player.getUuid());
		if (state == null) {
			resume(player);
			state = STATES.get(player.getUuid());
		}
		PlayerAbilities abilities = player.getAbilities();
		if (!abilities.allowFlying || abilities.getFlySpeed() != SPEED) {
			abilities.allowFlying = true;
			abilities.flying = true;
			abilities.setFlySpeed(SPEED);
			player.sendAbilitiesUpdate();
		}

		// garde-fous : on ne traverse ni la bedrock (ni aucun bloc incassable), ni le fond du monde
		Box box = player.getBoundingBox();
		if (player.getY() < world.getBottomY() + 1 || touchesUnbreakable(world, box)) {
			player.requestTeleport(state.safe.x, state.safe.y, state.safe.z);
			return;
		}
		state.safe = player.getEntityPos();

		player.setAir(player.getMaxAir());
		if (player.isOnFire()) {
			player.extinguish();
		}
		if (tick % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
		}

		boolean inside = !world.isSpaceEmpty(player, box.contract(0.01));
		double rise = player.getY() - state.lastY;
		state.lastY = player.getY();
		if (inside) {
			state.entered = true;
			state.airTicks = 0;
			if (tick % 3 == 0) {
				BlockPos at = BlockPos.ofFloored(player.getX(), player.getY() + 0.5, player.getZ());
				BlockState block = world.getBlockState(at);
				if (!block.isAir()) {
					world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block),
							player.getX(), player.getY() + 0.9, player.getZ(), 4, 0.3, 0.5, 0.3, 0.05);
				}
			}
		} else {
			state.airTicks++;
			// on ressort quand on remonte à travers le sol, ou dès qu'on voit le ciel
			boolean emerged = state.wasInside && rise > 0.02;
			boolean sky = world.isSkyVisible(BlockPos.ofFloored(player.getEyePos()));
			if (state.entered ? emerged || sky : state.airTicks > START_GRACE) {
				surface(player, state.entered);
				return;
			}
		}
		state.wasInside = inside;
	}

	private static boolean touchesUnbreakable(ServerWorld world, Box box) {
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ),
				BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && state.getHardness(world, pos) < 0.0F) {
				return true;
			}
		}
		return false;
	}

	/** Sortie du sol : un bond hors de terre, sans dégâts de chute. */
	private static void surface(ServerPlayerEntity player, boolean leap) {
		stop(player, false);
		if (!leap) {
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		BlockPos ground = BlockPos.ofFloored(player.getX(), player.getY() - 0.5, player.getZ());
		BlockState block = world.getBlockState(ground);
		if (!block.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block),
					player.getX(), player.getY(), player.getZ(), 50, 0.5, 0.3, 0.5, 0.3);
			world.playSound(null, ground, block.getSoundGroup().getBreakSound(), SoundCategory.PLAYERS, 1.2F, 0.8F);
		}
		world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.3, player.getZ(), 12, 0.4, 0.2, 0.4, 0.05);
		player.addVelocity(0.0, 0.55, 0.0);
		player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
	}

	/**
	 * Fin de la plongée : le client arrête de traverser les blocs et le joueur retrouve son vol normal
	 * (celui des ailes de l'infini s'il les porte). Avec {@code rescue}, s'il est encore dans la roche
	 * (bottes enlevées sous terre…), il est remonté à la surface pour ne pas étouffer.
	 */
	public static void stop(ServerPlayerEntity player, boolean rescue) {
		player.removeCommandTag(TAG);
		STATES.remove(player.getUuid());
		send(player, false);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().flying = false;
		}
		InfinityWings.restoreAbilities(player);
		StatusEffectInstance vision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= NIGHT_VISION_TICKS) {
			player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		FALL_IMMUNE_UNTIL.put(player.getUuid(), world.getServer().getTicks() + FALL_IMMUNITY_TICKS);
		if (rescue && !world.isSpaceEmpty(player, player.getBoundingBox().contract(0.01))) {
			int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
			player.requestTeleport(player.getX(), top + 0.1, player.getZ());
		}
	}

	private static void send(ServerPlayerEntity player, boolean active) {
		if (ServerPlayNetworking.canSend(player, BurrowPayload.ID)) {
			ServerPlayNetworking.send(player, new BurrowPayload(active));
		}
	}

	private static void forget(UUID id) {
		SNEAK.remove(id);
		STATES.remove(id);
		FALL_IMMUNE_UNTIL.remove(id);
	}
}
