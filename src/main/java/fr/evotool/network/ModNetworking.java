package fr.evotool.network;

import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoMode;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playS2C().register(BurrowPayload.ID, BurrowPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(EvoSettingsPayload.ID, EvoSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(EvoSettingsPayload.ID, (payload, context) -> {
			ItemStack stack = context.player().getMainHandStack();
			EvoKind kind = EvoProgression.kindOf(stack);
			if (kind == null) {
				return;
			}
			EvoProfile profile = kind.profile();
			EvoData data = EvoProgression.get(stack);
			// Le serveur revérifie toujours le niveau : impossible de tricher depuis le client.
			EvoMode mode = EvoMode.byIndex(payload.mode());
			int newMode = profile.modeUnlocked(mode, data.level()) ? mode.ordinal() : data.mode();
			stack.set(ModComponents.EVO_DATA, data.withSettings(newMode, payload.flags()));
		});
	}
}
