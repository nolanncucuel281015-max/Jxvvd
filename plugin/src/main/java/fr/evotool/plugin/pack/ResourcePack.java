package fr.evotool.plugin.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.util.Lang;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Le pack de ressources du plugin : les modèles 3D, textures et traductions du mod (copiés dans le jar), plus
 * l'apparence des blocs du mod portés par des blocs musicaux. Le plugin le construit au démarrage, le sert lui-même
 * (petit serveur web) ou donne l'adresse réglée dans config.yml, et l'envoie à chaque joueur qui se connecte.
 */
public final class ResourcePack implements Listener {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static byte[] zip;
	private static byte[] sha1;
	private static HttpServer server;
	private static int port;

	private ResourcePack() {
	}

	public static Listener listener() {
		return new ResourcePack();
	}

	public static byte[] bytes() {
		return zip;
	}

	/** Construit le pack (et le serveur web si aucune adresse n'est réglée). */
	public static void enable(EvoToolPlugin plugin) {
		try {
			zip = build(plugin.jarFile());
			sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
			File out = new File(plugin.getDataFolder(), "evotool-pack.zip");
			out.getParentFile().mkdirs();
			Files.write(out.toPath(), zip);
			plugin.getLogger().info("Pack de ressources : " + zip.length / 1024 + " Ko (" + out.getPath() + "), SHA-1 "
					+ HexFormat.of().formatHex(sha1));
		} catch (Exception e) {
			plugin.getLogger().severe("Impossible de construire le pack de ressources : " + e);
			return;
		}
		FileConfiguration config = plugin.getConfig();
		if (config.getString("resource-pack.url", "").isBlank()) {
			port = config.getInt("resource-pack.port", 8163);
			try {
				server = HttpServer.create(new InetSocketAddress(port), 0);
				server.createContext("/", exchange -> {
					exchange.getResponseHeaders().add("Content-Type", "application/zip");
					exchange.sendResponseHeaders(200, zip.length);
					try (OutputStream body = exchange.getResponseBody()) {
						body.write(zip);
					}
				});
				server.start();
				plugin.getLogger().info("Pack de ressources servi sur le port " + port + " (ouvre ce port, ou mets une adresse dans config.yml)");
			} catch (IOException e) {
				plugin.getLogger().warning("Impossible d'ouvrir le port " + port + " pour le pack de ressources : " + e.getMessage()
						+ ". Mets l'adresse du fichier evotool-pack.zip dans config.yml (resource-pack.url).");
				server = null;
			}
		}
	}

	public static void disable() {
		if (server != null) {
			server.stop(0);
			server = null;
		}
	}

	/** L'adresse du pack pour ce joueur (celle qu'il a utilisée pour se connecter, au port du petit serveur web). */
	public static String url(Player player) {
		FileConfiguration config = EvoToolPlugin.instance().getConfig();
		String configured = config.getString("resource-pack.url", "");
		if (!configured.isBlank()) {
			return configured;
		}
		if (server == null) {
			return null;
		}
		String host = config.getString("resource-pack.host", "");
		if (host.isBlank() && player.getVirtualHost() != null) {
			host = player.getVirtualHost().getHostString();
		}
		if (host.isBlank()) {
			host = Bukkit.getIp().isBlank() ? "localhost" : Bukkit.getIp();
		}
		return "http://" + host + ":" + port + "/evotool-pack-" + HexFormat.of().formatHex(sha1).substring(0, 8) + ".zip";
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		send(event.getPlayer());
	}

	/** Envoie le pack au joueur (à la connexion, ou avec /evotool pack). */
	public static void send(Player player) {
		String url = url(player);
		if (url == null || zip == null) {
			return;
		}
		boolean required = EvoToolPlugin.instance().getConfig().getBoolean("resource-pack.required", false);
		player.setResourcePack(java.util.UUID.nameUUIDFromBytes("evotool".getBytes(StandardCharsets.UTF_8)), url, sha1,
				Lang.tr("plugin.evotool.pack.prompt").color(NamedTextColor.LIGHT_PURPLE), required);
	}

	// ------------------------------------------------------------------ construction

