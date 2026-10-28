package fr.evotool.weapon;

import fr.evotool.component.MaceData;
import fr.evotool.item.EvoliumMaceItem;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;

/** Pouvoirs de la masse en combat et gain d'XP quand elle tue un monstre. */
public final class MaceCombatHandler {
	private static final float LIFESTEAL_RATIO = 0.10F;
	private static final float SHOCKWAVE_RATIO = 0.5F;
	private static final float LIGHTNING_CHANCE = 0.2F;

	/** Évite que les dégâts de l'onde de choc / foudre / exécution relancent les pouvoirs en boucle. */
	private static boolean applyingBonusDamage = false;

	private MaceCombatHandler() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (applyingBonusDamage || blocked || damageTaken <= 0) {
				return;
			}
			ServerPlayerEntity player = maceWielder(source);
			if (player == null) {
				return;
			}
			ItemStack mace = player.getMainHandStack();
			MaceData data = MaceProgression.get(mace);
			int level = data.level();
			ServerWorld world = (ServerWorld) entity.getEntityWorld();

			if (level >= MaceProgression.LIFESTEAL_LEVEL) {
				player.heal(damageTaken * LIFESTEAL_RATIO);
			}

			applyingBonusDamage = true;
			try {
				if (level >= MaceProgression.SHOCKWAVE_LEVEL && data.shockwave()) {
					shockwave(world, player, entity, damageTaken * SHOCKWAVE_RATIO, MaceProgression.shockwaveRadius(level));
				}
				if (level >= MaceProgression.LIGHTNING_LEVEL && data.lightning() && entity.isAlive()
						&& world.getRandom().nextFloat() < LIGHTNING_CHANCE) {
					lightning(world, player, entity, level);
				}
				if (level >= MaceProgression.MAX_LEVEL && entity.isAlive() && entity instanceof Monster
						&& !MaceProgression.isBoss(entity)) {
					entity.damage(world, player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
				}
			} finally {
				applyingBonusDamage = false;
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			ServerPlayerEntity player = maceWielder(source);
			if (player == null || !(entity instanceof Monster) || player.isCreative()) {
				return;
			}
			MaceProgression.addXp(player.getMainHandStack(), MaceProgression.xpForKill(entity), player,
					player.getEntityWorld());
		});
	}

	private static ServerPlayerEntity maceWielder(DamageSource source) {
		if (source.getAttacker() instanceof ServerPlayerEntity player
				&& player.getMainHandStack().getItem() instanceof EvoliumMaceItem) {
			return player;
		}
		return null;
	}

	private static void shockwave(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage, double radius) {
		world.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6F, 1.4F);
		for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(radius),
				e -> e != target && e != player && e instanceof Monster && e.isAlive())) {
			other.damage(world, player.getDamageSources().playerAttack(player), damage);
		}
	}

	private static void lightning(ServerWorld world, ServerPlayerEntity player, LivingEntity target, int level) {
		LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (bolt != null) {
			// Éclair purement visuel : il ne touche pas le joueur à côté. Les dégâts sont appliqués à la main.
			bolt.refreshPositionAfterTeleport(new Vec3d(target.getX(), target.getY(), target.getZ()));
			bolt.setCosmetic(true);
			world.spawnEntity(bolt);
		}
		target.damage(world, world.getDamageSources().lightningBolt(), 4.0F + level * 0.1F);
		target.setOnFireFor(4.0F);
	}
}
