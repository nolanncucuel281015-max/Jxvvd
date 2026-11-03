package fr.evotool.client;

import fr.evotool.element.Element;
import fr.evotool.element.Elements;
import fr.evotool.network.WindJumpPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.util.math.Vec3d;

/**
 * Pouvoirs élémentaires qui dépendent des déplacements de notre joueur (le client les calcule) :
 * <ul>
 *   <li>bottes de glace : en sprintant, on glisse comme sur la glace, de plus en plus vite ;</li>
 *   <li>bottes de vent : un second saut en plein air.</li>
 * </ul>
 */
public final class ElementalClient {
	/** Vitesse maximale du glissement de glace (blocs par tick ; sprint normal : 0,28). */
	private static final double ICE_SLIDE_MAX = 0.85;
	/** Frottement du sol compensé à chaque tick : comme sur de la glace. */
	private static final double ICE_GLIDE = 1.6;
	private static boolean windJumpUsed;
	private static boolean wasJumping;

	private ElementalClient() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ElementalClient::tick);
	}

	private static void tick(MinecraftClient client) {
		ClientPlayerEntity player = client.player;
		if (player == null) {
			return;
		}
		// les sorts de déplacement du grimoire (vol de feu, glissade, bond sismique…)
		fr.evotool.element.MoveSpells.clientTick(player);
		Element boots = Elements.get(player.getEquippedStack(EquipmentSlot.FEET));
		boolean flying = player.getAbilities().flying;

		// glissement de glace
		if (boots == Element.ICE && player.isOnGround() && player.isSprinting() && !player.isSneaking() && !flying
				&& !player.isTouchingWater()) {
			Vec3d v = player.getVelocity();
			double speed = Math.hypot(v.x, v.z);
			if (speed > 0.05) {
				double boost = Math.min(ICE_GLIDE, ICE_SLIDE_MAX / speed);
				player.setVelocity(v.x * boost, v.y, v.z * boost);
			}
		}

		// double saut du vent
		boolean jump = player.input.playerInput.jump();
		if (player.isOnGround() || player.isTouchingWater() || flying || player.isClimbing()) {
			windJumpUsed = false;
		}
		if (boots == Element.WIND && jump && !wasJumping && !player.isOnGround() && !windJumpUsed && !flying
				&& !player.isTouchingWater() && !player.isGliding() && !player.hasVehicle()) {
			windJumpUsed = true;
			Vec3d v = player.getVelocity();
			player.setVelocity(v.x * 1.2, 0.72, v.z * 1.2);
			ClientPlayNetworking.send(WindJumpPayload.INSTANCE);
		}
		wasJumping = jump;
	}
}
