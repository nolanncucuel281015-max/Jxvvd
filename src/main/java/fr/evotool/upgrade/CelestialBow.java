package fr.evotool.upgrade;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/**
 * Arc Évolutif niveau 100 amélioré à l'évolite : clic droit = l'arc du joueur apparaît en géant devant lui
 * (« pfff »), bandé, et tire une flèche toutes les demi-secondes droit sur les monstres proches.
 */
public final class CelestialBow {
	private static final int ARROW_COUNT = 20;
	/** Une flèche toutes les demi-secondes. */
	private static final int ARROW_INTERVAL = 10;
	/** Le temps que l'arc géant apparaisse avant la première flèche. */
	private static final int APPEAR_TICKS = 8;
	private static final int VOLLEY_TICKS = APPEAR_TICKS + ARROW_COUNT * ARROW_INTERVAL;
	private static final int COOLDOWN = VOLLEY_TICKS + 40;
	private static final double RANGE = 48.0;
	private static final float ARROW_SPEED = 3.0F;
	private static final int ARROW_LIFETIME = 100;
	private static final float GIANT_SCALE = 6.0F;
	private static final double DISTANCE_IN_FRONT = 4.5;

	private static final class Volley {
		final ServerWorld world;
		final ServerPlayerEntity player;
		final ItemStack bow;
		final int startTick;
		final DisplayEntity.ItemDisplayEntity giant;
		int fired;

		Volley(ServerWorld world, ServerPlayerEntity player, ItemStack bow, int startTick, DisplayEntity.ItemDisplayEntity giant) {
			this.world = world;
			this.player = player;
			this.bow = bow;
			this.startTick = startTick;
			this.giant = giant;
		}
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
			DisplayEntity.ItemDisplayEntity giant = spawnGiant(serverWorld, serverPlayer, bow);
			VOLLEYS.add(new Volley(serverWorld, serverPlayer, bow, serverWorld.getServer().getTicks(), giant));
		}
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	/** L'arc du joueur, bandé, en géant devant lui. */
	private static DisplayEntity.ItemDisplayEntity spawnGiant(ServerWorld world, ServerPlayerEntity player, ItemStack bow) {
		ItemStack shown = bow.copyWithCount(1);
		float stage = EvoProgression.stage(EvoProgression.get(bow).level());
		// floats[0] = forme ; flags[0] = forcer le modèle bandé (voir items/evolving_bow.json)
		shown.set(DataComponentTypes.CUSTOM_MODEL_DATA,
				new CustomModelDataComponent(List.of(stage), List.of(true), List.of(), List.of()));
		DisplayEntity.ItemDisplayEntity giant = GiantItem.spawn(world, shown, ItemDisplayContext.FIXED, giantPosition(player),
				player.getYaw(), ROTATION, GIANT_SCALE, APPEAR_TICKS);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 1.2F);
		return giant;
	}

	/** Tourné de 45° pour que la corde soit verticale. */
	private static final Quaternionf ROTATION = new Quaternionf().rotationZ((float) Math.toRadians(-45));

	private static Vec3d giantPosition(ServerPlayerEntity player) {
		return player.getEyePos().add(player.getRotationVector().multiply(DISTANCE_IN_FRONT)).add(0, 0.5, 0);
	}

	private static void tick(int tick) {
		for (Iterator<Volley> it = VOLLEYS.iterator(); it.hasNext(); ) {
			Volley volley = it.next();
			int step = tick - volley.startTick;
			if (step >= VOLLEY_TICKS || volley.player.isRemoved() || volley.player.getEntityWorld() != volley.world) {
				GiantItem.remove(volley.world, volley.giant, GIANT_SCALE);
				it.remove();
				continue;
			}
			// L'arc géant suit le regard du joueur.
			Vec3d pos = giantPosition(volley.player);
			volley.giant.refreshPositionAndAngles(pos.x, pos.y, pos.z, volley.player.getYaw(), 0.0F);
			if (step >= APPEAR_TICKS && (step - APPEAR_TICKS) % ARROW_INTERVAL == 0 && volley.fired < ARROW_COUNT) {
				fire(volley, pos, tick);
				volley.fired++;
			}
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

	/** Une flèche, tirée depuis l'arc géant, sur le monstre le plus proche (ou tout droit s'il n'y en a pas). */
	private static void fire(Volley volley, Vec3d origin, int tick) {
		ServerWorld world = volley.world;
		ServerPlayerEntity player = volley.player;
		List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(RANGE),
				e -> e instanceof Monster && e.isAlive() && e.squaredDistanceTo(player) <= RANGE * RANGE);
		targets.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)));

		Vec3d direction;
		if (targets.isEmpty()) {
			direction = player.getRotationVector();
		} else {
			LivingEntity target = targets.get(volley.fired % Math.min(targets.size(), 3));
			Vec3d aim = target.getBoundingBox().getCenter();
			double travel = aim.distanceTo(origin) / ARROW_SPEED;
			direction = aim.add(target.getVelocity().multiply(travel)).subtract(origin);
		}
		ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), volley.bow.copy());
		arrow.setPosition(origin.x, origin.y, origin.z);
		arrow.setNoGravity(true);
		arrow.setCritical(true);
		arrow.setDamage(6.0);
		arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
		arrow.setVelocity(direction.x, direction.y, direction.z, ARROW_SPEED, 0.0F);
		world.spawnEntity(arrow);
		ARROWS.add(new Flying(arrow, tick));
		world.spawnParticles(ParticleTypes.END_ROD, origin.x, origin.y, origin.z, 12, 0.3, 0.3, 0.3, 0.08);
		world.playSound(null, BlockPos.ofFloored(origin), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.5F, 0.8F);
	}
}
