package fr.evotool.mixin.client;

import fr.evotool.client.TentacleTravel;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Clic droit (« utiliser ») : à main nue avec les tentacules, il attrape (ou projette) au lieu de l'action normale.
 * On passe par l'action du jeu elle-même : elle est déclenchée par chaque clic, même très bref.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
	private void evotool$tentacleGrab(CallbackInfo ci) {
		if (TentacleTravel.onUse((MinecraftClient) (Object) this)) {
			ci.cancel();
		}
	}
}
