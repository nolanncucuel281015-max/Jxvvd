package fr.evotool.item;

import fr.evotool.EvoTool;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import java.util.List;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.MaceItem;
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
	public static final TagKey<Block> GEM_ORES = TagKey.of(RegistryKeys.BLOCK, EvoTool.id("gem_ores"));

	/**
	 * Matériau de base : vitesse d'un outil en fer au début, mais peut tout récolter.
	 * La vitesse et les dégâts augmentent ensuite avec le niveau.
	 */
	public static final ToolMaterial EVOLIUM_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 4096, 6.0F, 4.0F, 22, EVOLIUM_REPAIR);

	public static final Item EVOLIUM = register("evolium", Item::new, new Item.Settings().rarity(Rarity.RARE));
	public static final Item RUBY = register("ruby", Item::new, new Item.Settings());
	public static final Item SAPPHIRE = register("sapphire", Item::new, new Item.Settings());

	public static final Item EVOLVING_PICKAXE = register("evolving_pickaxe",
			s -> new EvolvingToolItem(EvoKind.PICKAXE, s),
			evolving().pickaxe(EVOLIUM_MATERIAL, 1.0F, -2.8F));
	public static final Item EVOLVING_SHOVEL = register("evolving_shovel",
			s -> new EvolvingShovelItem(EVOLIUM_MATERIAL, 1.5F, -3.0F, s), evolving());
	public static final Item EVOLVING_AXE = register("evolving_axe",
			s -> new EvolvingAxeItem(EVOLIUM_MATERIAL, 5.0F, -3.0F, s), evolving());
	public static final Item EVOLVING_HOE = register("evolving_hoe",
			s -> new EvolvingHoeItem(EVOLIUM_MATERIAL, -4.0F, 0.0F, s), evolving());
	public static final Item EVOLVING_SWORD = register("evolving_sword",
			s -> new EvolvingToolItem(EvoKind.SWORD, s),
			evolving().sword(EVOLIUM_MATERIAL, 3.0F, -2.4F));
	public static final Item EVOLIUM_MACE = register("evolium_mace", EvoliumMaceItem::new,
			evolving()
					.maxDamage(2500)
					.component(DataComponentTypes.TOOL, MaceItem.createToolComponent())
					.attributeModifiers(EvoliumMaceItem.createEvoliumAttributeModifiers())
					.repairable(EVOLIUM_REPAIR)
					.enchantable(20));

	public static final List<Item> EVOLVING_ITEMS = List.of(
			EVOLVING_PICKAXE, EVOLVING_SHOVEL, EVOLVING_AXE, EVOLVING_HOE, EVOLVING_SWORD, EVOLIUM_MACE);

	private ModItems() {
	}

	/** Réglages communs : incassable, résiste à la lave, données d'évolution. */
	private static Item.Settings evolving() {
		return new Item.Settings()
				.component(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE)
				.component(ModComponents.EVO_DATA, EvoData.DEFAULT)
				.fireproof()
				.rarity(Rarity.EPIC)
				.maxCount(1);
	}

	private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, EvoTool.id(name));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}

	public static void init() {
	}
}
