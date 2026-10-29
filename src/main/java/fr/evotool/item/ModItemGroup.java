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
						entries.add(ModItems.EVOLITE_INGOT);
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
					})
					.build());

	private ModItemGroup() {
	}

	public static void init() {
	}
}
