package fr.evotool.item;

import fr.evotool.EvoTool;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ToolMaterial;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Rarity;
import net.minecraft.util.Unit;

public final class ModItems {
	public static final TagKey<Item> EVOLIUM_REPAIR = TagKey.of(RegistryKeys.ITEM, EvoTool.id("evolium_tool_materials"));
	public static final TagKey<Block> EVOLIUM_ORES = TagKey.of(RegistryKeys.BLOCK, EvoTool.id("evolium_ores"));
	public static final TagKey<Block> GEM_ORES = TagKey.of(RegistryKeys.BLOCK, EvoTool.id("gem_ores"));

	/**
	 * Matériau de base : vitesse d'une pioche en fer au début, mais peut tout récolter.
	 * La vitesse augmente ensuite avec le niveau (attribut d'efficacité de minage).
	 */
	public static final ToolMaterial EVOLIUM_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4096, 6.0F, 4.0F, 22, EVOLIUM_REPAIR);

	public static final Item EVOLIUM = register("evolium", Item::new, new Item.Settings().rarity(Rarity.RARE));
	public static final Item RUBY = register("ruby", Item::new, new Item.Settings());
	public static final Item SAPPHIRE = register("sapphire", Item::new, new Item.Settings());

	public static final Item EVOLVING_PICKAXE = register("evolving_pickaxe", EvolvingPickaxeItem::new,
			new Item.Settings()
					.pickaxe(EVOLIUM_MATERIAL, 1.0F, -2.8F)
					.component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
					.component(ModComponents.EVO_DATA, EvoData.DEFAULT)
					.fireproof()
					.rarity(Rarity.EPIC)
					.maxCount(1));

	private ModItems() {
	}

	private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, EvoTool.id(name));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}

	public static void init() {
	}
}
