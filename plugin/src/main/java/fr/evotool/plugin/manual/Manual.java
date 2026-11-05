package fr.evotool.plugin.manual;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.command.Commands;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Keys;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Le manuel du mod (11 volumes), en livre : clic droit pour le lire. Le texte est celui du mod, les touches remplacées
 * par celles du plugin. Il est offert à la première connexion (config.yml : give-manual).
 */
public final class Manual implements Listener {
	/** Les touches du mod → celles du plugin. */
	private static final Map<String, String> KEYS = new LinkedHashMap<>();

	static {
		KEYS.put("§eR§r", "§eaccroupi + F§r");
		KEYS.put("§eY§r", "§eF§r");
		KEYS.put("§eJ§r", "§eF à main vide§r");
		KEYS.put("§eG§r", "§eF§r");
		KEYS.put("§eClic droit à main nue§r", "§eAccroupi + clic gauche à main nue§r");
	}

	/** Lettres par page (un livre montre environ 14 lignes de 19 lettres ; la première page porte aussi le titre). */
	private static final int FIRST_PAGE_CHARS = 140;
	private static final int PAGE_CHARS = 200;
	private static Book book;

	public static void enable() {
		EvoToolPlugin.listen(new Manual());
		Commands.add("manuel", (sender, args) -> {
			if (sender instanceof Player player) {
				Commands.giveStack(player, Items.create(EvoItem.EVOTOOL_MANUAL));
			}
		});
		Commands.add("manual", (sender, args) -> {
			if (sender instanceof Player player) {
				open(player);
			}
		});
		fr.evotool.plugin.input.InputListener.onUse((player, stack, clicked, hand) -> {
			if (!Items.is(stack, EvoItem.EVOTOOL_MANUAL)) {
				return false;
			}
			open(player);
			return true;
		});
	}

	public static void open(Player player) {
		player.openBook(book());
	}

	public static Book book() {
		if (book == null) {
			book = build();
		}
		return book;
	}

	private static Book build() {
		List<Component> pages = new ArrayList<>();
		List<Component> contents = new ArrayList<>();
		List<String[]> chapters = new ArrayList<>();
		JsonObject root;
		try (InputStream in = EvoToolPlugin.class.getClassLoader().getResourceAsStream("pack/assets/evotool/manual/fr_fr.json")) {
			root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			return Book.book(Component.text("Evo Tool"), Component.text("Evo Tool"), Component.text("Manuel introuvable."));
		}
		// page 1 : sommaire (cliquable)
		pages.add(Component.empty());
		for (JsonElement c : root.getAsJsonArray("chapters")) {
			JsonObject chapter = c.getAsJsonObject();
			String title = chapter.get("title").getAsString();
			int first = pages.size() + 1;
			chapters.add(new String[] {title, String.valueOf(first)});
			for (JsonElement p : chapter.getAsJsonArray("pages")) {
				JsonObject page = p.getAsJsonObject();
				String text = page.get("text").getAsString();
				for (Map.Entry<String, String> key : KEYS.entrySet()) {
					text = text.replace(key.getKey(), key.getValue());
				}
				List<String> parts = split(text);
				for (int i = 0; i < parts.size(); i++) {
					Component body = LegacyComponentSerializer.legacySection().deserialize(parts.get(i));
					if (i == 0) {
						Component head = Component.text(page.get("title").getAsString() + "\n\n", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
						pages.add(head.append(body.decoration(TextDecoration.BOLD, false)));
					} else {
						pages.add(body);
					}
				}
			}
		}
		Component summary = Component.text("Evo Tool — manuel\n\n", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD);
		for (String[] chapter : chapters) {
			summary = summary.append(Component.text(chapter[0] + "\n", NamedTextColor.DARK_BLUE).decoration(TextDecoration.BOLD, false)
					.clickEvent(ClickEvent.changePage(Integer.parseInt(chapter[1]))));
		}
		pages.set(0, summary);
		contents.addAll(pages);
		return Book.book(Component.text("Evo Tool"), Component.text("Evo Tool"), contents);
	}

	/** Coupe un texte en pages de livre, aux fins de phrase ou de ligne si possible. */
	static List<String> split(String text) {
		List<String> parts = new ArrayList<>();
		String rest = text;
		String carry = "";
		while (visible(rest) > (parts.isEmpty() ? FIRST_PAGE_CHARS : PAGE_CHARS)) {
			int cut = cutAt(rest, parts.isEmpty() ? FIRST_PAGE_CHARS : PAGE_CHARS);
			String part = rest.substring(0, cut).stripTrailing();
			parts.add(carry + part);
			carry = lastFormat(carry + part);
			rest = rest.substring(cut).stripLeading();
		}
		parts.add(carry + rest);
		return parts;
	}

	private static int visible(String s) {
		return s.replaceAll("§.", "").length();
	}

	private static int cutAt(String s, int limit) {
		int seen = 0;
		int best = -1;
		for (int i = 0; i < s.length(); i++) {
			char ch = s.charAt(i);
			if (ch == '§' && i + 1 < s.length()) {
				i++;
				continue;
			}
			seen++;
			if (ch == '\n' || (ch == ' ' && i > 0 && ".!?…:".indexOf(s.charAt(i - 1)) >= 0)) {
				best = i + 1;
			} else if (ch == ' ' && best < 0) {
				best = i + 1;
			}
			if (seen >= limit) {
				break;
			}
		}
		return best > 0 ? best : Math.min(s.length(), limit);
	}

	private static String lastFormat(String s) {
		int at = s.lastIndexOf('§');
		return at >= 0 && at + 1 < s.length() && s.charAt(at + 1) != 'r' ? s.substring(at, at + 2) : "";
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (!EvoToolPlugin.instance().getConfig().getBoolean("give-manual", true)
				|| player.getPersistentDataContainer().has(Keys.MANUAL_GIVEN)) {
			return;
		}
		player.getPersistentDataContainer().set(Keys.MANUAL_GIVEN, PersistentDataType.BYTE, (byte) 1);
		Commands.giveStack(player, Items.create(EvoItem.EVOTOOL_MANUAL));
		player.sendMessage(fr.evotool.plugin.util.Lang.tr("plugin.evotool.welcome").color(NamedTextColor.LIGHT_PURPLE));
	}
}
