package fr.evotool.evo;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.item.ModItems;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalEntityTypeTags;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Règles communes d'évolution : XP, montées de niveau, annonces, enchantements et formes. */
public final class EvoProgression {
	public static final int MAX_LEVEL = 100;
	/** Le modèle 3D change de forme tous les 5 niveaux : 21 formes (0 à 20). */
	public static final int LEVELS_PER_STAGE = 5;
	public static final int MAX_STAGE = MAX_LEVEL / LEVELS_PER_STAGE;

	private static final Identifier SPEED_MODIFIER_ID = EvoTool.id("evolution_speed");
	private static final Identifier DAMAGE_MODIFIER_ID = EvoTool.id("evolution_damage");

	private EvoProgression() {
	}

	public static EvoData get(ItemStack stack) {
		return stack.getOrDefault(ModComponents.EVO_DATA, EvoData.DEFAULT);
	}

	public static EvoKind kindOf(ItemStack stack) {
		return stack.getItem() instanceof EvolvingItem item ? item.evoKind() : null;
	}

	public static int stage(int level) {
		return Math.min(level, MAX_LEVEL) / LEVELS_PER_STAGE;
	}

	public static Text stageName(int level) {
		return Text.translatable("stage.evotool." + stage(level));
	}

	/** Pouvoir débloqué et, s'il peut être coupé, activé dans le menu. */
	public static boolean isActive(EvoProfile profile, EvoData data, Power power) {
		return profile.hasPower(power, data.level()) && (!power.toggleable() || (data.flags() & power.bit()) != 0);
	}

	/** Mode sélectionné, ou 1 bloc s'il n'est pas (ou plus) débloqué. */
	public static EvoMode selectedMode(EvoProfile profile, EvoData data) {
		EvoMode mode = EvoMode.byIndex(data.mode());
		return profile.modeUnlocked(mode, data.level()) ? mode : EvoMode.SINGLE;
	}

	/** Bonus d'efficacité de minage. Avec le minage instantané, il suffit à casser l'obsidienne d'un coup. */
	public static double miningBonus(EvoProfile profile, int level) {
		return profile.hasPower(Power.INSTAMINE, level) ? 5000.0 : level * 0.25;
	}

	public static double damageBonus(int level) {
		return level * 0.1;
	}

	public static int xpForBlock(BlockState state) {
		if (state.isOf(ModBlocks.DEEPSLATE_EVOLIUM_ORE)) {
			return 60;
		}
		if (state.isOf(ModBlocks.EVOLITE_ORE)) {
			return 40;
		}
		if (state.isIn(ModItems.GEM_ORES)) {
			return 15;
		}
		if (state.isIn(ConventionalBlockTags.ORES)) {
			return 6;
		}
		return 1;
	}

	/** La moitié des PV max du monstre (un zombie = 10 XP), x5 pour un boss. */
	public static int xpForKill(LivingEntity entity) {
		int xp = Math.max(1, Math.round(entity.getMaxHealth() / 2.0F));
		return isBoss(entity) ? xp * 5 : xp;
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity.getType().isIn(ConventionalEntityTypeTags.BOSSES);
	}

	/**
	 * Ajoute de l'XP et gère les montées de niveau (messages, son, déblocages).
	 *
	 * @param countIncrement de combien augmenter le compteur affiché
	 */
	public static void addXp(ItemStack stack, int amount, int countIncrement, PlayerEntity player, World world) {
		EvoKind kind = kindOf(stack);
		if (kind == null || amount <= 0) {
			return;
		}
		EvoProfile profile = kind.profile();
		int startLevel = get(stack).level();
		int level = gainXp(stack, amount, countIncrement, world);
		for (int reached = startLevel + 1; reached <= level; reached++) {
			announceUnlocks(profile, player, reached);
		}

		Text name = stack.getName();
		if (level != startLevel) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
					SoundCategory.PLAYERS, 0.8F, 0.6F + level / 200.0F);
			player.sendMessage(Text.translatable("message.evotool.level_up", name, level)
					.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
		}

