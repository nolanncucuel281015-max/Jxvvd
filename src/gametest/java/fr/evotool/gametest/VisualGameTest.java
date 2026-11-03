package fr.evotool.gametest;

import fr.evotool.EvoTool;
import fr.evotool.armor.AbyssalArmor;
import fr.evotool.client.EvoToolClient;
import fr.evotool.armor.Tentacles;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestClientWorldContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Test visuel dans le vrai client (CI, commit contenant [visual]) : équipe l'armure abyssale niveau 150 et prend
 * des captures d'écran du coup de tentacule, de la prise et du mode déplacement, en vue à la première et à la
 * troisième personne. La CI les affiche dans son journal.
 */
public class VisualGameTest implements FabricClientGameTest {
	private static LivingEntity target;

	@Override
	public void runTest(ClientGameTestContext context) {
		// rendu logiciel dans la CI : petit monde à charger. Serveur dédié à part (un serveur intégré avancerait
		// au rythme du client, beaucoup trop lent ici pour préparer le monde à temps).
		context.runOnClient(client -> {
			client.options.getViewDistance().setValue(3);
			client.options.getSimulationDistance().setValue(5);
		});
		try (TestDedicatedServerContext server = context.worldBuilder().createServer();
				TestServerConnection connection = server.connect()) {
			run(context, server, connection.getClientWorld());
		}
	}

	private static void run(ClientGameTestContext context, TestServerContext gameServer, TestClientWorldContext clientWorld) {
		Game game = new Game(gameServer, clientWorld);
		game.getClientWorld().waitForChunksRender();
		game.getServer().runCommand("time set noon");
		game.getServer().runCommand("weather clear");
		game.getServer().runOnServer(VisualGameTest::equip);
		// la vache droit devant, le viseur sur elle
		game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 6");
		context.waitTicks(20);
		game.getServer().runOnServer(VisualGameTest::spawnTarget);
		context.waitTicks(20);
		game.getClientWorld().waitForChunksRender();
		context.takeScreenshot("evotool_00_rest_first");
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.takeScreenshot("evotool_01_rest_third");

		// clic gauche à main nue, avec la vraie souris : le coup
		perspective(context, Perspective.FIRST_PERSON);
		context.getInput().pressKey(options -> options.attackKey);
		context.waitTicks(4);
		context.takeScreenshot("evotool_02_strike_first");
		log(game, "strike first");
		context.waitTicks(40);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.getInput().pressKey(options -> options.attackKey);
		context.waitTicks(5);
		context.takeScreenshot("evotool_03_strike_third");
		log(game, "strike third");
		context.waitTicks(40);

		// clic droit à main nue : la prise, puis le lancer
		perspective(context, Perspective.FIRST_PERSON);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(25);
		context.takeScreenshot("evotool_04_grab_first");
		log(game, "grab");
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.takeScreenshot("evotool_05_grab_third");
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(30);
		log(game, "throw");

		// touche J : le mode déplacement
		context.getInput().pressKey(EvoToolClient.TENTACLES);
		context.waitTicks(40);
		context.takeScreenshot("evotool_06_travel_third");
		perspective(context, Perspective.FIRST_PERSON);
		context.takeScreenshot("evotool_07_travel_first");
		context.getInput().pressKey(EvoToolClient.TENTACLES);
		context.waitTicks(20);

		// le combineur élémentaire : le menu, l'animation de fusion, le résultat
		game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 35");
		game.getServer().runOnServer(VisualGameTest::placeCombiner);
		context.waitTicks(10);
		game.getServer().runOnServer(server -> player(server).openHandledScreen(combiner(server)));
		context.waitTicks(10);
		context.takeScreenshot("evotool_08_combiner_gui");
		context.runOnClient(client -> client.player.closeHandledScreen());
		context.waitTicks(5);
		game.getServer().runOnServer(server -> combiner(server).start());
		context.waitTicks(25);
		context.takeScreenshot("evotool_09_combiner_fusion");
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.takeScreenshot("evotool_10_combiner_fusion_third");
		perspective(context, Perspective.FIRST_PERSON);
		context.waitTicks(45);
		game.getServer().runOnServer(server -> player(server).openHandledScreen(combiner(server)));
		context.waitTicks(10);
		context.takeScreenshot("evotool_11_combiner_done");
		game.getServer().runOnServer(server -> EvoTool.LOGGER.info("EVOTOOL VISUAL combiner result: {}",
				combiner(server).items().getStack(fr.evotool.combiner.ElementalCombinerBlockEntity.OUTPUT_SLOT)));
		context.runOnClient(client -> client.player.closeHandledScreen());
		context.waitTicks(5);

		// le grimoire en main : une boule de feu (trait), puis le brasier (explosion), avec la vraie souris
		game.getServer().runOnServer(server -> {
			ServerPlayerEntity player = player(server);
			ItemStack grimoire = combiner(server).items().getStack(fr.evotool.combiner.ElementalCombinerBlockEntity.OUTPUT_SLOT).copy();
			combiner(server).items().setStack(fr.evotool.combiner.ElementalCombinerBlockEntity.OUTPUT_SLOT, ItemStack.EMPTY);
			player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, grimoire);
			player.getEntityWorld().setBlockState(combinerPos, net.minecraft.block.Blocks.AIR.getDefaultState());
		});
		game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 4");
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(3);
		context.takeScreenshot("evotool_12_spell_bolt");
		context.waitTicks(30);
		// accroupi (déjà quelques ticks, comme en jeu) + clic droit : sort suivant
		context.getInput().holdKey(options -> options.sneakKey);
		context.waitTicks(4);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(3);
		context.getInput().releaseKey(options -> options.sneakKey);
		context.waitTicks(5);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(4);
		context.takeScreenshot("evotool_13_spell_burst");
		game.getServer().runOnServer(server -> EvoTool.LOGGER.info("EVOTOOL VISUAL grimoire: {}",
				player(server).getMainHandStack().get(fr.evotool.component.ModComponents.GRIMOIRE)));
		context.waitTicks(20);

