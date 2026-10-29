package fr.evotool.upgrade;

import fr.evotool.armor.ArmorSet;
import fr.evotool.upgrade.EvoliteUpgrade.Upgrade;
import java.util.HashMap;
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
import net.minecraft.entity.player.PlayerAbilities;
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
	/** Bouclier magique du plastron : points d'absorption (8 = 4 cœurs dorés). */
	public static final double SHIELD_MAX = 8.0;
	/** Le bouclier se recharge si on n'a pas été touché depuis 5 secondes. */
	private static final int SHIELD_DELAY_TICKS = 100;

	private static final int SLASH_COOLDOWN = 20;
	private static final double SLASH_RANGE = 8.0;
	private static final double SLASH_WIDTH = 1.6;
	private static final int SOAR_COOLDOWN = 60;
	private static final int SOAR_FALL_IMMUNITY_TICKS = 200;
	private static final double KNIGHT_SIGHT_RANGE = 24.0;

	/** Tag posé sur le joueur quand c'est le nuage magique qui lui donne le vol (sauvegardé avec le joueur). */
	private static final String CLOUD_TAG = "evotool_magic_cloud";

	private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
	private static final Map<UUID, Integer> SOAR_IMMUNITY_UNTIL = new HashMap<>();

	private MagicPowers() {
	}

	public static void init() {
		UseItemCallback.EVENT.register(MagicPowers::onUseItem);

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayerEntity player) {
				LAST_HURT.put(player.getUuid(), player.getEntityWorld().getServer().getTicks());
			}
		});

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
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (tick % 2 == 0) {
					cloudParticles(player);
				}
				if (tick % 20 == 0) {
					magicCloud(player);
					knightSight(player);
					magicShield(player, tick);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_HURT.remove(handler.getPlayer().getUuid());
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

	// ------------------------------------------------------------------ bottes : nuage magique

	/**
	 * Avec les bottes améliorées, le joueur a le droit de voler : le client vanilla gère alors
	 * le double saut pour s'envoler, exactement comme en créatif.
	 */
	private static void magicCloud(ServerPlayerEntity player) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		boolean cloud = EvoliteUpgrade.has(worn(player, EquipmentSlot.FEET), Upgrade.MAGIC_CLOUD);
		PlayerAbilities abilities = player.getAbilities();
		if (cloud && !abilities.allowFlying) {
			abilities.allowFlying = true;
			player.addCommandTag(CLOUD_TAG);
			player.sendAbilitiesUpdate();
		} else if (!cloud && player.getCommandTags().contains(CLOUD_TAG)) {
			abilities.allowFlying = false;
			abilities.flying = false;
			player.removeCommandTag(CLOUD_TAG);
			player.sendAbilitiesUpdate();
		}
	}

	private static void cloudParticles(ServerPlayerEntity player) {
		if (player.getAbilities().flying && !player.isCreative() && player.getCommandTags().contains(CLOUD_TAG)) {
			ServerWorld world = (ServerWorld) player.getEntityWorld();
			world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() - 0.2, player.getZ(), 4, 0.4, 0.05, 0.4, 0.0);
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

	// ------------------------------------------------------------------ plastron : bouclier magique

	private static void magicShield(ServerPlayerEntity player, int tick) {
		if (!EvoliteUpgrade.has(worn(player, EquipmentSlot.CHEST), Upgrade.MAGIC_SHIELD)) {
			return;
		}
		int lastHurt = LAST_HURT.getOrDefault(player.getUuid(), Integer.MIN_VALUE / 2);
		float current = player.getAbsorptionAmount();
		if (tick - lastHurt >= SHIELD_DELAY_TICKS && current < SHIELD_MAX) {
			player.setAbsorptionAmount((float) Math.min(SHIELD_MAX, current + 2.0F));
			ServerWorld world = (ServerWorld) player.getEntityWorld();
			world.spawnParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.4, 0.6, 0.4, 0.5);
		}
	}
}
