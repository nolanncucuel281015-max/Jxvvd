package fr.evotool.client;

import fr.evotool.component.MaceData;
import fr.evotool.item.EvoliumMaceItem;
import fr.evotool.mining.EvoProgression;
import fr.evotool.network.MaceSettingsPayload;
import fr.evotool.weapon.MaceProgression;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Menu de la masse (touche Y) : progression, pouvoirs débloqués, onde de choc et foudre. */
public class MaceScreen extends Screen {
	private static final int BUTTON_WIDTH = 170;
	private static final int BUTTON_HEIGHT = 20;
	private static final int PANEL_WIDTH = 240;

	private MaceData data = MaceData.DEFAULT;
	private boolean shockwave;
	private boolean lightning;

	public MaceScreen() {
		super(Text.translatable("screen.evotool.mace_title"));
	}

	@Override
	protected void init() {
		ItemStack stack = heldMace();
		if (stack.isEmpty()) {
			close();
			return;
		}
		data = MaceProgression.get(stack);
		shockwave = data.shockwave();
		lightning = data.lightning();
		int level = data.level();

		int x = (width - BUTTON_WIDTH) / 2;
		int y = height / 2 + 44;
		ButtonWidget shockButton = ButtonWidget.builder(toggleLabel("shockwave", shockwave,
				level >= MaceProgression.SHOCKWAVE_LEVEL, MaceProgression.SHOCKWAVE_LEVEL), b -> {
			shockwave = !shockwave;
			send();
			clearAndInit();
		}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		shockButton.active = level >= MaceProgression.SHOCKWAVE_LEVEL;
		addDrawableChild(shockButton);

		ButtonWidget lightningButton = ButtonWidget.builder(toggleLabel("lightning", lightning,
				level >= MaceProgression.LIGHTNING_LEVEL, MaceProgression.LIGHTNING_LEVEL), b -> {
			lightning = !lightning;
			send();
			clearAndInit();
		}).dimensions(x, y + BUTTON_HEIGHT + 4, BUTTON_WIDTH, BUTTON_HEIGHT).build();
		lightningButton.active = level >= MaceProgression.LIGHTNING_LEVEL;
		addDrawableChild(lightningButton);
	}

	private ItemStack heldMace() {
		if (client == null || client.player == null) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = client.player.getMainHandStack();
		return stack.getItem() instanceof EvoliumMaceItem ? stack : ItemStack.EMPTY;
	}

	private void send() {
		ClientPlayNetworking.send(new MaceSettingsPayload(shockwave ? 1 : 0, lightning ? 1 : 0));
	}

	private static Text toggleLabel(String power, boolean enabled, boolean unlocked, int requiredLevel) {
		Text name = Text.translatable("screen.evotool.power." + power);
		if (!unlocked) {
			return Text.translatable("screen.evotool.locked", name, requiredLevel).formatted(Formatting.DARK_GRAY);
		}
		return Text.translatable(enabled ? "screen.evotool.toggle_on" : "screen.evotool.toggle_off", name);
	}

	/** Liste des bonus actuellement actifs. */
	private List<Text> activeBonuses(int level) {
		List<Text> bonuses = new ArrayList<>();
		bonuses.add(Text.translatable("screen.evotool.damage_bonus", String.format("%.1f", MaceProgression.damageBonus(level))));
		if (level >= MaceProgression.LIFESTEAL_LEVEL) {
			bonuses.add(Text.translatable("message.evotool.unlock.lifesteal"));
		}
		addEnchant(bonuses, "enchantment.minecraft.density", MaceProgression.countReached(level, MaceProgression.DENSITY_LEVELS));
		addEnchant(bonuses, "enchantment.minecraft.breach", MaceProgression.countReached(level, MaceProgression.BREACH_LEVELS));
		addEnchant(bonuses, "enchantment.minecraft.wind_burst", MaceProgression.countReached(level, MaceProgression.WIND_BURST_LEVELS));
		if (level >= MaceProgression.MAX_LEVEL) {
			bonuses.add(Text.translatable("message.evotool.unlock.execute").formatted(Formatting.GOLD));
		}
		return bonuses;
	}

	private static void addEnchant(List<Text> bonuses, String key, int enchantLevel) {
		if (enchantLevel > 0) {
			bonuses.add(Text.translatable(key).append(" ").append(Text.translatable("enchantment.level." + enchantLevel)));
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		int level = data.level();
		int left = (width - PANEL_WIDTH) / 2;
		int top = height / 2 - 100;
		int bottom = height / 2 + 96;
		context.fill(left, top, left + PANEL_WIDTH, bottom, 0xC0180E10);
		context.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFFFF5C6C);
		context.fill(left, bottom - 1, left + PANEL_WIDTH, bottom, 0xFFFF5C6C);

		super.render(context, mouseX, mouseY, delta);

		int center = width / 2;
		context.drawCenteredTextWithShadow(textRenderer, title, center, top + 8, 0xFFFF9CA8);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.evotool.level_form", level, MaceProgression.MAX_LEVEL, EvoProgression.stageName(level)),
				center, top + 22, 0xFFFFFFFF);

		int barLeft = left + 20;
		int barRight = left + PANEL_WIDTH - 20;
		int barTop = top + 36;
		context.fill(barLeft, barTop, barRight, barTop + 6, 0xFF3A2A2A);
		int needed = MaceProgression.xpToNext(level);
		float progress = needed == 0 ? 1.0F : Math.min(1.0F, data.xp() / (float) needed);
		context.fill(barLeft, barTop, barLeft + (int) ((barRight - barLeft) * progress), barTop + 6, 0xFFFF6A5C);
		Text xpText = needed == 0
				? Text.translatable("screen.evotool.mace_max")
				: Text.translatable("screen.evotool.xp", data.xp(), needed);
		context.drawCenteredTextWithShadow(textRenderer, xpText, center, barTop + 9, 0xFFB0A0A0);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable("tooltip.evotool.kills", data.kills()),
				center, barTop + 21, 0xFFB0A0A0);

		int y = barTop + 38;
		for (Text bonus : activeBonuses(level)) {
			context.drawCenteredTextWithShadow(textRenderer, bonus, center, y, 0xFFFFD866);
			y += 11;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