	private static byte[] build(File jarFile) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		Map<String, JsonObject> lang = new LinkedHashMap<>();
		try (ZipOutputStream out = new ZipOutputStream(bytes); JarFile jar = new JarFile(jarFile)) {
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("pack/") || entry.isDirectory()) {
					continue;
				}
				String path = name.substring("pack/".length());
				if (path.startsWith("assets/evotool/lang/")) {
					try (InputStream in = jar.getInputStream(entry)) {
						lang.put(path, JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
					}
					continue;
				}
				try (InputStream in = jar.getInputStream(entry)) {
					put(out, path, in.readAllBytes());
				}
			}
			// les traductions du mod, avec celles du plugin par-dessus (contrôles du plugin au lieu des touches du mod)
			for (Map.Entry<String, JsonObject> file : lang.entrySet()) {
				String locale = file.getKey().substring(file.getKey().lastIndexOf('/') + 1);
				JsonEntry: {
					JarEntry extra = jar.getJarEntry("plugin-lang/" + locale);
					if (extra == null) {
						break JsonEntry;
					}
					try (InputStream in = jar.getInputStream(extra)) {
						JsonObject overrides = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
						for (Map.Entry<String, JsonElement> e : overrides.entrySet()) {
							file.getValue().add(e.getKey(), e.getValue());
						}
					}
				}
				put(out, file.getKey(), GSON.toJson(file.getValue()).getBytes(StandardCharsets.UTF_8));
			}
			put(out, "pack.mcmeta", packMeta().getBytes(StandardCharsets.UTF_8));
			put(out, "assets/minecraft/blockstates/note_block.json", noteBlockStates().getBytes(StandardCharsets.UTF_8));
			for (String axis : new String[] {"ns", "ew"}) {
				put(out, "assets/evotool/items/evolution_portal_" + axis + ".json", ("{\"model\":{\"type\":\"minecraft:model\","
						+ "\"model\":\"evotool:block/evolution_portal_" + axis + "\"}}").getBytes(StandardCharsets.UTF_8));
			}
		}
		return bytes.toByteArray();
	}

	private static void put(ZipOutputStream out, String path, byte[] data) throws IOException {
		ZipEntry entry = new ZipEntry(path);
		entry.setTime(0L);
		out.putNextEntry(entry);
		out.write(data);
		out.closeEntry();
	}

	/** Le format de pack de ressources de cette version de Minecraft (lu dans le jeu du serveur). */
	public static int resourceFormat() {
		try (InputStream in = Bukkit.getServer().getClass().getClassLoader().getResourceAsStream("version.json")) {
			if (in != null) {
				JsonObject version = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject pack = version.getAsJsonObject("pack_version");
				if (pack != null) {
					if (pack.has("resource_major")) {
						return pack.get("resource_major").getAsInt();
					}
					if (pack.has("resource")) {
						return pack.get("resource").getAsInt();
					}
				}
			}
		} catch (Exception e) {
			// valeur par défaut ci-dessous
		}
		return 75;
	}

	private static String packMeta() {
		int format = resourceFormat();
		JsonObject pack = new JsonObject();
		pack.addProperty("description", "Evo Tool");
		pack.addProperty("pack_format", format);
		pack.addProperty("min_format", format);
		pack.addProperty("max_format", format);
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return GSON.toJson(root);
	}

	/**
	 * Tous les états du bloc musical : les états « alimentés » réservés montrent les blocs du mod, tous les autres
	 * gardent l'apparence du bloc musical.
	 */
	private static String noteBlockStates() {
		JsonObject variants = new JsonObject();
		for (int i = 0; i < CustomBlock.INSTRUMENTS.length; i++) {
			for (int note = 0; note < 25; note++) {
				for (boolean powered : new boolean[] {false, true}) {
					String key = "instrument=" + CustomBlock.INSTRUMENTS[i] + ",note=" + note + ",powered=" + powered;
					JsonObject model = new JsonObject();
					model.addProperty("model", "minecraft:block/note_block");
					if (powered) {
						int index = i * 25 + note;
						CustomBlock block = byIndex(index);
						if (block != null) {
							int variant = index - block.index(0);
							model.addProperty("model", block.model(variant));
							int[] rotation = block.rotation(variant);
							if (rotation[0] != 0) {
								model.addProperty("x", rotation[0]);
							}
							if (rotation[1] != 0) {
								model.addProperty("y", rotation[1]);
							}
						}
					}
					variants.add(key, model);
				}
			}
		}
		JsonObject root = new JsonObject();
		root.add("variants", variants);
		return GSON.toJson(root);
	}

	private static CustomBlock byIndex(int index) {
		for (CustomBlock block : CustomBlock.values()) {
			if (index >= block.index(0) && index <= block.index(block.variants() - 1)) {
				return block;
			}
		}
		return null;
	}
}
