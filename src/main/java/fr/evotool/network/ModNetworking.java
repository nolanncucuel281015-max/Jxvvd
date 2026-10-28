package fr.evotool.network;

import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.item.EvoliumMaceItem;
import fr.evotool.item.EvolvingPickaxeItem;
import fr.evotool.mining.EvoMode;
import fr.evotool.mining.EvoProgression;
import fr.evotool.weapon.MaceProgression;
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

		PayloadTypeRegistry.playC2S().register(MaceSettingsPayload.ID, MaceSettingsPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(MaceSettingsPayload.ID, (payload, context) -> {
			ItemStack stack = context.player().getMainHandStack();
			if (stack.getItem() instanceof EvoliumMaceItem) {
				stack.set(ModComponents.MACE_DATA, MaceProgression.get(stack)
						.withToggles(payload.shockwave() != 0, payload.lightning() != 0));
			}
		});
	}
}
