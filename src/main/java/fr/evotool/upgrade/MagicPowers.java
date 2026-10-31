package fr.evotool.upgrade;

import fr.evotool.armor.ArmorSet;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.upgrade.EvoliteUpgrade.Upgrade;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/** Les pouvoirs des objets améliorés à l'évolite. */
public final class MagicPowers {
	private static final int SLASH_COOLDOWN = 20;
	private static final double SLASH_RANGE = 8.0;
	private static final double SLASH_WIDTH = 1.6;
	private static final int SOAR_COOLDOWN = 60;
	private static final int SOAR_FALL_IMMUNITY_TICKS = 200;
	private static final double KNIGHT_SIGHT_RANGE = 24.0;

	/** Explosion céleste de l'épée (accroupi + clic droit). */
	private static final double EXPLOSION_RADIUS = 20.0;
	private static final double EXPLOSION_WAVE_SPEED = 2.0;
	private static final int EXPLOSION_COOLDOWN = 200;

	/** Une onde d'explosion qui s'étend de 2 blocs par tick jusqu'à 20 blocs. */
	private record ExplosionWave(ServerWorld world, ServerPlayerEntity player, Vec3d center, int startTick, float damage) {
	}

	private static final List<ExplosionWave> WAVES = new ArrayList<>();

	private static final Map<UUID, Integer> SOAR_IMMUNITY_UNTIL = new HashMap<>();

	private MagicPowers() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(MagicPowers::onUseItem);

