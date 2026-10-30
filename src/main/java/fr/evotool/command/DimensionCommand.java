package fr.evotool.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.evotool.block.ModBlocks;
import fr.evotool.boss.BellBossEntity;
import fr.evotool.boss.ModEntities;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.dimension.Sanctuary;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;

/**
 * {@code /dimension} (opérateurs) :
 * <ul>
 * <li>{@code spawn [forme]} : fait apparaître le Slime Cloche devant soi (forme 0 à 5) ;</li>
 * <li>{@code locate} : coordonnées du sanctuaire le plus proche ;</li>
 * <li>{@code speed [minutes]} : minutes entre deux évolutions (blocs et boss), 5 par défaut ;</li>
 * <li>{@code tp [sanctuaire]} : aller dans la dimension (ou en revenir), ou directement au sanctuaire ;</li>
 * <li>{@code phase <0-5>} : règle la phase d'évolution du joueur ;</li>
 * <li>{@code evolve} : le boss le plus proche évolue tout de suite.</li>
 * </ul>
 */
public final class DimensionCommand {
	private DimensionCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("dimension")
				.requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
				.then(CommandManager.literal("spawn")
						.executes(context -> spawn(context.getSource(), 0))
						.then(CommandManager.argument("forme", IntegerArgumentType.integer(0, BellBossEntity.FORMS - 1))
								.executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "forme")))))
				.then(CommandManager.literal("locate").executes(context -> locate(context.getSource())))
				.then(CommandManager.literal("speed")
						.executes(context -> showSpeed(context.getSource()))
						.then(CommandManager.argument("minutes", IntegerArgumentType.integer(1, EvoDimension.MAX_MINUTES))
								.executes(context -> setSpeed(context.getSource(), IntegerArgumentType.getInteger(context, "minutes")))))
				.then(CommandManager.literal("tp")
						.executes(context -> travel(context.getSource()))
						.then(CommandManager.literal("sanctuaire").executes(context -> toSanctuary(context.getSource())))
						.then(CommandManager.literal("sanctuary").executes(context -> toSanctuary(context.getSource()))))
				.then(CommandManager.literal("phase")
						.then(CommandManager.argument("phase", IntegerArgumentType.integer(0, EvoDimension.MAX_PHASE))
								.executes(context -> phase(context.getSource(), IntegerArgumentType.getInteger(context, "phase")))))
				.then(CommandManager.literal("evolve").executes(context -> evolve(context.getSource()))));
	}

	private static int spawn(ServerCommandSource source, int form) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d look = player.getRotationVector();
		Vec3d front = new Vec3d(player.getX() + look.x * 5, player.getY(), player.getZ() + look.z * 5);
		BlockPos pos = BlockPos.ofFloored(front);
		BellBossEntity boss = EvoDimension.spawnBoss(world, pos, form);
		if (boss == null) {
			source.sendError(Text.translatable("command.evotool.dimension.spawn_failed"));
			return 0;
		}
		source.sendFeedback(() -> Text.translatable("command.evotool.dimension.spawned",
				Text.translatable(BellBossEntity.formKey(form)), EvoDimension.minutes()).formatted(Formatting.GOLD), true);
		return 1;
	}

	private static ServerWorld dimension(ServerCommandSource source) {
		ServerWorld world = source.getServer().getWorld(EvoDimension.WORLD);
		if (world == null) {
			source.sendError(Text.translatable("command.evotool.dimension.missing"));
		}
		return world;
	}

	private static int locate(ServerCommandSource source) {
		ServerWorld world = dimension(source);
		if (world == null) {
			return 0;
		}
		Vec3d from = source.getPosition();
		BlockPos sanctuary = Sanctuary.nearest(world.getSeed(), (int) Math.floor(from.x), (int) Math.floor(from.z));
		int distance = (int) Math.round(Math.sqrt(Math.pow(sanctuary.getX() - from.x, 2) + Math.pow(sanctuary.getZ() - from.z, 2)));
		boolean inside = source.getWorld().getRegistryKey() == EvoDimension.WORLD;
		Text coordinates = Text.literal("[" + sanctuary.getX() + ", ~, " + sanctuary.getZ() + "]")
				.styled(style -> style.withColor(Formatting.GREEN).withUnderline(true)
						.withClickEvent(new ClickEvent.RunCommand("/dimension tp sanctuaire")));
		source.sendFeedback(() -> Text.translatable(inside ? "command.evotool.dimension.locate" : "command.evotool.dimension.locate_outside",
				coordinates, distance).formatted(Formatting.LIGHT_PURPLE), false);
		return distance;
	}

	private static int showSpeed(ServerCommandSource source) {
		source.sendFeedback(() -> Text.translatable("command.evotool.dimension.speed", EvoDimension.minutes())
				.formatted(Formatting.LIGHT_PURPLE), false);
		return EvoDimension.minutes();
	}

	private static int setSpeed(ServerCommandSource source, int minutes) {
		EvoDimension.setMinutes(source.getServer(), minutes);
		source.sendFeedback(() -> Text.translatable("command.evotool.dimension.speed_set", EvoDimension.minutes())
				.formatted(Formatting.GOLD), true);
		return EvoDimension.minutes();
	}

	/** Sol à (x, z) dans {@code world} (le chunk est chargé d'abord). */
	private static Vec3d surface(ServerWorld world, int x, int z) {
		world.getChunk(x >> 4, z >> 4);
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= world.getBottomY() + 1) {
			y = 100;
		}
		return new Vec3d(x + 0.5, y, z + 0.5);
	}

	private static void teleport(ServerPlayerEntity player, ServerWorld world, Vec3d pos) {
		player.teleportTo(new TeleportTarget(world, pos, Vec3d.ZERO, player.getYaw(), player.getPitch(),
				TeleportTarget.ADD_PORTAL_CHUNK_TICKET));
	}

	private static int travel(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		boolean inside = player.getEntityWorld().getRegistryKey() == EvoDimension.WORLD;
		ServerWorld destination = inside ? source.getServer().getOverworld() : dimension(source);
		if (destination == null) {
			return 0;
		}
		teleport(player, destination, surface(destination, player.getBlockX(), player.getBlockZ()));
		return 1;
	}

	private static int toSanctuary(ServerCommandSource source) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ServerWorld world = dimension(source);
		if (world == null) {
			return 0;
		}
		BlockPos center = Sanctuary.nearest(world.getSeed(), player.getBlockX(), player.getBlockZ());
		// on arrive sur le parvis, face à l'autel
		teleport(player, world, surface(world, center.getX(), center.getZ() + 7));
		BlockPos altar = findAltar(world, center);
		source.sendFeedback(() -> Text.translatable(altar != null ? "command.evotool.dimension.tp_sanctuary"
				: "command.evotool.dimension.tp_sanctuary_missing").formatted(Formatting.LIGHT_PURPLE), false);
		return 1;
	}

	/** Autel du sanctuaire centré sur {@code center} (colonne du centre), ou null. */
	public static BlockPos findAltar(ServerWorld world, BlockPos center) {
		world.getChunk(center.getX() >> 4, center.getZ() >> 4);
		BlockPos.Mutable p = new BlockPos.Mutable(center.getX(), world.getTopYInclusive(), center.getZ());
		while (p.getY() > world.getBottomY()) {
			if (world.getBlockState(p).isOf(ModBlocks.SANCTUARY_ALTAR)) {
				return p.toImmutable();
			}
			p.move(0, -1, 0);
		}
		return null;
	}

	private static int phase(ServerCommandSource source, int phase) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		EvoDimension.setPhase(player, phase);
		source.sendFeedback(() -> Text.translatable("command.evotool.dimension.phase", phase, EvoDimension.MAX_PHASE)
				.formatted(Formatting.LIGHT_PURPLE), true);
		return phase;
	}

	private static int evolve(ServerCommandSource source) {
		ServerWorld world = source.getWorld();
		Vec3d from = source.getPosition();
		List<BellBossEntity> bosses = world.getEntitiesByType(ModEntities.BELL_BOSS, new Box(from, from).expand(128), Entity::isAlive);
		BellBossEntity nearest = null;
		for (BellBossEntity boss : bosses) {
			if (nearest == null || boss.squaredDistanceTo(from) < nearest.squaredDistanceTo(from)) {
				nearest = boss;
			}
		}
		if (nearest == null) {
			source.sendError(Text.translatable("command.evotool.dimension.no_boss"));
			return 0;
		}
		if (nearest.getForm() >= BellBossEntity.FORMS - 1) {
			source.sendError(Text.translatable("command.evotool.dimension.max_form"));
			return 0;
		}
		nearest.evolve(world, nearest.getForm() + 1);
		return nearest.getForm();
	}
}
