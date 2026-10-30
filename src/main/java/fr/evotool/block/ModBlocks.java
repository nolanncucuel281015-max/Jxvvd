package fr.evotool.block;

import fr.evotool.EvoTool;
import fr.evotool.dimension.EvolutionPortalBlock;
import fr.evotool.dimension.EvolvingBlock;
import fr.evotool.dimension.EvolvingPillarBlock;
import fr.evotool.dimension.SanctuaryAltarBlock;
import fr.evotool.dimension.SanctuaryFeature;
import java.util.function.Function;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

public final class ModBlocks {
	// Évolium : uniquement dans l'ardoise des abîmes, plus rare que le diamant, pioche en diamant requise.
	public static final Block DEEPSLATE_EVOLIUM_ORE = register("deepslate_evolium_ore",
			s -> new ExperienceDroppingBlock(UniformIntProvider.create(5, 9), s),
			AbstractBlock.Settings.copy(Blocks.DEEPSLATE_DIAMOND_ORE).strength(5.5F, 6.0F).luminance(state -> 3),
			Rarity.RARE);
	public static final Block EVOLIUM_BLOCK = register("evolium_block", Block::new,
			AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK).luminance(state -> 7), Rarity.RARE);

	public static final Block RUBY_ORE = register("ruby_ore",
			s -> new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), s),
			AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE), Rarity.COMMON);
	public static final Block DEEPSLATE_RUBY_ORE = register("deepslate_ruby_ore",
			s -> new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), s),
			AbstractBlock.Settings.copy(Blocks.DEEPSLATE_DIAMOND_ORE), Rarity.COMMON);
	public static final Block RUBY_BLOCK = register("ruby_block", Block::new,
			AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK), Rarity.COMMON);

	public static final Block SAPPHIRE_ORE = register("sapphire_ore",
			s -> new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), s),
			AbstractBlock.Settings.copy(Blocks.DIAMOND_ORE), Rarity.COMMON);
	public static final Block DEEPSLATE_SAPPHIRE_ORE = register("deepslate_sapphire_ore",
			s -> new ExperienceDroppingBlock(UniformIntProvider.create(3, 7), s),
			AbstractBlock.Settings.copy(Blocks.DEEPSLATE_DIAMOND_ORE), Rarity.COMMON);
	public static final Block SAPPHIRE_BLOCK = register("sapphire_block", Block::new,
			AbstractBlock.Settings.copy(Blocks.DIAMOND_BLOCK), Rarity.COMMON);

	// Évolite : dans la pierre de l'End, un peu plus fréquent que les débris antiques. Pioche en diamant requise.
	public static final Block EVOLITE_ORE = register("evolite_ore", Block::new,
			AbstractBlock.Settings.copy(Blocks.ANCIENT_DEBRIS).luminance(state -> 5), Rarity.RARE);
	public static final Block EVOLITE_BLOCK = register("evolite_block", Block::new,
			AbstractBlock.Settings.copy(Blocks.NETHERITE_BLOCK).luminance(state -> 9), Rarity.EPIC);

	// --- Dimension évolutive : blocs à 6 niveaux d'évolution (de plus en plus lumineux)
	public static final Block EVOLVING_STONE = register("evolving_stone", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.STONE).ticksRandomly().luminance(state -> state.get(EvolvingBlock.EVOLUTION) * 2),
			Rarity.COMMON);
	public static final Block EVOLVING_DIRT = register("evolving_dirt", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.DIRT).ticksRandomly().luminance(state -> state.get(EvolvingBlock.EVOLUTION)),
			Rarity.COMMON);
	public static final Block EVOLVING_GRASS = register("evolving_grass", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.GRASS_BLOCK).ticksRandomly().luminance(state -> state.get(EvolvingBlock.EVOLUTION) * 2),
			Rarity.COMMON);
	public static final Block EVOLVING_LOG = register("evolving_log", EvolvingPillarBlock::new,
			AbstractBlock.Settings.copy(Blocks.CHERRY_LOG).ticksRandomly().luminance(state -> state.get(EvolvingBlock.EVOLUTION)),
			Rarity.COMMON);
	public static final Block EVOLVING_LEAVES = register("evolving_leaves", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.CHERRY_LEAVES).ticksRandomly().luminance(state -> state.get(EvolvingBlock.EVOLUTION) * 2),
			Rarity.COMMON);
	public static final Block EVOLVING_CRYSTAL = register("evolving_crystal", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.AMETHYST_BLOCK).ticksRandomly()
					.luminance(state -> Math.min(15, 6 + state.get(EvolvingBlock.EVOLUTION) * 2)),
			Rarity.UNCOMMON);
	public static final Block EVOLVING_ORE = register("evolving_ore", EvolvingBlock::new,
			AbstractBlock.Settings.copy(Blocks.DEEPSLATE_DIAMOND_ORE).strength(4.0F, 6.0F).ticksRandomly()
					.luminance(state -> 3 + state.get(EvolvingBlock.EVOLUTION) * 2),
			Rarity.RARE);
	/** Portail évolutif : allumé dans un cadre de blocs de rubis avec le briquet d'évolium. */
	public static final Block EVOLUTION_PORTAL = registerBlockOnly("evolution_portal", EvolutionPortalBlock::new,
			AbstractBlock.Settings.create().noCollision().strength(-1.0F).sounds(BlockSoundGroup.GLASS)
					.luminance(state -> 12).dropsNothing().pistonBehavior(PistonBehavior.BLOCK));

	/**
	 * Autel des sanctuaires de la dimension évolutive : un clic droit réveille le boss.
	 * Il se fabrique aussi (cloche, bloc d'évolium, or, quartz) et se récupère à la pioche.
	 */
	public static final Block SANCTUARY_ALTAR = register("sanctuary_altar", SanctuaryAltarBlock::new,
			AbstractBlock.Settings.copy(Blocks.QUARTZ_BLOCK).strength(5.0F, 1200.0F).requiresTool().luminance(state -> 13)
					.nonOpaque().pistonBehavior(PistonBehavior.BLOCK),
			Rarity.EPIC);

	/** Génération des sanctuaires (voir worldgen/configured_feature/sanctuary.json). */
	public static final Feature<DefaultFeatureConfig> SANCTUARY_FEATURE = Registry.register(Registries.FEATURE,
			EvoTool.id("sanctuary"), new SanctuaryFeature(DefaultFeatureConfig.CODEC));

	private ModBlocks() {
	}

	private static Block registerBlockOnly(String name, Function<AbstractBlock.Settings, Block> factory, AbstractBlock.Settings settings) {
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, EvoTool.id(name));
		return Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));
	}

	private static Block register(String name, Function<AbstractBlock.Settings, Block> factory,
			AbstractBlock.Settings settings, Rarity rarity) {
		RegistryKey<Block> blockKey = RegistryKey.of(RegistryKeys.BLOCK, EvoTool.id(name));
		Block block = Registry.register(Registries.BLOCK, blockKey, factory.apply(settings.registryKey(blockKey)));

		RegistryKey<Item> itemKey = RegistryKey.of(RegistryKeys.ITEM, EvoTool.id(name));
		Registry.register(Registries.ITEM, itemKey, new BlockItem(block,
				new Item.Settings().registryKey(itemKey).useBlockPrefixedTranslationKey().rarity(rarity)));
		return block;
	}

	public static void init() {
	}
}
