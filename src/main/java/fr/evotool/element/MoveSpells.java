package fr.evotool.element;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Sorts de déplacement du grimoire. Le mouvement lui-même est fait par le client du joueur (il gère ses
 * déplacements : c'est fluide), le serveur ajoute les effets, les dégâts et les téléportations.
 * <ul>
 *   <li>Glace — Glissade de givre : fonce droit devant en gelant l'eau et ce qu'il percute.</li>
 *   <li>Feu — Vol de feu : 3 secondes de vol dans la direction du regard, traînée de flammes.</li>
 *   <li>Électricité — Saut éclair : téléportation jusqu'à 16 blocs, la foudre frappe sur le passage.</li>
 *   <li>Eau — Chevauchée de vague : glisse loin devant sur une vague qui repousse les monstres.</li>
 *   <li>Vent — Bond du vent : un saut immense, sans dégâts de chute.</li>
 *   <li>Terre — Bond sismique : saute très haut puis s'écrase dans un séisme.</li>
 *   <li>Ombre — Pas de l'ombre : apparaît contre la créature visée (ou 10 blocs devant), invisible.</li>
 *   <li>Lumière — Ailes de lumière : s'envole puis plane longuement.</li>
 *   <li>Poison — Brume toxique : invisible et rapide, empoisonne les monstres qu'on frôle.</li>
 * </ul>
 */
public final class MoveSpells {
	private static final double BLINK_RANGE = 16.0;
	private static final double STEP_RANGE = 24.0;
	/** Après un déplacement, encore 4 secondes sans dégâts de chute. */
	private static final int SAFE_AFTER = 80;

	private record Active(Spell spell, int start) {
	}

	/** Côté serveur et côté client (en solo, les deux sont dans le même programme). */
	private static final Map<UUID, Active> SERVER = new HashMap<>();
	private static final Map<UUID, Active> CLIENT = new HashMap<>();
	private static final Map<UUID, Integer> SAFE_UNTIL = new HashMap<>();
	/** Créatures déjà touchées par la glissade ou la vague en cours. */
	private static final Map<UUID, Set<Integer>> HIT = new HashMap<>();

	private MoveSpells() {
	}

	/** Lance un déplacement (appelé des deux côtés). */
	public static void start(PlayerEntity player, Spell spell) {
		if (player.getEntityWorld().isClient()) {
			CLIENT.put(player.getUuid(), new Active(spell, player.age));
			clientStart(player, spell);
			return;
		}
		ServerPlayerEntity server = (ServerPlayerEntity) player;
		SERVER.put(player.getUuid(), new Active(spell, player.age));
		SAFE_UNTIL.put(player.getUuid(), player.age + spell.duration() + SAFE_AFTER);
		HIT.remove(player.getUuid());
		serverStart(server, spell);
	}

	/** Pas de dégâts de chute pendant (et juste après) un déplacement. */
	public static boolean safe(ServerPlayerEntity player) {
		Integer until = SAFE_UNTIL.get(player.getUuid());
		return until != null && player.age <= until;
	}

	public static void forget(UUID id) {
		SERVER.remove(id);
		SAFE_UNTIL.remove(id);
		HIT.remove(id);
	}

	private static Vec3d flat(Vec3d look) {
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		return flat.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : flat.normalize();
	}

	// ------------------------------------------------------------------ client : le mouvement

	private static void clientStart(PlayerEntity player, Spell spell) {
		Vec3d v = player.getVelocity();
		Vec3d flat = flat(player.getRotationVector());
		switch (spell) {
			case WIND_LEAP -> player.setVelocity(flat.x * 1.3, 1.5, flat.z * 1.3);
			case EARTH_SLAM -> player.setVelocity(v.x, 1.3, v.z);
			case LIGHT_WINGS -> player.setVelocity(v.x + flat.x * 0.4, 1.1, v.z + flat.z * 0.4);
			default -> {
			}
		}
	}

	/** Un tick du déplacement de notre joueur (appelé par le client). */
	public static void clientTick(PlayerEntity player) {
		Active active = CLIENT.get(player.getUuid());
		if (active == null) {
			return;
		}
		int t = player.age - active.start();
		if (t > active.spell().duration() || !player.isAlive()) {
			CLIENT.remove(player.getUuid());
			return;
		}
		Vec3d look = player.getRotationVector();
		Vec3d flat = flat(look);
		Vec3d v = player.getVelocity();
		switch (active.spell()) {
			case FIRE_FLIGHT -> player.setVelocity(look.multiply(1.1));
			case FROST_DASH -> player.setVelocity(flat.x * 1.6, Math.min(v.y, 0.1), flat.z * 1.6);
			case TIDE_RIDE -> player.setVelocity(flat.x * 0.95, player.isTouchingWater() ? 0.08 : v.y, flat.z * 0.95);
			case EARTH_SLAM -> {
				if (t > 4 && player.isOnGround()) {
					CLIENT.remove(player.getUuid());
				} else if (t >= 12) {
					player.setVelocity(v.x * 0.5, -2.5, v.z * 0.5);
				}
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ serveur : effets, dégâts, téléportations

	private static void serverStart(ServerPlayerEntity player, Spell spell) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		switch (spell) {
			case LIGHTNING_BLINK -> blink(world, player, spell);
			case SHADOW_STEP -> shadowStep(world, player, spell);
			case LIGHT_WINGS -> {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, spell.duration(), 0, false, false, true));
				world.spawnParticles(ParticleTypes.END_ROD, x, y + 1, z, 40, 0.6, 0.6, 0.6, 0.1);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, 1.8F);
			}
			case TOXIC_MIST -> {
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, spell.duration(), 0, false, false, true));
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, spell.duration(), 2, false, false, true));
				world.spawnParticles(ParticleTypes.ITEM_SLIME, x, y + 1, z, 40, 0.6, 0.8, 0.6, 0.05);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0F, 0.6F);
			}
			case FIRE_FLIGHT -> {
				world.spawnParticles(ParticleTypes.FLAME, x, y + 0.2, z, 40, 0.4, 0.2, 0.4, 0.15);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.2F, 0.6F);
			}
			case WIND_LEAP -> {
				world.spawnParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 30, 0.5, 0.1, 0.5, 0.15);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.4F, 0.6F);
			}
			case FROST_DASH -> world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0F, 0.6F);
			case TIDE_RIDE -> {
				world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.2, z, 60, 0.6, 0.2, 0.6, 0.3);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.2F, 0.8F);
			}
			case EARTH_SLAM -> {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, world.getBlockState(player.getBlockPos().down())),
						x, y + 0.1, z, 40, 0.5, 0.1, 0.5, 0.2);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.0F, 0.6F);
			}
			default -> {
			}
		}
	}

	/** Un tick du déplacement côté serveur : traînées, coups au passage, séisme à l'atterrissage. */
	public static void serverTick(ServerPlayerEntity player) {
		Active active = SERVER.get(player.getUuid());
		if (active == null) {
			return;
		}
		int t = player.age - active.start();
		Spell spell = active.spell();
		if (t > spell.duration() || !player.isAlive()) {
			SERVER.remove(player.getUuid());
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		double x = player.getX();
		double y = player.getY();
		double z = player.getZ();
		switch (spell) {
			case FIRE_FLIGHT -> {
				// une vraie traînée de feu : flammes, braises et fumée
				world.spawnParticles(ParticleTypes.FLAME, x, y + 0.4, z, 12, 0.3, 0.4, 0.3, 0.04);
				world.spawnParticles(ParticleTypes.LAVA, x, y + 0.2, z, 1, 0.2, 0.1, 0.2, 0.0);
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.2, z, 2, 0.2, 0.2, 0.2, 0.01);
				if (t % 6 == 0) {
					world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.4F, 1.4F);
				}
			}
			case FROST_DASH -> {
				world.spawnParticles(ParticleTypes.SNOWFLAKE, x, y + 0.5, z, 6, 0.3, 0.3, 0.3, 0.02);
				ElementalPowers.freezeWater(world, player);
				ram(world, player, spell, 1.8);
			}
			case TIDE_RIDE -> {
				world.spawnParticles(ParticleTypes.SPLASH, x, y + 0.2, z, 12, 0.6, 0.1, 0.6, 0.2);
				world.spawnParticles(ParticleTypes.BUBBLE, x, y + 0.3, z, 3, 0.4, 0.2, 0.4, 0.05);
				ram(world, player, spell, 2.2);
			}
			case WIND_LEAP -> {
				if (t < 12) {
					world.spawnParticles(ParticleTypes.CLOUD, x, y, z, 3, 0.2, 0.1, 0.2, 0.02);
				}
			}
			case EARTH_SLAM -> {
				if (t > 5 && player.isOnGround()) {
					ElementalPowers.quake(world, player, player.getEntityPos(), 5.0, spell.damage(), null);
					world.spawnParticles(ParticleTypes.EXPLOSION, x, y + 0.5, z, 3, 1.5, 0.2, 1.5, 0.0);
					SERVER.remove(player.getUuid());
				}
			}
			case LIGHT_WINGS -> {
				if (!player.isOnGround() && t % 2 == 0) {
					world.spawnParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 2, 0.5, 0.3, 0.5, 0.01);
				}
			}
			case TOXIC_MIST -> {
				if (t % 5 == 0) {
					world.spawnParticles(ParticleTypes.ITEM_SLIME, x, y + 0.6, z, 4, 0.4, 0.4, 0.4, 0.02);
					for (LivingEntity monster : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(2.5),
							e -> e instanceof Monster && e.isAlive())) {
						monster.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 80, 1), player);
					}
				}
			}
			default -> {
			}
		}
	}

	/** Les créatures qu'on percute pendant une glissade ou une vague : touchées une fois chacune. */
	private static void ram(ServerWorld world, ServerPlayerEntity player, Spell spell, double radius) {
		Set<Integer> hit = HIT.computeIfAbsent(player.getUuid(), k -> new HashSet<>());
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(radius),
				e -> Spells.hittable(player, e) && !hit.contains(e.getId()))) {
			hit.add(target.getId());
			target.damage(world, player.getDamageSources().indirectMagic(player, player), spell.damage());
			ElementalPowers.hitEffect(world, player, target, spell.element());
		}
	}

	private static boolean free(ServerWorld world, ServerPlayerEntity player, Vec3d feet) {
		return world.isSpaceEmpty(player, player.getBoundingBox().offset(feet.subtract(player.getEntityPos())));
	}

	/** La place libre la plus loin possible le long du regard, jusqu'à {@code distance} blocs (null s'il n'y en a pas). */
	private static Vec3d furthestFree(ServerWorld world, ServerPlayerEntity player, Vec3d look, double distance) {
		Vec3d eye = player.getEyePos();
		BlockHitResult hit = world.raycast(new RaycastContext(eye, eye.add(look.multiply(distance)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? distance : Math.max(0.0, eye.distanceTo(hit.getPos()) - 0.8);
		Vec3d start = player.getEntityPos();
		for (double d = reach; d >= 1.0; d -= 0.5) {
			Vec3d feet = start.add(look.multiply(d));
			if (free(world, player, feet)) {
				return feet;
			}
			if (free(world, player, feet.add(0, 1, 0))) {
				return feet.add(0, 1, 0);
			}
		}
		return null;
	}

	/** Saut éclair : téléportation le long du regard ; la foudre frappe ce qui est sur le chemin. */
	private static void blink(ServerWorld world, ServerPlayerEntity player, Spell spell) {
		Vec3d look = player.getRotationVector();
		Vec3d dest = furthestFree(world, player, look, BLINK_RANGE);
		if (dest == null) {
			return;
		}
		Vec3d from = player.getEntityPos().add(0, 1, 0);
		Vec3d to = dest.add(0, 1, 0);
		ElementalPowers.arc(world, from, to);
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, from.x, from.y, from.z, 30, 0.4, 0.8, 0.4, 0.3);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(from, to).expand(1.5),
				e -> Spells.hittable(player, e) && e.getBoundingBox().expand(1.0).raycast(from, to).isPresent())) {
			target.damage(world, player.getDamageSources().indirectMagic(player, player), spell.damage());
			ElementalPowers.hitEffect(world, player, target, spell.element());
		}
		player.requestTeleport(dest.x, dest.y, dest.z);
		player.onLanding();
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z, 30, 0.4, 0.8, 0.4, 0.3);
		world.playSound(null, BlockPos.ofFloored(dest), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.8F, 1.6F);
	}

	/** Pas de l'ombre : on surgit contre la créature visée (sinon 10 blocs devant), invisible quelques secondes. */
	private static void shadowStep(ServerWorld world, ServerPlayerEntity player, Spell spell) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector();
		Vec3d far = eye.add(look.multiply(STEP_RANGE));
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, far, new Box(eye, far).expand(1.0),
				e -> e instanceof LivingEntity living && Spells.hittable(player, living), STEP_RANGE * STEP_RANGE);
		Vec3d dest = null;
		if (aimed != null) {
			Vec3d at = aimed.getEntity().getEntityPos();
			Vec3d back = flat(at.subtract(player.getEntityPos()));
			for (double d = 1.6; d <= 3.0 && dest == null; d += 0.5) {
				Vec3d feet = at.subtract(back.multiply(d));
				if (free(world, player, feet)) {
					dest = feet;
				}
			}
		}
		if (dest == null) {
			dest = furthestFree(world, player, look, 10.0);
		}
		if (dest == null) {
			return;
		}
		Vec3d from = player.getEntityPos();
		world.spawnParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1, from.z, 30, 0.4, 0.8, 0.4, 0.05);
		player.requestTeleport(dest.x, dest.y, dest.z);
		player.onLanding();
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 60, 0, false, false, true));
		world.spawnParticles(ParticleTypes.SQUID_INK, dest.x, dest.y + 1, dest.z, 30, 0.4, 0.8, 0.4, 0.05);
		world.playSound(null, BlockPos.ofFloored(dest), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}
}
