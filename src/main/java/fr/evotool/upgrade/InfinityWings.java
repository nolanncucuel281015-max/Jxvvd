package fr.evotool.upgrade;

import fr.evotool.upgrade.EvoliteUpgrade.Upgrade;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Plastron amélioré : les ailes de l'infini. Le joueur peut voler (double saut, comme en créatif) et plus
 * vite qu'en créatif, sans dégâts de chute. Les ailes elles-mêmes (vrais modèles 3D, tools/generate_assets.py
 * gen_wings) sont dessinées par chaque client sur le buste du joueur, comme une cape : voir InfinityWingsFeature.
 */
public final class InfinityWings {
	public static final float DEFAULT_FLY_SPEED = 0.05F;
	/** Vitesse de vol avec les ailes (créatif : 0,05). */
	public static final float SPEED = 0.11F;
	/** Tag posé sur le joueur quand ce sont les ailes qui lui donnent le vol (sauvegardé avec le joueur). */
	private static final String TAG = "evotool_infinity_wings";
	/** Ancien tag du nuage magique (bottes), remplacé par la plongée souterraine. */
	private static final String OLD_CLOUD_TAG = "evotool_magic_cloud";

	private InfinityWings() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 5 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					abilities(player);
				}
			}
		});
		// des ailes : on plane jusqu'au sol, jamais de dégâts de chute
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FALL) && hasWings(player)));
	}

	/** Vrai si ce plastron porte les ailes de l'infini (utilisé aussi par le client pour les dessiner). */
	public static boolean isWingChestplate(ItemStack stack) {
		return EvoliteUpgrade.has(stack, Upgrade.INFINITY_WINGS);
	}

	public static boolean hasWings(PlayerEntity player) {
		return isWingChestplate(player.getEquippedStack(EquipmentSlot.CHEST));
	}

	/** Donne (ou retire) le vol rapide selon que le joueur porte les ailes. */
	private static void abilities(ServerPlayerEntity player) {
		if (player.isCreative() || player.isSpectator() || Burrow.isBurrowing(player)) {
			return;
		}
		Set<String> tags = player.getCommandTags();
		PlayerAbilities abilities = player.getAbilities();
		if (hasWings(player)) {
			if (!tags.contains(TAG)) {
				// les ailes s'ouvrent dans un éclat d'étoiles
				ServerWorld world = (ServerWorld) player.getEntityWorld();
				world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.3, player.getZ(), 30, 0.5, 0.5, 0.5, 0.08);
				world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 0.8F);
			}
			if (!abilities.allowFlying || abilities.getFlySpeed() != SPEED || !tags.contains(TAG)) {
				restoreAbilities(player);
			}
		} else if (tags.contains(TAG) || tags.contains(OLD_CLOUD_TAG)) {
			restoreAbilities(player);
		}
	}

	/** Remet le vol du joueur dans l'état normal : celui des ailes s'il les porte, sinon celui de son mode de jeu. */
	public static void restoreAbilities(ServerPlayerEntity player) {
		PlayerAbilities abilities = player.getAbilities();
		player.removeCommandTag(OLD_CLOUD_TAG);
		if (player.isCreative() || player.isSpectator()) {
			abilities.allowFlying = true;
			abilities.setFlySpeed(DEFAULT_FLY_SPEED);
			player.removeCommandTag(TAG);
		} else if (hasWings(player)) {
			abilities.allowFlying = true;
			abilities.setFlySpeed(SPEED);
			player.addCommandTag(TAG);
		} else {
			abilities.allowFlying = false;
			abilities.flying = false;
			abilities.setFlySpeed(DEFAULT_FLY_SPEED);
			player.removeCommandTag(TAG);
		}
		player.sendAbilitiesUpdate();
	}
}
