package fr.evotool.weapon;

import fr.evotool.EvoTool;
import fr.evotool.component.MaceData;
import fr.evotool.component.ModComponents;
import fr.evotool.mining.EvoProgression;
import java.util.List;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.world.World;

/**
 * Règles d'évolution de la masse en évolium (XP en tuant des monstres).
 *
 * <ul>
 *   <li>Tous les 5 niveaux : nouvelle forme du modèle 3D (21 formes)</li>
 *   <li>Chaque niveau : +0,1 dégât</li>
 *   <li>Niveau 10 : vol de vie (10 % des dégâts infligés)</li>
 *   <li>Niveaux 15 / 25 / 35 / 45 / 55 : Densité I à V</li>
 *   <li>Niveaux 20 / 30 / 40 / 50 : Brèche I à IV</li>
 *   <li>Niveau 30 : onde de choc (les monstres autour prennent la moitié des dégâts)</li>
 *   <li>Niveau 50 : foudre (20 % de chance de foudroyer la cible)</li>
 *   <li>Niveaux 60 / 70 / 80 : Charge de vent I à III</li>
 *   <li>Niveau 100 : exécution, tue d'un coup tout monstre qui n'est pas un boss</li>
 * </ul>
 *
 * XP par monstre tué : la moitié de ses PV max (un zombie = 10 XP, un boss = 5x plus).
 * ~400 XP pour le niveau 20, ~1,1 million pour le niveau 100.
 */
public final class MaceProgression {
	public static final int MAX_LEVEL = 100;
	public static final int LIFESTEAL_LEVEL = 10;
	public static final int SHOCKWAVE_LEVEL = 30;
	public static final int LIGHTNING_LEVEL = 50;
	public static final int[] DENSITY_LEVELS = {15, 25, 35, 45, 55};
	public static final int[] BREACH_LEVELS = {20, 30, 40, 50};
	public static final int[] WIND_BURST_LEVELS = {60, 70, 80};

	private static final Identifier DAMAGE_MODIFIER_ID = EvoTool.id("evolution_damage");

	private MaceProgression() {
	}

	public static int xpToNext(int level) {
		if (level >= MAX_LEVEL) {
			return 0;
		}
		return (int) (3 + 0.75 * level + 0.00015 * Math.pow(level, 4.3));
	}

	public static int xpForKill(LivingEntity entity) {
		int xp = Math.max(1, Math.round(entity.getMaxHealth() / 2.0F));
		return entity.getType().isIn(ConventionalEntityTypeTags.BOSSES) ? xp * 5 : xp;
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity.getType().isIn(ConventionalEntityTypeTags.BOSSES);
	}

	public static int countReached(int level, int[] thresholds) {
		int count = 0;
		for (int threshold : thresholds) {
			if (level >= threshold) {
				count++;
			}
		}
		return count;
	}

	public static double damageBonus(int level) {
		return level * 0.1;
	}

	public static double shockwaveRadius(int level) {
		return level >= 75 ? 5.0 : 3.0;
	}

	public static MaceData get(ItemStack stack) {
		return stack.getOrDefault(ModComponents.MACE_DATA, MaceData.DEFAULT);
	}

	public static void addXp(ItemStack stack, int amount, PlayerEntity player, World world) {
		MaceData data = get(stack);
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

		stack.set(ModComponents.MACE_DATA, data.withLevelXp(level, xp, data.kills() + 1));

		if (level != startLevel) {
			refresh(stack, world.getRegistryManager());
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
					SoundCategory.PLAYERS, 0.8F, 0.5F + level / 200.0F);
			player.sendMessage(Text.translatable("message.evotool.mace_level_up", level)
					.formatted(Formatting.RED, Formatting.BOLD), false);
		}

		if (level >= MAX_LEVEL) {
			player.sendMessage(Text.translatable("message.evotool.mace_actionbar_max").formatted(Formatting.GOLD), true);
		} else {
			player.sendMessage(Text.translatable("message.evotool.mace_actionbar", level, xp, xpToNext(level))
					.formatted(Formatting.RED), true);
		}
	}

	private static void announceUnlocks(PlayerEntity player, int level) {
		if (level % EvoProgression.LEVELS_PER_STAGE == 0) {
			player.sendMessage(Text.translatable("message.evotool.new_form", EvoProgression.stageName(level),
					EvoProgression.stage(level), EvoProgression.MAX_STAGE).formatted(Formatting.AQUA, Formatting.BOLD), false);
		}
		if (level == LIFESTEAL_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.lifesteal"));
		}
		if (level == SHOCKWAVE_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.shockwave"));
		}
		if (level == 75) {
			unlock(player, Text.translatable("message.evotool.unlock.shockwave_big"));
		}
		if (level == LIGHTNING_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.lightning"));
		}
		announceEnchant(player, level, DENSITY_LEVELS, "enchantment.minecraft.density");
		announceEnchant(player, level, BREACH_LEVELS, "enchantment.minecraft.breach");
		announceEnchant(player, level, WIND_BURST_LEVELS, "enchantment.minecraft.wind_burst");
		if (level == MAX_LEVEL) {
			unlock(player, Text.translatable("message.evotool.unlock.execute"));
		}
	}

	private static void announceEnchant(PlayerEntity player, int level, int[] thresholds, String key) {
		for (int i = 0; i < thresholds.length; i++) {
			if (level == thresholds[i]) {
				unlock(player, Text.translatable(key).append(" ").append(Text.translatable("enchantment.level." + (i + 1))));
			}
		}
	}

	private static void unlock(PlayerEntity player, Text what) {
		player.sendMessage(Text.translatable("message.evotool.unlocked", what).formatted(Formatting.GOLD), false);
	}

	/** Recalcule les enchantements, les dégâts bonus et la forme du modèle selon le niveau. */
	public static void refresh(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
		int level = get(stack).level();
		RegistryWrapper.Impl<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
		applyEnchant(stack, enchantments, Enchantments.DENSITY, countReached(level, DENSITY_LEVELS));
		applyEnchant(stack, enchantments, Enchantments.BREACH, countReached(level, BREACH_LEVELS));
		applyEnchant(stack, enchantments, Enchantments.WIND_BURST, countReached(level, WIND_BURST_LEVELS));

		AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.DEFAULT);
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers.with(EntityAttributes.ATTACK_DAMAGE,
				new EntityAttributeModifier(DAMAGE_MODIFIER_ID, damageBonus(level), EntityAttributeModifier.Operation.ADD_VALUE),
				AttributeModifierSlot.MAINHAND));

		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
				List.of((float) EvoProgression.stage(level)), List.of(), List.of(), List.of()));
	}

	private static void applyEnchant(ItemStack stack, RegistryWrapper.Impl<Enchantment> enchantments,
			RegistryKey<Enchantment> key, int wanted) {
		if (wanted <= 0) {
			return;
		}
		RegistryEntry<Enchantment> entry = enchantments.getOrThrow(key);
		if (EnchantmentHelper.getLevel(entry, stack) < wanted) {
			EnchantmentHelper.apply(stack, builder -> builder.set(entry, wanted));
		}
	}
}
