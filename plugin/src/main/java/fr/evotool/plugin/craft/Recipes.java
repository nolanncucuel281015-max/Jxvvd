package fr.evotool.plugin.craft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Keys;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Keyed;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CookingBookCategory;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/**
 * Les recettes du mod (les mêmes fichiers que le mod, copiés dans le jar) : établi, four et haut fourneau. Les objets
 * du mod ne servent jamais dans une recette de Minecraft (l'évolium a beau être du papier dessous, il ne fait pas de
 * livre ; une baguette n'est pas un bâton pour faire des torches).
 */
public final class Recipes implements Listener {
	/** Recettes cachées du livre (le briquet est un secret à trouver). */
	private static final List<String> SECRET = List.of("evolium_lighter");
	private static final List<NamespacedKey> KEYS = new ArrayList<>();

	public static List<NamespacedKey> keys() {
		return KEYS;
	}

	public static void register(EvoToolPlugin plugin) {
		File file = plugin.jarFile();
		int count = 0;
		try (JarFile jar = new JarFile(file)) {
			for (Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("recipe/") || !name.endsWith(".json")) {
					continue;
				}
				String id = name.substring("recipe/".length(), name.length() - ".json".length());
				try (InputStream in = jar.getInputStream(entry)) {
					JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
					Recipe recipe = parse(Keys.key(id), json);
					if (recipe != null) {
						Bukkit.removeRecipe(Keys.key(id));
						Bukkit.addRecipe(recipe);
						KEYS.add(Keys.key(id));
						count++;
					}
				} catch (RuntimeException e) {
					plugin.getLogger().warning("Recette " + id + " ignorée : " + e);
				}
			}
		} catch (IOException e) {
			plugin.getLogger().severe("Impossible de lire les recettes : " + e);
		}
		plugin.getLogger().info(count + " recettes Evo Tool");
	}

	public static void unregister() {
		KEYS.forEach(Bukkit::removeRecipe);
		KEYS.clear();
	}

	static Recipe parse(NamespacedKey key, JsonObject json) {
		String type = json.get("type").getAsString().replace("minecraft:", "");
		ItemStack result = result(json.get("result"));
		if (result == null) {
			return null;
		}
		String category = json.has("category") ? json.get("category").getAsString() : "misc";
		switch (type) {
			case "crafting_shaped" -> {
				ShapedRecipe recipe = new ShapedRecipe(key, result);
				JsonArray pattern = json.getAsJsonArray("pattern");
				String[] rows = new String[pattern.size()];
				for (int i = 0; i < rows.length; i++) {
					rows[i] = pattern.get(i).getAsString();
				}
				recipe.shape(rows);
				for (Map.Entry<String, JsonElement> k : json.getAsJsonObject("key").entrySet()) {
					recipe.setIngredient(k.getKey().charAt(0), choice(k.getValue()));
				}
				recipe.setCategory(craftingCategory(category));
				return recipe;
			}
			case "crafting_shapeless" -> {
				ShapelessRecipe recipe = new ShapelessRecipe(key, result);
				for (JsonElement ingredient : json.getAsJsonArray("ingredients")) {
					recipe.addIngredient(choice(ingredient));
				}
				recipe.setCategory(craftingCategory(category));
				return recipe;
			}
			case "smelting", "blasting" -> {
				RecipeChoice input = choice(json.get("ingredient"));
				float xp = json.has("experience") ? json.get("experience").getAsFloat() : 0.0F;
				int time = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : type.equals("smelting") ? 200 : 100;
				if (type.equals("smelting")) {
					FurnaceRecipe recipe = new FurnaceRecipe(key, result, input, xp, time);
					recipe.setCategory(cookingCategory(category));
					return recipe;
				}
				BlastingRecipe recipe = new BlastingRecipe(key, result, input, xp, time);
				recipe.setCategory(cookingCategory(category));
				return recipe;
			}
			default -> {
				return null;
			}
		}
	}

	private static CraftingBookCategory craftingCategory(String name) {
		return switch (name) {
			case "equipment" -> CraftingBookCategory.EQUIPMENT;
			case "building" -> CraftingBookCategory.BUILDING;
			case "redstone" -> CraftingBookCategory.REDSTONE;
			default -> CraftingBookCategory.MISC;
		};
	}

	private static CookingBookCategory cookingCategory(String name) {
		return switch (name) {
			case "food" -> CookingBookCategory.FOOD;
			case "blocks" -> CookingBookCategory.BLOCKS;
			default -> CookingBookCategory.MISC;
		};
	}

	private static ItemStack result(JsonElement json) {
		String id;
		int count = 1;
		if (json.isJsonObject()) {
			JsonObject o = json.getAsJsonObject();
			id = o.get("id").getAsString();
			if (o.has("count")) {
				count = o.get("count").getAsInt();
			}
		} else {
			id = json.getAsString();
		}
		ItemStack stack = stack(id);
		if (stack != null) {
			stack.setAmount(count);
		}
		return stack;
	}

	/** Un objet par son identifiant (evotool:… ou minecraft:…). */
	public static ItemStack stack(String id) {
		if (id.startsWith("evotool:")) {
			EvoItem item = EvoItem.byId(id.substring("evotool:".length()));
			return item == null ? null : Items.create(item);
		}
		Material material = Material.matchMaterial(id);
		return material == null ? null : new ItemStack(material);
	}

	private static RecipeChoice choice(JsonElement json) {
		if (json.isJsonArray()) {
			List<ItemStack> exact = new ArrayList<>();
			List<Material> materials = new ArrayList<>();
			for (JsonElement e : json.getAsJsonArray()) {
				String id = e.getAsString();
				if (id.startsWith("evotool:")) {
					exact.add(stack(id));
				} else {
					materials.add(Material.matchMaterial(id));
				}
			}
			return exact.isEmpty() ? new RecipeChoice.MaterialChoice(materials) : new RecipeChoice.ExactChoice(exact);
		}
		String id = json.getAsString();
		if (id.startsWith("#")) {
			NamespacedKey tag = NamespacedKey.fromString(id.substring(1));
			Tag<Material> items = Bukkit.getTag(Tag.REGISTRY_ITEMS, tag, Material.class);
			if (items == null) {
				throw new IllegalArgumentException("étiquette inconnue " + id);
			}
			return new RecipeChoice.MaterialChoice(items);
		}
		if (id.startsWith("evotool:")) {
			ItemStack stack = stack(id);
			if (stack == null) {
				throw new IllegalArgumentException("objet inconnu " + id);
			}
			return new RecipeChoice.ExactChoice(stack);
		}
		Material material = Material.matchMaterial(id);
		if (material == null) {
			throw new IllegalArgumentException("objet inconnu " + id);
		}
		return new RecipeChoice.MaterialChoice(material);
	}

	// ------------------------------------------------------------------ garde-fous

	private static boolean isOurs(Recipe recipe) {
		return recipe instanceof Keyed keyed && keyed.getKey().getNamespace().equals(Keys.NAMESPACE);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepare(PrepareItemCraftEvent event) {
		CraftingInventory inventory = event.getInventory();
		Recipe recipe = event.getRecipe();
		if (recipe == null || isOurs(recipe)) {
			return;
		}
		for (ItemStack ingredient : inventory.getMatrix()) {
			if (Items.of(ingredient) != null) {
				inventory.setResult(null);   // un objet du mod dans une recette de Minecraft (réparation comprise)
				return;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onSmelt(FurnaceSmeltEvent event) {
		if (Items.of(event.getSource()) != null && (event.getRecipe() == null || !isOurs(event.getRecipe()))) {
			event.setCancelled(true);
		}
	}

	/** Les recettes du mod dans le livre de recettes (sauf les secrètes). */
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		discover(event.getPlayer());
	}

	public static void discover(Player player) {
		List<NamespacedKey> shown = new ArrayList<>();
		for (NamespacedKey key : KEYS) {
			if (!SECRET.contains(key.getKey())) {
				shown.add(key);
			}
		}
		player.discoverRecipes(shown);
	}
}
