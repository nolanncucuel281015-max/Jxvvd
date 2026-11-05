package fr.evotool.plugin.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Les textes du mod : des composants traduisibles (le pack de ressources contient les traductions du mod, le jeu
 * les affiche dans la langue du joueur), avec le texte français en secours pour qui n'a pas le pack.
 */
public final class Lang {
	private static final Map<String, String> FRENCH = new HashMap<>();

	private Lang() {
	}

	/** Les textes du mod, puis ceux propres au plugin (qui remplacent ceux qui parlent des touches du mod). */
	public static void load(ClassLoader loader) {
		read(loader, "pack/assets/evotool/lang/fr_fr.json");
		read(loader, "plugin-lang/fr_fr.json");
	}

	private static void read(ClassLoader loader, String path) {
		try (InputStream in = loader.getResourceAsStream(path)) {
			if (in == null) {
				return;
			}
			JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			json.entrySet().forEach(e -> FRENCH.put(e.getKey(), e.getValue().getAsString()));
		} catch (Exception e) {
			// sans le fichier, les clés restent affichées telles quelles
		}
	}

	/** Le texte français d'une clé (ou la clé elle-même). */
	public static String french(String key) {
		return FRENCH.getOrDefault(key, key);
	}

	public static boolean has(String key) {
		return FRENCH.containsKey(key);
	}

	/** Texte traduisible, avec le français en secours. */
	public static TranslatableComponent tr(String key, ComponentLike... args) {
		return Component.translatable(key, FRENCH.get(key), args);
	}

	public static TranslatableComponent tr(String key, Object... args) {
		ComponentLike[] parts = new ComponentLike[args.length];
		for (int i = 0; i < args.length; i++) {
			parts[i] = args[i] instanceof ComponentLike c ? c : Component.text(String.valueOf(args[i]));
		}
		return Component.translatable(key, FRENCH.get(key), parts);
	}

	public static Component colored(Component text, TextColor color, boolean bold) {
		Component c = text.color(color);
		return bold ? c.decorate(TextDecoration.BOLD) : c;
	}

	/** Une ligne d'infobulle : pas d'italique (le jeu en met par défaut dans les descriptions). */
	public static Component line(Component text, TextColor color) {
		return text.color(color).decoration(TextDecoration.ITALIC, false);
	}

	public static Component line(String key, TextColor color, Object... args) {
		return line(tr(key, args), color);
	}

	public static Component gray(String key, Object... args) {
		return line(key, NamedTextColor.GRAY, args);
	}
}
