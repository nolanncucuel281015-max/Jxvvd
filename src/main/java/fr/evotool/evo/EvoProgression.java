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
	/** Armure améliorée à l'abyssite : elle continue jusqu'au niveau 150, avec 11 formes noires de plus (21 à 31). */
	public static final int ABYSS_MAX_LEVEL = 150;
	public static final int ABYSS_MAX_STAGE = MAX_STAGE + 1 + (ABYSS_MAX_LEVEL - MAX_LEVEL) / LEVELS_PER_STAGE;

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

	/** Pièce d'armure améliorée à l'abyssite. */
	public static boolean isAbyssal(ItemStack stack) {
		return stack.contains(ModComponents.ABYSSAL);
	}

	public static int maxLevel(ItemStack stack) {
		return isAbyssal(stack) ? ABYSS_MAX_LEVEL : MAX_LEVEL;
	}

	public static int maxStage(ItemStack stack) {
		return isAbyssal(stack) ? ABYSS_MAX_STAGE : MAX_STAGE;
	}

	/** Forme de l'objet : pour une pièce abyssale, 21 au niveau 100 puis une de plus tous les 5 niveaux (31 au niveau 150). */
	public static int stage(ItemStack stack) {
		int level = get(stack).level();
		if (isAbyssal(stack) && level >= MAX_LEVEL) {
			return MAX_STAGE + 1 + (Math.min(level, ABYSS_MAX_LEVEL) - MAX_LEVEL) / LEVELS_PER_STAGE;
		}
		return stage(level);
	}

	public static Text stageName(ItemStack stack) {
		return Text.translatable("stage.evotool." + stage(stack));
	}

	/** XP qu'il faut à cet objet pour son prochain niveau (0 au maximum). */
	public static int xpToNext(ItemStack stack) {
		EvoKind kind = kindOf(stack);
		return kind == null ? 0 : kind.profile().curve().xpToNext(get(stack).level(), maxLevel(stack));
	}

	/** Le niveau suivant commence une nouvelle forme : il faudra faire évoluer l'objet avec la touche R. */
	public static boolean isFormGate(int level) {
		return isFormGate(level, MAX_LEVEL);
	}

	public static boolean isFormGate(int level, int maxLevel) {
		return level < maxLevel && (level + 1) % LEVELS_PER_STAGE == 0;
	}

	/** L'objet a toute l'XP de sa prochaine forme : il attend qu'on appuie sur R pour évoluer. */
	public static boolean isReadyToEvolve(ItemStack stack) {
		EvoKind kind = kindOf(stack);
		if (kind == null) {
			return false;
		}
		EvoData data = get(stack);
		return isFormGate(data.level(), maxLevel(stack)) && data.xp() >= xpToNext(stack);
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

	/**
	 * Bonus d'efficacité de minage : +0,2 par niveau, et minage instantané au niveau 100 (assez pour casser
	 * l'obsidienne d'un coup). Le minage instantané ne doit jamais être retiré ni affaibli : règle du joueur.
	 */
	public static double miningBonus(EvoProfile profile, int level) {
		return profile.hasPower(Power.INSTAMINE, level) ? INSTAMINE_BONUS : level * 0.2;
	}

	/** Efficacité donnée par le minage instantané : de quoi casser l'obsidienne (dureté 50) en un coup. */
	public static final double INSTAMINE_BONUS = 5000.0;

	/** Bonus de dégâts : +8 au niveau 100. */
	public static double damageBonus(int level) {
		return level * 0.08;
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
		boolean wasReady = isReadyToEvolve(stack);
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

		if (isReadyToEvolve(stack)) {
			if (!wasReady) {
				announceReady(player, world, name);
			}
			player.sendMessage(Text.translatable("message.evotool.actionbar_ready", name, evolveKey())
					.formatted(Formatting.LIGHT_PURPLE), true);
		} else if (level >= maxLevel(stack)) {
			player.sendMessage(Text.translatable("message.evotool.actionbar_max", name).formatted(Formatting.GOLD), true);
		} else {
			player.sendMessage(Text.translatable("message.evotool.actionbar", name, level, get(stack).xp(),
					xpToNext(stack)).formatted(Formatting.AQUA), true);
		}
	}

	/** Nom de la touche d'évolution, tel que le joueur l'a réglée (R par défaut). */
	public static Text evolveKey() {
		return Text.keybind("key.evotool.evolve").formatted(Formatting.YELLOW, Formatting.BOLD);
	}

	/** Prévient le joueur que cet objet peut évoluer. */
	public static void announceReady(PlayerEntity player, World world, Text name) {
		player.sendMessage(Text.translatable("message.evotool.ready", name, evolveKey())
				.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 1.4F);
	}

	/**
	 * Évolution (touche R) : l'objet prêt passe au niveau de sa nouvelle forme, avec ses déblocages.
	 *
	 * @return faux si l'objet n'était pas prêt
	 */
	public static boolean evolve(ItemStack stack, PlayerEntity player, World world) {
		if (!isReadyToEvolve(stack)) {
			return false;
		}
		EvoData data = get(stack);
		int level = data.level() + 1;
		stack.set(ModComponents.EVO_DATA, data.withProgress(level, 0, data.count()));
		refresh(stack, world.getRegistryManager());
		if (player != null) {
			if (level > MAX_LEVEL) {
				player.sendMessage(Text.translatable("message.evotool.new_form", stageName(stack), stage(stack), ABYSS_MAX_STAGE)
						.formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
			} else {
				announceUnlocks(kindOf(stack).profile(), player, level);
			}
		}
		return true;
	}

	/**
	 * Ajoute de l'XP sans rien annoncer (l'armure annonce une seule fois pour tout l'ensemble).
	 * Avant chaque nouvelle forme, l'objet s'arrête : il faut le faire évoluer avec la touche R.
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
		int max = maxLevel(stack);
		int level = data.level();
		int xp = data.xp() + amount;
		while (level < max && xp >= curve.xpToNext(level, max)) {
			if (isFormGate(level, max)) {
				xp = curve.xpToNext(level, max);   // prêt à évoluer : on attend la touche R
				break;
			}
			xp -= curve.xpToNext(level, max);
			level++;
		}
		if (level >= max) {
			xp = 0;
		}
		boolean leveledUp = level != data.level();
		stack.set(ModComponents.EVO_DATA, data.withProgress(level, xp, data.count() + countIncrement));
		if (leveledUp) {
			refresh(stack, world.getRegistryManager());
		}
		return level;
	}

	public static void announceUnlocks(EvoProfile profile, PlayerEntity player, int level) {
		if (level % LEVELS_PER_STAGE == 0 && level <= MAX_LEVEL) {
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
			RegistryEntry<Enchantment> entry = enchantments.getOrThrow(step.enchantment());
			int current = EnchantmentHelper.getLevel(entry, stack);
			// Jamais plus que ce que donne l'évolution ou que le maximum normal (une enclume ne fait pas mieux) :
			// les objets d'une ancienne version (Fortune VI…) redescendent.
			int cap = Math.max(step.levels().length, entry.value().getMaxLevel());
			int target = exact ? wanted : Math.min(Math.max(current, wanted), cap);
			if (target != current) {
				// Un niveau 0 retire l'enchantement.
				EnchantmentHelper.apply(stack, builder -> builder.set(entry, target));
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

		applyForm(stack);
	}

	/** Met le modèle (et la texture portée de l'armure) de la forme actuelle. */
	public static void applyForm(ItemStack stack) {
		EvoKind kind = kindOf(stack);
		if (kind == null) {
			return;
		}
		EvoProfile profile = kind.profile();
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(
				List.of((float) stage(stack)), List.of(), List.of(), List.of()));

		// Plastron, jambières et bottes : la texture portée change aussi avec la forme.
		// (Le casque est un modèle 3D porté sur la tête : il suit déjà custom_model_data.)
		if (profile.isArmor() && profile.armorSlot() != EquipmentSlot.HEAD) {
			stack.set(DataComponentTypes.EQUIPPABLE, ModItems.equippable(profile.armorSlot(), stage(stack)));
		}
	}
}
