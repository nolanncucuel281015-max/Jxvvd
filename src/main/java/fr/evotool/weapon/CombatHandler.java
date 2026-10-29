package fr.evotool.weapon;

import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import fr.evotool.upgrade.EvoliteUpgrade;
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

/** Pouvoirs de combat (épée, lance, hache, masse, arc) et gain d'XP quand l'arme tue un monstre. */
public final class CombatHandler {
	private static final float LIFESTEAL_RATIO = 0.10F;
	private static final float SHOCKWAVE_RATIO = 0.5F;
	private static final float LIGHTNING_CHANCE = 0.2F;

	/** Évite que les dégâts de l'onde de choc / foudre / exécution relancent les pouvoirs en boucle. */
	private static boolean applyingBonusDamage = false;

	private CombatHandler() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (applyingBonusDamage || blocked || damageTaken <= 0) {
				return;
			}
			Wielded wielded = wielded(source);
			if (wielded == null) {
				return;
			}
			ServerPlayerEntity player = wielded.player();
			ItemStack weapon = wielded.weapon();
			EvoProfile profile = EvoProgression.kindOf(weapon).profile();
			EvoData data = EvoProgression.get(weapon);
			ServerWorld world = (ServerWorld) entity.getEntityWorld();

			if (EvoProgression.kindOf(weapon) == EvoKind.SPEAR) {
				// Une lance d'une ancienne version peut encore avoir l'Élan vanilla : on le convertit.
				EvoProgression.convertVanillaLunge(weapon, world.getRegistryManager());
			}
			if (EvoProgression.isActive(profile, data, Power.LIFESTEAL)) {
				player.heal(damageTaken * LIFESTEAL_RATIO);
			}

			applyingBonusDamage = true;
			try {
				if (EvoProgression.isActive(profile, data, Power.SHOCKWAVE)) {
					double radius = EvoProgression.isActive(profile, data, Power.WIDE_SHOCKWAVE) ? 5.0 : 3.0;
					shockwave(world, player, entity, damageTaken * SHOCKWAVE_RATIO, radius);
				}
				if (EvoProgression.isActive(profile, data, Power.LIGHTNING) && entity.isAlive()
						&& world.getRandom().nextFloat() < LIGHTNING_CHANCE) {
					lightning(world, entity, data.level());
				}
				// Lance améliorée à l'évolite : chaque coup porté pendant la charge foudroie la cible.
				if (EvoliteUpgrade.has(weapon, EvoliteUpgrade.Upgrade.LIGHTNING_CHARGE) && player.isUsingItem() && entity.isAlive()) {
					lightning(world, entity, data.level());
				}
				if (EvoProgression.isActive(profile, data, Power.EXECUTION) && entity.isAlive()
						&& entity instanceof Monster && !EvoProgression.isBoss(entity)) {
					entity.damage(world, player.getDamageSources().playerAttack(player), Float.MAX_VALUE);
				}
			} finally {
				applyingBonusDamage = false;
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			Wielded wielded = wielded(source);
			if (wielded == null || !(entity instanceof Monster) || wielded.player().isCreative()) {
				return;
			}
			ServerPlayerEntity player = wielded.player();
			ItemStack weapon = wielded.weapon();
			// Pour la hache, le compteur affiché reste celui des blocs ; pour l'épée et la masse, ce sont les monstres.
			int countIncrement = EvoProgression.kindOf(weapon).profile().counter() == EvoProfile.Counter.KILLS ? 1 : 0;
			EvoProgression.addXp(weapon, EvoProgression.xpForKill(entity), countIncrement, player, entity.getEntityWorld());
		});
	}

	/** Le joueur et l'arme évolutive qui ont porté le coup. */
	private record Wielded(ServerPlayerEntity player, ItemStack weapon) {
	}

	/**
	 * Arme au corps à corps : celle en main. Flèche tirée par l'Arc Évolutif : l'arc du joueur
	 * (la flèche ne garde qu'une copie de l'arc, l'XP doit aller au vrai).
	 */
	private static Wielded wielded(DamageSource source) {
		if (!(source.getAttacker() instanceof ServerPlayerEntity player)) {
			return null;
		}
		ItemStack shotFrom = source.getWeaponStack();
		if (source.getSource() != player && shotFrom != null && EvoProgression.kindOf(shotFrom) == EvoKind.BOW) {
			ItemStack bow = findBow(player);
			return bow.isEmpty() ? null : new Wielded(player, bow);
		}
		ItemStack main = player.getMainHandStack();
		EvoKind kind = EvoProgression.kindOf(main);
		if (kind != null && kind != EvoKind.BOW && kind.profile().gainsKillXp()) {
			return new Wielded(player, main);
		}
		return null;
	}

	public static ItemStack findBow(ServerPlayerEntity player) {
		if (EvoProgression.kindOf(player.getMainHandStack()) == EvoKind.BOW) {
			return player.getMainHandStack();
		}
		if (EvoProgression.kindOf(player.getOffHandStack()) == EvoKind.BOW) {
			return player.getOffHandStack();
		}
		for (int i = 0; i < player.getInventory().size(); i++) {
			if (EvoProgression.kindOf(player.getInventory().getStack(i)) == EvoKind.BOW) {
				return player.getInventory().getStack(i);
			}
		}
		return ItemStack.EMPTY;
	}

	private static void shockwave(ServerWorld world, ServerPlayerEntity player, LivingEntity target, float damage, double radius) {
		world.spawnParticles(ParticleTypes.SONIC_BOOM, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.6F, 1.4F);
		for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(radius),
				e -> e != target && e != player && e instanceof Monster && e.isAlive())) {
			other.damage(world, player.getDamageSources().playerAttack(player), damage);
		}
	}

	private static void lightning(ServerWorld world, LivingEntity target, int level) {
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
