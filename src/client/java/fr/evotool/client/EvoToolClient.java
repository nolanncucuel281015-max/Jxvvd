package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import fr.evotool.boss.ModEntities;
import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import fr.evotool.upgrade.EvoliteUpgrade;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.entity.EmptyEntityRenderer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class EvoToolClient implements ClientModInitializer {
	public static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(EvoTool.id("main"));

	public static final KeyBinding OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.evotool.open_menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY));

	@Override
	public void onInitializeClient() {
		// Le boss est dessiné par ses objets d'affichage animés (côté serveur) : l'entité elle-même est invisible.
		EntityRendererRegistry.register(ModEntities.BELL_BOSS, EmptyEntityRenderer::new);
		BlockRenderLayerMap.putBlock(ModBlocks.EVOLUTION_PORTAL, BlockRenderLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(ModBlocks.EVOLVING_LEAVES, BlockRenderLayer.CUTOUT);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (OPEN_MENU.wasPressed()) {
				if (client.player == null) {
					continue;
				}
				if (!EvoScreen.findStack(client.player).isEmpty()) {
					client.setScreen(new EvoScreen());
				} else {
					client.player.sendMessage(Text.translatable("message.evotool.hold_tool").formatted(Formatting.RED), true);
				}
			}
		});

		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
			EvoKind kind = EvoProgression.kindOf(stack);
			if (kind == null) {
				return;
			}
			EvoProfile profile = kind.profile();
			EvoData data = EvoProgression.get(stack);
			int level = data.level();
			int index = 1;
			lines.add(index++, Text.translatable("tooltip.evotool.level", level, EvoProgression.MAX_LEVEL)
					.formatted(Formatting.LIGHT_PURPLE));
			lines.add(index++, Text.translatable("tooltip.evotool.form", EvoProgression.stageName(level),
					EvoProgression.stage(level), EvoProgression.MAX_STAGE).formatted(Formatting.YELLOW));
			if (level < EvoProgression.MAX_LEVEL) {
				lines.add(index++, Text.translatable("tooltip.evotool.xp", data.xp(), profile.curve().xpToNext(level))
						.formatted(Formatting.GRAY));
			}
			if (profile.modes().size() > 1) {
				lines.add(index++, Text.translatable("tooltip.evotool.mode",
						Text.translatable(EvoProgression.selectedMode(profile, data).translationKey())).formatted(Formatting.AQUA));
			}
			for (Power power : profile.powers().keySet()) {
				if (power.toggleable() && profile.hasPower(power, level)) {
					boolean on = EvoProgression.isActive(profile, data, power);
					lines.add(index++, Text.translatable(on ? "tooltip.evotool.power_on" : "tooltip.evotool.power_off",
							Text.translatable(power.translationKey())).formatted(on ? Formatting.GOLD : Formatting.DARK_GRAY));
				}
			}
			if (EvoliteUpgrade.isUpgraded(stack)) {
				EvoliteUpgrade.Upgrade upgrade = EvoliteUpgrade.upgradeFor(kind);
				lines.add(index++, Text.translatable("tooltip.evotool.upgraded", Text.translatable(upgrade.translationKey()))
						.formatted(Formatting.AQUA, Formatting.BOLD));
				lines.add(index++, Text.translatable(upgrade.descriptionKey()).formatted(Formatting.DARK_AQUA));
			}
			lines.add(index++, Text.translatable(profile.counter().translationKey(), data.count()).formatted(Formatting.DARK_GRAY));
			if (kind == EvoKind.SWORD) {
				lines.add(index++, Text.translatable("tooltip.evotool.sword_hint").formatted(Formatting.RED));
			}
			if (kind == EvoKind.PICKAXE) {
				lines.add(index++, Text.translatable("tooltip.evotool.pickaxe_hint").formatted(Formatting.GOLD));
			}
			if (kind == EvoKind.BOW) {
				lines.add(index++, Text.translatable("tooltip.evotool.bow_hint").formatted(Formatting.LIGHT_PURPLE));
			}
			if (kind == EvoKind.AXE) {
				lines.add(index++, Text.translatable("tooltip.evotool.axe_hint").formatted(Formatting.GREEN));
			}
			lines.add(index, Text.translatable(profile.isArmor() ? "tooltip.evotool.armor_hint" : "tooltip.evotool.hint")
					.formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
		});
	}
}
