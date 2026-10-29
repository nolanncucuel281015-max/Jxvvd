package fr.evotool.client;

import fr.evotool.armor.ArmorSet;
import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import fr.evotool.network.EvoSettingsPayload;
import fr.evotool.upgrade.EvoliteUpgrade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Menu ouvert avec la touche Y pour n'importe quel outil évolutif :
 * progression, choix du mode de zone, pouvoirs à activer / couper et bonus actifs.
 */
public class EvoScreen extends Screen {
	private static final int BUTTON_WIDTH = 170;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_STEP = 23;
	private static final int PANEL_WIDTH = 250;
	private static final int HEADER_HEIGHT = 70;
	private static final int LINE_HEIGHT = 11;

	private EvoProfile profile;
	/** Pouvoir d'amélioration à l'évolite, ou null si l'objet n'est pas amélioré. */
	private EvoliteUpgrade.Upgrade upgrade;
	private EvoData data = EvoData.DEFAULT;
	private Text itemName = Text.empty();
	private int selectedMode;
	private int flags;
	private int panelTop;
	private int panelBottom;
	private int bonusTop;

	public EvoScreen() {
		super(Text.translatable("screen.evotool.title"));
	}

	@Override
	protected void init() {
		ItemStack stack = client == null || client.player == null ? ItemStack.EMPTY : findStack(client.player);
		EvoKind kind = EvoProgression.kindOf(stack);
		if (kind == null) {
			close();
			return;
		}
		boolean firstInit = profile == null;
		profile = kind.profile();
		itemName = stack.getName();
		upgrade = EvoliteUpgrade.isUpgraded(stack) ? EvoliteUpgrade.upgradeFor(kind) : null;
		if (firstInit) {
			data = EvoProgression.get(stack);
			selectedMode = EvoProgression.selectedMode(profile, data).ordinal();
			flags = data.flags();
		}
		// Ensuite on garde les choix locaux : la synchro du serveur peut arriver un peu après.
		data = EvoProgression.get(stack).withSettings(selectedMode, flags);
		int level = data.level();

		List<Power> toggles = profile.powers().keySet().stream().filter(Power::toggleable).toList();
		int modeCount = profile.modes().size() > 1 ? profile.modes().size() : 0;
		int buttons = modeCount + toggles.size();
		int bonusLines = Math.max(1, activeBonuses(level).size());
		int total = HEADER_HEIGHT + buttons * BUTTON_STEP + (modeCount > 0 && !toggles.isEmpty() ? 6 : 0)
				+ 8 + bonusLines * LINE_HEIGHT + 8;
		panelTop = Math.max(4, (height - total) / 2);
		panelBottom = panelTop + total;

		int x = (width - BUTTON_WIDTH) / 2;
		int y = panelTop + HEADER_HEIGHT;
		if (modeCount > 0) {
			for (EvoProfile.ModeUnlock unlock : profile.modes()) {
				boolean unlocked = level >= unlock.level();
				boolean selected = unlock.mode().ordinal() == selectedMode;
				Text name = Text.translatable(unlock.mode().translationKey());
				Text label = !unlocked
						? Text.translatable("screen.evotool.locked", name, unlock.level()).formatted(Formatting.DARK_GRAY)
						: selected ? Text.literal("» ").append(name).append(" «").formatted(Formatting.GREEN) : name;
				ButtonWidget button = ButtonWidget.builder(label, b -> {
					selectedMode = unlock.mode().ordinal();
					send();
				}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
				button.active = unlocked && !selected;
				addDrawableChild(button);
				y += BUTTON_STEP;
			}
			if (!toggles.isEmpty()) {
				y += 6;
			}
		}

		for (Power power : toggles) {
			int required = profile.powers().get(power);
			boolean unlocked = level >= required;
			boolean on = (flags & power.bit()) != 0;
			Text name = Text.translatable(power.translationKey());
			Text label = !unlocked
					? Text.translatable("screen.evotool.locked", name, required).formatted(Formatting.DARK_GRAY)
					: Text.translatable(on ? "screen.evotool.toggle_on" : "screen.evotool.toggle_off", name);
			ButtonWidget button = ButtonWidget.builder(label, b -> {
				flags ^= power.bit();
				send();
			}).dimensions(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build();
			button.active = unlocked;
			addDrawableChild(button);
			y += BUTTON_STEP;
		}
		bonusTop = y + 8;
	}

	/** L'outil évolutif en main, sinon la première pièce d'armure évolutive portée. */
	public static ItemStack findStack(PlayerEntity player) {
		if (EvoProgression.kindOf(player.getMainHandStack()) != null) {
			return player.getMainHandStack();
		}
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			if (ArmorSet.isEvolvingArmor(player.getEquippedStack(slot), slot)) {
				return player.getEquippedStack(slot);
			}
		}
		return ItemStack.EMPTY;
	}

	private void send() {
		ClientPlayNetworking.send(new EvoSettingsPayload(selectedMode, flags));
		clearAndInit();
	}

	/** Enchantements gagnés et pouvoirs permanents déjà débloqués. */
	private List<Text> activeBonuses(int level) {
		List<Text> bonuses = new ArrayList<>();
		if (upgrade != null) {
			bonuses.add(Text.translatable("tooltip.evotool.upgraded", Text.translatable(upgrade.translationKey()))
					.formatted(Formatting.AQUA));
		}
		if (profile.isArmor() && client != null && client.player != null) {
			// Les bonus de l'armure dépendent de tout l'ensemble porté.
			int setLevel = ArmorSet.setLevel(client.player);
			bonuses.add(Text.translatable("armor.evotool.set_level", setLevel).formatted(Formatting.AQUA));
			bonuses.addAll(ArmorSet.describe(setLevel));
			return bonuses;
		}
		if (profile.damageBonus()) {
			bonuses.add(Text.translatable("screen.evotool.damage_bonus", String.format("%.1f", EvoProgression.damageBonus(level))));
		}
		for (EvoProfile.EnchantStep step : profile.enchants()) {
			int enchantLevel = step.levelAt(level);
			if (enchantLevel > 0) {
				bonuses.add(EvoProgression.enchantText(step, enchantLevel));
			}
		}
		for (Map.Entry<Power, Integer> entry : profile.powers().entrySet()) {
			if (!entry.getKey().toggleable() && level >= entry.getValue()) {
				bonuses.add(Text.translatable(entry.getKey().translationKey()));
			}
		}
		return bonuses;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		if (profile == null) {
			return;
		}
		int level = data.level();
		int left = (width - PANEL_WIDTH) / 2;
		context.fill(left, panelTop, left + PANEL_WIDTH, panelBottom, 0xC0101018);
		context.fill(left, panelTop, left + PANEL_WIDTH, panelTop + 1, 0xFFB15CFF);
		context.fill(left, panelBottom - 1, left + PANEL_WIDTH, panelBottom, 0xFFB15CFF);

		super.render(context, mouseX, mouseY, delta);

		int center = width / 2;
		context.drawCenteredTextWithShadow(textRenderer, itemName, center, panelTop + 8, 0xFFE0B0FF);
		context.drawCenteredTextWithShadow(textRenderer,
				Text.translatable("screen.evotool.level_form", level, EvoProgression.MAX_LEVEL, EvoProgression.stageName(level)),
				center, panelTop + 22, 0xFFFFFFFF);

		int barLeft = left + 20;
		int barRight = left + PANEL_WIDTH - 20;
		int barTop = panelTop + 36;
		context.fill(barLeft, barTop, barRight, barTop + 6, 0xFF2A2A3A);
		int needed = profile.curve().xpToNext(level);
		float progress = needed == 0 ? 1.0F : Math.min(1.0F, data.xp() / (float) needed);
		context.fill(barLeft, barTop, barLeft + (int) ((barRight - barLeft) * progress), barTop + 6, 0xFF7CF0FF);
		Text xpText = needed == 0
				? Text.translatable("screen.evotool.max")
				: Text.translatable("screen.evotool.xp", data.xp(), needed);
		context.drawCenteredTextWithShadow(textRenderer, xpText, center, barTop + 9, 0xFFA0A0B0);
		context.drawCenteredTextWithShadow(textRenderer, Text.translatable(profile.counter().translationKey(), data.count()),
				center, barTop + 20, 0xFF8A8A9A);

		List<Text> bonuses = activeBonuses(level);
		if (bonuses.isEmpty()) {
			bonuses = List.of(Text.translatable("screen.evotool.no_bonus").formatted(Formatting.GRAY));
		}
		int y = bonusTop;
		for (Text bonus : bonuses) {
			context.drawCenteredTextWithShadow(textRenderer, bonus, center, y, 0xFFFFD866);
			y += LINE_HEIGHT;
		}
	}

	@Override
	public boolean shouldPause() {
		return false;
	}
}
