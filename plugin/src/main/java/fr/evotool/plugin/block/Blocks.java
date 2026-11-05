package fr.evotool.plugin.block;

import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** Pose, reconnaissance et butin des blocs du mod (voir {@link CustomBlock}). */
public final class Blocks {
	private Blocks() {
	}

	public static CustomBlock at(Block block) {
		return CustomBlock.of(block);
	}

	/** Pose le bloc du mod (sans mise à jour des voisins : les blocs musicaux restent dans leur état). */
	public static void place(Block block, CustomBlock custom, int variant) {
		block.setBlockData(custom.data(variant), false);
	}

	public static void place(Block block, CustomBlock custom) {
		place(block, custom, 0);
	}

	public static int variant(Block block) {
		return CustomBlock.variantOf(block.getBlockData());
	}

	public static int evolution(Block block) {
		CustomBlock custom = at(block);
		return custom == null ? 0 : CustomBlock.evolutionOf(custom, variant(block));
	}

	/** Orientation d'une bûche selon la face cliquée : 0 debout, 1 nord-sud, 2 est-ouest. */
	public static int axis(BlockFace face) {
		return switch (face) {
			case NORTH, SOUTH -> 1;
			case EAST, WEST -> 2;
			default -> 0;
		};
	}

	/** Niveau de la pioche (0 : pas une pioche, 1 bois… 5 netherite et outils du mod). */
	public static int pickaxeTier(ItemStack tool) {
		if (tool == null) {
			return 0;
		}
		return switch (tool.getType()) {
			case WOODEN_PICKAXE, GOLDEN_PICKAXE -> 1;
			case STONE_PICKAXE, COPPER_PICKAXE -> 2;
			case IRON_PICKAXE -> 3;
			case DIAMOND_PICKAXE -> 4;
			case NETHERITE_PICKAXE -> 5;
			default -> 0;
		};
	}

	/** L'outil peut-il récolter ce bloc ? (minerais : pioche assez solide). */
	public static boolean canHarvest(CustomBlock block, ItemStack tool) {
		return block.tier() <= 0 || pickaxeTier(tool) >= block.tier();
	}

	/** Bon outil pour casser vite ce bloc (minage en zone : pioche pour la pierre et les minerais, pelle pour la terre…). */
	public static boolean isSuitable(CustomBlock block, ItemStack tool) {
		Material type = tool == null ? Material.AIR : tool.getType();
		return switch (block) {
			case EVOLVING_DIRT, EVOLVING_GRASS -> Tag.ITEMS_SHOVELS.isTagged(type);
			case EVOLVING_LOG -> Tag.ITEMS_AXES.isTagged(type);
			case EVOLVING_LEAVES -> Tag.ITEMS_HOES.isTagged(type) || type == Material.SHEARS;
			default -> Tag.ITEMS_PICKAXES.isTagged(type);
		};
	}

	private static int fortuneCount(int base, ItemStack tool) {
		int fortune = tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.FORTUNE);
		if (fortune <= 0) {
			return base;
		}
		int bonus = ThreadLocalRandom.current().nextInt(fortune + 2) - 1;
		return base * (Math.max(0, bonus) + 1);
	}

	private static boolean silk(ItemStack tool) {
		return tool != null && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
	}

	/** Ce que lâche ce bloc du mod cassé avec cet outil (les machines ajoutent leur contenu elles-mêmes). */
	public static List<ItemStack> drops(Block block, ItemStack tool) {
		List<ItemStack> drops = new ArrayList<>();
		CustomBlock custom = at(block);
		if (custom == null || !canHarvest(custom, tool)) {
			return drops;
		}
		EvoItem self = EvoItem.of(custom);
		switch (custom.drop()) {
			case SELF, MACHINE -> drops.add(Items.create(self));
			case ORE -> {
				if (silk(tool)) {
					drops.add(Items.create(self));
				} else {
					EvoItem gem = switch (custom) {
						case DEEPSLATE_EVOLIUM_ORE -> EvoItem.EVOLIUM;
						case RUBY_ORE, DEEPSLATE_RUBY_ORE -> EvoItem.RUBY;
						case SAPPHIRE_ORE, DEEPSLATE_SAPPHIRE_ORE -> EvoItem.SAPPHIRE;
						default -> EvoItem.ABYSSITE;
					};
					drops.add(Items.create(gem, fortuneCount(1, tool)));
				}
			}
			case EVOLVING_GRASS -> drops.add(Items.create(silk(tool) ? self : EvoItem.EVOLVING_DIRT));
			case EVOLVING_ORE -> {
				if (silk(tool)) {
					drops.add(Items.create(self));
				} else {
					drops.add(Items.create(EvoItem.EVOLIUM, fortuneCount(1 + evolution(block), tool)));
				}
			}
			case NONE_UNLESS_SHEARS -> {
				if (silk(tool) || (tool != null && tool.getType() == Material.SHEARS)) {
					drops.add(Items.create(self));
				}
			}
		}
		return drops;
	}

	/** XP (orbes) d'un minerai cassé sans toucher de soie. */
	public static int experience(Block block, ItemStack tool) {
		CustomBlock custom = at(block);
		if (custom == null || custom.xpMax() <= 0 || silk(tool) || !canHarvest(custom, tool)) {
			return 0;
		}
		return ThreadLocalRandom.current().nextInt(custom.xpMin(), custom.xpMax() + 1);
	}
}
