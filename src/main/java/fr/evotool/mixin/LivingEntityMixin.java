package fr.evotool.mixin;

import fr.evotool.element.Element;
import fr.evotool.element.Elements;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Bottes d'eau (combineur élémentaire) : on marche sur l'eau comme un arpenteur sur la lave ; accroupi, on plonge. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(method = "canWalkOnFluid", at = @At("HEAD"), cancellable = true)
	private void evotool$walkOnWater(FluidState state, CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof PlayerEntity player && state.isIn(FluidTags.WATER) && !player.isSneaking()
				&& Elements.wears(player, EquipmentSlot.FEET, Element.WATER)) {
			cir.setReturnValue(true);
		}
	}
}
