package fr.evotool.gametest;

import fr.evotool.EvoTool;
import fr.evotool.armor.AbyssalArmor;
import fr.evotool.client.EvoToolClient;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import fr.evotool.upgrade.EvoliteUpgrade;
import fr.evotool.upgrade.GiantItem;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import org.joml.Quaternionf;

/**
 * Images de présentation du mod (CI, commit contenant [showcase], variable EVOTOOL_SHOWCASE) : un vrai monde (un
 * bosquet de cerisiers en montagne), sans interface, en 1920 x 1080. Armure abyssale et tentacules, gant de
 * Spider-Man, grimoire, combineur, bouclier, outils et armes géants, portail de rubis, boss divin, dimension, manuel.
 */
public class ShowcaseGameTest implements FabricClientGameTest {
	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;
	/** Fin d'après-midi : lumière chaude, ciel doré vers l'ouest. */
	private static final String TIME = "time set 11000";

	/** Le belvédère (au sol) et la direction où la vue est la plus dégagée. */
	private static Vec3d spot;
	private static float vista;
	/** Rotation calculée côté serveur pour la prochaine vue. */
	private static float aimYaw;
	private static float aimPitch;
	private static final List<DisplayEntity.ItemDisplayEntity> GALLERY = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext context) {
		if (System.getenv("EVOTOOL_SHOWCASE") == null) {
			return;
		}
		context.runOnClient(client -> {
			client.options.getViewDistance().setValue(10);
			client.options.getSimulationDistance().setValue(6);
		});
		Properties properties = new Properties();
		properties.setProperty("view-distance", "10");
		properties.setProperty("simulation-distance", "6");
		properties.setProperty("spawn-protection", "0");
		properties.setProperty("difficulty", "normal");
		try (TestDedicatedServerContext server = context.worldBuilder()
				.setUseConsistentSettings(false)
				.adjustSettings(creator -> creator.setSeed("evotool"))
				.createServer(properties);
				TestServerConnection connection = server.connect()) {
			run(context, server, connection.getClientWorld());
		}
	}

	private static void run(ClientGameTestContext context, TestServerContext server, TestClientWorldContext world) {
		chunks(world);
		server.runCommand("gamemode creative @a");
		server.runCommand("weather clear 1000000");
		server.runOnServer(ShowcaseGameTest::findSpot);
		server.runOnServer(ShowcaseGameTest::equip);
		stand(server, spot, vista, 10.0F);
		context.waitTicks(200);
		chunks(world);
		context.runOnClient(client -> client.options.hudHidden = true);

		scene(context, "armure abyssale", () -> heroShots(context, server));
		scene(context, "prise", () -> grabShot(context, server));
		scene(context, "toile", () -> swingShot(context, server));
		scene(context, "bombe et grimoire", () -> bombAndBreath(context, server));
		scene(context, "combineur", () -> combinerShot(context, server));
		scene(context, "bouclier", () -> shieldShot(context, server));
		scene(context, "galeries", () -> galleries(context, server));
		scene(context, "portail", () -> portalShot(context, server));
		scene(context, "boss", () -> bossShots(context, server, world));
		scene(context, "dimension", () -> dimensionShot(context, server, world));
		scene(context, "manuel", () -> manualShot(context));
	}

	/** Une scène ; si elle rate, on le note et on passe à la suivante (les autres images restent). */
	private static void scene(ClientGameTestContext context, String name, Runnable scene) {
		EvoTool.LOGGER.info("EVOTOOL SHOWCASE scene: {}", name);
		try {
			scene.run();
		} catch (Throwable t) {
			EvoTool.LOGGER.error("EVOTOOL SHOWCASE scene {} failed", name, t);
		}
		perspective(context, Perspective.FIRST_PERSON);
	}

	// ------------------------------------------------------------------ les scènes

	/** L'armure abyssale niveau 150, ses ailes et ses tentacules : de face, puis portée par les tentacules (J). */
	private static void heroShots(ClientGameTestContext context, TestServerContext server) {
		server.runCommand(TIME);
		stand(server, spot, vista + 180.0F, -8.0F);
		perspective(context, Perspective.THIRD_PERSON_FRONT);
		context.waitTicks(30);
		shot(context, "01_abyssal_armor");

		stand(server, spot, vista, 12.0F);
		context.waitTicks(10);
		context.getInput().pressKey(EvoToolClient.TENTACLES);
		context.waitTicks(50);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		server.runCommand(TIME);
		shot(context, "02_tentacle_travel");
		context.getInput().pressKey(EvoToolClient.TENTACLES);
		context.waitTicks(30);
	}

	/** Un vindicateur attrapé par une tentacule (clic droit à main nue), tenu en l'air. */
	private static void grabShot(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Vec3d at = ahead(vista, 5.0);
			MobEntity mob = mob(world, EntityType.VINDICATOR, new Vec3d(at.x, spot.y + 0.3, at.z), new ItemStack(Items.IRON_AXE));
			if (mob != null) {
				mob.setNoGravity(true);
				look(spot.add(0, 1.62, 0), mob.getBoundingBox().getCenter());
			}
		});
		stand(server, spot, aimYaw, aimPitch);
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(35);
		stand(server, spot, vista, 4.0F);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.waitTicks(10);
		server.runCommand(TIME);
		shot(context, "03_tentacle_grab");
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(30);
		server.runCommand("kill @e[type=minecraft:vindicator]");
	}

	/** Le gant de Spider-Man : la toile accrochée à la montagne, on se balance. */
	private static void swingShot(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			player(s).setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.SPIDER_GLOVE));
			findSwing(s.getOverworld());
		});
		stand(server, spot, aimYaw, aimPitch);
		context.waitTicks(15);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(9);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		server.runCommand(TIME);
		shot(context, "04_spider_web_swing");
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(40);
	}

	/** Des pillards englués par une bombe de toile, puis le souffle du dragon du grimoire de feu. */
	private static void bombAndBreath(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Vec3d center = ahead(vista, 8.0);
			Vec3d side = Vec3d.fromPolar(0.0F, vista + 90.0F);
			for (int i = -1; i <= 1; i++) {
				Vec3d p = center.add(side.multiply(i * 1.8)).add(Vec3d.fromPolar(0.0F, vista).multiply(Math.abs(i) * 0.8));
				MobEntity mob = mob(world, i == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER,
						new Vec3d(p.x, ground(world, p.x, p.z), p.z), new ItemStack(i == 0 ? Items.IRON_AXE : Items.CROSSBOW));
				if (mob != null) {
					mob.setHealth(mob.getMaxHealth());
				}
			}
			look(spot.add(0, 1.62, 0), new Vec3d(center.x, ground(world, center.x, center.z) + 1.0, center.z));
		});
		stand(server, spot, aimYaw, aimPitch);
		context.waitTicks(10);
		server.runOnServer(s -> fr.evotool.spider.SpiderGlove.webBomb(player(s)));
		context.waitTicks(14);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		server.runCommand(TIME);
		shot(context, "05_spider_web_bomb");

		perspective(context, Perspective.FIRST_PERSON);
		server.runOnServer(s -> {
			ItemStack grimoire = new ItemStack(ModItems.ELEMENTAL_GRIMOIRE);
			grimoire.set(ModComponents.GRIMOIRE, new fr.evotool.element.Grimoire(List.of(fr.evotool.element.Element.FIRE), 2));
			player(s).setStackInHand(Hand.MAIN_HAND, grimoire);
		});
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(3);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		shot(context, "06_grimoire_dragon_breath");
		context.waitTicks(40);
		server.runCommand("kill @e[type=minecraft:pillager]");
		server.runCommand("kill @e[type=minecraft:vindicator]");
		server.runCommand("execute as @a at @s run fill ~-16 ~-8 ~-16 ~16 ~8 ~16 minecraft:air replace minecraft:cobweb");
		server.runCommand("execute as @a at @s run fill ~-16 ~-8 ~-16 ~16 ~8 ~16 minecraft:air replace minecraft:fire");
		server.runOnServer(s -> player(s).setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY));
	}

	private static BlockPos combinerPos;

	/** Le combineur élémentaire en pleine fusion (plastron + glace compacte), de tout près. */
	private static void combinerShot(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Vec3d at = ahead(vista + 90.0F, 3.0);
			combinerPos = BlockPos.ofFloored(at.x, ground(world, at.x, at.z), at.z);
			world.setBlockState(combinerPos, fr.evotool.block.ModBlocks.ELEMENTAL_COMBINER.getDefaultState());
			if (world.getBlockEntity(combinerPos) instanceof fr.evotool.combiner.ElementalCombinerBlockEntity combiner) {
				combiner.items().setStack(0, evolved(s, EvoKind.CHESTPLATE, 60));
				combiner.items().setStack(1, new ItemStack(Items.PACKED_ICE));
			}
			Vec3d eye = Vec3d.ofBottomCenter(combinerPos).add(Vec3d.fromPolar(0.0F, vista + 90.0F).multiply(-2.6)).add(0, 1.5, 0);
			Vec3d feet = new Vec3d(eye.x, ground(world, eye.x, eye.z), eye.z);
			aimSpot = feet;
			look(feet.add(0, 1.62, 0), Vec3d.ofCenter(combinerPos).add(0, 0.4, 0));
		});
		stand(server, aimSpot, aimYaw, aimPitch);
		context.waitTicks(15);
		server.runOnServer(s -> {
			if (s.getOverworld().getBlockEntity(combinerPos) instanceof fr.evotool.combiner.ElementalCombinerBlockEntity combiner) {
				combiner.start();
			}
		});
		context.waitTicks(28);
		server.runCommand(TIME);
		shot(context, "07_elemental_combiner");
		context.waitTicks(60);
		server.runOnServer(s -> s.getOverworld().setBlockState(combinerPos, net.minecraft.block.Blocks.AIR.getDefaultState()));
		server.runCommand("kill @e[type=minecraft:item]");
	}

	private static Vec3d aimSpot;
	private static BlockPos shieldPos;

	/** Le dôme du générateur de bouclier, vu de l'extérieur. */
	private static void shieldShot(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Vec3d at = ahead(vista - 90.0F, 13.0);
			shieldPos = BlockPos.ofFloored(at.x, ground(world, at.x, at.z), at.z);
			world.setBlockState(shieldPos, fr.evotool.block.ModBlocks.SHIELD_GENERATOR.getDefaultState());
			if (world.getBlockEntity(shieldPos) instanceof fr.evotool.shield.ShieldGeneratorBlockEntity shield) {
				shield.load(100000L, 7);
			}
			look(spot.add(0, 1.62, 0), Vec3d.ofCenter(shieldPos).add(0, 2.0, 0));
		});
		stand(server, spot, aimYaw, aimPitch);
		context.waitTicks(80);
		server.runCommand(TIME);
		shot(context, "08_shield_dome");
		server.runOnServer(s -> s.getOverworld().setBlockState(shieldPos, net.minecraft.block.Blocks.AIR.getDefaultState()));
		server.runCommand("kill @e[type=minecraft:item]");
		context.waitTicks(20);
	}

	/** Outils, armes moddées et armes magiques au niveau 100, en géant, en arc de cercle face au couchant. */
	private static void galleries(ClientGameTestContext context, TestServerContext server) {
		EvoKind[][] rows = {
				{EvoKind.PICKAXE, EvoKind.SHOVEL, EvoKind.AXE, EvoKind.HOE, EvoKind.SWORD, EvoKind.SPEAR, EvoKind.BOW, EvoKind.MACE},
				{EvoKind.SCYTHE, EvoKind.KATANA, EvoKind.WARHAMMER, EvoKind.GREATSWORD, EvoKind.DAGGER, EvoKind.HALBERD,
						EvoKind.STAFF, EvoKind.BLAST_BLADE},
				{EvoKind.ARCANE_WAND, EvoKind.THUNDER_GAUNTLET, EvoKind.CELESTIAL_ORB, EvoKind.VOID_GRIMOIRE,
						EvoKind.SOLAR_CHAKRAM, EvoKind.SPIRIT_LANTERN, EvoKind.EVOLIUM_BLASTER}};
		String[] names = {"09_evolved_tools", "10_modded_weapons", "11_magic_weapons"};
		stand(server, spot, vista, 0.0F);
		for (int r = 0; r < rows.length; r++) {
			EvoKind[] row = rows[r];
			server.runOnServer(s -> {
				ServerWorld world = s.getOverworld();
				Vec3d eye = spot.add(0, 1.62, 0);
				for (int i = 0; i < row.length; i++) {
					float yaw = vista + (i - (row.length - 1) / 2.0F) * 12.0F;
					Vec3d pos = eye.add(Vec3d.fromPolar(0.0F, yaw).multiply(6.5)).add(0, -0.1, 0);
					GALLERY.add(GiantItem.create(world, evolved(s, row[i], 100), ItemDisplayContext.FIXED, pos, yaw,
							new Quaternionf(), 1.6F, 1));
				}
			});
			context.waitTicks(25);
			server.runCommand(TIME);
			shot(context, names[r]);
			server.runOnServer(s -> {
				GALLERY.forEach(GiantItem::discard);
				GALLERY.clear();
			});
			context.waitTicks(5);
		}
	}

	/** Le portail en blocs de rubis, allumé. */
	private static void portalShot(ClientGameTestContext context, TestServerContext server) {
		server.runOnServer(s -> {
			ServerWorld world = s.getOverworld();
			Vec3d at = ahead(vista, 9.0);
			BlockPos base = fr.evotool.dimension.RubyPortal.build(world, MathHelper.floor(at.x), MathHelper.floor(at.z));
			// face au portail (il s'étend sur l'axe X) : 7 blocs devant lui
			Vec3d center = new Vec3d(base.getX() + 1.0, base.getY() + 1.6, base.getZ() + 0.5);
			double side = spot.z < center.z ? -7.0 : 7.0;
			Vec3d feet = new Vec3d(center.x, 0, center.z + side);
			feet = new Vec3d(feet.x, Math.max(ground(world, feet.x, feet.z), base.getY()), feet.z);
			aimSpot = feet;
			look(feet.add(0, 1.62, 0), center);
		});
		stand(server, aimSpot, aimYaw, aimPitch);
		context.waitTicks(40);
		server.runCommand(TIME);
		shot(context, "12_ruby_portal");
	}

	/** Le sanctuaire et la Cloche Divine (forme finale du boss) : de loin, puis de près. */
	private static void bossShots(ClientGameTestContext context, TestServerContext server, TestClientWorldContext world) {
		server.runOnServer(s -> {
			ServerWorld overworld = s.getOverworld();
			Vec3d at = ahead(vista + 180.0F, 26.0);
			BlockPos altar = fr.evotool.dimension.Sanctuary.build(overworld, MathHelper.floor(at.x), MathHelper.floor(at.z),
					Heightmap.Type.MOTION_BLOCKING_NO_LEAVES);
			fr.evotool.boss.BellBossEntity boss = fr.evotool.dimension.EvoDimension.spawnBoss(overworld, altar.up(3),
					fr.evotool.boss.BellBossEntity.FORMS - 1);
			if (boss != null) {
				boss.setAiDisabled(true);
				boss.setNoGravity(true);
			}
			bossCenter = Vec3d.ofCenter(altar).add(0, 5.0, 0);
			Vec3d toSpot = spot.subtract(Vec3d.ofCenter(altar));
			toSpot = new Vec3d(toSpot.x, 0, toSpot.z).normalize();
			Vec3d near = Vec3d.ofCenter(altar).add(toSpot.multiply(15.0));
			aimSpot = new Vec3d(near.x, ground(overworld, near.x, near.z), near.z);
			look(spot.add(0, 1.62, 0), bossCenter);
		});
		stand(server, spot, aimYaw, aimPitch);
		context.waitTicks(60);
		chunks(world);
		server.runCommand(TIME);
		shot(context, "13_sanctuary_divine_bell");
		server.runOnServer(s -> look(aimSpot.add(0, 1.62, 0), bossCenter));
		stand(server, aimSpot, aimYaw, aimPitch);
		context.waitTicks(30);
		server.runCommand(TIME);
		shot(context, "14_divine_bell_boss");
		server.runCommand("kill @e[type=evotool:bell_boss]");
		context.waitTicks(10);
	}

	private static Vec3d bossCenter;

	/** La dimension évolutive à sa dernière phase. */
	private static void dimensionShot(ClientGameTestContext context, TestServerContext server, TestClientWorldContext world) {
		server.runOnServer(s -> {
			ServerWorld evo = s.getWorld(fr.evotool.dimension.EvoDimension.WORLD);
			if (evo == null) {
				return;
			}
			int y = evo.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, 8, 8);
			player(s).teleport(evo, 8.5, y, 8.5, Set.of(), 30.0F, 8.0F, true);
		});
		context.waitTicks(200);
		chunks(world);
		server.runOnServer(s -> fr.evotool.dimension.EvoDimension.setPhase(player(s), fr.evotool.dimension.EvoDimension.MAX_PHASE));
		context.waitTicks(80);
		chunks(world);
		shot(context, "15_evolving_dimension");
		server.runOnServer(s -> player(s).teleport(s.getOverworld(), spot.x, spot.y, spot.z, Set.of(), vista, 10.0F, true));
		context.waitTicks(100);
	}

	/** Le manuel illustré, ouvert sur une page avec une image. */
	private static void manualShot(ClientGameTestContext context) {
		context.runOnClient(client -> {
			fr.evotool.client.ManualScreen manual = new fr.evotool.client.ManualScreen();
			client.setScreen(manual);
			manual.open(2, 1);
		});
		context.waitTicks(10);
		shot(context, "16_illustrated_manual");
		context.runOnClient(client -> client.setScreen(null));
		context.waitTicks(5);
	}

	// ------------------------------------------------------------------ préparation

	/** Cherche un bosquet de cerisiers, puis son point le plus haut à découvert, et la direction la plus dégagée. */
	private static void findSpot(MinecraftServer server) {
		ServerWorld world = server.getOverworld();
		BlockPos start = player(server).getBlockPos();
		com.mojang.datafixers.util.Pair<BlockPos, RegistryEntry<Biome>> found = world.locateBiome(
				entry -> entry.matchesKey(BiomeKeys.CHERRY_GROVE), start, 3200, 32, 64);
		BlockPos center = found != null ? found.getFirst() : start;
		BlockPos best = null;
		for (int dx = -32; dx <= 32; dx += 16) {
			for (int dz = -32; dz <= 32; dz += 16) {
				int x = center.getX() + dx;
				int z = center.getZ() + dz;
				int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, x, z);
				BlockPos below = new BlockPos(x, ground - 1, z);
				if (top != ground || !world.getFluidState(below).isEmpty()) {
					continue;
				}
				if (best == null || ground > best.getY()) {
					best = new BlockPos(x, ground, z);
				}
			}
		}
		if (best == null) {
			best = new BlockPos(center.getX(), world.getTopY(Heightmap.Type.MOTION_BLOCKING, center.getX(), center.getZ()), center.getZ());
		}
		spot = Vec3d.ofBottomCenter(best);
		double lowest = Double.MAX_VALUE;
		for (int i = 0; i < 8; i++) {
			float yaw = i * 45.0F;
			Vec3d dir = Vec3d.fromPolar(0.0F, yaw);
			double sum = 0;
			for (int d = 16; d <= 48; d += 16) {
				sum += world.getTopY(Heightmap.Type.MOTION_BLOCKING, MathHelper.floor(spot.x + dir.x * d), MathHelper.floor(spot.z + dir.z * d));
			}
			if (sum < lowest) {
				lowest = sum;
				vista = yaw;
			}
		}
		EvoTool.LOGGER.info("EVOTOOL SHOWCASE spot {} (cherry grove: {}), vista yaw {}", spot, found != null, vista);
	}

	/** Une direction où une toile tirée vers le haut s'accroche entre 10 et 40 blocs (sinon, un pilier). */
	private static void findSwing(ServerWorld world) {
		Vec3d eye = spot.add(0, 1.62, 0);
		double bestScore = Double.MAX_VALUE;
		for (float pitch : new float[] {-30.0F, -20.0F, -40.0F, -12.0F}) {
			for (int i = 0; i < 16; i++) {
				float yaw = i * 22.5F;
				Vec3d end = eye.add(Vec3d.fromPolar(pitch, yaw).multiply(48.0));
				BlockHitResult hit = world.raycast(new RaycastContext(eye, end, RaycastContext.ShapeType.COLLIDER,
						RaycastContext.FluidHandling.NONE, player(world.getServer())));
				if (hit.getType() != HitResult.Type.BLOCK) {
					continue;
				}
				double distance = hit.getPos().distanceTo(eye);
				double score = Math.abs(distance - 24.0);
				if (distance >= 10.0 && distance <= 40.0 && score < bestScore) {
					bestScore = score;
					aimYaw = yaw;
					aimPitch = pitch;
				}
			}
			if (bestScore < Double.MAX_VALUE) {
				return;
			}
		}
		// rien à portée : un pilier de pierre taillée devant le belvédère
		Vec3d at = ahead(vista, 18.0);
		int x = MathHelper.floor(at.x);
		int z = MathHelper.floor(at.z);
		int y0 = (int) ground(world, at.x, at.z);
		for (int y = y0; y < y0 + 24; y++) {
			world.setBlockState(new BlockPos(x, y, z), net.minecraft.block.Blocks.POLISHED_DEEPSLATE.getDefaultState());
		}
		look(eye, new Vec3d(x + 0.5, y0 + 20.0, z + 0.5));
	}

	/** Armure abyssale niveau 150, plastron aux ailes de l'infini. */
	private static void equip(MinecraftServer server) {
		ServerPlayerEntity player = player(server);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		EvoKind[] pieces = {EvoKind.HELMET, EvoKind.CHESTPLATE, EvoKind.LEGGINGS, EvoKind.BOOTS};
		for (int i = 0; i < slots.length; i++) {
			ItemStack piece = new ItemStack(ModItems.itemFor(pieces[i]));
			AbyssalArmor.apply(piece);
			piece.set(ModComponents.EVO_DATA, new EvoData(150, 0, 0, -1, 0L));
			if (pieces[i] == EvoKind.CHESTPLATE) {
				EvoliteUpgrade.apply(piece);
			}
			EvoProgression.refresh(piece, server.getRegistryManager());
			player.equipStack(slots[i], piece);
		}
		player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
	}

	private static ItemStack evolved(MinecraftServer server, EvoKind kind, int level) {
		ItemStack stack = new ItemStack(ModItems.itemFor(kind));
		stack.set(ModComponents.EVO_DATA, new EvoData(level, 0, 0, -1, 0L));
		EvoProgression.refresh(stack, server.getRegistryManager());
		return stack;
	}

	/** Une créature immobile, tournée vers le belvédère. */
	private static MobEntity mob(ServerWorld world, EntityType<? extends MobEntity> type, Vec3d pos, ItemStack weapon) {
		MobEntity mob = type.create(world, SpawnReason.COMMAND);
		if (mob == null) {
			return null;
		}
		double dx = spot.x - pos.x;
		double dz = spot.z - pos.z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		mob.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
		mob.setHeadYaw(yaw);
		mob.setBodyYaw(yaw);
		mob.setAiDisabled(true);
		mob.setPersistent();
		mob.equipStack(EquipmentSlot.MAINHAND, weapon);
		world.spawnEntity(mob);
		return mob;
	}

	// ------------------------------------------------------------------ outils

	/** Attend que les tronçons autour soient reçus et dessinés (2 minutes au plus ; sinon on continue quand même). */
	private static void chunks(TestClientWorldContext world) {
		try {
			world.waitForChunksRender(true, 20 * 120);
		} catch (AssertionError e) {
			EvoTool.LOGGER.warn("EVOTOOL SHOWCASE chunks still loading, going on");
		}
	}

	private static void shot(ClientGameTestContext context, String name) {
		context.runOnClient(client -> client.getToastManager().clear());
		context.waitTicks(2);
		context.takeScreenshot(TestScreenshotOptions.of("showcase_" + name).withSize(WIDTH, HEIGHT));
	}

	private static void stand(TestServerContext server, Vec3d pos, float yaw, float pitch) {
		server.runOnServer(s -> player(s).teleport(s.getOverworld(), pos.x, pos.y, pos.z, Set.of(), yaw, pitch, true));
	}

	private static void perspective(ClientGameTestContext context, Perspective perspective) {
		context.runOnClient(client -> client.options.setPerspective(perspective));
		context.waitTicks(3);
	}

	/** Rotation (aimYaw, aimPitch) pour regarder {@code to} depuis {@code from}. */
	private static void look(Vec3d from, Vec3d to) {
		double dx = to.x - from.x;
		double dy = to.y - from.y;
		double dz = to.z - from.z;
		aimYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
	}

	private static Vec3d ahead(float yaw, double distance) {
		return spot.add(Vec3d.fromPolar(0.0F, yaw).multiply(distance));
	}

	private static double ground(ServerWorld world, double x, double z) {
		return world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(x), MathHelper.floor(z));
	}

	private static ServerPlayerEntity player(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}
}
