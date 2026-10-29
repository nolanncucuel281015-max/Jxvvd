package fr.evotool;

import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import fr.evotool.mixin.ForgingScreenHandlerAccessor;
import fr.evotool.upgrade.EvoliteUpgrade;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.gen.feature.PlacedFeature;

/**
 * Auto-test lancé par la CI (-Devotool.selftest=true) sur un vrai serveur : vérifie que le mod se charge
 * et que chaque objet fonctionne à chaque niveau, puis arrête le serveur.
 */
final class SelfTest {
	private SelfTest() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			List<String> failures = new ArrayList<>();
			int checks = 0;
			try {
				checks = run(server, failures);
			} catch (Throwable t) {
				EvoTool.LOGGER.error("EVOTOOL SELFTEST CRASH", t);
				failures.add("crash: " + t);
			}
			if (failures.isEmpty()) {
				EvoTool.LOGGER.info("EVOTOOL SELFTEST PASSED ({} checks)", checks);
			} else {
				failures.forEach(f -> EvoTool.LOGGER.error("EVOTOOL SELFTEST FAILURE: {}", f));
				EvoTool.LOGGER.error("EVOTOOL SELFTEST FAILED ({} failures / {} checks)", failures.size(), checks);
			}
			server.stop(false);
		});
	}

	private static int run(MinecraftServer server, List<String> failures) {
		int checks = 0;

		// Mixins appliqués (charger la classe applique les mixins ; une injection ratée plante ici).
		checks++;
		if (!ForgingScreenHandlerAccessor.class.isAssignableFrom(AnvilScreenHandler.class)) {
			failures.add("mixin d'enclume non appliqué");
		}

		checks++;
		boolean hungerMixin = false;
		for (java.lang.reflect.Method method : net.minecraft.entity.player.PlayerEntity.class.getDeclaredMethods()) {
			hungerMixin |= method.getName().contains("noHungerWithSpear");
		}
		if (!hungerMixin) {
			failures.add("mixin anti-faim de la lance non appliqué");
		}

		// L'Élan vanilla d'une ancienne lance est remplacé par l'Élan évolutif.
		checks++;
		ItemStack oldSpear = new ItemStack(ModItems.EVOLVING_SPEAR);
		var enchantments = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
		net.minecraft.enchantment.EnchantmentHelper.apply(oldSpear,
				builder -> builder.set(enchantments.getOrThrow(net.minecraft.enchantment.Enchantments.LUNGE), 3));
		EvoProgression.refresh(oldSpear, server.getRegistryManager());
		if (net.minecraft.enchantment.EnchantmentHelper.getLevel(enchantments.getOrThrow(net.minecraft.enchantment.Enchantments.LUNGE), oldSpear) != 0
				|| net.minecraft.enchantment.EnchantmentHelper.getLevel(enchantments.getOrThrow(fr.evotool.evo.ModEnchantments.EVOLVING_LUNGE), oldSpear) != 3) {
			failures.add("Élan vanilla non converti sur la lance");
		}

		// Génération des minerais.
		for (String ore : new String[] {"ore_evolium", "ore_ruby", "ore_sapphire", "ore_evolite"}) {
			checks++;
			RegistryKey<PlacedFeature> key = RegistryKey.of(RegistryKeys.PLACED_FEATURE, EvoTool.id(ore));
			if (server.getRegistryManager().getOrThrow(RegistryKeys.PLACED_FEATURE).getOptional(key).isEmpty()) {
				failures.add("placed_feature manquante : " + ore);
			}
		}

		// Chaque objet, à chaque forme, puis amélioré.
		for (EvoKind kind : EvoKind.values()) {
			for (int level = 0; level <= EvoProgression.MAX_LEVEL; level += 5) {
				checks++;
				ItemStack stack = new ItemStack(ModItems.itemFor(kind));
				stack.set(ModComponents.EVO_DATA, new EvoData(level, 0, 0, -1, 0L));
				EvoProgression.refresh(stack, server.getRegistryManager(), true);
				CustomModelDataComponent model = stack.get(DataComponentTypes.CUSTOM_MODEL_DATA);
				float expected = EvoProgression.stage(level);
				if (model == null || model.floats().isEmpty() || model.floats().get(0) != expected) {
					failures.add(kind + " niv. " + level + " : mauvaise forme " + model);
				}
				EquipmentSlot slot = kind.profile().armorSlot();
				if (slot != null && slot != EquipmentSlot.HEAD && stack.get(DataComponentTypes.EQUIPPABLE) == null) {
					failures.add(kind + " niv. " + level + " : pas de composant équipable");
				}
				// XP : une montée de niveau ne doit pas planter.
				EvoProgression.gainXp(stack, 1_000_000, 1, server.getOverworld());
			}
			checks++;
			ItemStack upgraded = new ItemStack(ModItems.itemFor(kind));
			EvoliteUpgrade.apply(upgraded);
			if (!EvoliteUpgrade.has(upgraded, EvoliteUpgrade.upgradeFor(kind))) {
				failures.add(kind + " : amélioration à l'évolite non appliquée");
			}
		}
		return checks;
	}
}
