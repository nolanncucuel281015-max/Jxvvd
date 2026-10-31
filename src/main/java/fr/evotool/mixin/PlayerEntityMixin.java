package fr.evotool.mixin;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.upgrade.Burrow;
import net.minecraft.entity.player.PlayerEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	/** La Lance Évolutive ne fait jamais perdre de faim tant qu'on la tient en main (coups, Élan, charge…). */
	@Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
	private void evotool$noHungerWithSpear(float exhaustion, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (EvoProgression.kindOf(self.getMainHandStack()) == EvoKind.SPEAR) {
			ci.cancel();
		}
	}

	/**
	 * Plongée souterraine des bottes : le début du tick remet le passe-muraille à « spectateur ou non » ; juste après
	 * (avant le délai de ramassage de l'XP), on le rallume, des deux côtés car c'est le client qui déplace son joueur,
	 * et on garde le vol actif.
	 */
	@Inject(method = "tick", at = @At(value = "FIELD",
			target = "Lnet/minecraft/entity/player/PlayerEntity;experiencePickUpDelay:I", opcode = Opcodes.GETFIELD, ordinal = 0))
	private void evotool$burrow(CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (Burrow.isBurrowing(self)) {
			self.noClip = true;
			self.setOnGround(false);
			self.getAbilities().flying = true;
		}
	}
}
