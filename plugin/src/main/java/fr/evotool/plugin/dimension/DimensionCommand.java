package fr.evotool.plugin.dimension;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.util.Lang;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * {@code /dimension} (opérateurs) : {@code tp} (y aller, ou {@code tp sanctuaire}), {@code speed [minutes]},
 * {@code phase <0-5>}, et ce que d'autres modules ajoutent ({@code spawn}, {@code locate}, {@code evolve} : le boss
 * et les sanctuaires).
 */
public final class DimensionCommand implements BasicCommand {
	/** Sous-commandes ajoutées par d'autres modules : renvoient vrai si elles ont répondu. */
	private static final java.util.Map<String, BiFunction<Player, String[], Boolean>> EXTRA = new java.util.LinkedHashMap<>();

	public static void add(String name, BiFunction<Player, String[], Boolean> action) {
		EXTRA.put(name, action);
	}

	public static void enable() {
		EvoToolPlugin.instance().getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register("dimension", "Evo Tool : la dimension évolutive", List.of(), new DimensionCommand()));
	}

	@Override
	public String permission() {
		return "evotool.admin";
	}

	@Override
	public void execute(CommandSourceStack source, String[] args) {
		CommandSender sender = source.getSender();
		if (!(source.getExecutor() instanceof Player player)) {
			sender.sendMessage(Lang.tr("plugin.evotool.command.player_only").color(NamedTextColor.RED));
			return;
		}
		String sub = args.length == 0 ? "tp" : args[0].toLowerCase(Locale.ROOT);
		switch (sub) {
			case "tp" -> {
				if (args.length >= 2 && EXTRA.containsKey("tp_" + args[1].toLowerCase(Locale.ROOT))) {
					EXTRA.get("tp_" + args[1].toLowerCase(Locale.ROOT)).apply(player, args);
					return;
				}
				World world = EvoDimension.world();
				if (world == null) {
					sender.sendMessage(Lang.tr("command.evotool.dimension.missing").color(NamedTextColor.RED));
					return;
				}
				int x = player.getLocation().getBlockX();
				int z = player.getLocation().getBlockZ();
				world.getChunkAt(x >> 4, z >> 4);
				int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
				player.teleport(new Location(world, x + 0.5, Math.max(world.getMinHeight() + 2, y), z + 0.5, player.getLocation().getYaw(), 0));
			}
			case "speed" -> {
				if (args.length >= 2) {
					try {
						EvoDimension.setMinutes(Integer.parseInt(args[1]));
						sender.sendMessage(Lang.tr("command.evotool.dimension.speed_set", EvoDimension.minutes()).color(NamedTextColor.GREEN));
					} catch (NumberFormatException e) {
						sender.sendMessage(Lang.tr("command.evotool.dimension.speed", EvoDimension.minutes()).color(NamedTextColor.GRAY));
					}
				} else {
					sender.sendMessage(Lang.tr("command.evotool.dimension.speed", EvoDimension.minutes()).color(NamedTextColor.GRAY));
				}
			}
			case "phase" -> {
				int phase = 0;
				try {
					phase = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
				} catch (NumberFormatException e) {
					phase = 0;
				}
				EvoDimension.setPhase(player, phase);
				sender.sendMessage(Lang.tr("command.evotool.dimension.phase", EvoDimension.phase(player), EvoDimension.MAX_PHASE)
						.color(NamedTextColor.LIGHT_PURPLE));
			}
			default -> {
				BiFunction<Player, String[], Boolean> extra = EXTRA.get(sub);
				if (extra == null || !extra.apply(player, args)) {
					sender.sendMessage(net.kyori.adventure.text.Component.text("/dimension tp | speed [minutes] | phase <0-5>"
							+ (EXTRA.isEmpty() ? "" : " | " + String.join(" | ", EXTRA.keySet().stream().filter(k -> !k.startsWith("tp_")).toList())),
							NamedTextColor.GRAY));
				}
			}
		}
	}

	@Override
	public Collection<String> suggest(CommandSourceStack source, String[] args) {
		List<String> subs = new ArrayList<>(List.of("tp", "speed", "phase"));
		EXTRA.keySet().stream().filter(k -> !k.startsWith("tp_")).forEach(subs::add);
		if (args.length <= 1) {
			String p = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
			return subs.stream().filter(s -> s.startsWith(p)).toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("tp")) {
			return EXTRA.keySet().stream().filter(k -> k.startsWith("tp_")).map(k -> k.substring(3)).toList();
		}
		if (args.length == 2 && args[0].equalsIgnoreCase("phase")) {
			return List.of("0", "1", "2", "3", "4", "5");
		}
		return List.of();
	}
}