		// le vol de feu (4e sort du feu), vu de derrière
		game.getServer().runOnServer(server -> {
			ItemStack book = player(server).getMainHandStack();
			book.set(fr.evotool.component.ModComponents.GRIMOIRE, new fr.evotool.element.Grimoire(
					java.util.List.of(fr.evotool.element.Element.FIRE), 3));
		});
		game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 -20");
		context.waitTicks(80);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(15);
		context.takeScreenshot("evotool_14_fire_flight");
		game.getServer().runOnServer(server -> EvoTool.LOGGER.info("EVOTOOL VISUAL fire flight: y={} ", player(server).getY()));
		context.waitTicks(60);
		perspective(context, Perspective.FIRST_PERSON);

		// le gant de Spider-Man : la toile accrochée au sol, loin devant (vue de derrière)
		game.getServer().runOnServer(server -> player(server).setStackInHand(net.minecraft.util.Hand.MAIN_HAND,
				new ItemStack(fr.evotool.item.ModItems.SPIDER_GLOVE)));
		game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 12");
		context.waitTicks(30);
		perspective(context, Perspective.THIRD_PERSON_BACK);
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(6);
		context.takeScreenshot("evotool_15_spider_web");
		game.getServer().runOnServer(server -> EvoTool.LOGGER.info("EVOTOOL VISUAL web anchor on server: {}, glove in hand: {}",
				fr.evotool.spider.SpiderGlove.anchor(player(server)), player(server).getMainHandStack()));
		context.getInput().pressKey(options -> options.useKey);
		context.waitTicks(10);
		perspective(context, Perspective.FIRST_PERSON);

