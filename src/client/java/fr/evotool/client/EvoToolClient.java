package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.component.EvoData;
import fr.evotool.component.MaceData;
import fr.evotool.item.EvoliumMaceItem;
import fr.evotool.item.EvolvingPickaxeItem;
import fr.evotool.mining.EvoMode;
import fr.evotool.mining.EvoProgression;
import fr.evotool.weapon.MaceProgression;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class EvoToolClient implements ClientModInitializer {
	public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(EvoTool.id("main"));

	public static final KeyBinding OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.evotool.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));

	@Override
	public void onInitializeClient() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MENU.wasPressed()) {
				if (client.player == null) {
					continue;
				}
				if (client.player.getMainHandStack().getItem() instanceof EvolvingPickaxeItem) {
					client.setScreen(new EvoToolScreen());
				} else if (client.player.getMainHandStack().getItem() instanceof EvoliumMaceItem) {
					client.setScreen(new MaceScreen());
				} else {
					client.player.sendMessage(Text.translatable("message.evotool.hold_pickaxe").formatted(Formatting.RED), true);
				}
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			if (stack.getItem() instanceof EvoliumMaceItem) {
				appendMaceTooltip(stack, lines);
				return;
			}
			if (!(stack.getItem() instanceof EvolvingPickaxeItem)) {
				return;
			}
			EvoData data = EvoProgression.get(stack);
			int level = data.level();
			lines.add(1, Text.translatable("tooltip.evotool.level", level, EvoProgression.MAX_LEVEL).formatted(Formatting.LIGHT_PURPLE));
			int index = 2;
			lines.add(index++, Text.translatable("tooltip.evotool.form", EvoProgression.stageName(level),
					EvoProgression.stage(level), EvoProgression.MAX_STAGE).formatted(Formatting.YELLOW));
			if (level < EvoProgression.MAX_LEVEL) {
				lines.add(index++, Text.translatable("tooltip.evotool.xp", data.xp(), EvoProgression.xpToNext(level)).formatted(Formatting.GRAY));
			}
			lines.add(index++, Text.translatable("tooltip.evotool.mode", Text.translatable(EvoMode.byIndex(data.mode()).translationKey()))
					.formatted(Formatting.AQUA));
			if (EvoProgression.autosmeltUnlocked(level)) {
				lines.add(index++, Text.translatable(data.autosmelt() ? "tooltip.evotool.autosmelt_on" : "tooltip.evotool.autosmelt_off")
						.formatted(Formatting.GOLD));
			}
			lines.add(index++, Text.translatable("tooltip.evotool.blocks", data.totalBlocks()).formatted(Formatting.DARK_GRAY));
			lines.add(index, Text.translatable("tooltip.evotool.hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		});
	}

	private static void appendMaceTooltip(ItemStack stack, List<Text> lines) {
		MaceData data = MaceProgression.get(stack);
		int level = data.level();
		int index = 1;
		lines.add(index++, Text.translatable("tooltip.evotool.level", level, MaceProgression.MAX_LEVEL).formatted(Formatting.RED));
		lines.add(index++, Text.translatable("tooltip.evotool.form", EvoProgression.stageName(level),
				EvoProgression.stage(level), EvoProgression.MAX_STAGE).formatted(Formatting.YELLOW));
		if (level < MaceProgression.MAX_LEVEL) {
			lines.add(index++, Text.translatable("tooltip.evotool.xp", data.xp(), MaceProgression.xpToNext(level)).formatted(Formatting.GRAY));
		}
		lines.add(index++, Text.translatable("tooltip.evotool.kills", data.kills()).formatted(Formatting.DARK_GRAY));
		lines.add(index, Text.translatable("tooltip.evotool.mace_hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
	}
}
