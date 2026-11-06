package fr.evotool.plugin.test;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.craft.Recipes;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.mining.MiningListener;
import fr.evotool.plugin.pack.ResourcePack;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Auto-test du plugin sur un vrai serveur Paper (lancé par la CI avec -Devotool.selftest=true, ou /evotool selftest).
 * Écrit le résultat dans plugins/EvoTool/selftest.txt et dans la console.
 */
public final class SelfTest {
	private final List<String> failures = new ArrayList<>();
	private int checks;

	private SelfTest() {
	}

	private void check(boolean ok, String what) {
		checks++;
		if (!ok) {
			failures.add(what);
		}
	}

	/** Lance tous les contrôles ; renvoie la liste des échecs (vide : tout va bien). */
	public static List<String> run() {
		SelfTest test = new SelfTest();
		test.check(!Boolean.FALSE.equals(fr.evotool.plugin.EvoToolBootstrap.NoteBlocks.running()),
				"blocs musicaux figés dès le premier démarrage avec le plugin (" + fr.evotool.plugin.EvoToolBootstrap.NoteBlocks.running() + ")");
		test.items();
		test.instamine();
		test.pack();
		test.recipes();
		test.blocks();
		test.evolution();
		test.machines();
		test.dimension();
		test.check(fr.evotool.plugin.manual.Manual.book().pages().size() > 20, "manuel : " + fr.evotool.plugin.manual.Manual.book().pages().size() + " pages");
		test.check(Bukkit.getWorlds().get(0).getPopulators().stream().anyMatch(p -> p instanceof fr.evotool.plugin.world.OrePopulator),
				"minerais dans le monde normal");
		EvoToolPlugin plugin = EvoToolPlugin.instance();
		String result = test.failures.isEmpty() ? "EVOTOOL SELFTEST PASSED (" + test.checks + " checks)"
				: "EVOTOOL SELFTEST FAILED (" + test.failures.size() + "/" + test.checks + ")\n - " + String.join("\n - ", test.failures);
		plugin.getLogger().info(result);
		try {
			File out = new File(plugin.getDataFolder(), "selftest.txt");
			out.getParentFile().mkdirs();
			Files.writeString(out.toPath(), result + "\n", StandardCharsets.UTF_8);
		} catch (IOException e) {
			plugin.getLogger().warning("selftest.txt : " + e);
		}
		return test.failures;
	}

	private void items() {
		for (EvoItem item : EvoItem.values()) {
			ItemStack stack = Items.create(item);
			check(Items.of(stack) == item, "objet reconnu : " + item.id());
			check(Keys.key(item.id()).equals(stack.getItemMeta().getItemModel()), "modèle de l'objet : " + item.id());
			check(Lang.has(item.translationKey()), "nom traduit : " + item.translationKey());
			if (item.kind() != null) {
				check(Items.kindOf(stack) == item.kind(), "profil d'évolution : " + item.id());
				check(EvoData.get(stack).level() == 0, "niveau de départ : " + item.id());
			}
		}
	}

	/** La règle du joueur (CLAUDE.md) : au niveau 100, pioche, pelle et hache cassent tout en un coup. */
	private void instamine() {
		check(Progression.INSTAMINE_BONUS == 5000.0, "plus de minage instantané : bonus " + Progression.INSTAMINE_BONUS);
		World world = Bukkit.getWorlds().get(0);
		Block block = world.getBlockAt(world.getSpawnLocation().getBlockX(), world.getMinHeight() + 2, world.getSpawnLocation().getBlockZ());
		for (EvoKind kind : new EvoKind[] {EvoKind.PICKAXE, EvoKind.SHOVEL, EvoKind.AXE}) {
			Integer at = kind.profile().powers().get(Power.INSTAMINE);
			check(at != null && at == Progression.MAX_LEVEL, "plus de minage instantané : " + kind + " au niveau " + at);
			ItemStack tool = Items.create(EvoItem.of(kind));
			new EvoData(Progression.MAX_LEVEL, 0, 0, -1, 0L).write(tool);
			Progression.refresh(tool, true);
			check(MiningListener.isInstamine(tool), "plus de minage instantané : " + kind + " niveau 100");
			for (Material material : new Material[] {Material.STONE, Material.DEEPSLATE, Material.OBSIDIAN, Material.DIAMOND_ORE}) {
				Material before = block.getType();
				block.setType(material, false);
				BlockDamageEvent event = new BlockDamageEvent(null, block, BlockFace.UP, tool, false);
				new MiningListener().onDamage(event);
				check(event.getInstaBreak(), "plus de minage instantané : " + kind + " sur " + material);
				block.setType(before, false);
			}
			ItemStack low = Items.create(EvoItem.of(kind));
			new EvoData(Progression.MAX_LEVEL - 1, 0, 0, -1, 0L).write(low);
			Progression.refresh(low, true);
			check(!MiningListener.isInstamine(low), "minage instantané seulement au niveau 100 : " + kind);
		}
	}

