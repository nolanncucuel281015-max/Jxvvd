package fr.evotool.client;

import fr.evotool.combiner.ElementalCombinerBlockEntity;
import fr.evotool.combiner.ElementalCombinerScreenHandler;
import fr.evotool.element.Element;
import fr.evotool.element.Elements;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

/**
 * Menu du combineur élémentaire : équipement + élément → objet élémentaire. Les couleurs suivent l'élément posé,
 * la flèche se remplit pendant la fusion, et une ligne annonce le pouvoir que l'objet va recevoir.
 */
public class ElementalCombinerScreen extends HandledScreen<ElementalCombinerScreenHandler> {
	private static final int NEUTRAL = 0xFFB58CFF;
	private ButtonWidget combine;

	public ElementalCombinerScreen(ElementalCombinerScreenHandler handler, PlayerInventory inventory, Text title) {
		super(handler, inventory, title);
		backgroundWidth = 176;
		backgroundHeight = 166;
		playerInventoryTitleY = backgroundHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		combine = addDrawableChild(ButtonWidget.builder(Text.translatable("gui.evotool.combiner.combine"), b -> {
			if (client != null && client.interactionManager != null) {
				client.interactionManager.clickButton(handler.syncId, ElementalCombinerScreenHandler.BUTTON_COMBINE);
			}
		}).dimensions(x + 48, y + 57, 80, 13).build());
	}

	private int accent() {
		Element element = handler.element();
		return element == null ? NEUTRAL : 0xFF000000 | element.rgb();
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (combine != null) {
			combine.active = handler.ready();
			combine.setMessage(Text.translatable(handler.progress() >= 0 ? "gui.evotool.combiner.working" : "gui.evotool.combiner.combine"));
		}
		super.render(context, mouseX, mouseY, delta);
		drawMouseoverTooltip(context, mouseX, mouseY);
	}

	@Override
	protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
		int accent = accent();
		long time = System.currentTimeMillis();
		float glow = 0.5F + 0.5F * (float) Math.sin(time / 300.0);
		int border = blend(accent, 0xFFFFFFFF, glow * 0.35F);
		context.fill(x, y, x + backgroundWidth, y + backgroundHeight, 0xF0120E1C);
		context.fill(x, y, x + backgroundWidth, y + 1, border);
		context.fill(x, y + backgroundHeight - 1, x + backgroundWidth, y + backgroundHeight, border);
		context.fill(x, y, x + 1, y + backgroundHeight, border);
		context.fill(x + backgroundWidth - 1, y, x + backgroundWidth, y + backgroundHeight, border);
		for (Slot slot : handler.slots) {
			int sx = x + slot.x - 1;
			int sy = y + slot.y - 1;
			boolean machine = slot.id < 3;
			context.fill(sx, sy, sx + 18, sy + 18, machine ? accent : 0xFF3A3150);
			context.fill(sx + 1, sy + 1, sx + 17, sy + 17, machine ? 0xFF241A3A : 0xFF1C1628);
		}
		int sy = y + ElementalCombinerScreenHandler.SLOTS_Y;
		// le « + » entre l'équipement et l'élément
		int px = x + (ElementalCombinerScreenHandler.EQUIPMENT_X + 16 + ElementalCombinerScreenHandler.ELEMENT_X) / 2;
		context.fill(px - 4, sy + 7, px + 5, sy + 9, accent);
		context.fill(px - 1, sy + 4, px + 2, sy + 12, accent);
		// la flèche, qui se remplit pendant la fusion
		int ax0 = x + ElementalCombinerScreenHandler.ELEMENT_X + 22;
		int ax1 = x + ElementalCombinerScreenHandler.OUTPUT_X - 6;
		context.fill(ax0, sy + 6, ax1, sy + 10, 0xFF3A3150);
		for (int i = 0; i < 5; i++) {
			context.fill(ax1 + i - 1, sy + 3 + i, ax1 + i, sy + 13 - i, 0xFF3A3150);
		}
		int progress = handler.progress();
		if (progress >= 0) {
			int filled = (ax1 + 4 - ax0) * progress / ElementalCombinerBlockEntity.DURATION;
			context.fill(ax0, sy + 6, Math.min(ax1, ax0 + filled), sy + 10, accent);
			for (int i = 0; i < 5 && ax1 + i - 1 < ax0 + filled; i++) {
				context.fill(ax1 + i - 1, sy + 3 + i, ax1 + i, sy + 13 - i, accent);
			}
		}
	}

	@Override
	protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
		context.drawText(textRenderer, title, titleX, titleY, 0xFFFFE08A, false);
		context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY, 0xFFA594C8, false);
		Element element = handler.element();
		Text line;
		int color;
		if (element != null && Elements.canCombine(handler.equipment())) {
			line = Text.translatable("gui.evotool.combiner.preview", element.symbol(), Text.translatable(element.translationKey()),
					Text.translatable(Elements.abilityKey(element, handler.equipment())));
			color = 0xFF000000 | element.rgb();
		} else if (element != null) {
			line = Text.translatable("gui.evotool.combiner.need_equipment");
			color = 0xFF000000 | element.rgb();
		} else {
			line = Text.translatable("gui.evotool.combiner.need_element");
			color = 0xFFA594C8;
		}
		context.getMatrices().pushMatrix();
		context.getMatrices().translate(8, 43);
		context.getMatrices().scale(0.75F, 0.75F);
		java.util.List<net.minecraft.text.OrderedText> wrapped = textRenderer.wrapLines(line, (int) ((backgroundWidth - 16) / 0.75F));
		for (int i = 0; i < Math.min(2, wrapped.size()); i++) {
			context.drawText(textRenderer, wrapped.get(i), 0, i * 9, color, false);
		}
		context.getMatrices().popMatrix();
	}

	private static int blend(int a, int b, float t) {
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}
}
