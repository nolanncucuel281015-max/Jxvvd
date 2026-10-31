package fr.evotool.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.evotool.upgrade.InfinityWings;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public abstract class EntityMixin {
	/**
	 * Le serveur refuse qu'on chevauche une entité qui n'est pas sauvegardée (un joueur) : on fait une exception
	 * pour les ailes de l'infini, qui chevauchent le joueur pour suivre chacun de ses mouvements.
	 */
	@WrapOperation(method = "startRiding(Lnet/minecraft/entity/Entity;ZZ)Z",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/EntityType;isSaveable()Z"))
	private boolean evotool$wingsRidePlayers(EntityType<?> vehicleType, Operation<Boolean> original) {
		return original.call(vehicleType) || ((Entity) (Object) this).getCommandTags().contains(InfinityWings.PART_TAG);
	}
}
