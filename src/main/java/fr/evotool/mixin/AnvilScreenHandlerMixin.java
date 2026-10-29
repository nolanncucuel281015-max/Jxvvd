package fr.evotool.mixin;

import fr.evotool.item.ModItems;
import fr.evotool.upgrade.EvoliteUpgrade;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.Property;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Enclume : objet évolutif + lingot d'évolite = version améliorée. */
@Mixin(AnvilScreenHandler.class)
public abstract class AnvilScreenHandlerMixin {
	@Shadow
	@Final
	private Property levelCost;

	@Shadow
	private int repairItemUsage;

	@Shadow
	@Nullable
	private String newItemName;

	@Inject(method = "updateResult", at = @At("HEAD"), cancellable = true)
	private void evotool$evoliteUpgrade(CallbackInfo ci) {
		ForgingScreenHandlerAccessor self = (ForgingScreenHandlerAccessor) this;
		ItemStack tool = self.evotool$getInput().getStack(0);
		ItemStack ingot = self.evotool$getInput().getStack(1);
		if (!EvoliteUpgrade.canUpgrade(tool) || !ingot.isOf(ModItems.EVOLITE_INGOT)) {
			return;
		}

		ItemStack result = tool.copy();
		EvoliteUpgrade.apply(result);
		if (newItemName != null && !newItemName.isBlank()) {
			result.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newItemName));
		}
		self.evotool$getOutput().setStack(0, result);
		levelCost.set(EvoliteUpgrade.LEVEL_COST);
		// Un seul lingot consommé.
		repairItemUsage = 1;
		((ScreenHandler) (Object) this).sendContentUpdates();
		ci.cancel();
	}
}
