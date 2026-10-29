package fr.evotool;

import fr.evotool.block.ModBlocks;
import fr.evotool.boss.BellBossEntity;
import fr.evotool.component.EvoData;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.dimension.EvolvingBlock;
import fr.evotool.dimension.RubyPortal;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
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
	private static int trackedTicks;
	private static int bossTick = -1;
	private static BellBossEntity boss;

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				checks = run(server, FAILURES);
				// Objet géant : il doit rester dans le monde (bug corrigé : il était supprimé dès son apparition).
				ServerWorld world = server.getOverworld();
				world.setChunkForced(0, 0, true);
				giant = GiantItem.spawn(world, new ItemStack(ModItems.EVOLVING_BOW), ItemDisplayContext.FIXED,
						new Vec3d(8, 200, 8), 0.0F, new Quaternionf(), 2.0F, 4);
				checks++;
				if (GiantItem.isLeftover(giant)) {
					FAILURES.add("le nettoyage des sauvegardes supprimerait le géant dès son apparition");
				}
				checks++;
				DisplayEntity.ItemDisplayEntity stray = new DisplayEntity.ItemDisplayEntity(net.minecraft.entity.EntityType.ITEM_DISPLAY, world);
				stray.addCommandTag(GiantItem.TAG);
				if (!GiantItem.isLeftover(stray)) {
					FAILURES.add("un géant resté dans une sauvegarde ne serait pas nettoyé");
				}
				startTick = server.getTicks();
			} catch (Throwable t) {
				EvoTool.LOGGER.error("EVOTOOL SELFTEST CRASH", t);
				FAILURES.add("crash: " + t);
				finish(server);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (startTick < 0) {
				return;
			}
			int waited = server.getTicks() - startTick;
			if (giant.isRemoved()) {
				FAILURES.add("l'objet géant a disparu juste après être apparu (retiré : " + giant.getRemovalReason() + ")");
			} else if (server.getOverworld().getEntity(giant.getUuid()) != null) {
				// Le chunk est chargé : le géant doit y rester une seconde entière.
				if (++trackedTicks < 20) {
					return;
				}
				GiantItem.remove(server.getOverworld(), giant, 2.0F);
			} else if (waited < 600) {
				return;
			} else {
				EvoTool.LOGGER.info("EVOTOOL giant: chunk 0,0 never loaded, in-world check skipped");
			}
			EvoTool.LOGGER.info("EVOTOOL giant checked after {} ticks ({} ticks in a loaded chunk)", waited, trackedTicks);
			startTick = -1;
			checks++;
			// Après le géant : le scan génère beaucoup de chunks et décharge ceux du spawn.
			try {
				checks += checkOres(server, FAILURES);
				checks += checkDimension(server, FAILURES);
			} catch (Throwable t) {
				EvoTool.LOGGER.error("EVOTOOL SELFTEST CRASH", t);
				FAILURES.add("crash: " + t);
			}
			if (boss != null) {
				bossTick = server.getTicks();
			} else {
				finish(server);
			}
		});
		// Le boss doit construire son apparence (objets d'affichage) dans les secondes qui suivent son apparition.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (bossTick < 0) {
				return;
			}
			int parts = boss.getEntityWorld().getEntitiesByType(net.minecraft.entity.EntityType.ITEM_DISPLAY,
					boss.getBoundingBox().expand(4), e -> true).size();
			int waited = server.getTicks() - bossTick;
			if (parts < 5 && waited < 300) {
				return;
			}
			bossTick = -1;
			checks++;
			EvoTool.LOGGER.info("EVOTOOL boss: {} display parts after {} ticks, alive={}", parts, waited, boss.isAlive());
			if (!boss.isAlive() || parts < 5) {
				FAILURES.add("apparence du boss non construite (" + parts + " morceaux, vivant=" + boss.isAlive() + ")");
			}
			boss.discard();
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

	private static int checkOres(MinecraftServer server, List<String> failures) {
		int checks = 0;
		// Génération des minerais : on génère 64 chunks et on compte les minerais du mod sous terre.
		checks++;
		ServerWorld overworld = server.getOverworld();
		int evolium = 0;
		int ruby = 0;
		int sapphire = 0;
		int diamond = 0;
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
							} else if (state.isOf(net.minecraft.block.Blocks.DIAMOND_ORE) || state.isOf(net.minecraft.block.Blocks.DEEPSLATE_DIAMOND_ORE)) {
								diamond++;
							}
						}
					}
				}
			}
		}
		EvoTool.LOGGER.info("EVOTOOL ORES in 64 chunks: evolium={} diamond={} ruby={} sapphire={}", evolium, diamond, ruby, sapphire);
		if (evolium == 0 || ruby == 0 || sapphire == 0) {
			failures.add("minerais absents de la génération : évolium=" + evolium + " rubis=" + ruby + " saphir=" + sapphire);
		}
		// L'évolium doit rester plus rare que le diamant, mais pas introuvable.
		checks++;
		if (evolium >= diamond || evolium * 4 < diamond) {
			failures.add("rareté de l'évolium : " + evolium + " blocs pour " + diamond + " diamants (attendu entre 1/4 et 1 fois le diamant)");
		}
		return checks;
	}

	private static int checkDimension(MinecraftServer server, List<String> failures) {
		int checks = 1;
		ServerWorld evo = server.getWorld(EvoDimension.WORLD);
		if (evo == null) {
			failures.add("dimension évolutive absente");
			return checks;
		}
		// Relief de la dimension : fait de blocs évolutifs.
		checks++;
		int stone = 0;
		int grass = 0;
		int others = 0;
		for (int cx = 0; cx < 3; cx++) {
			for (int cz = 0; cz < 3; cz++) {
				net.minecraft.world.chunk.Chunk chunk = evo.getChunk(cx, cz);
				for (int x = 0; x < 16; x++) {
					for (int z = 0; z < 16; z++) {
						for (int y = -64; y < 240; y++) {
							net.minecraft.block.BlockState state = chunk.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z));
							if (state.isOf(ModBlocks.EVOLVING_STONE)) {
								stone++;
							} else if (state.isOf(ModBlocks.EVOLVING_GRASS)) {
								grass++;
							} else if (state.contains(EvolvingBlock.EVOLUTION)) {
								others++;
							}
						}
					}
				}
			}
		}
		EvoTool.LOGGER.info("EVOTOOL dimension in 9 chunks: stone={} grass={} other evolving blocks={}", stone, grass, others);
		if (stone == 0 || grass == 0) {
			failures.add("relief de la dimension évolutive : pierre=" + stone + " herbe=" + grass);
		}

		// Portail : un cadre de rubis 4x5 s'allume au briquet, un cadre incomplet non.
		ServerWorld overworld = server.getOverworld();
		overworld.getChunk(2000 >> 4, 2000 >> 4);
		int surface = overworld.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 2000, 2000) + 2;
		BlockPos base = new BlockPos(2000, surface, 2000);
		BlockPos broken = base.add(10, 0, 0);
		for (BlockPos b : new BlockPos[] {base, broken}) {
			for (int dx = -2; dx <= 3; dx++) {
				for (int dy = -1; dy <= 4; dy++) {
					overworld.setBlockState(b.add(dx, dy, 0), net.minecraft.block.Blocks.AIR.getDefaultState());
				}
			}
			for (int dx = -1; dx <= 2; dx++) {
				overworld.setBlockState(b.add(dx, -1, 0), ModBlocks.RUBY_BLOCK.getDefaultState());
				overworld.setBlockState(b.add(dx, 3, 0), ModBlocks.RUBY_BLOCK.getDefaultState());
			}
			for (int dy = 0; dy < 3; dy++) {
				overworld.setBlockState(b.add(-1, dy, 0), ModBlocks.RUBY_BLOCK.getDefaultState());
				overworld.setBlockState(b.add(2, dy, 0), ModBlocks.RUBY_BLOCK.getDefaultState());
			}
		}
		overworld.setBlockState(broken.add(1, 3, 0), net.minecraft.block.Blocks.AIR.getDefaultState());
		checks++;
		boolean lit = RubyPortal.tryLight(overworld, base.up(), true);
		int portals = 0;
		for (int dx = 0; dx < 2; dx++) {
			for (int dy = 0; dy < 3; dy++) {
				portals += overworld.getBlockState(base.add(dx, dy, 0)).isOf(ModBlocks.EVOLUTION_PORTAL) ? 1 : 0;
			}
		}
		if (!lit || portals != 6) {
			failures.add("portail de rubis : allumé=" + lit + ", " + portals + " blocs de portail au lieu de 6");
		}
		checks++;
		if (RubyPortal.tryLight(overworld, broken.up(), false)) {
			failures.add("un cadre de rubis incomplet s'allume quand même");
		}

		// Arrivée dans la dimension (portail trouvé ou construit), puis retour au portail de départ.
		checks++;
		BlockPos arrival = RubyPortal.findOrBuild(evo, base);
		if (!evo.getBlockState(arrival).isOf(ModBlocks.EVOLUTION_PORTAL)) {
			failures.add("pas de portail à l'arrivée dans la dimension (" + arrival + ")");
		}
		checks++;
		BlockPos back = RubyPortal.findOrBuild(overworld, arrival);
		if (back.getSquaredDistance(base) > 4) {
			failures.add("le retour ne retrouve pas le portail de départ : " + back + " au lieu de " + base);
		}

		// Un bloc évolutif gagne une évolution à la fois jusqu'à la phase du joueur.
		checks++;
		BlockPos test = new BlockPos(arrival.getX(), evo.getTopYInclusive() - 3, arrival.getZ());
		evo.setBlockState(test, ModBlocks.EVOLVING_STONE.getDefaultState());
		EvoDimension.evolveToward(evo, test, evo.getBlockState(test), 3);
		EvoDimension.evolveToward(evo, test, evo.getBlockState(test), 3);
		EvoDimension.evolveToward(evo, test, evo.getBlockState(test), 3);
		EvoDimension.evolveToward(evo, test, evo.getBlockState(test), 3);
		int evolution = evo.getBlockState(test).get(EvolvingBlock.EVOLUTION);
		if (evolution != 3) {
			failures.add("bloc évolutif : évolution " + evolution + " au lieu de 3");
		}

		// Boss : chaque forme est plus grande et plus solide que la précédente.
		evo.setChunkForced(arrival.getX() >> 4, arrival.getZ() >> 4, true);
		boss = EvoDimension.spawnBoss(evo, arrival.add(1, 0, 2), 0);
		checks++;
		if (boss == null) {
			failures.add("le boss n'apparaît pas");
			return checks;
		}
		float height = 0;
		float health = 0;
		for (int form = 0; form < BellBossEntity.FORMS; form++) {
			checks++;
			boss.applyForm(form, true);
			if (boss.getHeight() <= height || boss.getMaxHealth() <= health || boss.getForm() != form) {
				failures.add("forme " + form + " du boss : hauteur " + boss.getHeight() + ", vie " + boss.getMaxHealth());
			}
			height = boss.getHeight();
			health = boss.getMaxHealth();
		}
		boss.applyForm(3, true);
		return checks;
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
