package fr.evotool.item;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;

public final class ModItemGroup {
	public static final ItemGroup EVOTOOL = Registry.register(Registries.ITEM_GROUP, EvoTool.id("main"),
			FabricItemGroup.builder()
					.icon(() -> new ItemStack(ModItems.EVOLVING_PICKAXE))
					.displayName(Text.translatable("itemGroup.evotool.main"))
					.entries((context, entries) -> {
						ModItems.EVOLVING_ITEMS.forEach(entries::add);
						ModItems.MODDED_WEAPONS.forEach(entries::add);
						ModItems.MAGIC_WEAPONS.forEach(entries::add);
						entries.add(ModBlocks.SANCTUARY_ALTAR);
						entries.add(ModBlocks.SHIELD_GENERATOR);
						entries.add(ModBlocks.ELEMENTAL_COMBINER);
						entries.add(ModItems.MANUAL);
						entries.add(ModItems.BOSS_HEART);
						entries.add(ModItems.SPIDER_GLOVE);
						// un grimoire qui connaît déjà les 9 éléments (en survie, il se fait au combineur)
						net.minecraft.item.ItemStack grimoire = new net.minecraft.item.ItemStack(ModItems.ELEMENTAL_GRIMOIRE);
						grimoire.set(fr.evotool.component.ModComponents.GRIMOIRE,
								new fr.evotool.element.Grimoire(java.util.List.of(fr.evotool.element.Element.values()), 0));
						grimoire.set(net.minecraft.component.DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
						entries.add(grimoire);
						entries.add(ModItems.EVOLITE_INGOT);
						entries.add(ModItems.ABYSSITE);
						entries.add(ModBlocks.ABYSSITE_ORE);
						entries.add(ModBlocks.EVOLITE_BLOCK);
						entries.add(ModBlocks.EVOLITE_ORE);
						entries.add(ModItems.EVOLIUM);
						entries.add(ModBlocks.EVOLIUM_BLOCK);
						entries.add(ModBlocks.DEEPSLATE_EVOLIUM_ORE);
						entries.add(ModItems.RUBY);
						entries.add(ModBlocks.RUBY_BLOCK);
						entries.add(ModBlocks.RUBY_ORE);
						entries.add(ModBlocks.DEEPSLATE_RUBY_ORE);
						entries.add(ModItems.SAPPHIRE);
						entries.add(ModBlocks.SAPPHIRE_BLOCK);
						entries.add(ModBlocks.SAPPHIRE_ORE);
						entries.add(ModBlocks.DEEPSLATE_SAPPHIRE_ORE);
						entries.add(ModItems.EVOLIUM_LIGHTER);
						entries.add(ModBlocks.EVOLVING_STONE);
						entries.add(ModBlocks.EVOLVING_DIRT);
						entries.add(ModBlocks.EVOLVING_GRASS);
						entries.add(ModBlocks.EVOLVING_LOG);
						entries.add(ModBlocks.EVOLVING_LEAVES);
						entries.add(ModBlocks.EVOLVING_CRYSTAL);
						entries.add(ModBlocks.EVOLVING_ORE);
					})
					.build());

	private ModItemGroup() {
	}

	public static void init() {
	}
}
