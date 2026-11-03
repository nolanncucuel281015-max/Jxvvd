package fr.evotool.item;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Le manuel est offert à la première connexion (une seule fois par joueur). */
public final class ManualGift {
	private static final String TAG = "evotool_manual_given";

	private ManualGift() {
	}

	public static void init() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.getPlayer();
			if (player.getCommandTags().contains(TAG)) {
				return;
			}
			player.addCommandTag(TAG);
			player.giveItemStack(new ItemStack(ModItems.MANUAL));
			player.sendMessage(Text.translatable("message.evotool.manual_given").formatted(Formatting.GOLD), false);
		});
	}
}
