package fr.evotool.mixin;

import fr.evotool.weapon.WeaponPowers;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chaque coup d'épée (le joueur balance le bras, même dans le vide) déclenche le slash explosif de la Lame Détonante. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
	@Shadow
	public ServerPlayerEntity player;

	@Inject(method = "onHandSwing", at = @At("TAIL"))
	private void evotool$blastSlash(HandSwingC2SPacket packet, CallbackInfo ci) {
		if (packet.getHand() == Hand.MAIN_HAND) {
			WeaponPowers.onSwing(this.player);
		}
	}
}
