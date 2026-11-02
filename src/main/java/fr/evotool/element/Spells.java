package fr.evotool.element;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
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
 * Lancer les sorts du grimoire. Un trait est un projectile d'énergie (simulé ici, tick par tick) qui frappe la
 * première créature sur son chemin ; une explosion frappe tout ce qui est autour du point visé (jusqu'à 24 blocs).
 */
public final class Spells {
	private static final double RANGE = 24.0;
	private static final int BOLT_LIFE = 30;

	private static final class Bolt {
		final ServerPlayerEntity caster;
		final ServerWorld world;
		final Spell spell;
		Vec3d pos;
		final Vec3d velocity;
		int age;

		Bolt(ServerPlayerEntity caster, ServerWorld world, Spell spell, Vec3d pos, Vec3d velocity) {
			this.caster = caster;
			this.world = world;
			this.spell = spell;
			this.pos = pos;
			this.velocity = velocity;
		}
	}

	private static final List<Bolt> BOLTS = new ArrayList<>();

	private Spells() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tickBolts());
	}

	/** Lance ce sort (public pour l'auto-test). */
	public static void cast(ServerPlayerEntity player, Spell spell) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d look = player.getRotationVector();
		Vec3d hand = player.getEyePos().add(look.multiply(0.6)).add(0, -0.2, 0);
		Element element = spell.element();
		world.spawnParticles(element.particle(), hand.x, hand.y, hand.z, 12, 0.15, 0.15, 0.15, 0.05);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 0.8F,
				spell.bolt() ? 1.6F : 1.0F);
		if (spell.bolt()) {
			BOLTS.add(new Bolt(player, world, spell, hand, look.multiply(spell.speed())));
		} else {
			burst(world, player, spell, aim(world, player));
		}
	}

	/** Le point visé : la première créature ou le premier bloc devant le joueur, sinon à 24 blocs. */
	private static Vec3d aim(ServerWorld world, ServerPlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVector().multiply(RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.MISS ? far : block.getPos();
		EntityHitResult entity = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.squaredDistanceTo(end));
		return entity != null ? entity.getEntity().getEntityPos() : end;
	}

	private static boolean hittable(ServerPlayerEntity caster, LivingEntity target) {
		return target != caster && target.isAlive() && !target.isSpectator()
				&& !(target instanceof TameableEntity tame && tame.isOwner(caster));
	}

	// ------------------------------------------------------------------ traits

	private static void tickBolts() {
		for (Iterator<Bolt> it = BOLTS.iterator(); it.hasNext(); ) {
			Bolt bolt = it.next();
			if (stepBolt(bolt)) {
				it.remove();
			}
		}
	}

	/** Fait avancer les traits d'un tick (auto-test). */
	public static void tickForTest() {
		tickBolts();
	}

	public static int activeBolts() {
		return BOLTS.size();
	}

	/** Un tick de vol ; vrai quand le trait a touché quelque chose (ou s'est éteint). */
	private static boolean stepBolt(Bolt bolt) {
		ServerWorld world = bolt.world;
		bolt.age++;
		if (bolt.age > BOLT_LIFE || bolt.caster.isRemoved() || bolt.caster.getEntityWorld() != world) {
			return true;
		}
		Vec3d from = bolt.pos;
		Vec3d to = from.add(bolt.velocity);
		BlockHitResult block = world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, bolt.caster));
		Vec3d end = block.getType() == HitResult.Type.MISS ? to : block.getPos();
		// la créature touchée la plus proche sur le trajet
		LivingEntity hit = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(from, end).expand(1.0),
				e -> hittable(bolt.caster, e))) {
			Optional<Vec3d> point = target.getBoundingBox().expand(0.3).raycast(from, end);
			if (point.isPresent() && point.get().squaredDistanceTo(from) < best) {
				best = point.get().squaredDistanceTo(from);
				hit = target;
			}
		}
		trail(world, bolt.spell, from, end);
		if (hit != null) {
			Vec3d at = hit.getBoundingBox().getCenter();
			hit.damage(world, bolt.caster.getDamageSources().indirectMagic(bolt.caster, bolt.caster), bolt.spell.damage());
			ElementalPowers.hitEffect(world, bolt.caster, hit, bolt.spell.element());
			impact(world, bolt.spell, at, 0.6);
			return true;
		}
		if (block.getType() != HitResult.Type.MISS) {
			impact(world, bolt.spell, end, 0.6);
			return true;
		}
		bolt.pos = to;
		return false;
	}

	/** La traînée du trait : particules de l'élément le long du trajet de ce tick, avec un cœur lumineux. */
	private static void trail(ServerWorld world, Spell spell, Vec3d from, Vec3d to) {
		Vec3d step = to.subtract(from);
		int count = Math.max(2, (int) (step.length() * 3));
		for (int i = 0; i < count; i++) {
			Vec3d p = from.add(step.multiply(i / (double) count));
			world.spawnParticles(spell.element().particle(), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			if (i % 2 == 0) {
				world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
	}

	private static void impact(ServerWorld world, Spell spell, Vec3d at, double size) {
		world.spawnParticles(spell.element().particle(), at.x, at.y, at.z, (int) (20 * size + 10), size, size, size, 0.1);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS,
				0.3F + 0.2F * (float) size, 1.6F);
	}

	// ------------------------------------------------------------------ explosions

	/** Tout ce qui est autour du point visé est frappé par l'élément. */
	private static void burst(ServerWorld world, ServerPlayerEntity player, Spell spell, Vec3d center) {
		double r = spell.radius();
		Element element = spell.element();
		// l'anneau qui s'étend, et la colonne au centre
		for (int k = 0; k < 48; k++) {
			double a = Math.PI * 2 * k / 48;
			for (double rr = r * 0.5; rr <= r; rr += r * 0.5) {
				world.spawnParticles(element.particle(), center.x + Math.cos(a) * rr, center.y + 0.2, center.z + Math.sin(a) * rr,
						1, 0.05, 0.1, 0.05, 0.02);
			}
		}
		world.spawnParticles(element.particle(), center.x, center.y + 1.5, center.z, 60, 0.5, 1.5, 0.5, 0.1);
		world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.5, center.z, 3, r * 0.3, 0.3, r * 0.3, 0.0);
		switch (element) {
			case LIGHTNING -> {
				// une vraie foudre (sans feu ni dégâts : ce sont ceux du sort)
				LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
				if (bolt != null) {
					bolt.refreshPositionAfterTeleport(center);
					bolt.setCosmetic(true);
					world.spawnEntity(bolt);
				}
			}
			case FIRE -> world.spawnParticles(ParticleTypes.LAVA, center.x, center.y + 0.5, center.z, 30, r * 0.4, 0.5, r * 0.4, 0.1);
			case LIGHT -> world.spawnParticles(ParticleTypes.END_ROD, center.x, center.y + 4, center.z, 80, 0.3, 4.0, 0.3, 0.02);
			default -> {
			}
		}
		world.playSound(null, BlockPos.ofFloored(center), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0F, 1.1F);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(center, center).expand(r, 2.5, r),
				e -> hittable(player, e) && e.getEntityPos().squaredDistanceTo(center) <= (r + 0.5) * (r + 0.5))) {
			target.damage(world, player.getDamageSources().indirectMagic(player, player), spell.damage());
			ElementalPowers.hitEffect(world, player, target, element);
		}
	}
}