		if (level >= MAX_LEVEL) {
			player.sendMessage(Text.translatable("message.evotool.actionbar_max", name).formatted(Formatting.GOLD), true);
		} else {
			player.sendMessage(Text.translatable("message.evotool.actionbar", name, level, get(stack).xp(),
					profile.curve().xpToNext(level)).formatted(Formatting.AQUA), true);
		}
	}

	/**
	 * Ajoute de l'XP sans rien annoncer (l'armure annonce une seule fois pour tout l'ensemble).
	 *
	 * @return le nouveau niveau
	 */
	public static int gainXp(ItemStack stack, int amount, int countIncrement, World world) {
		EvoKind kind = kindOf(stack);
		EvoData data = get(stack);
		if (kind == null || amount <= 0) {
			return data.level();
		}
		XpCurve curve = kind.profile().curve();
		int level = data.level();
		int xp = data.xp() + amount;
		while (level < MAX_LEVEL && xp >= curve.xpToNext(level)) {
			xp -= curve.xpToNext(level);
			level++;
		}
		if (level >= MAX_LEVEL) {
			xp = 0;
		}
		boolean leveledUp = level != data.level();
		stack.set(ModComponents.EVO_DATA, data.withProgress(level, xp, data.count() + countIncrement));
		if (leveledUp) {
			refresh(stack, world.getRegistryManager());
		}
		return level;
	}

	private static void announceUnlocks(EvoProfile profile, PlayerEntity player, int level) {
		if (level % LEVELS_PER_STAGE == 0) {
			player.sendMessage(Text.translatable("message.evotool.new_form", stageName(level), stage(level), MAX_STAGE)
					.formatted(Formatting.AQUA, Formatting.BOLD), false);
		}
		for (Map.Entry<Power, Integer> entry : profile.powers().entrySet()) {
			if (entry.getValue() == level) {
				unlock(player, Text.translatable(entry.getKey().translationKey()));
			}
		}
		for (EvoProfile.ModeUnlock unlock : profile.modes()) {
			if (unlock.level() > 0 && unlock.level() == level) {
				unlock(player, Text.translatable("message.evotool.unlock.mode", Text.translatable(unlock.mode().translationKey())));
			}
		}
		for (EvoProfile.EnchantStep step : profile.enchants()) {
			for (int i = 0; i < step.levels().length; i++) {
				if (step.levels()[i] == level) {
					unlock(player, enchantText(step, i + 1));
				}
			}
		}
	}

	public static MutableText enchantText(EvoProfile.EnchantStep step, int enchantLevel) {
		return Text.translatable(step.translationKey()).append(" ").append(Text.translatable("enchantment.level." + enchantLevel));
	}

	private static void unlock(PlayerEntity player, Text what) {
		player.sendMessage(Text.translatable("message.evotool.unlocked", what).formatted(Formatting.GOLD), false);
	}

	/** Recalcule ce qui dépend du niveau : enchantements, bonus de vitesse / dégâts et forme du modèle. */
	public static void refresh(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
		refresh(stack, registries, false);
	}

	/**
	 * L'Élan vanilla coûte de la faim : sur la Lance Évolutive, on le remplace par l'Élan évolutif
	 * (même niveau, ou plus si l'évolution en donne plus).
	 */
	public static void convertVanillaLunge(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
		RegistryWrapper.Impl<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
		RegistryEntry<Enchantment> vanilla = enchantments.getOrThrow(Enchantments.LUNGE);
		int level = EnchantmentHelper.getLevel(vanilla, stack);
		if (level <= 0) {
			return;
		}
		RegistryEntry<Enchantment> evolving = enchantments.getOrThrow(ModEnchantments.EVOLVING_LUNGE);
		int target = Math.max(level, EnchantmentHelper.getLevel(evolving, stack));
		EnchantmentHelper.apply(stack, builder -> {
			builder.set(vanilla, 0);
			builder.set(evolving, target);
		});
	}

	/**
	 * @param exact si vrai, les enchantements d'évolution sont mis exactement au niveau prévu
	 *              (utilisé par /upgrade pour pouvoir redescendre). Sinon ils ne font qu'augmenter,
	 *              pour ne pas écraser un enchantement plus fort mis à l'enclume.
	 */
	public static void refresh(ItemStack stack, RegistryWrapper.WrapperLookup registries, boolean exact) {
		EvoKind kind = kindOf(stack);
		if (kind == null) {
			return;
		}
		EvoProfile profile = kind.profile();
		int level = get(stack).level();

		RegistryWrapper.Impl<Enchantment> enchantments = registries.getOrThrow(RegistryKeys.ENCHANTMENT);
		if (kind == EvoKind.SPEAR) {
			convertVanillaLunge(stack, registries);
		}
		for (EvoProfile.EnchantStep step : profile.enchants()) {
			int wanted = step.levelAt(level);
			if (wanted <= 0 && !exact) {
				continue;
			}
			RegistryEntry<Enchantment> entry = enchantments.getOrThrow(step.enchantment());
			int current = EnchantmentHelper.getLevel(entry, stack);
			if (exact ? current != wanted : current < wanted) {
				// Un niveau 0 retire l'enchantement.
				EnchantmentHelper.apply(stack, builder -> builder.set(entry, wanted));
			}
		}

		AttributeModifiersComponent modifiers = stack.getOrDefault(DataComponentTypes.ATTRIBUTE_MODIFIERS,
				AttributeModifiersComponent.DEFAULT);
		if (profile.speedBonus()) {
			modifiers = modifiers.with(EntityAttributes.MINING_EFFICIENCY,
					new EntityAttributeModifier(SPEED_MODIFIER_ID, miningBonus(profile, level), EntityAttributeModifier.Operation.ADD_VALUE),
					AttributeModifierSlot.MAINHAND);
		}
		if (profile.damageBonus()) {
			modifiers = modifiers.with(EntityAttributes.ATTACK_DAMAGE,
					new EntityAttributeModifier(DAMAGE_MODIFIER_ID, damageBonus(level), EntityAttributeModifier.Operation.ADD_VALUE),
					AttributeModifierSlot.MAINHAND);
		}
		stack.set(DataComponentTypes.ATTRIBUTE_MODIFIERS, modifiers);

		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
				List.of((float) stage(level)), List.of(), List.of(), List.of()));

		// Plastron, jambières et bottes : la texture portée change aussi avec la forme.
		// (Le casque est un modèle 3D porté sur la tête : il suit déjà custom_model_data.)
		if (profile.isArmor() && profile.armorSlot() != EquipmentSlot.HEAD) {
			stack.set(DataComponentTypes.EQUIPPABLE, ModItems.equippable(profile.armorSlot(), stage(level)));
		}
	}
}
