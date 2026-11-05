package fr.evotool.plugin.evo;

import com.google.common.collect.Multimap;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;

/** Règles communes d'évolution : XP, montées de niveau, annonces, enchantements et formes (comme le mod). */
public final class Progression {
	public static final int MAX_LEVEL = 100;
	/** Le modèle 3D change de forme tous les 5 niveaux : 21 formes (0 à 20). */
	public static final int LEVELS_PER_STAGE = 5;
	public static final int MAX_STAGE = MAX_LEVEL / LEVELS_PER_STAGE;
	/** Armure abyssale : jusqu'au niveau 150, avec 11 formes noires de plus (21 à 31). */
	public static final int ABYSS_MAX_LEVEL = 150;
	public static final int ABYSS_MAX_STAGE = MAX_STAGE + 1 + (ABYSS_MAX_LEVEL - MAX_LEVEL) / LEVELS_PER_STAGE;
	/**
	 * Efficacité donnée par le minage instantané (niveau 100) : de quoi casser l'obsidienne en un coup.
	 * Règle du joueur : le minage instantané ne doit JAMAIS être retiré ni affaibli.
	 */
	public static final double INSTAMINE_BONUS = 5000.0;

	private Progression() {
	}

	public static EvoData get(ItemStack stack) {
		return EvoData.get(stack);
	}

	public static EvoKind kindOf(ItemStack stack) {
		return Items.kindOf(stack);
	}

	public static int stage(int level) {
		return Math.min(level, MAX_LEVEL) / LEVELS_PER_STAGE;
	}

	public static boolean isAbyssal(ItemStack stack) {
		return stack != null && stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(Keys.ABYSSAL);
	}

	public static int maxLevel(ItemStack stack) {
		return isAbyssal(stack) ? ABYSS_MAX_LEVEL : MAX_LEVEL;
	}

	public static int maxStage(ItemStack stack) {
		return isAbyssal(stack) ? ABYSS_MAX_STAGE : MAX_STAGE;
	}

	/** Forme de l'objet : pour une pièce abyssale, 21 au niveau 100 puis une de plus tous les 5 niveaux. */
	public static int stage(ItemStack stack) {
		int level = get(stack).level();
		if (isAbyssal(stack) && level >= MAX_LEVEL) {
			return MAX_STAGE + 1 + (Math.min(level, ABYSS_MAX_LEVEL) - MAX_LEVEL) / LEVELS_PER_STAGE;
		}
		return stage(level);
	}

	public static Component stageName(ItemStack stack) {
		return Lang.tr("stage.evotool." + stage(stack));
	}

	public static Component stageName(int level) {
		return Lang.tr("stage.evotool." + stage(level));
	}

	public static int xpToNext(ItemStack stack) {
		EvoKind kind = kindOf(stack);
		return kind == null ? 0 : kind.profile().curve().xpToNext(get(stack).level(), maxLevel(stack));
	}

	public static boolean isFormGate(int level, int maxLevel) {
		return level < maxLevel && (level + 1) % LEVELS_PER_STAGE == 0;
	}

	/** L'objet a toute l'XP de sa prochaine forme : il attend qu'on le fasse évoluer (accroupi + F). */
	public static boolean isReadyToEvolve(ItemStack stack) {
		if (kindOf(stack) == null) {
			return false;
		}
		EvoData data = get(stack);
		return isFormGate(data.level(), maxLevel(stack)) && data.xp() >= xpToNext(stack);
	}

	/** Pouvoir débloqué et, s'il peut être coupé, activé dans le menu. */
	public static boolean isActive(EvoProfile profile, EvoData data, Power power) {
		return profile.hasPower(power, data.level()) && (!power.toggleable() || (data.flags() & power.bit()) != 0);
	}

	public static boolean isActive(ItemStack stack, Power power) {
		EvoKind kind = kindOf(stack);
		return kind != null && isActive(kind.profile(), get(stack), power);
	}

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

	/** Bonus de dégâts : +8 au niveau 100. */
	public static double damageBonus(int level) {
		return level * 0.08;
	}

	public static int xpForBlock(Block block) {
		CustomBlock custom = CustomBlock.of(block);
		if (custom == CustomBlock.DEEPSLATE_EVOLIUM_ORE) {
			return 60;
		}
		if (custom == CustomBlock.EVOLITE_ORE) {
			return 40;
		}
		if (custom == CustomBlock.RUBY_ORE || custom == CustomBlock.DEEPSLATE_RUBY_ORE || custom == CustomBlock.SAPPHIRE_ORE
				|| custom == CustomBlock.DEEPSLATE_SAPPHIRE_ORE || custom == CustomBlock.ABYSSITE_ORE
				|| custom == CustomBlock.EVOLVING_ORE) {
			return 15;
		}
		if (isOre(block)) {
			return 6;
		}
		return 1;
	}

