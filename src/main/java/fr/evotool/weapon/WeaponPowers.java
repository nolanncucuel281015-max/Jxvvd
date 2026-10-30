package fr.evotool.weapon;

import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModdedWeaponItem;
import fr.evotool.upgrade.EvoliteUpgrade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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
 * Pouvoirs des armes moddées : un passif à chaque coup porté, et une attaque spéciale au clic droit
 * ({@link ModdedWeaponItem#use}). Les attaques ne touchent ni les joueurs, ni les animaux apprivoisés,
 * ni les villageois, ni les golems construits par les joueurs.
 */
public final class WeaponPowers {
	private static final float SCYTHE_LIFESTEAL = 0.15F;
	private static final float CLEAVE_RATIO = 0.4F;
	private static final float PIERCE_RATIO = 0.7F;

	/** Évite que les dégâts des pouvoirs relancent les passifs en boucle. */
	private static boolean applyingBonusDamage = false;
	/** Multiplicateur de l'attaque spéciale en cours (niveau d'évolution, amélioration à l'évolite). */
	private static float power = 1.0F;
	/** Lame Détonante : dernier slash explosif de chaque joueur (un toutes les demi-secondes au plus). */
	private static final Map<UUID, Integer> LAST_BLAST = new HashMap<>();
	private static final int BLAST_INTERVAL = 10;

	/** Projectile en vol : vague de l'espadon ou éclat du sceptre, avancé à chaque tick. */
	private static final class Shot {
		final ServerWorld world;
		final ServerPlayerEntity player;
		final WeaponKind kind;
		final Vec3d dir;
		final double speed;
		final double range;
		final float power;
		final Set<LivingEntity> hit = new HashSet<>();
		Vec3d pos;
		double travelled;

		Shot(ServerWorld world, ServerPlayerEntity player, WeaponKind kind, Vec3d pos, Vec3d dir, double speed, double range) {
			this.world = world;
			this.player = player;
			this.kind = kind;
			this.pos = pos;
			this.dir = dir;
			this.speed = speed;
			this.range = range;
			this.power = WeaponPowers.power;
		}
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	private WeaponPowers() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(WeaponPowers::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(server -> tickShots());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> SHOTS.clear());
	}

	/** Un ennemi qu'on peut toucher avec les pouvoirs. */
	public static boolean isFoe(PlayerEntity player, Entity e) {
		return e instanceof LivingEntity living && living != player && living.isAlive()
				&& !(e instanceof PlayerEntity) && !(e instanceof ArmorStandEntity) && !(e instanceof MerchantEntity)
				&& !(e instanceof TameableEntity tame && tame.isTamed())
				&& !(e instanceof IronGolemEntity golem && golem.isPlayerCreated());
	}

	private static List<LivingEntity> foes(ServerWorld world, PlayerEntity player, Box box) {
		return world.getEntitiesByClass(LivingEntity.class, box, e -> isFoe(player, e));
	}

	private static void hurt(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float amount) {
		hurt(world, target, player.getDamageSources().playerAttack(player), amount);
	}

	/** Dégâts d'une attaque spéciale : x2 au niveau 100, x1,5 de plus avec l'amélioration à l'évolite. */
	private static void strike(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float amount) {
		hurt(world, player, target, amount * power);
	}

	public static float powerOf(ItemStack stack) {
		float p = 1.0F + EvoProgression.get(stack).level() / 100.0F;
		return EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED) ? p * 1.5F : p;
	}

	/** Temps de recharge de l'attaque spéciale : jusqu'à 2 fois plus court au niveau 100, encore 2 fois plus amélioré. */
	public static int cooldown(WeaponKind kind, ItemStack stack) {
		double c = kind.cooldown() * (1.0 - EvoProgression.get(stack).level() / 200.0);
		if (EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED)) {
			c *= 0.5;
		}
		return Math.max(6, (int) Math.round(c));
	}

	private static void hurt(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
		applyingBonusDamage = true;
		try {
			target.damage(world, source, amount);
		} finally {
			applyingBonusDamage = false;
		}
	}

	private static Vec3d feet(Entity e) {
		return new Vec3d(e.getX(), e.getY(), e.getZ());
	}

	/** Direction du regard à l'horizontale (jamais nulle). */
	private static Vec3d flatLook(Entity e) {
		Vec3d look = e.getRotationVector();
		Vec3d flat = new Vec3d(look.x, 0, look.z);
		return flat.lengthSquared() < 1.0E-4 ? Vec3d.fromPolar(0.0F, e.getYaw()) : flat.normalize();
	}

	private static double flatDistance(Entity a, Entity b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ------------------------------------------------------------------ passifs (chaque coup à l'arme)

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (applyingBonusDamage || blocked || damageTaken <= 0 || !(source.getAttacker() instanceof ServerPlayerEntity player)
				|| source.getSource() != player || !(player.getMainHandStack().getItem() instanceof ModdedWeaponItem weapon)) {
			return;
		}
		ServerWorld world = (ServerWorld) entity.getEntityWorld();
		Vec3d c = entity.getBoundingBox().getCenter();
		switch (weapon.weaponKind()) {
			case SCYTHE -> {
				player.heal(damageTaken * SCYTHE_LIFESTEAL);
				world.spawnParticles(ParticleTypes.WITCH, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
			}
			case KATANA -> {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), player);
				world.spawnParticles(ParticleTypes.CRIMSON_SPORE, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.02);
			}
			case WARHAMMER -> {
				entity.addVelocity(0, 0.45, 0);
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), player);
				world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getY() + 0.1, entity.getZ(), 8, 0.4, 0.05, 0.4, 0.05);
			}
			case GREATSWORD -> {
				for (LivingEntity other : foes(world, player, entity.getBoundingBox().expand(3.0, 1.0, 3.0))) {
					if (other != entity) {
						hurt(world, player, other, damageTaken * CLEAVE_RATIO);
						Vec3d o = other.getBoundingBox().getCenter();
						world.spawnParticles(ParticleTypes.SWEEP_ATTACK, o.x, o.y, o.z, 1, 0, 0, 0, 0);
					}
				}
			}
			case DAGGER -> {
				Vec3d facing = flatLook(entity);
				Vec3d toTarget = new Vec3d(entity.getX() - player.getX(), 0, entity.getZ() - player.getZ());
				if (entity.isAlive() && toTarget.lengthSquared() > 1.0E-4 && facing.dotProduct(toTarget.normalize()) > 0.3) {
					// coup dans le dos : les dégâts sont doublés
					entity.timeUntilRegen = 0;
					hurt(world, player, entity, damageTaken);
					world.spawnParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.3);
					world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 6, 0.2, 0.3, 0.2, 0.01);
					world.playSound(null, entity.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 0.6F);
				}
			}
			case HALBERD -> {
				Vec3d eye = player.getEyePos();
				Vec3d look = player.getRotationVector();
				double reach = c.subtract(eye).dotProduct(look);
				for (LivingEntity other : foes(world, player, entity.getBoundingBox().expand(3.5, 1.5, 3.5))) {
					Vec3d o = other.getBoundingBox().getCenter();
					double along = o.subtract(eye).dotProduct(look);
					if (other != entity && along > reach && along < reach + 3.5
							&& o.distanceTo(eye.add(look.multiply(along))) < 1.2 + other.getWidth() / 2) {
						hurt(world, player, other, damageTaken * PIERCE_RATIO);
						world.spawnParticles(ParticleTypes.ENCHANTED_HIT, o.x, o.y, o.z, 10, 0.2, 0.3, 0.2, 0.1);
					}
				}
			}
			case STAFF -> {
				entity.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0), player);
				world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.03);
			}
		}
	}

	// ------------------------------------------------------------------ attaques spéciales (clic droit)

	public static void activate(WeaponKind kind, ServerWorld world, ServerPlayerEntity player, ItemStack stack) {
		power = powerOf(stack);
		switch (kind) {
			case SCYTHE -> reap(world, player);
			case KATANA -> iaijutsu(world, player);
			case WARHAMMER -> earthquake(world, player);
			case GREATSWORD -> {
				Vec3d dir = flatLook(player);
				SHOTS.add(new Shot(world, player, kind, feet(player).add(0, 1.0, 0).add(dir), dir, 1.5, 16.0));
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 0.5F);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.8F, 1.6F);
			}
			case DAGGER -> shadowStep(world, player);
			case HALBERD -> royalSweep(world, player);
			case STAFF -> {
				Vec3d dir = player.getRotationVector();
				SHOTS.add(new Shot(world, player, kind, player.getEyePos().add(dir.multiply(0.8)), dir, 2.5, 32.0));
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 1.4F);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.6F, 1.8F);
			}
			case BLAST_BLADE -> {
				Vec3d dir = flatLook(player);
				SHOTS.add(new Shot(world, player, kind, feet(player).add(0, 0.6, 0).add(dir.multiply(2.0)), dir, 2.0, 14.0));
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 1.0F, 1.4F);
			}
		}
		power = 1.0F;
	}

	/** Faux : tourbillon de 4,5 blocs qui attire les ennemis et rend de la vie pour chacun. */
	private static void reap(ServerWorld world, ServerPlayerEntity player) {
		double radius = 4.5;
		for (int i = 0; i < 24; i++) {
			double a = Math.PI * 2 * i / 24;
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + Math.cos(a) * 2.8, player.getY() + 1.0,
					player.getZ() + Math.sin(a) * 2.8, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.WITCH, player.getX() + Math.cos(a) * radius, player.getY() + 0.6,
					player.getZ() + Math.sin(a) * radius, 2, 0.1, 0.2, 0.1, 0.02);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 0.6F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 0.7F);
		float healed = 0;
		for (LivingEntity foe : foes(world, player, player.getBoundingBox().expand(radius, 1.5, radius))) {
			if (flatDistance(player, foe) > radius + foe.getWidth() / 2) {
				continue;
			}
			strike(world, player, foe, 10.0F);
			Vec3d pull = new Vec3d(player.getX() - foe.getX(), 0, player.getZ() - foe.getZ()).normalize().multiply(0.35);
			foe.addVelocity(pull.x, 0.15, pull.z);
			healed += 1.5F;
		}
		if (healed > 0) {
			player.heal(Math.min(8.0F, healed));
			world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.0, player.getZ(), 3, 0.4, 0.2, 0.4, 0);
		}
	}

	/** Point d'arrivée d'une ruée : on avance tant que le joueur tient sans entrer dans un bloc. */
	private static Vec3d dashEnd(ServerWorld world, ServerPlayerEntity player, Vec3d dir, double distance) {
		Vec3d start = feet(player);
		Vec3d end = start;
		for (double d = 0.5; d <= distance; d += 0.5) {
			Vec3d next = start.add(dir.multiply(d));
			if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(next.subtract(start)))) {
				break;
			}
			end = next;
		}
		return end;
	}

	/** Katana : ruée éclair de 8 blocs, chaque ennemi traversé est tranché et saigne. */
	private static void iaijutsu(ServerWorld world, ServerPlayerEntity player) {
		Vec3d dir = flatLook(player);
		Vec3d start = feet(player);
		Vec3d end = dashEnd(world, player, dir, 8.0);
		Box path = player.getBoundingBox().union(player.getBoundingBox().offset(end.subtract(start))).expand(1.2, 0.5, 1.2);
		for (LivingEntity foe : foes(world, player, path)) {
			strike(world, player, foe, 12.0F);
			foe.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 80, 1), player);
			Vec3d c = foe.getBoundingBox().getCenter();
			world.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.1);
		}
		double length = end.distanceTo(start);
		for (double d = 0; d <= length; d += 0.8) {
			Vec3d p = start.add(dir.multiply(d));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 1.0, p.z, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.CRIMSON_SPORE, p.x, p.y + 1.0, p.z, 4, 0.3, 0.4, 0.3, 0.01);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.2F, 1.5F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.4F);
		player.requestTeleport(end.x, end.y, end.z);
		player.onLanding();
	}

	/** Marteau : le sol tremble sur 6 blocs, les ennemis sont projetés en l'air et ralentis. */
	private static void earthquake(ServerWorld world, ServerPlayerEntity player) {
		double radius = 6.0;
		BlockState ground = world.getBlockState(player.getBlockPos().down());
		if (ground.isAir()) {
			ground = Blocks.STONE.getDefaultState();
		}
		BlockStateParticleEffect debris = new BlockStateParticleEffect(ParticleTypes.BLOCK, ground);
		for (int ring = 1; ring <= 3; ring++) {
			double r = radius * ring / 3;
			int n = 12 * ring;
			for (int i = 0; i < n; i++) {
				double a = Math.PI * 2 * i / n;
				double x = player.getX() + Math.cos(a) * r;
				double z = player.getZ() + Math.sin(a) * r;
				world.spawnParticles(debris, x, player.getY() + 0.1, z, 6, 0.3, 0.1, 0.3, 0.15);
				if (i % 3 == 0) {
					world.spawnParticles(ParticleTypes.EXPLOSION, x, player.getY() + 0.3, z, 1, 0, 0, 0, 0);
				}
			}
		}
		world.spawnParticles(ParticleTypes.GUST, player.getX(), player.getY() + 0.2, player.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0F, 0.7F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_MACE_SMASH_GROUND_HEAVY, SoundCategory.PLAYERS, 1.5F, 0.8F);
		for (LivingEntity foe : foes(world, player, player.getBoundingBox().expand(radius, 3.0, radius))) {
			double d = flatDistance(player, foe);
			if (d > radius + foe.getWidth() / 2) {
				continue;
			}
			strike(world, player, foe, (float) (14.0 * (1.0 - d / (radius + 2.0))));
			Vec3d away = new Vec3d(foe.getX() - player.getX(), 0, foe.getZ() - player.getZ()).normalize().multiply(0.5);
			foe.addVelocity(away.x, 0.9, away.z);
			foe.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), player);
		}
	}

	/** Dague : téléportation dans le dos de l'ennemi visé (16 blocs), invisible et rapide pendant 3 secondes. */
	private static void shadowStep(ServerWorld world, ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVector().multiply(32.0));
		BlockHitResult wall = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getPos();
		}
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, end,
				player.getBoundingBox().stretch(end.subtract(eye)).expand(1.0), e -> isFoe(player, e), eye.squaredDistanceTo(end));
		Vec3d from = feet(player);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 20, 0.3, 0.6, 0.3, 0.02);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity target) {
			Vec3d facing = flatLook(target);
			Vec3d center = feet(target);
			Vec3d destination = null;
			double back = target.getWidth() / 2 + 0.9;
			// dans le dos ; sinon sur un côté ; sinon devant
			for (Vec3d side : new Vec3d[] {facing.multiply(-1), new Vec3d(-facing.z, 0, facing.x), new Vec3d(facing.z, 0, -facing.x), facing}) {
				Vec3d candidate = center.add(side.multiply(back));
				if (world.isSpaceEmpty(player, player.getBoundingBox().offset(candidate.subtract(from)))) {
					destination = candidate;
					break;
				}
			}
			if (destination != null) {
				float yaw = (float) (Math.toDegrees(Math.atan2(target.getZ() - destination.z, target.getX() - destination.x)) - 90.0);
				player.teleport(world, destination.x, destination.y, destination.z, Set.of(), yaw, 10.0F, true);
			}
		} else {
			Vec3d destination = dashEnd(world, player, flatLook(player), 14.0);
			player.requestTeleport(destination.x, destination.y, destination.z);
		}
		player.onLanding();
		// 20 secondes d'invisibilité après chaque pas de l'ombre
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 400, 0, false, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1, false, false, true));
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.3, 0.6, 0.3, 0.05);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.6, 0.3, 0.01);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.6F);
	}

	/** Hallebarde : grand balayage en demi-cercle devant le joueur. */
	private static void royalSweep(ServerWorld world, ServerPlayerEntity player) {
		double radius = 5.5;
		Vec3d look = flatLook(player);
		for (int i = -6; i <= 6; i++) {
			double a = Math.toRadians(i * 15);
			double dx = look.x * Math.cos(a) - look.z * Math.sin(a);
			double dz = look.x * Math.sin(a) + look.z * Math.cos(a);
			for (double r = 2.0; r <= 4.0; r += 2.0) {
				world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + dx * r, player.getY() + 1.1, player.getZ() + dz * r, 1, 0, 0, 0, 0);
			}
			world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX() + dx * radius, player.getY() + 1.0, player.getZ() + dz * radius,
					3, 0.1, 0.2, 0.1, 0.05);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.4F, 0.7F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 1.0F, 0.8F);
		for (LivingEntity foe : foes(world, player, player.getBoundingBox().expand(radius, 2.0, radius))) {
			Vec3d v = new Vec3d(foe.getX() - player.getX(), 0, foe.getZ() - player.getZ());
			if (v.length() > radius + foe.getWidth() / 2 || (v.lengthSquared() > 1.0E-4 && v.normalize().dotProduct(look) < -0.1)) {
				continue;
			}
			strike(world, player, foe, 11.0F);
			Vec3d away = v.lengthSquared() > 1.0E-4 ? v.normalize().multiply(0.9) : look.multiply(0.9);
			foe.addVelocity(away.x, 0.3, away.z);
		}
	}

	// ------------------------------------------------------------------ projectiles

	private static void tickShots() {
		Iterator<Shot> it = SHOTS.iterator();
		while (it.hasNext()) {
			if (!step(it.next())) {
				it.remove();
			}
		}
	}

	/** Fait avancer un projectile d'un tick ; renvoie false quand il a fini. */
	private static boolean step(Shot s) {
		if (s.player.isRemoved() || s.player.getEntityWorld() != s.world) {
			return false;
		}
		if (s.kind == WeaponKind.BLAST_BLADE) {
			return stepBlast(s);
		}
		boolean wave = s.kind == WeaponKind.GREATSWORD;
		int steps = (int) Math.ceil(s.speed / 0.5);
		for (int i = 0; i < steps; i++) {
			s.pos = s.pos.add(s.dir.multiply(s.speed / steps));
			s.travelled += s.speed / steps;
			BlockPos block = BlockPos.ofFloored(s.pos);
			if (!s.world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) || s.world.getBlockState(block).isSolidBlock(s.world, block)) {
				if (!wave) {
					burst(s);
				}
				return false;
			}
			for (LivingEntity foe : foes(s.world, s.player, new Box(s.pos, s.pos).expand(wave ? 1.6 : 0.5))) {
				if (!s.hit.add(foe)) {
					continue;
				}
				Vec3d c = foe.getBoundingBox().getCenter();
				if (wave) {
					hurt(s.world, s.player, foe, 14.0F * s.power);
					foe.addVelocity(s.dir.x * 0.8, 0.25, s.dir.z * 0.8);
					s.world.spawnParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.2);
				} else {
					hurt(s.world, foe, s.player.getDamageSources().indirectMagic(s.player, s.player), 8.0F * s.power);
					foe.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0), s.player);
					burst(s);
					return false;
				}
			}
			if (s.travelled >= s.range) {
				if (!wave) {
					burst(s);
				}
				return false;
			}
		}
		if (wave) {
			Vec3d right = new Vec3d(-s.dir.z, 0, s.dir.x);
			for (double k = -1.5; k <= 1.5; k += 0.75) {
				Vec3d p = s.pos.add(right.multiply(k)).add(0, -Math.abs(k) * 0.25, 0);
				s.world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
				s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
			s.world.spawnParticles(ParticleTypes.SWEEP_ATTACK, s.pos.x, s.pos.y, s.pos.z, 1, 0, 0, 0, 0);
		} else {
			s.world.spawnParticles(ParticleTypes.END_ROD, s.pos.x, s.pos.y, s.pos.z, 3, 0.08, 0.08, 0.08, 0.0);
			s.world.spawnParticles(ParticleTypes.GLOW, s.pos.x, s.pos.y, s.pos.z, 1, 0.1, 0.1, 0.1, 0.0);
		}
		return true;
	}

	/** Lame Détonante, clic droit : une explosion tous les 2 blocs en ligne droite, sans casser le sol. */
	private static boolean stepBlast(Shot s) {
		s.pos = s.pos.add(s.dir.multiply(s.speed));
		s.travelled += s.speed;
		BlockPos block = BlockPos.ofFloored(s.pos);
		if (!s.world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) || s.world.getBlockState(block).isSolidBlock(s.world, block)) {
			// on longe le relief : un bloc plus haut si possible
			BlockPos up = block.up();
			if (s.world.getBlockState(up).isSolidBlock(s.world, up)) {
				return false;
			}
			s.pos = s.pos.add(0, 1, 0);
		}
		explode(s.world, s.player, s.pos, 3.0, 10.0F * s.power, s.hit, 1.0F);
		return s.travelled < s.range;
	}

	/** Explosion « propre » : particules, son, dégâts et recul aux ennemis, mais aucun bloc cassé. */
	private static void explode(ServerWorld world, ServerPlayerEntity player, Vec3d at, double radius, float damage,
			Set<LivingEntity> already, float volume) {
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, radius * 0.25, 0.3, radius * 0.25, 0);
		world.spawnParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 8, radius * 0.3, 0.3, radius * 0.3, 0.05);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3, at.z, 4, radius * 0.2, 0.2, radius * 0.2, 0.02);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, volume, 1.1F);
		for (LivingEntity foe : foes(world, player, new Box(at, at).expand(radius))) {
			Vec3d away = foe.getBoundingBox().getCenter().subtract(at);
			if (away.length() > radius + foe.getWidth() / 2 || (already != null && !already.add(foe))) {
				continue;
			}
			hurt(world, player, foe, damage);
			away = away.lengthSquared() < 1.0E-4 ? new Vec3d(0, 1, 0) : away.normalize();
			foe.addVelocity(away.x * 0.8, 0.35, away.z * 0.8);
		}
	}

	/**
	 * Lame Détonante : chaque coup d'épée (même dans le vide) fait un slash en arc qui explose devant le joueur.
	 * Appelé quand le joueur balance le bras (paquet de swing, voir ServerPlayNetworkHandlerMixin).
	 */
	public static void onSwing(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof ModdedWeaponItem weapon) || weapon.weaponKind() != WeaponKind.BLAST_BLADE
				|| !(player.getEntityWorld() instanceof ServerWorld world) || player.isSpectator()) {
			return;
		}
		int now = world.getServer().getTicks();
		Integer last = LAST_BLAST.get(player.getUuid());
		if (last != null && now - last >= 0 && now - last < BLAST_INTERVAL) {
			return;
		}
		LAST_BLAST.put(player.getUuid(), now);
		blastSlash(world, player, powerOf(stack));
	}

	public static void blastSlash(ServerWorld world, ServerPlayerEntity player, float strength) {
		Vec3d look = flatLook(player);
		Vec3d origin = feet(player).add(0, 1.0, 0);
		Set<LivingEntity> hit = new HashSet<>();
		for (int i = -2; i <= 2; i++) {
			double a = Math.toRadians(i * 22.0);
			Vec3d dir = new Vec3d(look.x * Math.cos(a) - look.z * Math.sin(a), 0, look.x * Math.sin(a) + look.z * Math.cos(a));
			Vec3d at = origin.add(dir.multiply(2.6 - Math.abs(i) * 0.25));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			explode(world, player, at, 1.8, 5.0F * strength, hit, i == 0 ? 0.7F : 0.0F);
		}
	}

	/** Éclat du sceptre qui se brise : gerbe d'étincelles de cristal. */
	private static void burst(Shot s) {
		s.world.spawnParticles(ParticleTypes.END_ROD, s.pos.x, s.pos.y, s.pos.z, 20, 0.2, 0.2, 0.2, 0.15);
		s.world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, s.pos.x, s.pos.y, s.pos.z, 12, 0.3, 0.3, 0.3, 0.2);
		s.world.playSound(null, BlockPos.ofFloored(s.pos), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 0.8F);
	}
}
