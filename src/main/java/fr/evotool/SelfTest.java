package fr.evotool;

import fr.evotool.block.ModBlocks;
import fr.evotool.boss.BellBossEntity;
import fr.evotool.component.EvoData;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.dimension.EvolvingBlock;
import fr.evotool.dimension.RubyPortal;
import fr.evotool.dimension.Sanctuary;
import fr.evotool.dimension.SanctuaryAltarBlock;
import fr.evotool.command.DimensionCommand;
import fr.evotool.item.MagicWeaponItem;
import fr.evotool.weapon.ArcaneWeapons;
import fr.evotool.weapon.MagicKind;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import fr.evotool.item.ModdedWeaponItem;
import fr.evotool.mixin.ForgingScreenHandlerAccessor;
import fr.evotool.upgrade.EvoliteUpgrade;
import fr.evotool.upgrade.Burrow;
import fr.evotool.upgrade.GiantItem;
import fr.evotool.upgrade.InfinityWings;
import fr.evotool.upgrade.PickaxeSlam;
import fr.evotool.weapon.WeaponKind;
import fr.evotool.weapon.WeaponPowers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
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
	private static BlockPos altarPos;

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			try {
				checks = run(server, FAILURES);
				prepareArena(server);
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
				checks += checkWeapons(server, FAILURES);
				checks += checkArmorPowers(server, FAILURES);
				checks += checkElements(server, FAILURES);
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
			if (parts < 5 && waited < 600) {
				return;
			}
			bossTick = -1;
			checks++;
			EvoTool.LOGGER.info("EVOTOOL boss: {} display parts after {} ticks, alive={}", parts, waited, boss.isAlive());
			if (!boss.isAlive() || parts < 5) {
				FAILURES.add("apparence du boss non construite (" + parts + " morceaux, vivant=" + boss.isAlive() + ")");
			}
			checks++;
			if (SanctuaryAltarBlock.summon((ServerWorld) boss.getEntityWorld(), altarPos, null) != null) {
				FAILURES.add("l'autel réveille un deuxième boss alors que le premier est là");
			}
			boss.discard();
			try {
				checks += checkCommand(server, FAILURES);
				checks += checkWeapons(server, FAILURES);
				checks += checkArmorPowers(server, FAILURES);
				checks += checkElements(server, FAILURES);
			} catch (Throwable t) {
				EvoTool.LOGGER.error("EVOTOOL SELFTEST CRASH", t);
				FAILURES.add("crash: " + t);
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
		int abyssite = 0;
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
							} else if (state.isOf(ModBlocks.ABYSSITE_ORE)) {
								abyssite++;
							} else if (state.contains(EvolvingBlock.EVOLUTION)) {
								others++;
							}
						}
					}
				}
			}
		}
		EvoTool.LOGGER.info("EVOTOOL dimension in 9 chunks: stone={} grass={} other evolving blocks={} abyssite ore={}",
				stone, grass, others, abyssite);
		if (stone == 0 || grass == 0) {
			failures.add("relief de la dimension évolutive : pierre=" + stone + " herbe=" + grass);
		}
		checks++;
		if (abyssite == 0) {
			failures.add("pas de minerai d'abyssite dans la dimension évolutive");
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

		// Sanctuaire : généré avec le monde à l'endroit annoncé par /dimension locate, avec son autel.
		checks++;
		BlockPos center = Sanctuary.nearest(evo.getSeed(), arrival.getX(), arrival.getZ());
		altarPos = DimensionCommand.findAltar(evo, center);
		EvoTool.LOGGER.info("EVOTOOL sanctuary near {}: center {}, altar {}", arrival, center, altarPos);
		if (altarPos == null) {
			failures.add("pas de sanctuaire (autel) généré en " + center);
			altarPos = arrival.add(1, 0, 2);
		}
		checks++;
		if (center.getSquaredDistance(arrival.getX(), center.getY(), arrival.getZ()) > 600 * 600) {
			failures.add("sanctuaire le plus proche trop loin : " + center);
		}

		// Boss : réveillé par un clic droit sur l'autel ; chaque forme est plus grande et plus solide.
		evo.setChunkForced(altarPos.getX() >> 4, altarPos.getZ() >> 4, true);
		// le boss apparaît 5 blocs au sud de l'autel, souvent dans le tronçon voisin : il doit lui aussi rester chargé
		BlockPos bossSpot = altarPos.add(0, -1, 5);
		evo.setChunkForced(bossSpot.getX() >> 4, bossSpot.getZ() >> 4, true);
		boss = SanctuaryAltarBlock.summon(evo, altarPos, null);
		checks++;
		if (boss == null) {
			failures.add("l'autel ne réveille pas le boss");
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
		// Minuteur : le boss prend la forme suivante à chaque intervalle (5 minutes par défaut).
		checks++;
		boss.applyForm(0, true);
		boss.advanceEvolution(evo, EvoDimension.intervalTicks() - 1);
		int formBefore = boss.getForm();
		boss.advanceEvolution(evo, 1);
		if (formBefore != 0 || boss.getForm() != 1) {
			failures.add("le boss n'évolue pas au bout de " + EvoDimension.minutes() + " minutes (forme " + boss.getForm() + ")");
		}
		// Trois phases : à 2/3 de la vie le boss se transforme (invulnérable), à 1/3 c'est l'ascension dorée.
		boss.applyForm(3, true);
		checks++;
		boss.setHealth(boss.getMaxHealth() * 0.5F);
		boss.checkPhase(evo);
		if (boss.getPhase() != 2 || !boss.isTransforming()) {
			failures.add("phase 2 du boss : phase " + boss.getPhase() + ", transformation " + boss.isTransforming());
		}
		checks++;
		float before = boss.getHealth();
		boss.damage(evo, evo.getDamageSources().generic(), 20.0F);
		if (boss.getHealth() < before) {
			failures.add("le boss prend des dégâts pendant sa transformation");
		}
		checks++;
		boss.applyForm(3, true);
		if (boss.getPhase() != 1 || boss.isTransforming()) {
			failures.add("une nouvelle forme du boss ne repart pas en phase 1 (phase " + boss.getPhase() + ")");
		}
		checks++;
		boss.setHealth(boss.getMaxHealth() * 0.25F);
		boss.checkPhase(evo);
		if (boss.getPhase() != 3) {
			failures.add("phase 3 du boss : phase " + boss.getPhase());
		}
		return checks;
	}

	private static final BlockPos ARENA = new BlockPos(3000, 250, 3000);

	/**
	 * Arène des armes, préparée dès le démarrage : un chunk tout juste chargé n'est pas encore « suivi »
	 * et ses entités restent invisibles quelques ticks, le temps que le chargement forcé soit pris en compte.
	 */
	private static void prepareArena(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		for (int dz = 0; dz <= 16; dz += 16) {
			world.setChunkForced(ARENA.getX() >> 4, (ARENA.getZ() + dz) >> 4, true);
			world.getChunk(ARENA.getX() >> 4, (ARENA.getZ() + dz) >> 4);
		}
		for (int dx = -4; dx <= 4; dx++) {
			for (int dz = -4; dz <= 14; dz++) {
				world.setBlockState(ARENA.add(dx, -1, dz), net.minecraft.block.Blocks.STONE.getDefaultState());
			}
		}
	}

	/**
	 * Armure améliorée : ailes de l'infini du plastron (modèles 3D présents, vol rapide)
	 * et plongée souterraine des bottes (accroupi au sol, on traverse le sol, on ressort en remontant).
	 */
	private static int checkArmorPowers(MinecraftServer server, List<String> failures) {
		int checks = 0;
		ServerWorld world = server.getOverworld();
		FakePlayer player = FakePlayer.get(world);
		double x = ARENA.getX() + 0.5;
		double y = ARENA.getY();
		double z = ARENA.getZ() + 6.5;
		player.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
		int tick = server.getTicks();

		checks++;
		if (EvoliteUpgrade.upgradeFor(EvoKind.CHESTPLATE) != EvoliteUpgrade.Upgrade.INFINITY_WINGS
				|| EvoliteUpgrade.upgradeFor(EvoKind.BOOTS) != EvoliteUpgrade.Upgrade.BURROW) {
			failures.add("le plastron et les bottes améliorés n'ont pas les ailes et la plongée");
		}

		ItemStack chest = new ItemStack(ModItems.itemFor(EvoKind.CHESTPLATE));
		EvoliteUpgrade.apply(chest);
		checks++;
		if (!InfinityWings.isWingChestplate(chest)) {
			failures.add("ailes de l'infini : le plastron amélioré n'est pas reconnu");
		}
		// les modèles 3D des ailes, dessinés par le client sur le buste du joueur
		for (String model : new String[] {"infinity_wing", "infinity_wing_right", "infinity_emblem"}) {
			checks++;
			if (SelfTest.class.getResource("/assets/evotool/items/wings/" + model + ".json") == null
					|| SelfTest.class.getResource("/assets/evotool/models/item/wings/" + model + ".json") == null) {
				failures.add("ailes de l'infini : modèle manquant " + model);
			}
		}
		player.equipStack(EquipmentSlot.CHEST, chest);
		checks++;
		InfinityWings.restoreAbilities(player);
		if (!player.getAbilities().allowFlying || player.getAbilities().getFlySpeed() <= InfinityWings.DEFAULT_FLY_SPEED) {
			failures.add("ailes de l'infini : pas de vol plus rapide qu'en créatif");
		}
		player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
		InfinityWings.restoreAbilities(player);
		checks++;
		if (player.getAbilities().allowFlying) {
			failures.add("ailes de l'infini : on vole encore une fois le plastron enlevé");
		}

		// évolution à la touche R : animation devant le joueur, l'objet passe à sa nouvelle forme
		ItemStack pickaxe = new ItemStack(ModItems.itemFor(EvoKind.PICKAXE));
		EvoProgression.gainXp(pickaxe, 1_000_000, 0, world);
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, pickaxe);
		checks++;
		boolean started = fr.evotool.evo.Evolution.start(player);
		boolean running = fr.evotool.evo.Evolution.isRunning(player);
		boolean evolved = fr.evotool.evo.Evolution.completeNow(player);
		EvoTool.LOGGER.info("EVOTOOL evolution: started={} running={} evolved={} level={}", started, running, evolved,
				EvoProgression.get(pickaxe).level());
		if (!started || !running || !evolved || EvoProgression.get(pickaxe).level() != 5) {
			failures.add("touche R : l'évolution animée ne fait pas évoluer la pioche (lancée=" + started + ", évoluée=" + evolved
					+ ", niveau " + EvoProgression.get(pickaxe).level() + ")");
		}
		checks++;
		if (fr.evotool.evo.Evolution.start(player)) {
			failures.add("touche R : une évolution démarre alors que rien n'est prêt");
			fr.evotool.evo.Evolution.completeNow(player);
		}
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);

		// règle du joueur : au niveau 100, pioche, pelle et hache cassent tout en un coup (pierre, ardoise, obsidienne…)
		for (EvoKind kind : new EvoKind[] {EvoKind.PICKAXE, EvoKind.SHOVEL, EvoKind.AXE}) {
			checks++;
			ItemStack tool = new ItemStack(ModItems.itemFor(kind));
			tool.set(ModComponents.EVO_DATA, new EvoData(100, 0, 0, -1, 0L));
			EvoProgression.refresh(tool, server.getRegistryManager());
			double efficiency = tool.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS, AttributeModifiersComponent.DEFAULT)
					.modifiers().stream().filter(m -> m.attribute().equals(EntityAttributes.MINING_EFFICIENCY))
					.mapToDouble(m -> m.modifier().value()).sum();
			if (efficiency < EvoProgression.INSTAMINE_BONUS) {
				failures.add(kind + " niv. 100 : plus de minage instantané (efficacité " + efficiency
						+ ") — il ne doit jamais changer");
			}
		}

		// équilibrage : une pioche d'une ancienne version (Fortune VI) redescend à Fortune IV
		checks++;
		ItemStack old = new ItemStack(ModItems.itemFor(EvoKind.PICKAXE));
		old.set(ModComponents.EVO_DATA, new EvoData(100, 0, 0, -1, 0L));
		var fortune = server.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT)
				.getOrThrow(net.minecraft.enchantment.Enchantments.FORTUNE);
		net.minecraft.enchantment.EnchantmentHelper.apply(old, builder -> builder.set(fortune, 6));
		EvoProgression.refresh(old, server.getRegistryManager());
		int fortuneLevel = net.minecraft.enchantment.EnchantmentHelper.getLevel(fortune, old);
		if (fortuneLevel != 4) {
			failures.add("équilibrage : Fortune " + fortuneLevel + " au lieu de IV sur une vieille pioche");
		}

		// générateur de bouclier : il avale l'évolium, brûle 1 évolium, fait un dôme et rejette les monstres
		BlockPos shieldPos = ARENA.add(0, 0, 10);
		world.setBlockState(shieldPos, fr.evotool.block.ModBlocks.SHIELD_GENERATOR.getDefaultState());
		checks++;
		if (!(world.getBlockEntity(shieldPos) instanceof fr.evotool.shield.ShieldGeneratorBlockEntity shield)) {
			failures.add("générateur de bouclier : pas d'entité de bloc");
		} else {
			shield.input().setStack(0, new ItemStack(ModItems.EVOLIUM, 64));
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE);
			ZombieEntity intruder = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
			if (intruder != null) {
				intruder.refreshPositionAndAngles(shieldPos.getX() + 1.5, shieldPos.getY(), shieldPos.getZ() + 0.5, 0.0F, 0.0F);
				intruder.setAiDisabled(true);
				world.spawnEntity(intruder);
			}
			for (int i = 0; i < 4; i++) {
				shield.serverTick(world);
			}
			int domes = world.getEntitiesByType(EntityType.ITEM_DISPLAY, new net.minecraft.util.math.Box(shieldPos).expand(16),
					e -> e.getCommandTags().contains("evotool_shield_dome_" + shieldPos.asLong())).size();
			double away = intruder == null ? 99 : intruder.getEntityPos().distanceTo(net.minecraft.util.math.Vec3d.ofCenter(shieldPos));
			EvoTool.LOGGER.info("EVOTOOL shield: fuel={} radius={} active={} domes={} zombie at {} blocks", shield.fuel(),
					shield.radius(), shield.isActive(), domes, String.format(java.util.Locale.ROOT, "%.1f", away));
			checks++;
			if (shield.fuel() != 63 || shield.radius() != 6 || !shield.isActive()) {
				failures.add("générateur de bouclier : évolium " + shield.fuel() + " au lieu de 63, rayon " + shield.radius()
						+ ", actif=" + shield.isActive());
			}
			checks++;
			if (domes != 1) {
				failures.add("générateur de bouclier : " + domes + " dômes au lieu de 1");
			}
			checks++;
			if (away <= shield.radius()) {
				failures.add("générateur de bouclier : le zombie est resté dans le dôme (" + away + " blocs)");
			}
			// éteint : le dôme disparaît
			shield.press(fr.evotool.shield.ShieldGeneratorScreenHandler.BUTTON_TOGGLE);
			shield.serverTick(world);
			checks++;
			if (shield.isActive()) {
				failures.add("générateur de bouclier : toujours actif une fois éteint");
			}
			// cassé : il se lâche avec son évolium
			checks++;
			java.util.List<ItemStack> drops = shield.drops();
			if (drops.isEmpty() || drops.get(0).getOrDefault(ModComponents.SHIELD_FUEL, 0L) < 63) {
				failures.add("générateur de bouclier : l'évolium n'est pas gardé quand on le casse");
			}
			if (intruder != null) {
				intruder.discard();
			}
		}
		world.setBlockState(shieldPos, net.minecraft.block.Blocks.AIR.getDefaultState());

		// briquet d'évolium : recette secrète, elle marche mais le livre de recettes ne la montre pas
		checks++;
		if (server.getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, EvoTool.id("evolium_lighter"))).isEmpty()) {
			failures.add("le briquet d'évolium ne se fabrique pas");
		}
		checks++;
		if (server.getAdvancementLoader().get(EvoTool.id("recipes/equipment/evolium_lighter")) != null) {
			failures.add("la recette secrète du briquet apparaît dans le livre de recettes");
		}

		// armure abyssale : niveau 100 + abyssite, puis jusqu'au niveau 150 avec 11 formes noires
		ItemStack abyssChest = new ItemStack(ModItems.itemFor(EvoKind.CHESTPLATE));
		abyssChest.set(ModComponents.EVO_DATA, new EvoData(100, 0, 0, -1, 0L));
		checks++;
		if (!fr.evotool.armor.AbyssalArmor.canUpgrade(abyssChest)) {
			failures.add("armure abyssale : une pièce niveau 100 ne peut pas recevoir l'abyssite");
		}
		fr.evotool.armor.AbyssalArmor.apply(abyssChest);
		EvoProgression.gainXp(abyssChest, 10_000_000, 0, world);
		checks++;
		if (EvoProgression.get(abyssChest).level() != 104 || !EvoProgression.isReadyToEvolve(abyssChest)
				|| EvoProgression.stage(abyssChest) != 21) {
			failures.add("armure abyssale : niveau " + EvoProgression.get(abyssChest).level() + ", forme "
					+ EvoProgression.stage(abyssChest) + " (attendu 104 prêt à évoluer, forme 21)");
		}
		EvoProgression.evolve(abyssChest, null, world);
		checks++;
		if (EvoProgression.get(abyssChest).level() != 105 || EvoProgression.stage(abyssChest) != 22) {
			failures.add("armure abyssale : l'évolution ne passe pas à la forme 22");
		}

		// tentacules : ensemble abyssal complet au niveau 150
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		EvoKind[] pieces = {EvoKind.HELMET, EvoKind.CHESTPLATE, EvoKind.LEGGINGS, EvoKind.BOOTS};
		for (int i = 0; i < slots.length; i++) {
			ItemStack piece = new ItemStack(ModItems.itemFor(pieces[i]));
			fr.evotool.armor.AbyssalArmor.apply(piece);
			piece.set(ModComponents.EVO_DATA, new EvoData(150, 0, 0, -1, 0L));
			EvoProgression.refresh(piece, server.getRegistryManager());
			player.equipStack(slots[i], piece);
		}
		checks++;
		if (!fr.evotool.armor.Tentacles.hasTentacles(player) || EvoProgression.stage(player.getEquippedStack(EquipmentSlot.CHEST)) != 31) {
			failures.add("tentacules : pas débloquées avec l'ensemble abyssal niveau 150");
		}
		player.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
		ZombieEntity target = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
		float targetHealth = 0;
		if (target != null) {
			target.refreshPositionAndAngles(x, y, z + 4.0, 180.0F, 0.0F);
			target.setAiDisabled(true);
			world.spawnEntity(target);
			targetHealth = target.getHealth();
		}
		checks++;
		boolean struck = fr.evotool.armor.Tentacles.strike(player);
		int strikes = fr.evotool.armor.Tentacles.activeStrikes();
		for (int i = 0; i < 20; i++) {
			fr.evotool.armor.Tentacles.tickForTest();
		}
		EvoTool.LOGGER.info("EVOTOOL tentacles: strike={} active={} zombie {} -> {} left={}", struck, strikes, targetHealth,
				target == null ? -1 : target.getHealth(), fr.evotool.armor.Tentacles.activeStrikes());
		if (!struck || strikes < 1 || target == null || target.isAlive() && target.getHealth() >= targetHealth
				|| fr.evotool.armor.Tentacles.activeStrikes() != 0) {
			failures.add("tentacules : le coup ne touche pas le zombie devant le joueur");
		}
		if (target != null) {
			target.discard();
		}
		// clic droit : la tentacule attrape le zombie, le tient devant le joueur, puis le projette
		ZombieEntity grabTarget = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
		checks++;
		if (grabTarget == null) {
			failures.add("tentacules : impossible de créer le zombie à attraper");
		} else {
			grabTarget.refreshPositionAndAngles(x, y, z + 5.0, 180.0F, 0.0F);
			grabTarget.setAiDisabled(true);
			world.spawnEntity(grabTarget);
			boolean grabbed = fr.evotool.armor.Tentacles.grabOrThrow(player);
			// tenu bien plus que 5 s : la tentacule ne le lâche jamais toute seule (on le ramène à la base)
			for (int i = 0; i < 300; i++) {
				fr.evotool.armor.Tentacles.tickForTest();
			}
			Vec3d holdPoint = player.getEyePos().add(player.getRotationVector().multiply(6.0));
			double holdDistance = grabTarget.getBoundingBox().getCenter().distanceTo(holdPoint);
			boolean held = fr.evotool.armor.Tentacles.grabbed(player) == grabTarget;
			fr.evotool.armor.Tentacles.grabOrThrow(player);
			Vec3d thrown = grabTarget.getVelocity();
			for (int i = 0; i < 8; i++) {
				fr.evotool.armor.Tentacles.tickForTest();
			}
			EvoTool.LOGGER.info("EVOTOOL tentacle grab: grabbed={} held={} distance={} thrown={} left={}", grabbed, held,
					holdDistance, thrown, fr.evotool.armor.Tentacles.activeStrikes());
			if (!grabbed || !held || holdDistance > 1.5 || thrown.z < 1.5 || fr.evotool.armor.Tentacles.grabbed(player) != null
					|| fr.evotool.armor.Tentacles.activeStrikes() != 0) {
				failures.add("tentacules : le clic droit n'attrape pas / ne projette pas le zombie (tenu=" + held
						+ ", distance=" + holdDistance + ", vitesse=" + thrown + ")");
			}
			grabTarget.discard();
		}
		// regarder par terre + clic droit : il est posé doucement, sans dégâts
		ZombieEntity dropTarget = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
		checks++;
		if (dropTarget != null) {
			dropTarget.refreshPositionAndAngles(x, y, z + 5.0, 180.0F, 0.0F);
			dropTarget.setAiDisabled(true);
			world.spawnEntity(dropTarget);
			float health = dropTarget.getHealth();
			boolean grabbed = fr.evotool.armor.Tentacles.grabOrThrow(player);
			for (int i = 0; i < 14; i++) {
				fr.evotool.armor.Tentacles.tickForTest();
			}
			player.setPitch(80.0F);
			fr.evotool.armor.Tentacles.grabOrThrow(player);
			player.setPitch(0.0F);
			Vec3d velocity = dropTarget.getVelocity();
			double distance = dropTarget.getEntityPos().distanceTo(player.getEntityPos());
			for (int i = 0; i < 8; i++) {
				fr.evotool.armor.Tentacles.tickForTest();
			}
			EvoTool.LOGGER.info("EVOTOOL tentacle drop: grabbed={} velocity={} distance={} health {} -> {}", grabbed, velocity,
					distance, health, dropTarget.getHealth());
			if (!grabbed || velocity.length() > 0.1 || distance > 3.0 || dropTarget.getHealth() < health
					|| fr.evotool.armor.Tentacles.grabbed(player) != null || fr.evotool.armor.Tentacles.activeStrikes() != 0) {
				failures.add("tentacules : en regardant par terre, le clic droit ne pose pas doucement le monstre");
			}
			dropTarget.discard();
		}
		// les joueurs aussi se font attraper (le serveur les téléporte), puis projeter
		FakePlayer victim = FakePlayer.get(world, new com.mojang.authlib.GameProfile(
				java.util.UUID.nameUUIDFromBytes("evotool-tentacle-target".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
				"evotool_target"));
		victim.refreshPositionAndAngles(x, y, z + 5.0, 180.0F, 0.0F);
		checks++;
		boolean grabbedPlayer = fr.evotool.armor.Tentacles.grabEntity(player, victim);
		for (int i = 0; i < 30; i++) {
			fr.evotool.armor.Tentacles.tickForTest();
		}
		double playerHold = victim.getBoundingBox().getCenter()
				.distanceTo(player.getEyePos().add(player.getRotationVector().multiply(6.0)));
		boolean heldPlayer = fr.evotool.armor.Tentacles.grabbed(player) == victim;
		fr.evotool.armor.Tentacles.grabOrThrow(player);
		Vec3d playerThrown = victim.getVelocity();
		for (int i = 0; i < 8; i++) {
			fr.evotool.armor.Tentacles.tickForTest();
		}
		EvoTool.LOGGER.info("EVOTOOL tentacle grab player: grabbed={} held={} distance={} thrown={}", grabbedPlayer, heldPlayer,
				playerHold, playerThrown);
		if (!grabbedPlayer || !heldPlayer || playerHold > 1.5 || playerThrown.z < 1.5
				|| fr.evotool.armor.Tentacles.grabbed(player) != null || fr.evotool.armor.Tentacles.activeStrikes() != 0) {
			failures.add("tentacules : le clic droit n'attrape pas / ne projette pas un joueur (tenu=" + heldPlayer
					+ ", distance=" + playerHold + ", vitesse=" + playerThrown + ")");
		}
		victim.setVelocity(Vec3d.ZERO);
		// touche J : on reste au sol, plus rapide ; accroupi sur le sol, les tentacules creusent
		fr.evotool.armor.Tentacles.toggle(player);
		net.minecraft.entity.attribute.EntityAttributeInstance moveSpeed = player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
		checks++;
		if (!player.getCommandTags().contains(fr.evotool.armor.Tentacles.MOVE_TAG) || fr.evotool.upgrade.Burrow.phases(player)
				|| player.getAbilities().flying || !player.getAbilities().allowFlying
				|| moveSpeed == null || moveSpeed.getModifier(fr.evotool.armor.Tentacles.SPEED_ID) == null) {
			failures.add("tentacules : le mode déplacement (J) ne s'allume pas au sol (vol=" + player.getAbilities().flying + ")");
		}
		player.setSneaking(true);
		player.setOnGround(true);
		for (int i = 0; i < 10 && !Burrow.isBurrowing(player); i++) {
			Burrow.tick(player, tick + i);
		}
		player.setSneaking(false);
		checks++;
		if (!Burrow.isBurrowing(player) || !Burrow.phases(player)
				|| player.getAbilities().getFlySpeed() != fr.evotool.armor.Tentacles.DIG_SPEED) {
			failures.add("tentacules : accroupi en mode J, elles ne creusent pas sous terre");
		}
		fr.evotool.armor.Tentacles.toggle(player);
		checks++;
		if (player.getCommandTags().contains(fr.evotool.armor.Tentacles.MOVE_TAG) || fr.evotool.upgrade.Burrow.phases(player)
				|| moveSpeed != null && moveSpeed.getModifier(fr.evotool.armor.Tentacles.SPEED_ID) != null) {
			failures.add("tentacules : le mode déplacement (J) ne s'éteint pas");
		}
		for (EquipmentSlot slot : slots) {
			player.equipStack(slot, ItemStack.EMPTY);
		}
		InfinityWings.restoreAbilities(player);

		// bottes : accroupi sur le sol de l'arène, on plonge dedans
		ItemStack boots = new ItemStack(ModItems.itemFor(EvoKind.BOOTS));
		EvoliteUpgrade.apply(boots);
		player.equipStack(EquipmentSlot.FEET, boots);
		player.setSneaking(true);
		player.setOnGround(true);
		for (int i = 0; i < 10 && !Burrow.isBurrowing(player); i++) {
			Burrow.tick(player, tick + i);
		}
		checks++;
		if (!Burrow.isBurrowing(player) || !player.getAbilities().flying || player.getAbilities().getFlySpeed() != Burrow.SPEED) {
			failures.add("bottes : la plongée souterraine ne démarre pas (sous terre=" + Burrow.isBurrowing(player)
					+ ", vol=" + player.getAbilities().flying + ")");
		}
		player.setSneaking(false);
		// dans le sol (la dalle de pierre de l'arène) : on y reste
		player.refreshPositionAndAngles(x, y - 1.1, z, 0.0F, 0.0F);
		Burrow.tick(player, tick + 20);
		checks++;
		if (!Burrow.isBurrowing(player)) {
			failures.add("bottes : la plongée s'arrête dans le sol");
		}
		// on remonte à travers le sol : on ressort
		player.refreshPositionAndAngles(x, y + 0.5, z, 0.0F, 0.0F);
		Burrow.tick(player, tick + 21);
		checks++;
		if (Burrow.isBurrowing(player) || player.getAbilities().flying || player.getAbilities().allowFlying) {
			failures.add("bottes : on ne ressort pas du sol en remontant (sous terre=" + Burrow.isBurrowing(player) + ")");
		}
		// bottes enlevées sous terre : le joueur est remonté à la surface au lieu d'étouffer
		Burrow.start(player);
		player.refreshPositionAndAngles(x, y - 1.1, z, 0.0F, 0.0F);
		player.equipStack(EquipmentSlot.FEET, ItemStack.EMPTY);
		Burrow.tick(player, tick + 22);
		checks++;
		if (Burrow.isBurrowing(player) || player.getY() < y - 0.01) {
			failures.add("bottes enlevées sous terre : joueur pas remonté (y=" + player.getY() + ")");
		}
		EvoTool.LOGGER.info("EVOTOOL burrow: dive, underground, surface and rescue checked (y={})", player.getY());
		return checks;
	}

	/** Attaques spéciales des armes moddées, sur un zombie posé devant un faux joueur. */
	/** Combineur élémentaire : la fusion (3 secondes), puis les pouvoirs des éléments sur les armes et l'armure. */
	private static int checkElements(MinecraftServer server, List<String> failures) {
		int checks = 0;
		ServerWorld world = server.getOverworld();
		FakePlayer player = FakePlayer.get(world);
		double x = ARENA.getX() + 0.5;
		double y = ARENA.getY();
		double z = ARENA.getZ() + 6.5;
		player.refreshPositionAndAngles(x, y, z, 0.0F, 0.0F);
		for (EquipmentSlot slot : fr.evotool.element.Elements.ARMOR) {
			player.equipStack(slot, ItemStack.EMPTY);
		}
		InfinityWings.restoreAbilities(player);

		// le combineur : une épée en diamant + de la glace compacte
		BlockPos pos = ARENA.add(3, 0, 2);
		world.setBlockState(pos, fr.evotool.block.ModBlocks.ELEMENTAL_COMBINER.getDefaultState());
		checks++;
		if (!(world.getBlockEntity(pos) instanceof fr.evotool.combiner.ElementalCombinerBlockEntity combiner)) {
			failures.add("combineur : pas d'entité de bloc");
			return checks;
		}
		net.minecraft.block.BlockState state = world.getBlockState(pos);
		combiner.items().setStack(0, new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD));
		combiner.items().setStack(1, new ItemStack(net.minecraft.item.Items.PACKED_ICE, 3));
		boolean started = combiner.start();
		for (int i = 0; i < 5; i++) {
			fr.evotool.combiner.ElementalCombinerBlockEntity.tick(world, pos, state, combiner);
		}
		int shown = world.getEntitiesByType(net.minecraft.entity.EntityType.ITEM_DISPLAY,
				new net.minecraft.util.math.Box(pos).expand(3), e -> true).size();
		boolean working = combiner.isWorking();
		for (int i = 0; i < fr.evotool.combiner.ElementalCombinerBlockEntity.DURATION; i++) {
			fr.evotool.combiner.ElementalCombinerBlockEntity.tick(world, pos, state, combiner);
		}
		ItemStack result = combiner.items().getStack(2);
		EvoTool.LOGGER.info("EVOTOOL combiner: started={} working={} displays={} result={} element={} ice left={}", started, working,
				shown, result, fr.evotool.element.Elements.get(result), combiner.items().getStack(1).getCount());
		checks++;
		if (!started || !working || shown < 2 || combiner.isWorking() || !result.isOf(net.minecraft.item.Items.DIAMOND_SWORD)
				|| fr.evotool.element.Elements.get(result) != fr.evotool.element.Element.ICE
				|| !combiner.items().getStack(0).isEmpty() || combiner.items().getStack(1).getCount() != 2) {
			failures.add("combineur : l'épée + la glace ne donnent pas une épée de glace en 3 secondes");
		}
		// seau d'eau : le seau vide reste dans la case
		combiner.items().setStack(2, ItemStack.EMPTY);
		combiner.items().setStack(0, new ItemStack(net.minecraft.item.Items.IRON_BOOTS));
		combiner.items().setStack(1, new ItemStack(net.minecraft.item.Items.WATER_BUCKET));
		combiner.start();
		for (int i = 0; i < fr.evotool.combiner.ElementalCombinerBlockEntity.DURATION + 2; i++) {
			fr.evotool.combiner.ElementalCombinerBlockEntity.tick(world, pos, state, combiner);
		}
		checks++;
		if (fr.evotool.element.Elements.get(combiner.items().getStack(2)) != fr.evotool.element.Element.WATER
				|| !combiner.items().getStack(1).isOf(net.minecraft.item.Items.BUCKET)) {
			failures.add("combineur : bottes + seau d'eau ne donnent pas des bottes d'eau (et le seau vide)");
		}
		// pas d'élément pour un bloc de terre
		combiner.items().setStack(2, ItemStack.EMPTY);
		combiner.items().setStack(0, new ItemStack(net.minecraft.item.Items.DIRT));
		combiner.items().setStack(1, new ItemStack(net.minecraft.item.Items.GUNPOWDER));
		checks++;
		if (combiner.start()) {
			failures.add("combineur : il accepte de combiner un bloc de terre");
		}
		combiner.items().clear();
		world.setBlockState(pos, net.minecraft.block.Blocks.AIR.getDefaultState());

		// armes : la glace gèle, le feu enflamme, le vent projette
		fr.evotool.element.Element[] tested = {fr.evotool.element.Element.ICE, fr.evotool.element.Element.FIRE,
				fr.evotool.element.Element.WIND, fr.evotool.element.Element.EARTH, fr.evotool.element.Element.SHADOW,
				fr.evotool.element.Element.LIGHT, fr.evotool.element.Element.POISON};
		for (fr.evotool.element.Element element : tested) {
			net.minecraft.entity.passive.PigEntity pig = EntityType.PIG.create(world, SpawnReason.COMMAND);
			if (pig == null) {
				continue;
			}
			pig.refreshPositionAndAngles(x, y, z + 2.0, 180.0F, 0.0F);
			pig.setAiDisabled(true);
			world.spawnEntity(pig);
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
					fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_SWORD), element));
			pig.damage(world, player.getDamageSources().playerAttack(player), 1.0F);
			boolean ok = switch (element) {
				case ICE -> pig.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS);
				case FIRE -> pig.isOnFire();
				case EARTH -> pig.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS) != null
						&& pig.getStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS).getAmplifier() >= 4;
				case SHADOW -> pig.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WITHER);
				case LIGHT -> pig.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING);
				case POISON -> pig.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON);
				default -> pig.getVelocity().y > 0.5;
			};
			checks++;
			if (!ok) {
				failures.add("éléments : l'épée de " + element.id() + " ne fait pas son effet");
			}
			pig.discard();
		}
		player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, ItemStack.EMPTY);

		// armure : plastron de feu = vol de feu et résistance au feu
		player.equipStack(EquipmentSlot.CHEST,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE), fr.evotool.element.Element.FIRE));
		fr.evotool.element.ElementalPowers.tick(player, 0);
		checks++;
		if (!player.getAbilities().allowFlying || !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.FIRE_RESISTANCE)) {
			failures.add("éléments : le plastron de feu ne donne pas le vol de feu");
		}
		player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
		fr.evotool.element.ElementalPowers.tick(player, 1);
		checks++;
		if (player.getAbilities().allowFlying && !player.isCreative()) {
			failures.add("éléments : le vol de feu reste sans le plastron");
		}
		// bottes de vent : plus de dégâts de chute
		player.equipStack(EquipmentSlot.FEET,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_BOOTS), fr.evotool.element.Element.WIND));
		float health = player.getHealth();
		boolean hurt = player.damage(world, world.getDamageSources().fall(), 6.0F);
		checks++;
		if (hurt || player.getHealth() < health) {
			failures.add("éléments : les bottes de vent n'empêchent pas les dégâts de chute");
		}
		// bottes d'eau : on marche sur l'eau
		player.equipStack(EquipmentSlot.FEET,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_BOOTS), fr.evotool.element.Element.WATER));
		checks++;
		if (!player.canWalkOnFluid(net.minecraft.fluid.Fluids.WATER.getDefaultState())) {
			failures.add("éléments : les bottes d'eau ne marchent pas sur l'eau");
		}
		// catalyseurs de la terre et de l'ombre
		checks++;
		if (fr.evotool.element.Element.fromCatalyst(new ItemStack(net.minecraft.item.Items.DIRT)) != fr.evotool.element.Element.EARTH
				|| fr.evotool.element.Element.fromCatalyst(new ItemStack(net.minecraft.item.Items.INK_SAC)) != fr.evotool.element.Element.SHADOW) {
			failures.add("éléments : la terre ou l'encre ne donnent pas la terre / l'ombre");
		}
		// casque d'ombre : vision nocturne ; plastron de terre : Résistance
		player.equipStack(EquipmentSlot.HEAD,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_HELMET), fr.evotool.element.Element.SHADOW));
		player.equipStack(EquipmentSlot.CHEST,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE), fr.evotool.element.Element.EARTH));
		fr.evotool.element.ElementalPowers.tick(player, 40);
		checks++;
		if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.NIGHT_VISION)
				|| !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE)) {
			failures.add("éléments : le casque d'ombre / le plastron de terre ne donnent pas leurs effets");
		}
		// plastron de lumière : Régénération ; bottes de poison : immunité au poison
		player.equipStack(EquipmentSlot.CHEST,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_CHESTPLATE), fr.evotool.element.Element.LIGHT));
		player.equipStack(EquipmentSlot.FEET,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_BOOTS), fr.evotool.element.Element.POISON));
		player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.POISON, 200, 0));
		fr.evotool.element.ElementalPowers.tick(player, 60);
		checks++;
		if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION)
				|| player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.POISON)) {
			failures.add("éléments : le plastron de lumière / les bottes de poison ne donnent pas leurs effets");
		}
		checks++;
		if (fr.evotool.element.Element.fromCatalyst(new ItemStack(net.minecraft.item.Items.GLOWSTONE_DUST)) != fr.evotool.element.Element.LIGHT
				|| fr.evotool.element.Element.fromCatalyst(new ItemStack(net.minecraft.item.Items.SPIDER_EYE)) != fr.evotool.element.Element.POISON) {
			failures.add("éléments : la poudre lumineuse ou l'œil d'araignée ne donnent pas la lumière / le poison");
		}
		// jambières électriques : vitesse
		player.equipStack(EquipmentSlot.LEGS,
				fr.evotool.element.Elements.combine(new ItemStack(net.minecraft.item.Items.IRON_LEGGINGS), fr.evotool.element.Element.LIGHTNING));
		fr.evotool.element.ElementalPowers.tick(player, 20);
		checks++;
		if (!player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED)) {
			failures.add("éléments : les jambières électriques ne donnent pas la vitesse");
		}
		for (EquipmentSlot slot : fr.evotool.element.Elements.ARMOR) {
			player.equipStack(slot, ItemStack.EMPTY);
		}
		player.clearStatusEffects();
		InfinityWings.restoreAbilities(player);
		return checks;
	}

	private static int checkWeapons(MinecraftServer server, List<String> failures) {
		int checks = 0;
		ServerWorld world = server.getOverworld();
		BlockPos center = ARENA;
		FakePlayer player = FakePlayer.get(world);
		for (WeaponKind kind : WeaponKind.values()) {
			checks++;
			// le joueur regarde vers le sud (+z), le zombie est 2 blocs devant et lui fait face
			player.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0F, 0.0F);
			ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
			if (zombie == null) {
				failures.add("zombie de test non créé");
				return checks;
			}
			zombie.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 2.5, 180.0F, 0.0F);
			zombie.setAiDisabled(true);
			world.spawnEntity(zombie);
			if (world.getEntitiesByClass(ZombieEntity.class, zombie.getBoundingBox().expand(0.5), z -> z == zombie).isEmpty()) {
				failures.add("zombie de test invisible (chunk de l'arène pas encore suivi)");
				zombie.discard();
				break;
			}
			float health = zombie.getHealth();
			WeaponPowers.activate(kind, world, player, new ItemStack(ModItems.itemFor(fr.evotool.evo.EvoKind.valueOf(kind.name()))));
			if (kind == WeaponKind.BLAST_BLADE) {
				// chaque coup d'épée : slash explosif devant le joueur, sans casser de bloc
				int blocks = countBlocks(world, center, 4);
				WeaponPowers.blastSlash(world, player, 1.0F);
				if (countBlocks(world, center, 4) != blocks) {
					failures.add("la Lame Détonante casse des blocs");
				}
			}
			switch (kind) {
				case SCYTHE, KATANA, WARHAMMER, HALBERD, BLAST_BLADE -> {
					if (zombie.isAlive() && zombie.getHealth() >= health) {
						failures.add(kind + " : l'attaque spéciale ne touche pas le zombie devant le joueur");
					}
				}
				case DAGGER -> {
					if (player.getZ() <= zombie.getZ() || player.squaredDistanceTo(zombie) > 4.0) {
						failures.add("dague : pas téléporté dans le dos du zombie (joueur z=" + player.getZ() + ", zombie z=" + zombie.getZ() + ")");
					}
				}
				default -> {
					// projectiles : ils avancent aux ticks suivants
				}
			}
			EvoTool.LOGGER.info("EVOTOOL weapon {}: zombie {} -> {}", kind, health, zombie.getHealth());
			zombie.discard();
		}
		// Armes magiques : chaque sort coûte une charge, les charges reviennent avec le temps.
		for (MagicKind kind : MagicKind.values()) {
			checks++;
			player.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0F, 0.0F);
			ZombieEntity zombie = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
			if (zombie == null) {
				return checks;
			}
			zombie.refreshPositionAndAngles(center.getX() + 0.5, center.getY(), center.getZ() + 3.5, 180.0F, 0.0F);
			zombie.setAiDisabled(true);
			world.spawnEntity(zombie);
			ItemStack stack = new ItemStack(ModItems.itemFor(fr.evotool.evo.EvoKind.valueOf(kind.name())));
			int charges = ArcaneWeapons.charges(stack, kind);
			float health = zombie.getHealth();
			boolean cast = ArcaneWeapons.cast(kind, stack, world, player);
			if (!cast || ArcaneWeapons.charges(stack, kind) >= charges) {
				failures.add(kind + " : le sort ne coûte pas de charge (" + charges + " -> " + ArcaneWeapons.charges(stack, kind) + ")");
			}
			if ((kind == MagicKind.EVOLIUM_BLASTER || kind == MagicKind.THUNDER_GAUNTLET) && zombie.getHealth() >= health) {
				failures.add(kind + " : le sort ne touche pas le zombie visé");
			}
			// recharge : deux intervalles plus tard, deux charges de plus
			checks++;
			stack.set(ModComponents.CHARGES, 0);
			long now = world.getTime();
			stack.set(ModComponents.CHARGE_CLOCK, now - 2L * ArcaneWeapons.rechargeTicks(stack, kind));
			ArcaneWeapons.recharge(stack, kind, now);
			if (ArcaneWeapons.charges(stack, kind) != 2 || ArcaneWeapons.cast(kind, stack, world, player) == false) {
				failures.add(kind + " : recharge incorrecte (" + ArcaneWeapons.charges(stack, kind) + " charges)");
			}
			checks++;
			stack.set(ModComponents.CHARGES, 0);
			stack.set(ModComponents.CHARGE_CLOCK, world.getTime());
			if (ArcaneWeapons.cast(kind, stack, world, player)) {
				failures.add(kind + " : sort lancé sans charge");
			}
			EvoTool.LOGGER.info("EVOTOOL magic {}: zombie {} -> {}", kind, health, zombie.getHealth());
			zombie.discard();
		}
		for (int dz = 0; dz <= 16; dz += 16) {
			world.setChunkForced(ARENA.getX() >> 4, (ARENA.getZ() + dz) >> 4, false);
		}
		return checks;
	}

	private static int countBlocks(ServerWorld world, BlockPos center, int radius) {
		int count = 0;
		for (BlockPos p : BlockPos.iterate(center.add(-radius, -2, -radius), center.add(radius, 2, radius + 4))) {
			if (!world.getBlockState(p).isAir()) {
				count++;
			}
		}
		return count;
	}

	/** Commande /dimension : la vitesse d'évolution se règle et se garde. */
	private static int checkCommand(MinecraftServer server, List<String> failures) {
		int checks = 1;
		var dispatcher = server.getCommandManager().getDispatcher();
		try {
			dispatcher.execute("dimension speed 3", server.getCommandSource());
			boolean set = EvoDimension.minutes() == 3 && EvoDimension.intervalTicks() == 3 * 60 * 20;
			dispatcher.execute("dimension locate", server.getCommandSource());
			dispatcher.execute("dimension speed " + EvoDimension.DEFAULT_MINUTES, server.getCommandSource());
			if (!set || EvoDimension.minutes() != EvoDimension.DEFAULT_MINUTES) {
				failures.add("/dimension speed ne règle pas la vitesse d'évolution");
			}
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
			failures.add("/dimension refusée : " + e.getMessage());
		}
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

		// Armes moddées : dégâts, recette à la table de craft et déblocage dans le livre de recettes.
		for (Item item : ModItems.MODDED_WEAPONS) {
			checks++;
			WeaponKind kind = ((ModdedWeaponItem) item).weaponKind();
			AttributeModifiersComponent modifiers = new ItemStack(item).get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
			double damage = -1;
			if (modifiers != null) {
				for (AttributeModifiersComponent.Entry entry : modifiers.modifiers()) {
					if (entry.attribute().equals(EntityAttributes.ATTACK_DAMAGE)) {
						damage = entry.modifier().value() + 1.0;
					}
				}
			}
			if (Math.abs(damage - kind.damage()) > 1.0E-6) {
				failures.add(kind + " : dégâts " + damage + " au lieu de " + kind.damage());
			}
		}
		int recipes = 0;
		for (net.minecraft.recipe.RecipeEntry<?> recipe : server.getRecipeManager().values()) {
			net.minecraft.util.Identifier id = recipe.id().getValue();
			if (!id.getNamespace().equals(EvoTool.MOD_ID)) {
				continue;
			}
			recipes++;
			if (id.getPath().equals("evolium_lighter")) {
				continue;   // recette secrète : jamais dans le livre de recettes
			}
			checks++;
			boolean unlocked = false;
			for (String category : new String[] {"equipment", "building", "misc", "redstone"}) {
				unlocked |= server.getAdvancementLoader().get(EvoTool.id("recipes/" + category + "/" + id.getPath())) != null;
			}
			if (!unlocked) {
				failures.add("recette " + id + " absente du livre de recettes (pas de déblocage)");
			}
		}
		for (WeaponKind kind : WeaponKind.values()) {
			checks++;
			if (server.getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, EvoTool.id(kind.id()))).isEmpty()) {
				failures.add("recette manquante : " + kind.id());
			}
		}
		// l'autel du sanctuaire se fabrique aussi à la table de craft
		checks++;
		if (server.getRecipeManager().get(RegistryKey.of(RegistryKeys.RECIPE, EvoTool.id("sanctuary_altar"))).isEmpty()) {
			failures.add("recette manquante : sanctuary_altar");
		}
		EvoTool.LOGGER.info("EVOTOOL {} recipes of the mod, all in the recipe book", recipes);

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
				// XP : les niveaux montent seuls jusqu'à la forme suivante, puis l'objet attend la touche R.
				// (10 millions : assez pour remplir les niveaux 95 à 99 des outils de minage.)
				EvoProgression.gainXp(stack, 10_000_000, 1, server.getOverworld());
				if (level < EvoProgression.MAX_LEVEL) {
					checks++;
					int waiting = EvoProgression.get(stack).level();
					if (waiting != level + 4 || !EvoProgression.isReadyToEvolve(stack)) {
						failures.add(kind + " niv. " + level + " : devrait attendre au niveau " + (level + 4)
								+ " prêt à évoluer (niveau " + waiting + ", prêt=" + EvoProgression.isReadyToEvolve(stack) + ")");
					}
					checks++;
					if (!EvoProgression.evolve(stack, null, server.getOverworld()) || EvoProgression.get(stack).level() != level + 5
							|| EvoProgression.isReadyToEvolve(stack)) {
						failures.add(kind + " niv. " + level + " : l'évolution (touche R) ne passe pas à la forme suivante");
					}
				}
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
