package fr.evotool.mining;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.item.ModItems;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Règles d'évolution de la pioche.
 *
 * <ul>
 *   <li>Tous les 5 niveaux : nouvelle forme du modèle 3D (21 formes)</li>
 *   <li>Niveau 10 : auto-smelt</li>
 *   <li>Niveaux 15 / 20 / 25 / 30 / 35 / 40 : Fortune I à VI</li>
 *   <li>Niveaux 20 / 30 / 40 / 50 : 3x3, 3x3x3, 5x5, 5x5x5</li>
 *   <li>Niveau 100 : minage instantané de tout</li>
 * </ul>
 *
 * Courbe d'XP (1 XP par bloc, plus pour les minerais) : ~1 500 XP pour le niveau 20 (environ 10 min),
 * ~110 000 pour le niveau 50, ~4,4 millions pour le niveau 100.
 */
public final class EvoProgression {
	public static final int MAX_LEVEL = 100;
	public static final int AUTOSMELT_LEVEL = 10;
	public static final int[] FORTUNE_LEVELS = {15, 20, 25, 30, 35, 40};
	/** Le modèle 3D change de forme tous les 5 niveaux : 21 formes (0 à 20). */
	public static final int LEVELS_PER_STAGE = 5;
	public static final int MAX_STAGE = MAX_LEVEL / LEVELS_PER_STAGE;

	private static final Identifier SPEED_MODIFIER_ID = EvoTool.id("evolution_speed");

	private EvoProgression() {
	}

	public static int xpToNext(int level) {
		if (level >= MAX_LEVEL) {
			return 0;
		}
		return (int) (10 + 3 * level + 0.0006 * Math.pow(level, 4.3));
	}

	public static int fortuneFor(int level) {
		int fortune = 0;
		for (int threshold : FORTUNE_LEVELS) {
			if (level >= threshold) {
				fortune++;
			}
		}
		return fortune;
	}

	public static boolean autosmeltUnlocked(int level) {
		return level >= AUTOSMELT_LEVEL;
	}

	public static int stage(int level) {
		return Math.min(level, MAX_LEVEL) / LEVELS_PER_STAGE;
	}

	public static Text stageName(int level) {
		return Text.translatable("stage.evotool." + stage(level));
	}

	/** Bonus d'efficacité de minage. Au niveau 100 il suffit à casser l'obsidienne instantanément. */
	public static double miningBonus(int level) {
		return level >= MAX_LEVEL ? 5000.0 : level * 0.25;
	}

	public static int xpFor(BlockState state) {
		if (state.isOf(ModBlocks.DEEPSLATE_EVOLIUM_ORE)) {
			return 60;
		}
		if (state.isIn(ModItems.GEM_ORES)) {
			return 15;
		}
		if (state.isIn(ConventionalBlockTags.ORES)) {
			return 6;
		}
		return 1;
	}

	public static EvoData get(ItemStack stack) {
		return stack.getOrDefault(ModComponents.EVO_DATA, EvoData.DEFAULT);
	}

	/**
	 * Ajoute de l'XP et gère les montées de niveau (messages, son, déblocages).
	 */
	public static void addXp(ItemStack stack, int amount, int blocks, PlayerEntity player, World world) {
		EvoData data = get(stack);
		int level = data.level();
		int xp = data.xp() + amount;
		int startLevel = level;

		while (level < MAX_LEVEL && xp >= xpToNext(level)) {
			xp -= xpToNext(level);
			level++;
			announceUnlocks(player, level);
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}

		stack.set(ModComponents.EVO_DATA, data.withLevelXp(level, xp, data.totalBlocks() + blocks));

		if (level != startLevel) {
			refresh(stack, world.getRegistryManager());
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
					SoundCategory.PLAYERS, 0.8F, 0.6F + level / 200.0F);
			player.sendMessage(Text.translatable("message.evotool.level_up", level)
					.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
		}

		if (level >= MAX_LEVEL) {
			player.sendMessage(Text.translatable("message.evotool.actionbar_max").formatted(Formatting.GOLD), true);
		} else {
			player.sendMessage(Text.translatable("message.evotool.actionbar", level, xp, xpToNext(level))
					.formatted(Formatting.AQUA), true);
		}
	}

	private static void announceUnlocks(PlayerEntity player, int level) {
		if (level % LEVELS_PER_STAGE == 0) {
			player.sendMessage(Text.translatable("message.evotool.new_form", stageName(level), stage(level), MAX_STAGE)
					.formatted(Formatting.AQUA, Formatting.BOLD), false);
		}
		if (level == AUTOSMELT_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.autosmelt"));
		}
		for (int i = 0; i < FORTUNE_LEVELS.length; i++) {
			if (level == FORTUNE_LEVELS[i]) {
				unlock(player, Text.translatable("message.evotool.unlock.fortune", Text.translatable("enchantment.level." + (i + 1))));
			}
		}
		for (EvoMode mode : EvoMode.values()) {
			if (mode.requiredLevel() > 0 && level == mode.requiredLevel()) {
				unlock(player, Text.translatable("message.evotool.unlock.mode", Text.translatable(mode.translationKey())));
			}
		}
		if (level == MAX_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.instamine"));
		}
	}

	private static void unlock(PlayerEntity player, Text what) {
		player.sendMessage(Text.translatable("message.evotool.unlocked", what).formatted(Formatting.GOLD), false);
	}

	/**
	 * Recalcule ce qui dépend du niveau : Fortune, vitesse de minage et apparence du modèle.
	 */
	public static void refresh(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
		int level = get(stack).level();

		int fortune = fortuneFor(level);
		if (fortune > 0) {
			RegistryEntry<Enchantment> fortuneEntry = registries.getOrThrow(RegistryKeys.ENCHANTMENT)
					.getOrThrow(Enchantments.FORTUNE);
			if (EnchantmentHelper.getLevel(fortuneEntry, stack) < fortune) {
				EnchantmentHelper.apply(stack, builder -> builder.set(fortuneEntry, fortune));
			}
		}

		AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.DEFAULT);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers.with(EntityAttributes.MINING_EFFICIENCY,
				new EntityAttributeModifier(SPEED_MODIFIER_ID, miningBonus(level), EntityAttributeModifier.Operation.ADD_VALUE),
				AttributeModifierSlot.MAINHAND));

		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
				List.of((float) stage(level)), List.of(), List.of(), List.of()));
	}
}
