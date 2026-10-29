package fr.evotool;

import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import fr.evotool.mixin.ForgingScreenHandlerAccessor;
import fr.evotool.upgrade.EvoliteUpgrade;
import fr.evotool.upgrade.GiantItem;
import fr.evotool.upgrade.PickaxeSlam;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.joml.Quaternionf;

/**
 * Auto-test lancé par la CI (-Devotool.selftest=true) sur un vrai serveur : vérifie que le mod se charge
 * et que chaque objet fonctionne à chaque niveau, puis arrête le serveur.
 */
final class SelfTest {
	private SelfTest() {
	}

	private static final List<String> FAILURES = new ArrayList<>();
	private static int checks;
	private static int startTick = -1;
	private static DisplayEntity.ItemDisplayEntity giant;

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				checks = run(server, FAILURES);
				// Objet géant : il doit rester dans le monde (bug corrigé : il était supprimé dès son apparition).
				ServerWorld world = server.getOverworld();
				world.setChunkForced(0, 0, true);
				giant = GiantItem.spawn(world, new ItemStack(ModItems.EVOLVING_BOW), ItemDisplayContext.FIXED,
						new Vec3d(8, 200, 8), 0.0F, new Quaternionf(), 2.0F, 4);
				startTick = server.getTicks();
			} catch (Throwable t) {
				EvoTool.LOGGER.error("EVOTOOL SELFTEST CRASH", t);
				FAILURES.add("crash: " + t);
				finish(server);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (startTick < 0 || server.getTicks() - startTick < 20) {
				return;
			}
			startTick = -1;
			checks++;
			if (giant == null || giant.isRemoved() || server.getOverworld().getEntity(giant.getUuid()) == null) {
				FAILURES.add("l'objet géant a disparu juste après être apparu");
			} else {
				GiantItem.remove(server.getOverworld(), giant, 2.0F);
			}
			finish(server);
		});
	}

	private static void finish(MinecraftServer server) {
		if (FAILURES.isEmpty()) {
			EvoTool.LOGGER.info("EVOTOOL SELFTEST PASSED ({} checks)", checks);
		} else {
			FAILURES.forEach(f -> EvoTool.LOGGER.error("EVOTOOL SELFTEST FAILURE: {}", f));
			EvoTool.LOGGER.error("EVOTOOL SELFTEST FAILED ({} failures / {} checks)", FAILURES.size(), checks);
		}
		server.stop(false);
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

		// Tranchée du slash de la pioche : 100 x 7 colonnes, quelle que soit la direction (sans trous en diagonale).
		for (float yaw : new float[] {0.0F, 37.0F, 45.0F, 90.0F, 200.0F}) {
			checks++;
			Vec3d forward = PickaxeSlam.horizontal(yaw);
			Vec3d right = new Vec3d(-forward.z, 0, forward.x);
			int size = PickaxeSlam.trench(net.minecraft.util.math.BlockPos.ORIGIN, forward, right, 2, 2 + PickaxeSlam.LENGTH).size();
			int expected = PickaxeSlam.LENGTH * PickaxeSlam.WIDTH;
			if (size < expected || size > expected * 1.6) {
				failures.add("tranchée de la pioche (yaw " + yaw + ") : " + size + " colonnes au lieu d'environ " + expected);
			}
		}

		// Génération des minerais : on génère 64 chunks et on compte les minerais du mod sous terre.
		checks++;
		ServerWorld overworld = server.getOverworld();
		int evolium = 0;
		int ruby = 0;
		int sapphire = 0;
		for (int cx = 40; cx < 48; cx++) {
			for (int cz = 40; cz < 48; cz++) {
				net.minecraft.world.chunk.Chunk chunk = overworld.getChunk(cx, cz);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = -64; y < 64; y++) {
							net.minecraft.block.BlockState state = chunk.getBlockState(new net.minecraft.util.math.BlockPos(cx * 16 + x, y, cz * 16 + z));
							if (state.isOf(fr.evotool.block.ModBlocks.DEEPSLATE_EVOLIUM_ORE)) {
								evolium++;
							} else if (state.isOf(fr.evotool.block.ModBlocks.RUBY_ORE) || state.isOf(fr.evotool.block.ModBlocks.DEEPSLATE_RUBY_ORE)) {
								ruby++;
							} else if (state.isOf(fr.evotool.block.ModBlocks.SAPPHIRE_ORE) || state.isOf(fr.evotool.block.ModBlocks.DEEPSLATE_SAPPHIRE_ORE)) {
								sapphire++;
							}
						}
					}
				}
			}
		}
		EvoTool.LOGGER.info("EVOTOOL ORES in 64 chunks: evolium={} ruby={} sapphire={}", evolium, ruby, sapphire);
		if (evolium == 0 || ruby == 0 || sapphire == 0) {
			failures.add("minerais absents de la génération : évolium=" + evolium + " rubis=" + ruby + " saphir=" + sapphire);
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
