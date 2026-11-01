package fr.evotool.client;

import fr.evotool.item.ModItems;
import fr.evotool.shield.ShieldGeneratorBlockEntity;
import fr.evotool.shield.ShieldGeneratorScreenHandler;
import java.text.NumberFormat;
import java.util.Locale;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Écran du générateur de bouclier : évolium stocké (jusqu'à 1 milliard) et autonomie, rayon réglable
 * (-10, -1, +1, +10) et bouton marche / arrêt. Les boutons passent par le mécanisme de boutons des menus vanilla.
 */
public class ShieldGeneratorScreen extends HandledScreen<ShieldGeneratorScreenHandler> {
	private static final int TEXT = 0xFFE6DAFF;
	private static final int DIM = 0xFFA594C8;
	private ButtonWidget toggle;

	public ShieldGeneratorScreen(ShieldGeneratorScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		String[] labels = {"-10", "-1", "+1", "+10"};
		int[] ids = {ShieldGeneratorScreenHandler.BUTTON_MINUS_TEN, ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE,
				ShieldGeneratorScreenHandler.BUTTON_PLUS_ONE, ShieldGeneratorScreenHandler.BUTTON_PLUS_TEN};
		for (int i = 0; i < labels.length; i++) {
			int id = ids[i];
			addDrawableChild(ButtonWidget.builder(Text.literal(labels[i]), b -> press(id))
					.dimensions(x + 8 + i * 25, y + 54, 23, 14).build());
		}
		toggle = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> press(ShieldGeneratorScreenHandler.BUTTON_TOGGLE))
				.dimensions(x + 112, y + 54, 56, 14).build());
	}

	private void press(int id) {
		if (client != null && client.interactionManager != null) {
			client.interactionManager.clickButton(handler.syncId, id);
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (toggle != null) {
			toggle.setMessage(Text.translatable(handler.enabled() ? "gui.evotool.shield.on" : "gui.evotool.shield.off"));
		}
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xF0151020);
		context.fill(x, y, x + backgroundWidth, y + 1, 0xFF6FF2FF);
		context.fill(x, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, 0xFF6FF2FF);
		context.fill(x, y, x + 1, y + backgroundHeight, 0xFFB15CFF);
		context.fill(x + backgroundWidth - 1, y, x + backgroundWidth, y + backgroundHeight, 0xFFB15CFF);
		for (Slot slot : handler.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			boolean fuelSlot = slot.id == 0;
			context.fill(sx, sy, sx + 18, sy + 18, fuelSlot ? 0xFF6FF2FF : 0xFF3A3150);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, fuelSlot ? 0xFF241A3A : 0xFF1C1628);
		}
		// l'évolium stocké, en fantôme dans la case quand elle est vide
		Slot fuelSlot = handler.slots.get(0);
		if (!fuelSlot.hasStack() && handler.fuel() > 0) {
			int sx = x + fuelSlot.x;
			int sy = y + fuelSlot.y;
			context.drawItem(new ItemStack(ModItems.EVOLIUM), sx, sy);
			context.drawStackOverlay(textRenderer, new ItemStack(ModItems.EVOLIUM), sx, sy, compact(handler.fuel()));
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		// titres en clair (le fond est sombre)
		context.drawText(textRenderer, title, titleX, titleY, 0xFFFFE08A, false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, DIM, false);
		long fuel = handler.fuel();
		String count = NumberFormat.getIntegerInstance(Locale.FRANCE).format(fuel);
		context.drawText(textRenderer, Text.translatable("gui.evotool.shield.fuel", count), 8, 20, TEXT, false);
		long minutes = fuel * (ShieldGeneratorBlockEntity.TICKS_PER_EVOLIUM / 1200) + handler.burnTicks() / 1200;
		context.drawText(textRenderer, Text.translatable("gui.evotool.shield.autonomy", duration(minutes)), 8, 31, DIM, false);
		context.drawText(textRenderer, Text.translatable("gui.evotool.shield.radius", handler.radius()), 8, 42, TEXT, false);
		Text status;
		int color;
		if (!handler.enabled()) {
			status = Text.translatable("gui.evotool.shield.status_off");
			color = 0xFFFF6A6A;
		} else if (fuel <= 0 && handler.burnTicks() <= 0) {
			status = Text.translatable("gui.evotool.shield.status_empty");
			color = 0xFFFFB04A;
		} else {
			status = Text.translatable("gui.evotool.shield.status_on");
			color = 0xFF6AFF9A;
		}
		context.drawText(textRenderer, status, backgroundWidth - 8 - textRenderer.getWidth(status), playerInventoryTitleY, color, false);
	}

	/** 1 234 → « 1,2k », 1 500 000 → « 1,5M », 1 000 000 000 → « 1G ». */
	private static String compact(long value) {
		if (value >= 1_000_000_000L) {
			return String.format(Locale.FRANCE, "%.1fG", value / 1.0e9).replace(",0", "");
		}
		if (value >= 1_000_000L) {
			return String.format(Locale.FRANCE, "%.1fM", value / 1.0e6).replace(",0", "");
		}
		if (value >= 10_000L) {
			return String.format(Locale.FRANCE, "%.0fk", value / 1.0e3);
		}
		return Long.toString(value);
	}

	/** Autonomie lisible : minutes, heures, jours ou années. */
	private static Text duration(long minutes) {
		long days = minutes / (60 * 24);
		if (days >= 365) {
			return Text.translatable("gui.evotool.shield.time.years", NumberFormat.getIntegerInstance(Locale.FRANCE).format(days / 365));
		}
		if (days > 0) {
			return Text.translatable("gui.evotool.shield.time.days", days, (minutes / 60) % 24);
		}
		if (minutes >= 60) {
			return Text.translatable("gui.evotool.shield.time.hours", minutes / 60, minutes % 60);
		}
		return Text.translatable("gui.evotool.shield.time.minutes", minutes);
	}
}
