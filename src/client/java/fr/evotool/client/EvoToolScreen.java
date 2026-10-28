package fr.evotool.client;

import fr.evotool.component.EvoData;
import fr.evotool.item.EvolvingPickaxeItem;
import fr.evotool.mining.EvoMode;
import fr.evotool.mining.EvoProgression;
import fr.evotool.network.SelectModePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Interface ouverte avec la touche Y : choix du mode de minage et de l'auto-smelt. */
public class EvoToolScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PANEL_WIDTH = 220;

	private int selectedMode;
	private boolean autosmelt;
	private int level;
	private EvoData data = EvoData.DEFAULT;

	public EvoToolScreen() {
		super(Text.translatable("screen.evotool.title"));
	}

	@Override
	protected void init() {
		ItemStack stack = heldPickaxe();
		if (stack.isEmpty()) {
			close();
			return;
		}
		data = EvoProgression.get(stack);
		level = data.level();
		selectedMode = data.mode();
		autosmelt = data.autosmelt();

		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 - 40;
		for (EvoMode mode : EvoMode.values()) {
			boolean unlocked = mode.isUnlocked(level);
			ButtonWidget button = ButtonWidget.builder(modeLabel(mode, unlocked), b -> {
				selectedMode = mode.ordinal();
				send();
				clearAndInit();
			}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
			button.active = unlocked && mode.ordinal() != selectedMode;
			addDrawableChild(button);
			y += BUTTON_HEIGHT + 4;
		}

		y += 6;
		ButtonWidget smeltButton = ButtonWidget.builder(autosmeltLabel(), b -> {
			autosmelt = !autosmelt;
			send();
			clearAndInit();
		}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		smeltButton.active = EvoProgression.autosmeltUnlocked(level);
		addDrawableChild(smeltButton);
	}

	private ItemStack heldPickaxe() {
		if (client == null || client.player == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = client.player.getMainHandStack();
		return stack.getItem() instanceof EvolvingPickaxeItem ? stack : ItemStack.EMPTY;
	}

	private void send() {
		ClientPlayNetworking.send(new SelectModePayload(selectedMode, autosmelt ? 1 : 0));
	}

	private Text modeLabel(EvoMode mode, boolean unlocked) {
		Text name = Text.translatable(mode.translationKey());
		if (!unlocked) {
			return Text.translatable("screen.evotool.locked", name, mode.requiredLevel()).formatted(Formatting.DARK_GRAY);
		}
		if (mode.ordinal() == selectedMode) {
			return Text.literal("» ").append(name).append(" «").formatted(Formatting.GREEN);
		}
		return name;
	}

	private Text autosmeltLabel() {
		if (!EvoProgression.autosmeltUnlocked(level)) {
			return Text.translatable("screen.evotool.autosmelt_locked", EvoProgression.AUTOSMELT_LEVEL).formatted(Formatting.DARK_GRAY);
		}
		return Text.translatable(autosmelt ? "screen.evotool.autosmelt_on" : "screen.evotool.autosmelt_off");
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int left = (width - PANEL_WIDTH) / 2;
		int top = height / 2 - 100;
		int bottom = height / 2 + 128;
		context.fill(left, top, left + PANEL_WIDTH, bottom, 0xC0101018);
		context.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFFB15CFF);
		context.fill(left, bottom - 1, left + PANEL_WIDTH, bottom, 0xFFB15CFF);

		super.render(context, mouseX, mouseY, delta);

		int center = width / 2;
		context.drawCenteredTextWithShadow(textRenderer, title, center, top + 8, 0xFFE0B0FF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.evotool.level", level, EvoProgression.MAX_LEVEL), center, top + 22, 0xFFFFFFFF);

		// Barre d'XP
		int barLeft = left + 20;
		int barRight = left + PANEL_WIDTH - 20;
		int barTop = top + 36;
		context.fill(barLeft, barTop, barRight, barTop + 6, 0xFF2A2A3A);
		int needed = EvoProgression.xpToNext(level);
		float progress = needed == 0 ? 1.0F : Math.min(1.0F, data.xp() / (float) needed);
		context.fill(barLeft, barTop, barLeft + (int) ((barRight - barLeft) * progress), barTop + 6, 0xFF7CF0FF);
		Text xpText = needed == 0
				? Text.translatable("screen.evotool.max")
				: Text.translatable("screen.evotool.xp", data.xp(), needed);
		context.drawCenteredTextWithShadow(textRenderer, xpText, center, barTop + 9, 0xFFA0A0B0);

		int fortune = EvoProgression.fortuneFor(level);
		Text fortuneText = fortune > 0
				? Text.translatable("screen.evotool.fortune", Text.translatable("enchantment.level." + fortune))
				: Text.translatable("screen.evotool.fortune_locked");
		context.drawCenteredTextWithShadow(textRenderer, fortuneText, center, bottom - 15, 0xFFFFD866);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
