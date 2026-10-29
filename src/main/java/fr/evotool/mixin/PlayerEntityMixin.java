package fr.evotool.mixin;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** La Lance Évolutive ne fait jamais perdre de faim tant qu'on la tient en main (coups, Élan, charge…). */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
	@Inject(method = "addExhaustion", at = @At("HEAD"), cancellable = true)
	private void evotool$noHungerWithSpear(float exhaustion, CallbackInfo ci) {
		PlayerEntity self = (PlayerEntity) (Object) this;
		if (EvoProgression.kindOf(self.getMainHandStack()) == EvoKind.SPEAR) {
			ci.cancel();
		}
	}
}
