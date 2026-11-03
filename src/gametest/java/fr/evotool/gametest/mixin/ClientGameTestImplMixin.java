package fr.evotool.gametest.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Rejoindre le monde n'a qu'une minute : assez pour un monde plat, pas toujours pour un vrai monde sur la CI
 * (images de présentation). On laisse 5 minutes.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.gametest.util.ClientGameTestImpl", remap = false)
public abstract class ClientGameTestImplMixin {
	@ModifyConstant(method = "waitForWorldLoad", constant = @Constant(intValue = 1200))
	private static int evotool$waitLonger(int ticks) {
		return 20 * 60 * 5;
	}
}
