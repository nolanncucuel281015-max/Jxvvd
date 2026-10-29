package fr.evotool.block;

import fr.evotool.EvoTool;
import java.util.function.Function;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ExperienceDroppingBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.intprovider.UniformIntProvider;

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

	private ModBlocks() {
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
