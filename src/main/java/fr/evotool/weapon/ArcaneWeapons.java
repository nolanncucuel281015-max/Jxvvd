package fr.evotool.weapon;

import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.MagicWeaponItem;
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
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Les sorts des armes magiques ({@link MagicKind}) : charges qui se rechargent avec le temps, missiles à tête
 * chercheuse, éclairs en chaîne, étoiles en orbite, trous noirs, disques boomerang, feux follets et rayons.
 * Au corps à corps, chaque coup a une chance de rendre une charge.
 */
public final class ArcaneWeapons {
	private static final float SIPHON_CHANCE = 0.2F;

	/** Évite que les dégâts des sorts relancent les effets au corps à corps. */
	private static boolean applyingSpellDamage = false;
	/** Puissance du sort en cours (niveau d'évolution de l'arme, amélioration à l'évolite). */
	private static float power = 1.0F;

	private enum MissileType { ARCANE, STAR, WISP, CHAKRAM }

	/** Projectile magique, avancé à chaque tick. */
	private static final class Missile {
		final ServerWorld world;
		final ServerPlayerEntity owner;
		final MissileType type;
		final double speed;
		final int maxAge;
		final Set<LivingEntity> hit = new HashSet<>();
		final float power = ArcaneWeapons.power;
		Vec3d pos;
		Vec3d velocity;
		LivingEntity target;
		int age;
		double travelled;
		boolean returning;

		Missile(ServerWorld world, ServerPlayerEntity owner, MissileType type, Vec3d pos, Vec3d direction, double speed, int maxAge) {
			this.world = world;
			this.owner = owner;
			this.type = type;
			this.pos = pos;
			this.velocity = direction.normalize().multiply(speed);
			this.speed = speed;
			this.maxAge = maxAge;
		}
	}

	/** Éclats d'étoile qui tournent autour du joueur. */
	private static final class Orbit {
		final ServerWorld world;
		final ServerPlayerEntity owner;
		final Map<LivingEntity, Integer> lastHit = new HashMap<>();
		float power = ArcaneWeapons.power;
		int shards;
		long end;

		Orbit(ServerWorld world, ServerPlayerEntity owner, int shards, long end) {
			this.world = world;
			this.owner = owner;
			this.shards = shards;
			this.end = end;
		}
	}

	/** Trou noir : aspire les ennemis pendant 3 secondes puis implose. */
	private static final class Singularity {
		final ServerWorld world;
		final ServerPlayerEntity owner;
		final Vec3d center;
		final float power = ArcaneWeapons.power;
		int age;

		Singularity(ServerWorld world, ServerPlayerEntity owner, Vec3d center) {
			this.world = world;
			this.owner = owner;
			this.center = center;
		}
	}

	private static final List<Missile> MISSILES = new ArrayList<>();
	private static final Map<UUID, Orbit> ORBITS = new HashMap<>();
	private static final List<Singularity> SINGULARITIES = new ArrayList<>();

	private ArcaneWeapons() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(ArcaneWeapons::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MISSILES.clear();
			ORBITS.clear();
			SINGULARITIES.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(ArcaneWeapons::afterDamage);
	}

	// ------------------------------------------------------------------ charges

	/** Charges maximum : + 1 tous les 25 niveaux d'évolution. */
	public static int maxCharges(ItemStack stack, MagicKind kind) {
		return kind.maxCharges() + EvoProgression.get(stack).level() / 25;
	}