	public static boolean isOre(Block block) {
		return Tag.COAL_ORES.isTagged(block.getType()) || Tag.IRON_ORES.isTagged(block.getType())
				|| Tag.COPPER_ORES.isTagged(block.getType()) || Tag.GOLD_ORES.isTagged(block.getType())
				|| Tag.REDSTONE_ORES.isTagged(block.getType()) || Tag.LAPIS_ORES.isTagged(block.getType())
				|| Tag.DIAMOND_ORES.isTagged(block.getType()) || Tag.EMERALD_ORES.isTagged(block.getType())
				|| block.getType() == org.bukkit.Material.NETHER_QUARTZ_ORE || block.getType() == org.bukkit.Material.ANCIENT_DEBRIS;
	}

	/** La moitié des PV max du monstre (un zombie = 10 XP), x5 pour un boss. */
	public static int xpForKill(LivingEntity entity) {
		double max = entity.getAttribute(Attribute.MAX_HEALTH) != null ? entity.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
		int xp = Math.max(1, (int) Math.round(max / 2.0));
		return isBoss(entity) ? xp * 5 : xp;
	}

	public static boolean isBoss(LivingEntity entity) {
		return entity instanceof Boss || entity.getPersistentDataContainer().has(Keys.BOSS);
	}

	/** Comment on fait évoluer un objet dans le plugin (il n'y a pas de touche R) : « Accroupi + F ». */
	public static Component evolveKey() {
		return Lang.tr("plugin.evotool.key.evolve").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
	}