	private void pack() {
		byte[] zip = ResourcePack.bytes();
		check(zip != null && zip.length > 100_000, "pack de ressources construit");
		if (zip == null) {
			return;
		}
		Set<String> names = new HashSet<>();
		try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
			for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
				names.add(e.getName());
			}
		} catch (IOException e) {
			check(false, "pack lisible : " + e);
		}
		check(names.contains("pack.mcmeta"), "pack.mcmeta");
		check(names.contains("assets/minecraft/blockstates/note_block.json"), "apparence des blocs du mod");
		check(names.contains("assets/evotool/lang/fr_fr.json") && names.contains("assets/evotool/lang/en_us.json"), "traductions");
		for (EvoItem item : EvoItem.values()) {
			check(names.contains("assets/evotool/items/" + item.id() + ".json"), "modèle dans le pack : " + item.id());
		}
		check(ResourcePack.resourceFormat() > 0, "format du pack");
	}

	private void recipes() {
		check(Recipes.keys().size() >= 50, "recettes chargées : " + Recipes.keys().size());
		for (var key : Recipes.keys()) {
			check(Bukkit.getRecipe(key) != null, "recette enregistrée : " + key);
		}
		check(Bukkit.getRecipe(Keys.key("evolving_pickaxe")) != null, "recette de la pioche évolutive");
	}

	private void blocks() {
		World world = Bukkit.getWorlds().get(0);
		int x = world.getSpawnLocation().getBlockX();
		int z = world.getSpawnLocation().getBlockZ();
		int y = world.getMaxHeight() - 3;
		Block block = world.getBlockAt(x, y, z);
		Material before = block.getType();
		for (CustomBlock custom : CustomBlock.values()) {
			for (int variant = 0; variant < custom.variants(); variant++) {
				Blocks.place(block, custom, variant);
				check(Blocks.at(block) == custom && Blocks.variant(block) == variant, "bloc " + custom + " forme " + variant);
			}
			ItemStack tool = new ItemStack(custom == CustomBlock.EVOLVING_LEAVES ? Material.SHEARS : Material.NETHERITE_PICKAXE);
			check(!Blocks.drops(block, tool).isEmpty() || custom.name().contains("PORTAL")
					|| custom == CustomBlock.SHIELD_GENERATOR, "butin du bloc " + custom);
		}
		block.setType(before, false);
	}

	private void machines() {
		World world = Bukkit.getWorlds().get(0);
		Block at = world.getBlockAt(world.getSpawnLocation().getBlockX() + 3, world.getMaxHeight() - 6, world.getSpawnLocation().getBlockZ());
		String shield = fr.evotool.plugin.machine.ShieldGenerator.test(at);
		check(shield == null, "générateur de bouclier : " + shield);
		String combiner = fr.evotool.plugin.machine.ElementalCombiner.test(at);
		check(combiner == null, "combineur élémentaire : " + combiner);
	}

	private void dimension() {
		World dimension = fr.evotool.plugin.dimension.EvoDimension.world();
		check(dimension != null, "dimension évolutive chargée (pack de données)");
		if (dimension != null) {
			int x = 40;
			int z = 40;
			dimension.getChunkAt(x >> 4, z >> 4);
			int y = dimension.getHighestBlockYAt(x, z);
			boolean evolving = false;
			for (int dy = 0; dy < 30 && !evolving; dy++) {
				evolving = Blocks.at(dimension.getBlockAt(x, y - dy, z)) != null;
			}
			check(evolving, "le sol de la dimension est fait de blocs évolutifs (y " + y + " : " + dimension.getBlockAt(x, y, z).getType() + ")");
			Block block = dimension.getBlockAt(x, dimension.getMaxHeight() - 4, z);
			Blocks.place(block, CustomBlock.EVOLVING_STONE);
			fr.evotool.plugin.dimension.EvoDimension.evolveTo(block, 3);
			check(Blocks.at(block) == CustomBlock.EVOLVING_STONE && CustomBlock.evolutionOf(CustomBlock.EVOLVING_STONE, Blocks.variant(block)) == 3,
					"un bloc évolutif évolue");
			block.setType(Material.AIR, false);
		}
		World world = Bukkit.getWorlds().get(0);
		String portal = fr.evotool.plugin.dimension.RubyPortal.test(world, world.getSpawnLocation().getBlockX() + 8,
				world.getMaxHeight() - 12, world.getSpawnLocation().getBlockZ() + 8);
		check(portal == null, "portail en rubis : " + portal);
		// sanctuaire et boss : construit dans le monde normal (au-dessus de tout), l'autel réveille le boss
		int sx = world.getSpawnLocation().getBlockX() + 200;
		int sz = world.getSpawnLocation().getBlockZ() + 200;
		world.getChunkAt(sx >> 4, sz >> 4);
		Block altar = fr.evotool.plugin.dimension.Sanctuary.buildAt(world, sx, sz);
		check(Blocks.at(altar) == CustomBlock.SANCTUARY_ALTAR, "autel du sanctuaire");
		org.bukkit.entity.Ravager boss = fr.evotool.plugin.dimension.Sanctuary.summon(altar, null);
		check(boss != null && fr.evotool.plugin.boss.BellBoss.isBoss(boss), "l'autel réveille le boss");
		check(fr.evotool.plugin.dimension.Sanctuary.summon(altar, null) == null, "un seul boss à la fois");
		if (boss != null) {
			check(fr.evotool.plugin.boss.BellBoss.evolve(boss) && boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() == 200,
					"le boss évolue (forme 1 : 200 PV)");
			boss.remove();
		}
	}

	private void evolution() {
		ItemStack pickaxe = Items.create(EvoItem.EVOLVING_PICKAXE);
		int level = Progression.gainXp(pickaxe, 1_000_000, 0);
		check(level > 0 && level < Progression.MAX_LEVEL, "l'XP fait monter de niveau jusqu'à la forme suivante : " + level);
		check(Progression.isReadyToEvolve(pickaxe), "objet prêt à évoluer");
		check(Progression.evolve(pickaxe, null) && EvoData.get(pickaxe).level() == level + 1, "évolution");
	}
}
