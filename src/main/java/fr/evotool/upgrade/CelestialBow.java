package fr.evotool.upgrade;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Arc Évolutif niveau 100 amélioré à l'évolite : clic droit = un arc géant apparaît devant le joueur
 * et tire une pluie de flèches droit sur les monstres proches.
 */
public final class CelestialBow {
	private static final int VOLLEY_TICKS = 12;
	private static final int ARROWS_PER_TICK = 4;
	private static final int COOLDOWN = 60;
	private static final double RANGE = 48.0;
	private static final float ARROW_SPEED = 3.0F;
	private static final int ARROW_LIFETIME = 100;

	private record Volley(ServerWorld world, ServerPlayerEntity player, ItemStack bow, int startTick) {
	}

	private record Flying(ArrowEntity arrow, int spawnTick) {
	}

	private static final List<Volley> VOLLEYS = new ArrayList<>();
	private static final List<Flying> ARROWS = new ArrayList<>();

	private CelestialBow() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(CelestialBow::onUse);
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getTicks()));
	}

	public static boolean isReady(ItemStack stack) {
		return EvoProgression.kindOf(stack) == EvoKind.BOW
				&& EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.CELESTIAL_BOW)
				&& EvoProgression.get(stack).level() >= EvoProgression.MAX_LEVEL;
	}

	private static ActionResult onUse(PlayerEntity player, World world, Hand hand) {
		ItemStack bow = player.getStackInHand(hand);
		if (!isReady(bow)) {
			return ActionResult.PASS;
		}
		if (player.getItemCooldownManager().isCoolingDown(bow)) {
			return ActionResult.FAIL;
		}
		player.getItemCooldownManager().set(bow, COOLDOWN);
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			VOLLEYS.add(new Volley(serverWorld, serverPlayer, bow, serverWorld.getServer().getTicks()));
			serverWorld.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 1.2F);
		}
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	private static void tick(int tick) {
		for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext(); ) {
			Volley volley = it.next();
			int step = tick - volley.startTick();
			if (step >= VOLLEY_TICKS || volley.player().isRemoved()) {
				it.remove();
				continue;
			}
			fire(volley, step, tick);
		}
		// Les flèches de l'arc céleste volent sans gravité : on les retire si elles n'ont rien touché.
		ARROWS.removeIf(f -> {
			if (f.arrow().isRemoved()) {
				return true;
			}
			if (tick - f.spawnTick() > ARROW_LIFETIME) {
				f.arrow().discard();
				return true;
			}
			return false;
		});
	}

	private static void fire(Volley volley, int step, int tick) {
		ServerWorld world = volley.world();
		ServerPlayerEntity player = volley.player();
		Vec3d forward = player.getRotationVector().normalize();
		Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0));
		right = right.lengthSquared() < 1.0E-4 ? new Vec3d(1, 0, 0) : right.normalize();
		Vec3d up = right.crossProduct(forward).normalize();
		Vec3d center = player.getEyePos().add(forward.multiply(3.0));

		// L'arc géant : deux branches courbées vers le joueur et une corde tendue.
		Vec3d nock = center.subtract(forward.multiply(2.0));
		for (double t = -1.0; t <= 1.0; t += 0.05) {
			Vec3d p = center.add(up.multiply(t * 4.0)).subtract(forward.multiply(1.4 * t * t));
			world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
		Vec3d top = center.add(up.multiply(4.0)).subtract(forward.multiply(1.4));
		Vec3d bottom = center.subtract(up.multiply(4.0)).subtract(forward.multiply(1.4));
		for (double t = 0; t <= 1.0; t += 0.08) {
			Vec3d a = top.lerp(nock, t);
			Vec3d b = bottom.lerp(nock, t);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, a.x, a.y, a.z, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 1, 0, 0, 0, 0);
		}

		List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(RANGE),
				e -> e instanceof Monster && e.isAlive() && e.squaredDistanceTo(player) <= RANGE * RANGE);
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));

		for (int i = 0; i < ARROWS_PER_TICK; i++) {
			int index = step * ARROWS_PER_TICK + i;
			double spread = (i - (ARROWS_PER_TICK - 1) / 2.0) * 0.6;
			Vec3d origin = center.add(up.multiply(spread)).add(right.multiply(world.getRandom().nextGaussian() * 0.4));
			Vec3d direction;
			if (targets.isEmpty()) {
				direction = forward.add(up.multiply(spread * 0.03)).add(right.multiply(world.getRandom().nextGaussian() * 0.05));
			} else {
				LivingEntity target = targets.get(index % targets.size());
				Vec3d aim = target.getBoundingBox().getCenter();
				double travel = aim.distanceTo(origin) / ARROW_SPEED;
				direction = aim.add(target.getVelocity().multiply(travel)).subtract(origin);
			}
			ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), volley.bow().copy());
			arrow.setPosition(origin.x, origin.y, origin.z);
			arrow.setNoGravity(true);
			arrow.setCritical(true);
			arrow.setDamage(5.0);
			arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
			arrow.setVelocity(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0F);
			world.spawnEntity(arrow);
			ARROWS.add(new Flying(arrow, tick));
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.2F + step * 0.03F);
	}
}
