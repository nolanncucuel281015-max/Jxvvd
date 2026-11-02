package fr.evotool.gametest;

import fr.evotool.armor.AbyssalArmor;
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
		{
			game.getClientWorld().waitForChunksRender();
			game.getServer().runCommand("time set noon");
			game.getServer().runCommand("weather clear");
			game.getServer().runOnServer(VisualGameTest::equip);
			game.getServer().runCommand("execute as @a at @s run tp @s ~ ~ ~ 0 8");
			context.waitTicks(20);
			game.getServer().runOnServer(VisualGameTest::spawnTarget);
			context.waitTicks(20);
			game.getClientWorld().waitForChunksRender();
			context.takeScreenshot("evotool_00_base");

			// clic gauche : le coup
			game.getServer().runOnServer(server -> Tentacles.strike(player(server)));
			context.waitTicks(4);
			context.takeScreenshot("evotool_01_strike_first");
			context.waitTicks(30);
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(5);
			game.getServer().runOnServer(server -> Tentacles.strike(player(server)));
			context.waitTicks(5);
			context.takeScreenshot("evotool_02_strike_third");
			context.waitTicks(30);

			// clic droit : la prise
			game.getServer().runOnServer(server -> Tentacles.grabEntity(player(server), target));
			context.waitTicks(30);
			context.takeScreenshot("evotool_03_grab_third");
			context.runOnClient(client -> client.options.setPerspective(Perspective.FIRST_PERSON));
			context.waitTicks(5);
			context.takeScreenshot("evotool_04_grab_first");
			game.getServer().runOnServer(server -> Tentacles.grabOrThrow(player(server)));
			context.waitTicks(40);

			// touche J : le mode déplacement
			game.getServer().runOnServer(server -> Tentacles.toggle(player(server)));
			context.waitTicks(40);
			context.takeScreenshot("evotool_05_travel_first");
			context.runOnClient(client -> client.options.setPerspective(Perspective.THIRD_PERSON_BACK));
			context.waitTicks(10);
			context.takeScreenshot("evotool_06_travel_third");
			game.getServer().runOnServer(server -> Tentacles.toggle(player(server)));
			context.waitTicks(10);
		}
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
			cow.refreshPositionAndAngles(player.getX() + 1.5, player.getY(), player.getZ() + 8.0, 180.0F, 0.0F);
			cow.setAiDisabled(true);
			world.spawnEntity(cow);
			target = cow;
		}
	}
}
