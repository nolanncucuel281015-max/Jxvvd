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
			checks++;
			if (SanctuaryAltarBlock.summon((ServerWorld) boss.getEntityWorld(), altarPos, null) != null) {
				FAILURES.add("l'autel réveille un deuxième boss alors que le premier est là");
			}
			boss.discard();
			try {
				checks += checkCommand(server, FAILURES);
				checks += checkWeapons(server, FAILURES);
				checks += checkArmorPowers(server, FAILURES);
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
	 * Armure améliorée : ailes de l'infini du plastron (3 modèles 3D qui chevauchent le joueur, vol rapide)
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
		player.equipStack(EquipmentSlot.CHEST, chest);
		InfinityWings.update(player, tick);
		InfinityWings.update(player, tick + 1);
		checks++;
		int riding = world.getEntitiesByType(EntityType.ITEM_DISPLAY, player.getBoundingBox().expand(4),
				e -> e.getRootVehicle() == player).size();
		EvoTool.LOGGER.info("EVOTOOL wings: {} display parts riding the player", riding);
		if (riding != 3) {
			failures.add("ailes de l'infini : " + riding + " modèles sur le joueur au lieu de 3");
		}
		checks++;
		InfinityWings.restoreAbilities(player);
		if (!player.getAbilities().allowFlying || player.getAbilities().getFlySpeed() <= InfinityWings.DEFAULT_FLY_SPEED) {
			failures.add("ailes de l'infini : pas de vol plus rapide qu'en créatif");
		}
		player.equipStack(EquipmentSlot.CHEST, ItemStack.EMPTY);
		InfinityWings.update(player, tick + 2);
		InfinityWings.restoreAbilities(player);
		checks++;
		int left = world.getEntitiesByType(EntityType.ITEM_DISPLAY, player.getBoundingBox().expand(4),
				e -> e.getCommandTags().contains(GiantItem.TAG) && e.getRootVehicle() == player).size();
		if (left != 0 || player.getAbilities().allowFlying) {
			failures.add("ailes de l'infini : toujours là une fois le plastron enlevé (" + left + " modèles, vol="
					+ player.getAbilities().allowFlying + ")");
		}

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
			checks++;
			boolean unlocked = false;
			for (String category : new String[] {"equipment", "building", "misc"}) {
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