		// Pas de dégâts de chute après un bond de la masse.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FALL)) {
				Integer until = SOAR_IMMUNITY_UNTIL.get(player.getUuid());
				return until == null || player.getEntityWorld().getServer().getTicks() > until;
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			tickExplosionWaves(tick);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (tick % 5 == 0) {
					lightningCharge(player);
				}
				if (tick % 20 == 0) {
					knightSight(player);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			SOAR_IMMUNITY_UNTIL.remove(handler.getPlayer().getUuid());
		});
	}

	private static ItemStack worn(PlayerEntity player, EquipmentSlot slot) {
		ItemStack stack = player.getEquippedStack(slot);
		return ArmorSet.isEvolvingArmor(stack, slot) ? stack : ItemStack.EMPTY;
	}

	// ------------------------------------------------------------------ épée et masse (clic droit)

	private static ActionResult onUseItem(PlayerEntity player, net.minecraft.world.World world, Hand hand) {
		ItemStack stack = player.getStackInHand(hand);
		// Accroupi + clic droit avec l'épée évolutive : explosion céleste de 20 blocs.
		if (player.isSneaking() && EvoProgression.kindOf(stack) == EvoKind.SWORD) {
			if (player.getItemCooldownManager().isCoolingDown(stack)) {
				return ActionResult.FAIL;
			}
			player.getItemCooldownManager().set(stack, EXPLOSION_COOLDOWN);
			if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
				celestialExplosion(serverWorld, serverPlayer, EvoProgression.get(stack).level());
			}
			player.swingHand(hand);
			return ActionResult.SUCCESS;
		}
		boolean slash = EvoliteUpgrade.has(stack, Upgrade.MAGIC_SLASH);
		boolean soar = EvoliteUpgrade.has(stack, Upgrade.SOAR);
		if (!slash && !soar) {
			return ActionResult.PASS;
		}
		if (player.getItemCooldownManager().isCoolingDown(stack)) {
			return ActionResult.FAIL;
		}
		player.getItemCooldownManager().set(stack, slash ? SLASH_COOLDOWN : SOAR_COOLDOWN);

		if (soar) {
			// Le mouvement du joueur est géré par son client : on donne l'élan des deux côtés.
			Vec3d look = player.getRotationVector();
			player.addVelocity(look.x * 0.6, 1.4, look.z * 0.6);
		}
		if (world instanceof ServerWorld serverWorld && player instanceof ServerPlayerEntity serverPlayer) {
			if (slash) {
				magicSlash(serverWorld, serverPlayer);
			} else {
				SOAR_IMMUNITY_UNTIL.put(serverPlayer.getUuid(), serverWorld.getServer().getTicks() + SOAR_FALL_IMMUNITY_TICKS);
				serverWorld.spawnParticles(ParticleTypes.GUST, player.getX(), player.getY(), player.getZ(), 1, 0, 0, 0, 0);
				serverWorld.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH,
						SoundCategory.PLAYERS, 1.0F, 0.7F);
			}
		}
		player.swingHand(hand);
		return ActionResult.SUCCESS;
	}

	/** Vague tranchante : touche tous les monstres dans un couloir de 8 blocs devant le joueur. */
	private static void magicSlash(ServerWorld world, ServerPlayerEntity player) {
		Vec3d start = player.getEyePos();
		Vec3d dir = player.getRotationVector();
		float damage = (float) player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE);

		for (double d = 1.0; d <= SLASH_RANGE; d += 0.8) {
			Vec3d p = start.add(dir.multiply(d));
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y - 0.3, p.z, 1, 0, 0, 0, 0);
			world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y - 0.3, p.z, 2, 0.2, 0.2, 0.2, 0.01);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);

		Vec3d end = start.add(dir.multiply(SLASH_RANGE));
		Box area = new Box(start, end).expand(SLASH_WIDTH);
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
				e -> e != player && e.isAlive() && !(e instanceof PlayerEntity))) {
			Vec3d center = target.getBoundingBox().getCenter();
			double along = center.subtract(start).dotProduct(dir);
			if (along < 0 || along > SLASH_RANGE + 1) {
				continue;
			}
			Vec3d closest = start.add(dir.multiply(along));
			if (closest.distanceTo(center) <= SLASH_WIDTH + target.getWidth() / 2) {
				target.damage(world, player.getDamageSources().playerAttack(player), damage);
				world.spawnParticles(ParticleTypes.ENCHANTED_HIT, center.x, center.y, center.z, 8, 0.3, 0.3, 0.3, 0.1);
			}
		}
	}

	// ------------------------------------------------------------------ épée : explosion céleste

	private static void celestialExplosion(ServerWorld world, ServerPlayerEntity player, int level) {
		Vec3d center = new Vec3d(player.getX(), player.getY() + 0.5, player.getZ());
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 3, 1.0, 0.5, 1.0, 0.0);
		world.spawnParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1, center.z, 1, 0, 0, 0, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 4.0F, 0.6F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 2.0F, 0.8F);
		float damage = 20.0F + level * 0.3F;
		WAVES.add(new ExplosionWave(world, player, center, world.getServer().getTicks(), damage));
	}

	/** Fait avancer chaque onde : anneau de particules + dégâts et recul aux monstres qu'elle atteint. */
	private static void tickExplosionWaves(int tick) {
		for (Iterator<ExplosionWave> it = WAVES.iterator(); it.hasNext(); ) {
			ExplosionWave wave = it.next();
			int age = tick - wave.startTick();
			double outer = Math.min(EXPLOSION_RADIUS, (age + 1) * EXPLOSION_WAVE_SPEED);
			double inner = age * EXPLOSION_WAVE_SPEED;
			if (inner >= EXPLOSION_RADIUS || wave.player().isRemoved()) {
				it.remove();
				continue;
			}
			ServerWorld world = wave.world();
			Vec3d c = wave.center();
			int points = (int) (outer * 5);
			for (int i = 0; i < points; i++) {
				double angle = 2 * Math.PI * i / points;
				double x = c.x + Math.cos(angle) * outer;
				double z = c.z + Math.sin(angle) * outer;
				world.spawnParticles(i % 3 == 0 ? ParticleTypes.EXPLOSION : ParticleTypes.CLOUD, x, c.y, z, 1, 0.2, 0.3, 0.2, 0.02);
			}
			Box band = new Box(c.x - outer, c.y - 6, c.z - outer, c.x + outer, c.y + 6, c.z + outer);
			for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, band,
					e -> e instanceof Monster && e.isAlive())) {
				double dist = Math.sqrt(target.squaredDistanceTo(c.x, target.getY(), c.z));
				if (dist < inner || dist > outer) {
					continue;
				}
				float falloff = (float) (1.0 - dist / (EXPLOSION_RADIUS + 4.0));
				target.damage(world, wave.player().getDamageSources().playerAttack(wave.player()), wave.damage() * falloff);
				target.takeKnockback(2.5 * falloff, c.x - target.getX(), c.z - target.getZ());
			}
		}
	}

	// ------------------------------------------------------------------ lance : charge fulgurante

	private static void lightningCharge(ServerPlayerEntity player) {
		if (player.isUsingItem() && EvoliteUpgrade.has(player.getActiveItem(), Upgrade.LIGHTNING_CHARGE)) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 10, 2, true, false, false));
			ServerWorld world = (ServerWorld) player.getEntityWorld();
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 6, 0.4, 0.5, 0.4, 0.1);
		}
	}

	// ------------------------------------------------------------------ casque : vue du chevalier

	private static void knightSight(ServerPlayerEntity player) {
		if (!EvoliteUpgrade.has(worn(player, EquipmentSlot.HEAD), Upgrade.KNIGHT_SIGHT)) {
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		for (LivingEntity monster : world.getEntitiesByClass(LivingEntity.class,
				player.getBoundingBox().expand(KNIGHT_SIGHT_RANGE), e -> e instanceof Monster && e.isAlive())) {
			monster.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false, false));
		}
	}
}
