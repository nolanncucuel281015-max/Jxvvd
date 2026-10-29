package fr.evotool.item;

import fr.evotool.EvoTool;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.weapon.WeaponKind;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.EquippableComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.MaceItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.equipment.ArmorMaterial;
import net.minecraft.item.equipment.EquipmentAsset;
import net.minecraft.item.equipment.EquipmentAssetKeys;
import net.minecraft.item.equipment.EquipmentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundEvents;
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
	public static final Item EVOLITE_INGOT = register("evolite_ingot", Item::new,
			new Item.Settings().rarity(Rarity.EPIC).fireproof());

	/** Briquet d'évolium (évolium + silex) : allume le portail en blocs de rubis vers la dimension évolutive. */
	public static final Item EVOLIUM_LIGHTER = register("evolium_lighter", EvoliumLighterItem::new,
			new Item.Settings().maxDamage(128).rarity(Rarity.RARE));
	/** Morceaux du modèle du boss (objets d'affichage), pas dans l'inventaire créatif. */
	public static final Item BELL_BOSS_PART = register("bell_boss_part", Item::new, new Item.Settings().maxCount(1));

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
	/**
	 * Lance : mêmes réglages de charge que la lance en netherite de Minecraft 1.21.11,
	 * mais les estocs n'ont pas besoin d'être chargés : on peut enchaîner les Élans.
	 */
	public static final Item EVOLVING_SPEAR = register("evolving_spear",
			s -> new EvolvingToolItem(EvoKind.SPEAR, s),
			evolving().spear(EVOLIUM_MATERIAL, 1.15F, 1.2F, 0.4F, 2.5F, 7.0F, 5.5F, 5.1F, 8.75F, 4.6F)
					.component(DataComponentTypes.MINIMUM_ATTACK_CHARGE, 0.0F));
	public static final Item EVOLVING_BOW = register("evolving_bow", EvolvingBowItem::new,
			evolving().maxDamage(384).enchantable(1));
	public static final Item EVOLIUM_MACE = register("evolium_mace", EvoliumMaceItem::new,
			evolving()
					.maxDamage(2500)
					.component(DataComponentTypes.TOOL, MaceItem.createToolComponent())
					.attributeModifiers(EvoliumMaceItem.createEvoliumAttributeModifiers())
					.repairable(EVOLIUM_REPAIR)
					.enchantable(20));

	/**
	 * Armure en évolium : valeurs de la netherite. La texture portée dépend de la forme
	 * (evotool:evolium_stage0 à evolium_stage20, voir {@link #equippable}).
	 */
	public static final ArmorMaterial EVOLIUM_ARMOR = new ArmorMaterial(4096,
			Map.of(EquipmentType.HELMET, 3, EquipmentType.CHESTPLATE, 8, EquipmentType.LEGGINGS, 6,
					EquipmentType.BOOTS, 3, EquipmentType.BODY, 11),
			22, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 3.0F, 0.1F, EVOLIUM_REPAIR, equipmentAsset(0));

	/**
	 * Le casque n'a pas de texture d'armure : il est rendu sur la tête avec son modèle 3D
	 * (casque de chevalier à cornes), comme une citrouille sculptée.
	 */
	public static final Item EVOLVING_HELMET = register("evolving_helmet",
			s -> new EvolvingArmorItem(EvoKind.HELMET, s),
			evolving().armor(EVOLIUM_ARMOR, EquipmentType.HELMET)
					.component(DataComponentTypes.EQUIPPABLE, EquippableComponent.builder(EquipmentSlot.HEAD)
							.equipSound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE).build()));
	public static final Item EVOLVING_CHESTPLATE = register("evolving_chestplate",
			s -> new EvolvingArmorItem(EvoKind.CHESTPLATE, s), evolving().armor(EVOLIUM_ARMOR, EquipmentType.CHESTPLATE));
	public static final Item EVOLVING_LEGGINGS = register("evolving_leggings",
			s -> new EvolvingArmorItem(EvoKind.LEGGINGS, s), evolving().armor(EVOLIUM_ARMOR, EquipmentType.LEGGINGS));
	public static final Item EVOLVING_BOOTS = register("evolving_boots",
			s -> new EvolvingArmorItem(EvoKind.BOOTS, s), evolving().armor(EVOLIUM_ARMOR, EquipmentType.BOOTS));

	/** Matériaux des armes moddées (durabilité, enchantabilité, réparation à l'enclume). */
	public static final ToolMaterial RUBY_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 1800, 8.0F, 3.0F, 18,
			TagKey.of(RegistryKeys.ITEM, EvoTool.id("ruby_tool_materials")));
	public static final ToolMaterial SAPPHIRE_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 2000, 8.0F, 3.0F, 18,
			TagKey.of(RegistryKeys.ITEM, EvoTool.id("sapphire_tool_materials")));
	public static final ToolMaterial EVOLITE_MATERIAL = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 3000, 9.0F, 4.0F, 20,
			TagKey.of(RegistryKeys.ITEM, EvoTool.id("evolite_tool_materials")));

	// --- armes moddées (modèles 3D uniques, passif + attaque spéciale au clic droit)
	public static final Item EVOLIUM_SCYTHE = weapon(WeaponKind.SCYTHE, EVOLIUM_MATERIAL, Rarity.EPIC);
	public static final Item RUBY_KATANA = weapon(WeaponKind.KATANA, RUBY_MATERIAL, Rarity.RARE);
	public static final Item SAPPHIRE_WARHAMMER = weapon(WeaponKind.WARHAMMER, SAPPHIRE_MATERIAL, Rarity.RARE);
	public static final Item EVOLITE_GREATSWORD = weapon(WeaponKind.GREATSWORD, EVOLITE_MATERIAL, Rarity.EPIC);
	public static final Item SHADOW_DAGGER = weapon(WeaponKind.DAGGER, EVOLIUM_MATERIAL, Rarity.RARE);
	public static final Item ROYAL_HALBERD = weapon(WeaponKind.HALBERD, EVOLIUM_MATERIAL, Rarity.EPIC);
	public static final Item CRYSTAL_STAFF = weapon(WeaponKind.STAFF, EVOLIUM_MATERIAL, Rarity.EPIC);

	public static final List<Item> MODDED_WEAPONS = List.of(
			EVOLIUM_SCYTHE, RUBY_KATANA, SAPPHIRE_WARHAMMER, EVOLITE_GREATSWORD, SHADOW_DAGGER, ROYAL_HALBERD, CRYSTAL_STAFF);

	public static final List<Item> EVOLVING_ITEMS = List.of(
			EVOLVING_PICKAXE, EVOLVING_SHOVEL, EVOLVING_AXE, EVOLVING_HOE, EVOLVING_SWORD, EVOLVING_SPEAR, EVOLVING_BOW, EVOLIUM_MACE,
			EVOLVING_HELMET, EVOLVING_CHESTPLATE, EVOLVING_LEGGINGS, EVOLVING_BOOTS);

	private ModItems() {
	}

	public static Item itemFor(EvoKind kind) {
		return switch (kind) {
			case PICKAXE -> EVOLVING_PICKAXE;
			case SHOVEL -> EVOLVING_SHOVEL;
			case AXE -> EVOLVING_AXE;
			case HOE -> EVOLVING_HOE;
			case SWORD -> EVOLVING_SWORD;
			case SPEAR -> EVOLVING_SPEAR;
			case BOW -> EVOLVING_BOW;
			case MACE -> EVOLIUM_MACE;
			case HELMET -> EVOLVING_HELMET;
			case CHESTPLATE -> EVOLVING_CHESTPLATE;
			case LEGGINGS -> EVOLVING_LEGGINGS;
			case BOOTS -> EVOLVING_BOOTS;
		};
	}

	public static RegistryKey<EquipmentAsset> equipmentAsset(int stage) {
		return RegistryKey.of(EquipmentAssetKeys.REGISTRY_KEY, EvoTool.id("evolium_stage" + stage));
	}

	/** Composant « équipable » d'une pièce d'armure portée, avec la texture de la forme voulue. */
	public static EquippableComponent equippable(EquipmentSlot slot, int stage) {
		return EquippableComponent.builder(slot)
				.equipSound(SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE)
				.model(equipmentAsset(stage))
				.build();
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

	private static Item weapon(WeaponKind kind, ToolMaterial material, Rarity rarity) {
		return register(kind.id(), s -> new ModdedWeaponItem(kind, s),
				new Item.Settings().sword(material, 0.0F, 0.0F)
						.attributeModifiers(ModdedWeaponItem.attributes(kind))
						.rarity(rarity));
	}

	private static Item register(String name, Function<Item.Settings, Item> factory, Item.Settings settings) {
		RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, EvoTool.id(name));
		return Registry.register(Registries.ITEM, key, factory.apply(settings.registryKey(key)));
	}

	public static void init() {
	}
}