		// le manuel : la page du rituel (recette dessinée) et une page avec une image
		context.runOnClient(client -> {
			fr.evotool.client.ManualScreen manual = new fr.evotool.client.ManualScreen();
			client.setScreen(manual);
			manual.open(9, 0);
		});
		context.waitTicks(5);
		context.takeScreenshot("evotool_16_manual_ritual");
		context.runOnClient(client -> {
			if (client.currentScreen instanceof fr.evotool.client.ManualScreen manual) {
				manual.open(2, 1);
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("evotool_17_manual_image");
		context.runOnClient(client -> {
			if (client.currentScreen instanceof fr.evotool.client.ManualScreen manual) {
				manual.open(3, 0);
			}
		});
		context.waitTicks(5);
		context.takeScreenshot("evotool_18_manual_ores");
		context.runOnClient(client -> client.setScreen(null));
		context.waitTicks(5);
	}

	private static net.minecraft.util.math.BlockPos combinerPos;

	private static void placeCombiner(MinecraftServer server) {
		ServerPlayerEntity player = player(server);
		combinerPos = player.getBlockPos().add(0, 0, 3);
		player.getEntityWorld().setBlockState(combinerPos, fr.evotool.block.ModBlocks.ELEMENTAL_COMBINER.getDefaultState());
		fr.evotool.combiner.ElementalCombinerBlockEntity combiner = combiner(server);
		// un livre + un briquet : un grimoire de feu ; des ingrédients dans l'inventaire pour le panneau
		combiner.items().setStack(0, new ItemStack(net.minecraft.item.Items.BOOK));
		combiner.items().setStack(1, new ItemStack(net.minecraft.item.Items.FLINT_AND_STEEL));
		player.getInventory().insertStack(new ItemStack(net.minecraft.item.Items.PACKED_ICE, 8));
		player.getInventory().insertStack(new ItemStack(net.minecraft.item.Items.GUNPOWDER, 8));
		player.getInventory().insertStack(new ItemStack(net.minecraft.item.Items.INK_SAC, 8));
	}

	private static fr.evotool.combiner.ElementalCombinerBlockEntity combiner(MinecraftServer server) {
		return (fr.evotool.combiner.ElementalCombinerBlockEntity) player(server).getEntityWorld().getBlockEntity(combinerPos);
	}

	private static void perspective(ClientGameTestContext context, Perspective perspective) {
		context.runOnClient(client -> client.options.setPerspective(perspective));
		context.waitTicks(3);
	}

	private static void log(Game game, String step) {
		game.getServer().runOnServer(server -> EvoTool.LOGGER.info("EVOTOOL VISUAL {}: strikes={} grabbed={}", step,
				Tentacles.activeStrikes(), Tentacles.grabbed(player(server))));
	}

	/** Le serveur et le monde du client du test. */
	private record Game(TestServerContext server, TestClientWorldContext clientWorld) {
		TestServerContext getServer() {
			return server;
		}

		TestClientWorldContext getClientWorld() {
			return clientWorld;
		}
	}

	private static ServerPlayerEntity player(MinecraftServer server) {
		return server.getPlayerManager().getPlayerList().get(0);
	}

	private static void equip(MinecraftServer server) {
		ServerPlayerEntity player = player(server);
		EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		EvoKind[] pieces = {EvoKind.HELMET, EvoKind.CHESTPLATE, EvoKind.LEGGINGS, EvoKind.BOOTS};
		for (int i = 0; i < slots.length; i++) {
			ItemStack piece = new ItemStack(ModItems.itemFor(pieces[i]));
			AbyssalArmor.apply(piece);
			piece.set(ModComponents.EVO_DATA, new EvoData(150, 0, 0, -1, 0L));
			EvoProgression.refresh(piece, server.getRegistryManager());
			player.equipStack(slots[i], piece);
		}
	}

	private static void spawnTarget(MinecraftServer server) {
		ServerPlayerEntity player = player(server);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		CowEntity cow = EntityType.COW.create(world, SpawnReason.COMMAND);
		if (cow != null) {
			cow.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ() + 8.0, 180.0F, 0.0F);
			cow.setAiDisabled(true);
			cow.setInvulnerable(true);   // les coups de tentacule ne doivent pas la tuer avant la prise
			world.spawnEntity(cow);
			target = cow;
		}
	}
}
