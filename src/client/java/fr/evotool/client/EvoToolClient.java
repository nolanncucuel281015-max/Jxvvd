package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import fr.evotool.boss.ModEntities;
import fr.evotool.component.EvoData;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProfile;
import fr.evotool.evo.EvoProgression;
import fr.evotool.evo.Power;
import fr.evotool.item.MagicWeaponItem;
import fr.evotool.item.ModdedWeaponItem;
import fr.evotool.weapon.ArcaneWeapons;
import fr.evotool.weapon.MagicKind;
import fr.evotool.weapon.WeaponKind;
import fr.evotool.weapon.WeaponPowers;
import fr.evotool.component.ModComponents;
import fr.evotool.network.BurrowPayload;
import fr.evotool.shield.ShieldGeneratorBlockEntity;
import fr.evotool.shield.ShieldGeneratorScreenHandler;
import fr.evotool.network.EvolvePayload;
import fr.evotool.network.EvolvedPayload;
import fr.evotool.network.TentacleTogglePayload;
import fr.evotool.upgrade.Burrow;
import fr.evotool.upgrade.EvoliteUpgrade;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
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

	/** Allume / éteint le mode déplacement des tentacules (armure abyssale niveau 150). */
	public static final KeyBinding TENTACLES = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.evotool.tentacles", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY));

	/** Fait évoluer l'objet prêt (en main, ou l'armure portée) : le serveur joue l'animation. */
	public static final KeyBinding EVOLVE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
			"key.evotool.evolve", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY));

	@Override
	public void onInitializeClient() {
		// Le boss est dessiné par ses objets d'affichage animés (côté serveur) : l'entité elle-même est invisible.
		EntityRendererRegistry.register(ModEntities.BELL_BOSS, EmptyEntityRenderer::new);
		BlockRenderLayerMap.putBlock(ModBlocks.EVOLUTION_PORTAL, BlockRenderLayer.TRANSLUCENT);
		BlockRenderLayerMap.putBlock(ModBlocks.EVOLVING_LEAVES, BlockRenderLayer.CUTOUT);
		// Plongée souterraine des bottes : le serveur dit quand notre joueur traverse les blocs.
		ClientPlayNetworking.registerGlobalReceiver(BurrowPayload.ID, (payload, context) -> Burrow.clientActive = payload.active());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Burrow.clientActive = false);
		// Ailes de l'infini du plastron : dessinées sur le buste du joueur, comme une cape.
		InfinityWingsFeature.register();
		// Tentacules noires de l'armure abyssale niveau 150.
		TentacleFeature.register();
		// … et, en mode déplacement, celles qui s'accrochent devant nous et nous tirent (+ clic droit : attraper).
		TentacleTravel.register();
		// Générateur de bouclier : son menu.
		HandledScreens.register(ShieldGeneratorScreenHandler.TYPE, ShieldGeneratorScreen::new);
		// Combineur élémentaire : son menu, et les pouvoirs qui dépendent des déplacements (glissement, double saut).
		HandledScreens.register(fr.evotool.combiner.ElementalCombinerScreenHandler.TYPE, ElementalCombinerScreen::new);
		ElementalClient.register();
		// Évolution : la nouvelle forme jaillit à l'écran, comme un totem d'immortalité.
		ClientPlayNetworking.registerGlobalReceiver(EvolvedPayload.ID,
				(payload, context) -> context.client().gameRenderer.showFloatingItem(payload.stack()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (TENTACLES.wasPressed()) {
				if (client.player != null) {
					ClientPlayNetworking.send(TentacleTogglePayload.INSTANCE);
				}
			}
			while (EVOLVE.wasPressed()) {
				if (client.player != null) {
					ClientPlayNetworking.send(EvolvePayload.INSTANCE);
				}
			}
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
			// l'élément donné par le combineur, et ses pouvoirs sur cet objet
			fr.evotool.element.Element element = fr.evotool.element.Elements.get(stack);
			if (element != null) {
				int at = Math.min(1, lines.size());
				lines.add(at++, Text.translatable("tooltip.evotool.element", element.symbol(), Text.translatable(element.translationKey()))
						.formatted(element.color(), Formatting.BOLD));
				net.minecraft.entity.EquipmentSlot slot = fr.evotool.element.Elements.armorSlot(stack);
				if (slot == null) {
					lines.add(at, Text.translatable("tooltip.evotool.element.weapon." + element.id()).formatted(element.color()));
				} else {
					lines.add(at++, Text.translatable("tooltip.evotool.element.armor." + element.id()).formatted(element.color()));
					String special = fr.evotool.element.Elements.specialKey(element, slot);
					if (special != null) {
						lines.add(at, Text.translatable(special).formatted(element.color(), Formatting.ITALIC));
					}
				}
			}
			// grimoire élémentaire : ses sorts, le choisi en tête de flèche
			fr.evotool.element.Grimoire grimoire = stack.get(ModComponents.GRIMOIRE);
			if (grimoire != null) {
				int at = Math.min(1, lines.size());
				fr.evotool.element.Spell selected = grimoire.selectedSpell();
				for (fr.evotool.element.Spell spell : grimoire.spells()) {
					boolean chosen = spell == selected;
					net.minecraft.text.MutableText line = Text.literal((chosen ? "▶ " : "   ") + spell.element().symbol() + " ")
							.append(Text.translatable(spell.translationKey()))
							.append(Text.translatable(spell.kind().translationKey()))
							.formatted(spell.element().color());
					lines.add(at++, chosen ? line.formatted(Formatting.BOLD) : line);
				}
				lines.add(at++, Text.translatable("tooltip.evotool.grimoire.cast").formatted(Formatting.GRAY));
				lines.add(at++, Text.translatable("tooltip.evotool.grimoire.switch").formatted(Formatting.GRAY));
				lines.add(at, Text.translatable("tooltip.evotool.grimoire.more").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
				return;
			}
			if (stack.isOf(ModBlocks.ELEMENTAL_COMBINER.asItem())) {
				lines.add(Text.translatable("tooltip.evotool.combiner.hint").formatted(Formatting.LIGHT_PURPLE));
				return;
			}
			if (stack.isOf(ModBlocks.SHIELD_GENERATOR.asItem())) {
				lines.add(Text.translatable("tooltip.evotool.shield.fuel",
						java.text.NumberFormat.getIntegerInstance(java.util.Locale.FRANCE)
								.format(stack.getOrDefault(ModComponents.SHIELD_FUEL, 0L))).formatted(Formatting.AQUA));
				lines.add(Text.translatable("tooltip.evotool.shield.radius",
						stack.getOrDefault(ModComponents.SHIELD_RADIUS, ShieldGeneratorBlockEntity.DEFAULT_RADIUS)).formatted(Formatting.AQUA));
				lines.add(Text.translatable("tooltip.evotool.shield.hint").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
				return;
			}
			EvoKind kind = EvoProgression.kindOf(stack);
			if (kind == null) {
				return;
			}
			// armes moddées et magiques : pouvoirs propres, puis les lignes d'évolution communes
			int index = 1;
			if (stack.getItem() instanceof ModdedWeaponItem weapon) {
				WeaponKind w = weapon.weaponKind();
				lines.add(index++, Text.translatable("tooltip.evotool.weapon.passive", Text.translatable(w.passiveKey()))
						.formatted(Formatting.GOLD));
				lines.add(index++, Text.translatable("tooltip.evotool.weapon.ability", Text.translatable(w.abilityKey()),
						String.format(java.util.Locale.ROOT, "%.1f", WeaponPowers.cooldown(w, stack) / 20.0)).formatted(Formatting.LIGHT_PURPLE));
			}
			if (stack.getItem() instanceof MagicWeaponItem magic) {
				MagicKind m = magic.magicKind();
				lines.add(index++, Text.translatable("tooltip.evotool.magic.charges", ArcaneWeapons.charges(stack, m),
						ArcaneWeapons.maxCharges(stack, m),
						String.format(java.util.Locale.ROOT, "%.1f", ArcaneWeapons.rechargeTicks(stack, m) / 20.0))
						.formatted(Formatting.AQUA));
				lines.add(index++, Text.translatable("tooltip.evotool.magic.ability", Text.translatable(m.abilityKey()))
						.formatted(Formatting.LIGHT_PURPLE));
				lines.add(index++, Text.translatable("tooltip.evotool.magic.sneak", Text.translatable(m.sneakKey()))
						.formatted(Formatting.DARK_PURPLE));
			}
			EvoProfile profile = kind.profile();
			EvoData data = EvoProgression.get(stack);
			int level = data.level();
			lines.add(index++, Text.translatable("tooltip.evotool.level", level, EvoProgression.maxLevel(stack))
					.formatted(Formatting.LIGHT_PURPLE));
			lines.add(index++, Text.translatable("tooltip.evotool.form", EvoProgression.stageName(stack),
					EvoProgression.stage(stack), EvoProgression.maxStage(stack)).formatted(Formatting.YELLOW));
			if (EvoProgression.isAbyssal(stack)) {
				lines.add(index++, Text.translatable("tooltip.evotool.abyssal").formatted(Formatting.DARK_PURPLE, Formatting.BOLD));
			}
			if (EvoProgression.isReadyToEvolve(stack)) {
				lines.add(index++, Text.translatable("tooltip.evotool.ready", EvoProgression.evolveKey())
						.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
			} else if (level < EvoProgression.maxLevel(stack)) {
				lines.add(index++, Text.translatable("tooltip.evotool.xp", data.xp(), EvoProgression.xpToNext(stack))
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