	/** Ajoute de l'XP et gère les montées de niveau (messages, son, déblocages). */
	public static void addXp(ItemStack stack, int amount, int countIncrement, Player player) {
		EvoKind kind = kindOf(stack);
		if (kind == null || amount <= 0) {
			return;
		}
		EvoProfile profile = kind.profile();
		int startLevel = get(stack).level();
		boolean wasReady = isReadyToEvolve(stack);
		int level = gainXp(stack, amount, countIncrement);
		for (int reached = startLevel + 1; reached <= level; reached++) {
			announceUnlocks(profile, player, reached);
		}
		Component name = name(stack);
		if (level != startLevel) {
			player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8F,
					0.6F + level / 200.0F);
			player.sendMessage(Lang.tr("message.evotool.level_up", name, level).color(NamedTextColor.LIGHT_PURPLE)
					.decorate(TextDecoration.BOLD));
		}
		if (isReadyToEvolve(stack)) {
			if (!wasReady) {
				announceReady(player, name);
			}
			player.sendActionBar(Lang.tr("message.evotool.actionbar_ready", name, evolveKey()).color(NamedTextColor.LIGHT_PURPLE));
		} else if (level >= maxLevel(stack)) {
			player.sendActionBar(Lang.tr("message.evotool.actionbar_max", name).color(NamedTextColor.GOLD));
		} else {
			player.sendActionBar(Lang.tr("message.evotool.actionbar", name, level, get(stack).xp(), xpToNext(stack))
					.color(NamedTextColor.AQUA));
		}
	}

	public static Component name(ItemStack stack) {
		ItemMeta meta = stack.getItemMeta();
		if (meta != null && meta.hasCustomName()) {
			return meta.customName();
		}
		EvoItem item = Items.of(stack);
		return item != null ? Lang.tr(item.translationKey()) : Component.translatable(stack.getType().translationKey());
	}

	public static void announceReady(Player player, Component name) {
		player.sendMessage(Lang.tr("message.evotool.ready", name, evolveKey()).color(NamedTextColor.LIGHT_PURPLE)
				.decorate(TextDecoration.BOLD));
		player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 1.4F);
	}

	/** Évolution : l'objet prêt passe au niveau de sa nouvelle forme, avec ses déblocages. */
	public static boolean evolve(ItemStack stack, Player player) {
		if (!isReadyToEvolve(stack)) {
			return false;
		}
		EvoData data = get(stack);
		int level = data.level() + 1;
		data.withProgress(level, 0, data.count()).write(stack);
		refresh(stack, false);
		if (player != null) {
			if (level > MAX_LEVEL) {
				player.sendMessage(Lang.tr("message.evotool.new_form", stageName(stack), stage(stack), ABYSS_MAX_STAGE)
						.color(NamedTextColor.DARK_PURPLE).decorate(TextDecoration.BOLD));
			} else {
				announceUnlocks(kindOf(stack).profile(), player, level);
			}
		}
		return true;
	}

	/**
	 * Ajoute de l'XP sans rien annoncer. Avant chaque nouvelle forme, l'objet s'arrête : il faut le faire évoluer.
	 *
	 * @return le nouveau niveau
	 */
	public static int gainXp(ItemStack stack, int amount, int countIncrement) {
		EvoKind kind = kindOf(stack);
		EvoData data = get(stack);
		if (kind == null || amount <= 0) {
			return data.level();
		}
		XpCurve curve = kind.profile().curve();
		int max = maxLevel(stack);
		int level = data.level();
		long xp = (long) data.xp() + amount;
		while (level < max && xp >= curve.xpToNext(level, max)) {
			if (isFormGate(level, max)) {
				xp = curve.xpToNext(level, max);   // prêt à évoluer : on attend qu'on le fasse évoluer
				break;
			}
			xp -= curve.xpToNext(level, max);
			level++;
		}
		if (level >= max) {
			xp = 0;
		}
		boolean leveledUp = level != data.level();
		data.withProgress(level, (int) Math.min(Integer.MAX_VALUE, xp), data.count() + countIncrement).write(stack);
		if (leveledUp) {
			refresh(stack, false);
		} else {
			Lore.update(stack);
		}
		return level;
	}

	public static void announceUnlocks(EvoProfile profile, Player player, int level) {
		if (level % LEVELS_PER_STAGE == 0 && level <= MAX_LEVEL) {
			player.sendMessage(Lang.tr("message.evotool.new_form", stageName(level), stage(level), MAX_STAGE)
					.color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
		}
		for (Map.Entry<Power, Integer> entry : profile.powers().entrySet()) {
			if (entry.getValue() == level) {
				unlock(player, Lang.tr(entry.getKey().translationKey()));
			}
		}
		for (EvoProfile.ModeUnlock unlock : profile.modes()) {
			if (unlock.level() > 0 && unlock.level() == level) {
				unlock(player, Lang.tr("message.evotool.unlock.mode", Lang.tr(unlock.mode().translationKey())));
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

	public static Component enchantText(EvoProfile.EnchantStep step, int enchantLevel) {
		return Component.translatable(step.translationKey()).append(Component.text(" "))
				.append(Component.translatable("enchantment.level." + enchantLevel));
	}

	private static void unlock(Player player, Component what) {
		player.sendMessage(Lang.tr("message.evotool.unlocked", what).color(NamedTextColor.GOLD));
	}

	/**
	 * Recalcule ce qui dépend du niveau : enchantements, bonus de vitesse / dégâts, forme du modèle et infobulle.
	 *
	 * @param exact si vrai, les enchantements d'évolution sont mis exactement au niveau prévu (/upgrade).
	 *              Sinon ils ne font qu'augmenter, pour ne pas écraser un enchantement plus fort mis à l'enclume.
	 */
	public static void refresh(ItemStack stack, boolean exact) {
		EvoKind kind = kindOf(stack);
		EvoItem item = Items.of(stack);
		if (kind == null || item == null) {
			return;
		}
		EvoProfile profile = kind.profile();
		int level = get(stack).level();
		int stage = stage(stack);
		boolean upgraded = EvoliteUpgrade.isUpgraded(stack);
		stack.editMeta(meta -> {
			for (EvoProfile.EnchantStep step : profile.enchants()) {
				Enchantment enchantment = step.enchantment();
				int wanted = step.levelAt(level);
				int current = meta.getEnchantLevel(enchantment);
				int cap = Math.max(step.levels().length, enchantment.getMaxLevel());
				int target = exact ? wanted : Math.min(Math.max(current, wanted), cap);
				if (target != current) {
					meta.removeEnchant(enchantment);
					if (target > 0) {
						meta.addEnchant(enchantment, target, true);
					}
				}
			}
			Multimap<Attribute, AttributeModifier> modifiers = Items.baseAttributes(item);
			if (profile.speedBonus()) {
				modifiers.put(Attribute.MINING_EFFICIENCY,
						Items.modifier("evolution_speed", miningBonus(profile, level), EquipmentSlotGroup.MAINHAND));
			}
			if (profile.damageBonus()) {
				modifiers.put(Attribute.ATTACK_DAMAGE,
						Items.modifier("evolution_damage", damageBonus(level), EquipmentSlotGroup.MAINHAND));
			}
			if (upgraded) {
				EvoliteUpgrade.addModifiers(kind, modifiers);
			}
			meta.setAttributeModifiers(modifiers);
			Items.setFloats(meta, stage);
			if (profile.isArmor()) {
				EquippableComponent equippable = meta.getEquippable();
				equippable.setSlot(profile.armorSlot());
				equippable.setEquipSound(Sound.ITEM_ARMOR_EQUIP_NETHERITE);
				// Plastron, jambières et bottes : la texture portée change avec la forme. Le casque, lui, est un
				// modèle 3D porté sur la tête (comme une citrouille) : pas de texture d'armure.
				equippable.setModel(profile.armorSlot() == EquipmentSlot.HEAD ? null : Keys.key("evolium_stage" + stage));
				meta.setEquippable(equippable);
			}
			if (upgraded) {
				meta.setEnchantmentGlintOverride(true);
			}
			meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, item.id());
		});
		Lore.update(stack);
	}
}
