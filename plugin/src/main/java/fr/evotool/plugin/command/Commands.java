package fr.evotool.plugin.command;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.menu.Menu;
import fr.evotool.plugin.pack.ResourcePack;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Les commandes du plugin :
 * <ul>
 * <li>{@code /evotool} : aide, {@code give <objet> [joueur] [nombre]}, {@code catalogue}, {@code pack} ;</li>
 * <li>{@code /upgrade <outil> <niveau>} (et {@code /upgrade armure <niveau>}) : comme dans le mod.</li>
 * </ul>
 * Tout sauf l'aide et le pack est réservé aux opérateurs.
 */
public final class Commands {
	private static final String ADMIN = "evotool.admin";
	/** Sous-commandes de /evotool ajoutées par d'autres modules (manuel, auto-test…). */
	private static final Map<String, BiConsumer<CommandSender, String[]>> EXTRA = new java.util.LinkedHashMap<>();

	private Commands() {
	}

	public static void add(String name, BiConsumer<CommandSender, String[]> action) {
		EXTRA.put(name, action);
	}

	public static void register(EvoToolPlugin plugin) {
		plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
			event.registrar().register("evotool", "Evo Tool : aide, objets, catalogue", List.of("evo"), new EvoToolCommand());
			event.registrar().register("upgrade", "Met un outil évolutif au niveau voulu", List.of(), new UpgradeCommand());
		});
	}

	private static boolean admin(CommandSender sender) {
		return sender.isOp() || sender.hasPermission(ADMIN);
	}

	private static List<String> starting(String prefix, Collection<String> options) {
		String p = prefix.toLowerCase(Locale.ROOT);
		return options.stream().filter(o -> o.startsWith(p)).toList();
	}

	// ------------------------------------------------------------------ /evotool

	private static final class EvoToolCommand implements BasicCommand {
		@Override
		public void execute(CommandSourceStack source, String[] args) {
			CommandSender sender = source.getSender();
			String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
			switch (sub) {
				case "give", "donner" -> give(sender, args);
				case "catalogue", "catalog" -> {
					if (!admin(sender) || !(sender instanceof Player player)) {
						sender.sendMessage(Lang.tr("plugin.evotool.command.op_only").color(NamedTextColor.RED));
						return;
					}
					Catalogue.open(player, 0);
				}
				case "pack" -> {
					if (sender instanceof Player player) {
						ResourcePack.send(player);
					}
				}
				default -> {
					BiConsumer<CommandSender, String[]> extra = EXTRA.get(sub);
					if (extra != null) {
						extra.accept(sender, args);
					} else {
						help(sender);
					}
				}
			}
		}

		@Override
		public Collection<String> suggest(CommandSourceStack source, String[] args) {
			boolean admin = admin(source.getSender());
			if (args.length <= 1) {
				List<String> subs = new ArrayList<>(List.of("help", "pack"));
				if (admin) {
					subs.addAll(List.of("give", "catalogue"));
				}
				subs.addAll(EXTRA.keySet());
				return starting(args.length == 0 ? "" : args[0], subs);
			}
			if (admin && args[0].equalsIgnoreCase("give")) {
				if (args.length == 2) {
					return starting(args[1], Arrays.stream(EvoItem.values()).map(EvoItem::id).toList());
				}
				if (args.length == 3) {
					return starting(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
				}
			}
			return List.of();
		}
	}

	private static void help(CommandSender sender) {
		sender.sendMessage(Lang.tr("plugin.evotool.help.title").color(NamedTextColor.LIGHT_PURPLE));
		for (String line : new String[] {"menu", "evolve", "power", "armor", "craft", "pack"}) {
			sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_PURPLE).append(Lang.tr("plugin.evotool.help." + line)
					.color(NamedTextColor.GRAY)));
		}
		if (admin(sender)) {
			sender.sendMessage(Component.text(" • ", NamedTextColor.DARK_PURPLE).append(Lang.tr("plugin.evotool.help.admin")
					.color(NamedTextColor.GRAY)));
		}
	}

	private static void give(CommandSender sender, String[] args) {
		if (!admin(sender)) {
			sender.sendMessage(Lang.tr("plugin.evotool.command.op_only").color(NamedTextColor.RED));
			return;
		}
		if (args.length < 2) {
			sender.sendMessage(Component.text("/evotool give <objet> [joueur] [nombre]", NamedTextColor.RED));
			return;
		}
		String id = args[1].toLowerCase(Locale.ROOT).replace("evotool:", "");
		EvoItem item = EvoItem.byId(id);
		if (item == null) {
			sender.sendMessage(Lang.tr("plugin.evotool.command.unknown_item", id).color(NamedTextColor.RED));
			return;
		}
		Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : sender instanceof Player p ? p : null;
		if (target == null) {
			sender.sendMessage(Lang.tr("plugin.evotool.command.unknown_player").color(NamedTextColor.RED));
			return;
		}
		int count = 1;
		if (args.length >= 4) {
			try {
				count = Math.max(1, Math.min(6400, Integer.parseInt(args[3])));
			} catch (NumberFormatException e) {
				count = 1;
			}
		}
		int given = 0;
		while (given < count) {
			int batch = item.stackable() ? Math.min(64, count - given) : 1;
			giveStack(target, Items.create(item, batch));
			given += batch;
		}
		sender.sendMessage(Lang.tr("plugin.evotool.command.given", count, Lang.tr(item.translationKey()), target.getName())
				.color(NamedTextColor.GREEN));
	}

	public static void giveStack(Player player, ItemStack stack) {
		for (ItemStack rest : player.getInventory().addItem(stack).values()) {
			player.getWorld().dropItem(player.getLocation(), rest);
		}
	}

	// ------------------------------------------------------------------ /upgrade

	private static final String[][] NAMES = {
			{"pickaxe", "pioche"}, {"shovel", "pelle"}, {"axe", "hache"}, {"hoe", "houe"}, {"sword", "epee"}, {"spear", "lance"},
			{"bow", "arc"}, {"mace", "masse"}, {"helmet", "casque"}, {"chestplate", "plastron"}, {"leggings", "jambieres"},
			{"boots", "bottes"}, {"scythe", "faux"}, {"katana"}, {"warhammer", "marteau"}, {"greatsword", "espadon"},
			{"dagger", "dague"}, {"halberd", "hallebarde"}, {"staff", "sceptre"}, {"blast_blade", "lame_detonante"},
			{"wand", "baguette"}, {"gauntlet", "gantelet"}, {"orb", "orbe"}, {"grimoire"}, {"chakram"}, {"lantern", "lanterne"},
			{"blaster", "canon"},
	};
	private static final EvoKind[] ARMOR = {EvoKind.HELMET, EvoKind.CHESTPLATE, EvoKind.LEGGINGS, EvoKind.BOOTS};

	private static EvoKind kindNamed(String name) {
		for (EvoKind kind : EvoKind.values()) {
			if (kind.ordinal() < NAMES.length && Arrays.asList(NAMES[kind.ordinal()]).contains(name)) {
				return kind;
			}
		}
		return null;
	}

	private static final class UpgradeCommand implements BasicCommand {
		@Override
		public void execute(CommandSourceStack source, String[] args) {
			CommandSender sender = source.getSender();
			if (!admin(sender)) {
				sender.sendMessage(Lang.tr("plugin.evotool.command.op_only").color(NamedTextColor.RED));
				return;
			}
			if (!(source.getExecutor() instanceof Player player)) {
				sender.sendMessage(Lang.tr("plugin.evotool.command.player_only").color(NamedTextColor.RED));
				return;
			}
			if (args.length < 2) {
				sender.sendMessage(Component.text("/upgrade <outil|armure> <niveau>", NamedTextColor.RED));
				return;
			}
			int level;
			try {
				level = Integer.parseInt(args[1]);
			} catch (NumberFormatException e) {
				sender.sendMessage(Component.text("/upgrade <outil|armure> <niveau>", NamedTextColor.RED));
				return;
			}
			level = Math.max(0, Math.min(Progression.ABYSS_MAX_LEVEL, level));
			String name = args[0].toLowerCase(Locale.ROOT);
			if (name.equals("armor") || name.equals("armure")) {
				for (EvoKind kind : ARMOR) {
					upgrade(sender, player, kind, level);
				}
				return;
			}
			EvoKind kind = kindNamed(name);
			if (kind == null) {
				sender.sendMessage(Lang.tr("plugin.evotool.command.unknown_item", name).color(NamedTextColor.RED));
				return;
			}
			upgrade(sender, player, kind, level);
		}

		@Override
		public Collection<String> suggest(CommandSourceStack source, String[] args) {
			if (!admin(source.getSender())) {
				return List.of();
			}
			if (args.length <= 1) {
				List<String> names = new ArrayList<>(List.of("armure", "armor"));
				for (String[] n : NAMES) {
					names.addAll(Arrays.asList(n));
				}
				return starting(args.length == 0 ? "" : args[0], names);
			}
			if (args.length == 2) {
				return starting(args[1], List.of("0", "25", "50", "75", "100", "150"));
			}
			return List.of();
		}
	}

	public static ItemStack upgrade(CommandSender sender, Player player, EvoKind kind, int level) {
		ItemStack stack = find(player, kind);
		boolean given = stack == null;
		if (given) {
			stack = Items.create(EvoItem.of(kind));
		}
		if (level > Progression.MAX_LEVEL) {
			if (kind.profile().isArmor()) {
				stack.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ABYSSAL, PersistentDataType.BYTE, (byte) 1));
			} else {
				level = Progression.MAX_LEVEL;
			}
		}
		EvoData data = EvoData.get(stack);
		new EvoData(level, 0, data.mode(), data.flags(), data.count()).write(stack);
		// niveau imposé : les enchantements d'évolution suivent exactement le niveau, même vers le bas
		Progression.refresh(stack, true);
		if (given) {
			giveStack(player, stack);
		}
		sender.sendMessage(Lang.tr(given ? "command.evotool.upgrade.given" : "command.evotool.upgrade.done",
				Lang.tr(EvoItem.of(kind).translationKey()), level, Progression.stageName(stack)).color(NamedTextColor.GREEN));
		return stack;
	}

	/** Main principale, seconde main et armure portée, puis le reste de l'inventaire. */
	private static ItemStack find(Player player, EvoKind kind) {
		PlayerInventory inventory = player.getInventory();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE) {
				continue;
			}
			ItemStack stack = inventory.getItem(slot);
			if (Items.kindOf(stack) == kind) {
				return stack;
			}
		}
		for (ItemStack stack : inventory.getContents()) {
			if (Items.kindOf(stack) == kind) {
				return stack;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ catalogue

	/** Tous les objets du mod dans un menu (opérateurs) : un clic en donne un, maj + clic une pile. */
	private static final class Catalogue extends Menu {
		private static final int PER_PAGE = 45;

		private Catalogue(Player player, int page) {
			super(6, Lang.tr("plugin.evotool.catalogue.title"));
			List<EvoItem> items = Items.catalogue();
			int pages = (items.size() + PER_PAGE - 1) / PER_PAGE;
			for (int i = 0; i < PER_PAGE && page * PER_PAGE + i < items.size(); i++) {
				EvoItem item = items.get(page * PER_PAGE + i);
				button(i, Items.create(item), e -> {
					int count = e.isShiftClick() && item.stackable() ? 64 : 1;
					giveStack(player, Items.create(item, count));
				});
			}
			if (page > 0) {
				button(45, icon(org.bukkit.Material.ARROW, Lang.tr("plugin.evotool.catalogue.previous"), null), e -> open(player, page - 1));
			}
			if (page < pages - 1) {
				button(53, icon(org.bukkit.Material.ARROW, Lang.tr("plugin.evotool.catalogue.next"), null), e -> open(player, page + 1));
			}
			fill();
		}

		static void open(Player player, int page) {
			new Catalogue(player, page).open(player);
		}
	}
}
