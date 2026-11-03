package fr.evotool.gametest.mixin;

import java.util.function.Predicate;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Le serveur dédié des tests n'a que 10 s pour démarrer : assez pour un monde plat, pas pour un vrai monde (images de
 * présentation). On lui laisse 5 minutes.
 */
@Mixin(targets = "net.fabricmc.fabric.impl.client.gametest.util.DedicatedServerImplUtil", remap = false)
public abstract class DedicatedServerImplUtilMixin {
	@Redirect(method = "start", at = @At(value = "INVOKE",
			target = "Lnet/fabricmc/fabric/api/client/gametest/v1/context/ClientGameTestContext;waitFor(Ljava/util/function/Predicate;)I"))
	private static int evotool$waitLonger(ClientGameTestContext context, Predicate<MinecraftClient> predicate) {
		return context.waitFor(predicate, 20 * 60 * 5);
	}
}
