package fr.evotool.network;

import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.item.EvolvingPickaxeItem;
import fr.evotool.mining.EvoMode;
import fr.evotool.mining.EvoProgression;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

public final class ModNetworking {
	private ModNetworking() {
	}

	public static void init() {
		PayloadTypeRegistry.playC2S().register(SelectModePayload.ID, SelectModePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectModePayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			ItemStack stack = player.getMainHandStack();
			if (!(stack.getItem() instanceof EvolvingPickaxeItem)) {
				return;
			}
			EvoData data = EvoProgression.get(stack);
			EvoMode mode = EvoMode.byIndex(payload.mode());
			// Le serveur revérifie toujours le niveau : impossible de tricher depuis le client.
			if (mode.isUnlocked(data.level())) {
				data = data.withMode(mode.ordinal());
			}
			data = data.withAutosmelt(payload.autosmelt() != 0);
			stack.set(ModComponents.EVO_DATA, data);
		});
	}
}