	/** Ticks pour regagner une charge : jusqu'à 2 fois plus rapide au niveau 100, encore 2 fois plus amélioré. */
	public static int rechargeTicks(ItemStack stack, MagicKind kind) {
		double t = kind.rechargeTicks() * (1.0 - EvoProgression.get(stack).level() / 200.0);
		if (EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.EMPOWERED)) {
			t *= 0.5;
		}
		return Math.max(5, (int) Math.round(t));
	}

	public static int charges(ItemStack stack, MagicKind kind) {
		Integer charges = stack.get(ModComponents.CHARGES);
		int max = maxCharges(stack, kind);
		return charges == null ? max : MathHelper.clamp(charges, 0, max);
	}

	/** Recharge selon le temps écoulé depuis la dernière charge ; renvoie le nombre de charges regagnées. */
	public static int recharge(ItemStack stack, MagicKind kind, long now) {
		int charges = charges(stack, kind);
		int max = maxCharges(stack, kind);
		int ticks = rechargeTicks(stack, kind);
		if (charges >= max) {
			return 0;
		}
		Long clock = stack.get(ModComponents.CHARGE_CLOCK);
		if (clock == null || clock > now) {
			stack.set(ModComponents.CHARGE_CLOCK, now);
			return 0;
		}
		long gained = (now - clock) / ticks;
		if (gained <= 0) {
			return 0;
		}
		int after = (int) Math.min(max, charges + gained);
		stack.set(ModComponents.CHARGES, after);
		stack.set(ModComponents.CHARGE_CLOCK, clock + gained * ticks);
		return after - charges;
	}

	private static void consume(ItemStack stack, MagicKind kind, int used, long now) {
		int charges = charges(stack, kind);
		if (charges >= maxCharges(stack, kind)) {
			stack.set(ModComponents.CHARGE_CLOCK, now);   // la recharge démarre au premier sort
		}
		stack.set(ModComponents.CHARGES, Math.max(0, charges - used));
	}

	private static void addCharge(ItemStack stack, MagicKind kind) {
		int charges = charges(stack, kind);
		if (charges < maxCharges(stack, kind)) {
			stack.set(ModComponents.CHARGES, charges + 1);
		}
	}

	// ------------------------------------------------------------------ sorts

	/** Lance le sort de l'arme ; renvoie false s'il n'y a plus de charge. */
	public static boolean cast(MagicKind kind, ItemStack stack, ServerWorld world, ServerPlayerEntity player) {
		long now = world.getTime();
		recharge(stack, kind, now);
		int charges = charges(stack, kind);
		if (charges <= 0) {
			player.sendMessage(Text.translatable("message.evotool.no_charges").formatted(Formatting.GRAY), true);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6F, 1.4F);
			return false;
		}
		boolean sneak = player.isSneaking();
		power = WeaponPowers.powerOf(stack);
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector();
		int used = switch (kind) {
			case ARCANE_WAND -> {
				int n = sneak ? charges : 1;
				for (int i = 0; i < n; i++) {
					Vec3d dir = look.rotateY((float) Math.toRadians(n == 1 ? 0 : (i - (n - 1) / 2.0) * 12.0));
					MISSILES.add(new Missile(world, player, MissileType.ARCANE, eye.add(dir.multiply(0.8)).add(0, -0.2, 0), dir, 1.1, 70));
				}
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.0F, sneak ? 0.8F : 1.4F);
				yield n;
			}
			case THUNDER_GAUNTLET -> {
				if (sneak) {
					thunderWave(world, player);
				} else {
					chainLightning(world, player);
				}
				yield 1;
			}
			case CELESTIAL_ORB -> {
				Orbit orbit = ORBITS.get(player.getUuid());
				if (sneak) {
					int shards = orbit != null && orbit.world == world ? orbit.shards : 5;
					ORBITS.remove(player.getUuid());
					for (int i = 0; i < shards; i++) {
						Vec3d dir = look.rotateY((float) Math.toRadians((i - (shards - 1) / 2.0) * 10.0));
						MISSILES.add(new Missile(world, player, MissileType.STAR, eye.add(dir), dir, 1.4, 50));
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0F, 1.6F);
				} else {
					if (orbit == null || orbit.world != world) {
						ORBITS.put(player.getUuid(), new Orbit(world, player, 4, now + 200));
					} else {
						orbit.shards = Math.min(8, orbit.shards + 4);
						orbit.end = now + 200;
						orbit.power = power;
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 1.8F);
				}
				yield 1;
			}
			case VOID_GRIMOIRE -> {
				Vec3d center = sneak ? new Vec3d(player.getX(), player.getY() + 1.0, player.getZ()) : targetPoint(world, player, 24.0);
				SINGULARITIES.add(new Singularity(world, player, center));
				world.playSound(null, BlockPos.ofFloored(center), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.5F, 0.5F);
				world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 0.4F);
				yield 1;
			}
			case SOLAR_CHAKRAM -> {
				int n = sneak && charges >= 2 ? 3 : 1;
				for (int i = 0; i < n; i++) {
					Vec3d dir = look.rotateY((float) Math.toRadians(n == 1 ? 0 : (i - 1) * 20.0));
					MISSILES.add(new Missile(world, player, MissileType.CHAKRAM, eye.add(dir).add(0, -0.3, 0), dir, 1.3, 70));
				}
				world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8F, 1.4F);
				world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.6F);
				yield n == 3 ? 2 : 1;
			}
			case SPIRIT_LANTERN -> {
				if (sneak) {
					player.heal(6.0F);
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
					world.spawnParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.6, 0.8, 0.6, 0.02);
					world.spawnParticles(ParticleTypes.HEART, player.getX(), player.getY() + 2.0, player.getZ(), 5, 0.5, 0.3, 0.5, 0);
					world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, 1.8F);
				} else {
					for (int i = -1; i <= 1; i++) {
						Vec3d dir = look.rotateY((float) Math.toRadians(i * 30.0)).add(0, 0.25, 0);
						MISSILES.add(new Missile(world, player, MissileType.WISP, eye.add(dir.multiply(0.6)), dir, 0.55, 120));
					}
					world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.PLAYERS, 1.0F, 0.8F);
				}
				yield 1;
			}
			case EVOLIUM_BLASTER -> {
				int n = sneak ? charges : 1;
				beam(world, player, sneak ? 5.0F * n : 6.0F, sneak ? 1.6 : 0.6, sneak);
				yield n;
			}
		};
		consume(stack, kind, used, now);
		power = 1.0F;
		return true;
	}

	/** Point visé (bloc ou ennemi) jusqu'à range blocs. */
	private static Vec3d targetPoint(ServerWorld world, ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVector().multiply(range));
		BlockHitResult wall = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getPos().subtract(player.getRotationVector().multiply(0.5));
		}
		LivingEntity aimed = aimed(world, player, eye.distanceTo(end));
		return aimed != null ? aimed.getBoundingBox().getCenter() : end;
	}

	/** Ennemi visé (les blocs arrêtent la visée). */
	private static LivingEntity aimed(ServerWorld world, ServerPlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d end = eye.add(player.getRotationVector().multiply(range));
		BlockHitResult wall = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getPos();
		}
		EntityHitResult hit = ProjectileUtil.raycast(player, eye, end, player.getBoundingBox().stretch(end.subtract(eye)).expand(1.0),
				e -> WeaponPowers.isFoe(player, e), eye.squaredDistanceTo(end));
		return hit != null && hit.getEntity() instanceof LivingEntity living ? living : null;
	}

	private static List<LivingEntity> foes(ServerWorld world, ServerPlayerEntity player, Box box) {
		return world.getEntitiesByClass(LivingEntity.class, box, e -> WeaponPowers.isFoe(player, e));
	}

	private static LivingEntity nearestFoe(ServerWorld world, ServerPlayerEntity player, Vec3d from, double range, Set<LivingEntity> except) {
		LivingEntity best = null;
		double bestDistance = range * range;
		for (LivingEntity foe : foes(world, player, new Box(from, from).expand(range))) {
			double d = foe.getBoundingBox().getCenter().squaredDistanceTo(from);
			if (d < bestDistance && (except == null || !except.contains(foe))) {
				bestDistance = d;
				best = foe;
			}
		}
		return best;
	}

	private static void spell(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float amount) {
		hurt(world, target, player.getDamageSources().indirectMagic(player, player), amount * power);
	}

	private static void hurt(ServerWorld world, LivingEntity target, DamageSource source, float amount) {
		applyingSpellDamage = true;
		try {
			target.damage(world, source, amount);
		} finally {
			applyingSpellDamage = false;
		}
	}

	private static void sparkLine(ServerWorld world, Vec3d from, Vec3d to) {
		double length = from.distanceTo(to);
		Vec3d dir = to.subtract(from).normalize();
		for (double d = 0; d <= length; d += 0.35) {
			Vec3d p = from.add(dir.multiply(d));
			double jitter = Math.sin(d * 7.0) * 0.12;
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x + jitter, p.y - jitter, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	/** Gantelet : l'éclair frappe l'ennemi visé puis saute jusqu'à 4 ennemis proches. */
	private static void chainLightning(ServerWorld world, ServerPlayerEntity player) {
		Vec3d from = player.getEyePos().add(player.getRotationVector().multiply(0.6)).add(0, -0.4, 0);
		LivingEntity current = aimed(world, player, 18.0);
		if (current == null) {
			current = nearestFoe(world, player, player.getEyePos().add(player.getRotationVector().multiply(5.0)), 6.0, null);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6F, 1.7F);
		if (current == null) {
			sparkLine(world, from, from.add(player.getRotationVector().multiply(8.0)));
			return;
		}
		Set<LivingEntity> struck = new HashSet<>();
		float damage = 10.0F;
		for (int i = 0; i < 5 && current != null; i++) {
			Vec3d c = current.getBoundingBox().getCenter();
			sparkLine(world, from, c);
			if (i == 0) {
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.refreshPositionAfterTeleport(new Vec3d(current.getX(), current.getY(), current.getZ()));
					bolt.setCosmetic(true);
					world.spawnEntity(bolt);
				}
			}
			spell(world, player, current, damage);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 15, 0.3, 0.4, 0.3, 0.2);
			struck.add(current);
			from = c;
			damage *= 0.8F;
			current = nearestFoe(world, player, c, 7.0, struck);
		}
	}

	/** Gantelet, accroupi : onde de tonnerre de 5 blocs qui repousse et ralentit. */
	private static void thunderWave(ServerWorld world, ServerPlayerEntity player) {
		for (int i = 0; i < 40; i++) {
			double a = Math.PI * 2 * i / 40;
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX() + Math.cos(a) * 4.5, player.getY() + 0.3,
					player.getZ() + Math.sin(a) * 4.5, 2, 0.1, 0.2, 0.1, 0.1);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 1.5F, 0.8F);
		for (LivingEntity foe : foes(world, player, player.getBoundingBox().expand(5.0, 2.0, 5.0))) {
			Vec3d away = new Vec3d(foe.getX() - player.getX(), 0, foe.getZ() - player.getZ());
			if (away.length() > 5.5) {
				continue;
			}
			spell(world, player, foe, 6.0F);
			away = away.lengthSquared() < 1.0E-4 ? player.getRotationVector() : away.normalize();
			foe.addVelocity(away.x * 1.1, 0.45, away.z * 1.1);
			foe.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), player);
		}
	}

	/** Canon : rayon perforant (les blocs l'arrêtent). */
	private static void beam(ServerWorld world, ServerPlayerEntity player, float damage, double width, boolean overcharge) {
		Vec3d start = player.getEyePos();
		Vec3d dir = player.getRotationVector();
		Vec3d end = start.add(dir.multiply(40.0));
		BlockHitResult wall = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getPos();
		}
		double length = start.distanceTo(end);
		for (double d = 0.8; d <= length; d += 0.4) {
			Vec3d p = start.add(dir.multiply(d)).add(0, -0.15, 0);
			world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, overcharge ? 3 : 1, 0.03 * width, 0.03 * width, 0.03 * width, 0.0);
			if (overcharge && ((int) (d * 2.5)) % 8 == 0) {
				world.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
		}
		world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 20, 0.3, 0.3, 0.3, 0.2);
		if (overcharge) {
			world.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 3, 0.5, 0.5, 0.5, 0);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 1.2F, 1.3F);
		} else {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.PLAYERS, 1.0F, 1.8F);
		}
		for (LivingEntity foe : foes(world, player, new Box(start, end).expand(width + 1.0))) {
			Vec3d c = foe.getBoundingBox().getCenter();
			double along = c.subtract(start).dotProduct(dir);
			if (along < 0 || along > length + 1.0) {
				continue;
			}
			if (start.add(dir.multiply(along)).distanceTo(c) <= width + foe.getWidth() / 2) {
				spell(world, player, foe, damage);
				foe.addVelocity(dir.x * (overcharge ? 1.2 : 0.3), overcharge ? 0.4 : 0.1, dir.z * (overcharge ? 1.2 : 0.3));
				world.spawnParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.2);
			}
		}
	}

	// ------------------------------------------------------------------ corps à corps

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked) {
		if (applyingSpellDamage || blocked || damageTaken <= 0 || !(source.getAttacker() instanceof ServerPlayerEntity player)
				|| source.getSource() != player) {
			return;
		}
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof MagicWeaponItem weapon)) {
			return;
		}
		ServerWorld world = (ServerWorld) entity.getEntityWorld();
		MagicKind kind = weapon.magicKind();
		if (world.getRandom().nextFloat() < SIPHON_CHANCE && charges(stack, kind) < maxCharges(stack, kind)) {
			addCharge(stack, kind);
			world.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 20, 0.4, 0.5, 0.4, 0.5);
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8F, 2.0F);
		}
		if (kind == MagicKind.THUNDER_GAUNTLET) {
			Set<LivingEntity> except = new HashSet<>();
			except.add(entity);
			LivingEntity next = nearestFoe(world, player, entity.getBoundingBox().getCenter(), 4.0, except);
			if (next != null) {
				sparkLine(world, entity.getBoundingBox().getCenter(), next.getBoundingBox().getCenter());
				spell(world, player, next, 3.0F);
			}
		}
	}

	// ------------------------------------------------------------------ ticks

	private static void tick(MinecraftServer server) {
		Iterator<Missile> missiles = MISSILES.iterator();
		while (missiles.hasNext()) {
			Missile missile = missiles.next();
			power = missile.power;
			if (!step(missile)) {
				missiles.remove();
			}
		}
		Iterator<Map.Entry<UUID, Orbit>> orbits = ORBITS.entrySet().iterator();
		while (orbits.hasNext()) {
			Orbit orbit = orbits.next().getValue();
			power = orbit.power;
			if (!step(orbit, server.getTicks())) {
				orbits.remove();
			}
		}
		Iterator<Singularity> holes = SINGULARITIES.iterator();
		while (holes.hasNext()) {
			Singularity hole = holes.next();
			power = hole.power;
			if (!step(hole)) {
				holes.remove();
			}
		}
		power = 1.0F;
		if (server.getTicks() % 10 == 0) {
			long now = server.getOverworld().getTime();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				for (int i = 0; i < player.getInventory().size(); i++) {
					ItemStack stack = player.getInventory().getStack(i);
					if (stack.getItem() instanceof MagicWeaponItem weapon && recharge(stack, weapon.magicKind(), now) > 0
							&& stack == player.getMainHandStack()) {
						player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
								SoundCategory.PLAYERS, 0.3F, 1.6F);
					}
				}
			}
		}
	}

	private static boolean step(Missile m) {
		if (m.owner.isRemoved() || m.owner.getEntityWorld() != m.world || ++m.age > m.maxAge) {
			burst(m);
			return false;
		}
		switch (m.type) {
			case ARCANE, STAR, WISP -> {
				if (m.target == null || !m.target.isAlive() || m.target.squaredDistanceTo(m.pos) > 24 * 24) {
					m.target = nearestFoe(m.world, m.owner, m.pos.add(m.velocity.normalize().multiply(4.0)), 12.0, m.hit);
				}
				if (m.target != null && m.age > 2) {
					double turn = m.type == MissileType.WISP ? 0.18 : m.type == MissileType.STAR ? 0.12 : 0.3;
					Vec3d desired = m.target.getBoundingBox().getCenter().subtract(m.pos).normalize().multiply(m.speed);
					m.velocity = m.velocity.multiply(1 - turn).add(desired.multiply(turn)).normalize().multiply(m.speed);
				}
			}
			case CHAKRAM -> {
				if (!m.returning && (m.travelled >= 14.0 || m.age > 16)) {
					m.returning = true;
					m.hit.clear();
				}
				if (m.returning) {
					Vec3d home = m.owner.getEyePos().add(0, -0.4, 0);
					if (home.squaredDistanceTo(m.pos) < 1.6 * 1.6) {
						m.world.playSound(null, m.owner.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_GOLD.value(), SoundCategory.PLAYERS, 0.6F, 1.6F);
						return false;
					}
					m.velocity = home.subtract(m.pos).normalize().multiply(m.speed * 1.2);
				}
			}
		}
		int steps = (int) Math.ceil(m.velocity.length() / 0.4);
		for (int i = 0; i < steps; i++) {
			m.pos = m.pos.add(m.velocity.multiply(1.0 / steps));
			m.travelled += m.velocity.length() / steps;
			BlockPos block = BlockPos.ofFloored(m.pos);
			if (!m.world.isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) {
				return false;
			}
			if (m.world.getBlockState(block).isSolidBlock(m.world, block)) {
				if (m.type == MissileType.CHAKRAM && !m.returning) {
					m.returning = true;
					m.hit.clear();
					break;
				}
				if (m.type != MissileType.CHAKRAM) {
					burst(m);
					return false;
				}
			}
			double reach = m.type == MissileType.CHAKRAM ? 1.0 : 0.6;
			for (LivingEntity foe : foes(m.world, m.owner, new Box(m.pos, m.pos).expand(reach))) {
				if (!m.hit.add(foe)) {
					continue;
				}
				Vec3d c = foe.getBoundingBox().getCenter();
				switch (m.type) {
					case ARCANE -> spell(m.world, m.owner, foe, 7.0F);
					case STAR -> {
						spell(m.world, m.owner, foe, 6.0F);
						foe.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0), m.owner);
					}
					case WISP -> {
						spell(m.world, m.owner, foe, 6.0F);
						m.owner.heal(2.0F);
						foe.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), m.owner);
						m.world.spawnParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 8, 0.3, 0.3, 0.3, 0.03);
					}
					case CHAKRAM -> {
						hurt(m.world, foe, m.owner.getDamageSources().playerAttack(m.owner), 8.0F * power);
						foe.setOnFireFor(4.0F);
						m.world.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
					}
				}
				if (m.type != MissileType.CHAKRAM) {
					burst(m);
					return false;
				}
			}
		}
		trail(m);
		return true;
	}

	private static void trail(Missile m) {
		Vec3d p = m.pos;
		switch (m.type) {
			case ARCANE -> {
				m.world.spawnParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
				m.world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			case STAR -> {
				m.world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
				m.world.spawnParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			case WISP -> {
				m.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 2, 0.06, 0.06, 0.06, 0.0);
				m.world.spawnParticles(ParticleTypes.SOUL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			case CHAKRAM -> {
				double a = m.age * 1.3;
				for (int i = 0; i < 4; i++) {
					double b = a + i * Math.PI / 2;
					m.world.spawnParticles(ParticleTypes.FLAME, p.x + Math.cos(b) * 0.5, p.y, p.z + Math.sin(b) * 0.5, 1, 0, 0, 0, 0.0);
				}
				m.world.spawnParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
			}
		}
	}

	private static void burst(Missile m) {
		Vec3d p = m.pos;
		switch (m.type) {
			case ARCANE -> m.world.spawnParticles(ParticleTypes.WITCH, p.x, p.y, p.z, 15, 0.25, 0.25, 0.25, 0.1);
			case STAR -> m.world.spawnParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 12, 0.2, 0.2, 0.2, 0.1);
			case WISP -> m.world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 10, 0.2, 0.2, 0.2, 0.05);
			case CHAKRAM -> m.world.spawnParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 10, 0.2, 0.2, 0.2, 0.05);
		}
	}

	private static boolean step(Orbit orbit, int tick) {
		ServerPlayerEntity player = orbit.owner;
		if (player.isRemoved() || player.getEntityWorld() != orbit.world || orbit.world.getTime() > orbit.end) {
			if (!player.isRemoved()) {
				orbit.world.spawnParticles(ParticleTypes.FIREWORK, player.getX(), player.getY() + 1.2, player.getZ(), 20, 1.0, 0.5, 1.0, 0.1);
			}
			return false;
		}
		double base = tick * 0.22;
		for (int i = 0; i < orbit.shards; i++) {
			double a = base + Math.PI * 2 * i / orbit.shards;
			double radius = 2.2 + 0.3 * (i % 2);
			Vec3d p = new Vec3d(player.getX() + Math.cos(a) * radius, player.getY() + 1.1 + 0.35 * Math.sin(base * 1.7 + i),
					player.getZ() + Math.sin(a) * radius);
			orbit.world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			if (tick % 2 == 0) {
				orbit.world.spawnParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			for (LivingEntity foe : foes(orbit.world, player, new Box(p, p).expand(0.9))) {
				Integer last = orbit.lastHit.get(foe);
				if (last != null && tick - last < 10) {
					continue;
				}
				orbit.lastHit.put(foe, tick);
				spell(orbit.world, player, foe, 5.0F);
				Vec3d away = new Vec3d(foe.getX() - player.getX(), 0, foe.getZ() - player.getZ());
				if (away.lengthSquared() > 1.0E-4) {
					away = away.normalize();
					foe.addVelocity(away.x * 0.5, 0.2, away.z * 0.5);
				}
			}
		}
		return true;
	}

	private static boolean step(Singularity hole) {
		hole.age++;
		ServerWorld world = hole.world;
		Vec3d c = hole.center;
		if (hole.owner.isRemoved()) {
			return false;
		}
		// spirale d'encre et de portail aspirée vers le centre
		for (int i = 0; i < 3; i++) {
			double a = hole.age * 0.5 + i * Math.PI * 2 / 3;
			double r = 3.0 - (hole.age % 20) * 0.13;
			world.spawnParticles(ParticleTypes.SQUID_INK, c.x + Math.cos(a) * r, c.y + Math.sin(a * 0.7) * 0.6, c.z + Math.sin(a) * r,
					1, 0, 0, 0, 0.0);
		}
		world.spawnParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 12, 1.5, 1.5, 1.5, 1.0);
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
		if (hole.age < 60) {
			for (LivingEntity foe : foes(world, hole.owner, new Box(c, c).expand(8.0))) {
				Vec3d toCenter = c.subtract(foe.getBoundingBox().getCenter());
				double d = toCenter.length();
				if (d > 8.0 || d < 0.3) {
					continue;
				}
				Vec3d pull = toCenter.normalize().multiply(0.16 * (1.0 - d / 9.0) + 0.04);
				foe.addVelocity(pull.x, pull.y * 0.5 + 0.02, pull.z);
				if (hole.age % 10 == 0) {
					spell(world, hole.owner, foe, 2.0F);
				}
			}
			if (hole.age % 20 == 0) {
				world.playSound(null, BlockPos.ofFloored(c), SoundEvents.BLOCK_PORTAL_AMBIENT, SoundCategory.PLAYERS, 0.6F, 1.6F);
			}
			return true;
		}
		// implosion
		world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 4, 0.8, 0.8, 0.8, 0);
		world.spawnParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 80, 0.3, 0.3, 0.3, 0.6);
		world.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.5F, 0.5F);
		for (LivingEntity foe : foes(world, hole.owner, new Box(c, c).expand(4.5))) {
			Vec3d away = foe.getBoundingBox().getCenter().subtract(c);
			if (away.length() > 4.5) {
				continue;
			}
			spell(world, hole.owner, foe, 12.0F);
			away = away.lengthSquared() < 1.0E-4 ? new Vec3d(0, 1, 0) : away.normalize();
			foe.addVelocity(away.x * 1.2, 0.5, away.z * 1.2);
		}
		return false;
	}
}
