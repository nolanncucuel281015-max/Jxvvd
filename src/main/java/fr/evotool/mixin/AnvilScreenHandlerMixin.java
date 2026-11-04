package fr.evotool.mixin;

import fr.evotool.armor.AbyssalArmor;
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

/**
 * Enclume : objet évolutif + lingot d'évolite = version améliorée ; armure niveau 100 + abyssite (ou son minerai) =
 * armure abyssale. Si ça ne peut pas marcher, le joueur voit pourquoi (niveau trop bas, objets inversés…).
 */
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
		ItemStack result;
		int cost;
		if (AbyssalArmor.isAbyssite(ingot) && AbyssalArmor.canUpgrade(tool)) {
			// pièce d'armure niveau 100 + abyssite = armure abyssale (jusqu'au niveau 150, noire)
			result = tool.copy();
			AbyssalArmor.apply(result);
			cost = AbyssalArmor.LEVEL_COST;
		} else if (ingot.isOf(ModItems.EVOLITE_INGOT) && EvoliteUpgrade.canUpgrade(tool)) {
			result = tool.copy();
			EvoliteUpgrade.apply(result);
			cost = EvoliteUpgrade.LEVEL_COST;
		} else {
			evotool$explain(self, tool, ingot);
			return;
		}
		if (newItemName != null && !newItemName.isBlank()) {
			result.set(DataComponentTypes.CUSTOM_NAME, Text.literal(newItemName));
		}
		self.evotool$getOutput().setStack(0, result);
		levelCost.set(cost);
		// Un seul lingot consommé.
		repairItemUsage = 1;
		((ScreenHandler) (Object) this).sendContentUpdates();
		ci.cancel();
	}

	/** Abyssite posée mais rien ne sort : on dit au joueur pourquoi (dans la barre d'action). */
	@org.spongepowered.asm.mixin.Unique
	private static void evotool$explain(ForgingScreenHandlerAccessor self, ItemStack left, ItemStack right) {
		if (!(self.evotool$getPlayer() instanceof net.minecraft.server.network.ServerPlayerEntity player)) {
			return;
		}
		Text problem = null;
		if (AbyssalArmor.isAbyssite(left) && !right.isEmpty()) {
			problem = Text.translatable("message.evotool.abyssite.swap");
		} else if (AbyssalArmor.isAbyssite(right) && !left.isEmpty()) {
			problem = AbyssalArmor.problem(left);
		}
		if (problem != null) {
			player.sendMessage(problem.copy().formatted(net.minecraft.util.Formatting.RED), true);
		}
	}
}
