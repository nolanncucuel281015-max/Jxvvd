package fr.evotool.plugin.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import fr.evotool.plugin.block.CustomBlock;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Le pack de données de la dimension évolutive : les fichiers du mod (relief, biomes, arbres, géodes, minerais),
 * avec chaque bloc du mod remplacé par l'état de bloc musical qui le porte. Le sanctuaire (une structure en Java dans
 * le mod) est posé par le plugin lui-même (voir {@link SanctuaryPopulator}).
 */
public final class Datapack {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static final List<String> SKIPPED = List.of("worldgen/configured_feature/sanctuary.json", "worldgen/placed_feature/sanctuary.json");

	private Datapack() {
	}

	/** Écrit le pack dans {@code out} (remplacé à chaque démarrage) ; renvoie les problèmes rencontrés. */
	public static List<String> write(Path jar, Path out) throws IOException {
		List<String> problems = new ArrayList<>();
		if (Files.exists(out)) {
			try (Stream<Path> files = Files.walk(out)) {
				for (Path p : files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList()) {
					Files.delete(p);
				}
			}
		}
		Files.createDirectories(out);
		try (JarFile file = new JarFile(jar.toFile())) {
			for (Enumeration<JarEntry> entries = file.entries(); entries.hasMoreElements(); ) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (entry.isDirectory() || !name.startsWith("datapack/") || !name.endsWith(".json")) {
					continue;
				}
				String path = name.substring("datapack/".length());
				String inside = path.substring("data/evotool/".length());
				if (SKIPPED.contains(inside)) {
					continue;
				}
				JsonElement json;
				try (InputStream in = file.getInputStream(entry)) {
					json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				}
				json = convert(json, problems, path);
				if (inside.startsWith("worldgen/biome/")) {
					removeSanctuary(json.getAsJsonObject());
				}
				Path target = out.resolve(path);
				Files.createDirectories(target.getParent());
				Files.writeString(target, GSON.toJson(json), StandardCharsets.UTF_8);
			}
		}
		Files.writeString(out.resolve("pack.mcmeta"), packMeta(), StandardCharsets.UTF_8);
		return problems;
	}

	/** Remplace les blocs du mod (états et filtres de blocs) par leurs blocs musicaux. */
	static JsonElement convert(JsonElement json, List<String> problems, String file) {
		if (json.isJsonArray()) {
			JsonArray array = new JsonArray();
			for (JsonElement e : json.getAsJsonArray()) {
				array.add(convert(e, problems, file));
			}
			return array;
		}
		if (!json.isJsonObject()) {
			if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString() && blockId(json.getAsString()) != null) {
				problems.add(file + " : " + json.getAsString());
			}
			return json;
		}
		JsonObject object = json.getAsJsonObject();
		// un état de bloc : {"Name": "evotool:…", "Properties": {"evolution": "0", "axis": "y"}}
		if (object.has("Name") && object.get("Name").isJsonPrimitive()) {
			CustomBlock block = blockId(object.get("Name").getAsString());
			if (block != null) {
				int evolution = 0;
				int axis = 0;
				JsonObject properties = object.has("Properties") ? object.getAsJsonObject("Properties") : new JsonObject();
				if (properties.has("evolution")) {
					evolution = properties.get("evolution").getAsInt();
				}
				if (properties.has("axis")) {
					String a = properties.get("axis").getAsString();
					axis = a.equals("z") ? 1 : a.equals("x") ? 2 : 0;
				}
				return noteState(block, block.variant(evolution, axis));
			}
		}
		// un filtre : {"predicate_type": "minecraft:block_match", "block": "evotool:…"}
		if (object.has("predicate_type") && object.get("predicate_type").getAsString().equals("minecraft:block_match")
				&& object.has("block") && object.get("block").isJsonPrimitive()) {
			CustomBlock block = blockId(object.get("block").getAsString());
			if (block != null) {
				JsonObject predicate = new JsonObject();
				predicate.addProperty("predicate_type", "minecraft:blockstate_match");
				predicate.add("block_state", noteState(block, 0));
				return predicate;
			}
		}
		JsonObject copy = new JsonObject();
		for (Map.Entry<String, JsonElement> e : object.entrySet()) {
			copy.add(e.getKey(), convert(e.getValue(), problems, file));
		}
		return copy;
	}

	private static CustomBlock blockId(String id) {
		return id.startsWith("evotool:") ? CustomBlock.byId(id.substring("evotool:".length())) : null;
	}

	private static JsonObject noteState(CustomBlock block, int variant) {
		JsonObject state = new JsonObject();
		state.addProperty("Name", "minecraft:note_block");
		JsonObject properties = new JsonObject();
		block.properties(variant).forEach(properties::addProperty);
		state.add("Properties", properties);
		return state;
	}

	private static void removeSanctuary(JsonObject biome) {
		if (!biome.has("features")) {
			return;
		}
		JsonArray steps = new JsonArray();
		for (JsonElement step : biome.getAsJsonArray("features")) {
			JsonArray kept = new JsonArray();
			for (JsonElement feature : step.getAsJsonArray()) {
				if (!feature.getAsString().equals("evotool:sanctuary")) {
					kept.add(feature);
				}
			}
			steps.add(kept);
		}
		biome.add("features", steps);
	}

	/** Le format de pack de données de cette version de Minecraft (lu dans le jeu du serveur). */
	public static int dataFormat() {
		try (InputStream in = Datapack.class.getClassLoader().getResourceAsStream("version.json")) {
			if (in != null) {
				JsonObject version = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				JsonObject pack = version.getAsJsonObject("pack_version");
				if (pack != null) {
					for (String key : new String[] {"data_major", "data"}) {
						if (pack.has(key)) {
							return pack.get(key).getAsInt();
						}
					}
				}
			}
		} catch (Exception e) {
			// valeur par défaut ci-dessous
		}
		return 94;
	}

	private static String packMeta() {
		int format = dataFormat();
		JsonObject pack = new JsonObject();
		pack.add("description", new JsonPrimitive("Evo Tool : la dimension évolutive"));
		pack.addProperty("pack_format", format);
		pack.addProperty("min_format", format);
		pack.addProperty("max_format", format);
		JsonObject root = new JsonObject();
		root.add("pack", pack);
		return GSON.toJson(root);
	}
}
