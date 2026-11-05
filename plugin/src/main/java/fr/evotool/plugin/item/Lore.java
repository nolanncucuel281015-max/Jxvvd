package fr.evotool.plugin.item;

import fr.evotool.plugin.element.Element;
import fr.evotool.plugin.element.Elements;
import fr.evotool.plugin.element.Grimoire;
import fr.evotool.plugin.element.Spell;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.EvoProfile;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.spider.WebPower;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import fr.evotool.plugin.weapon.ArcaneWeapons;
import fr.evotool.plugin.weapon.MagicKind;
import fr.evotool.plugin.weapon.WeaponKind;
import fr.evotool.plugin.weapon.WeaponPowers;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * L'infobulle des objets du mod (dans le mod, le client l'écrivait lui-même ; ici, elle est rangée dans l'objet et
 * remise à jour quand il change : niveau, XP, charges, élément…).
 */
public final class Lore {
	private Lore() {
	}

	public static void update(ItemStack stack) {
		if (stack == null || stack.getType().isAir()) {
			return;
		}
		List<Component> lines = build(stack);
		stack.editMeta(meta -> meta.lore(lines.isEmpty() ? null : lines));
	}

	private static Component line(Component text, TextColor color) {
		return text.color(color).decoration(TextDecoration.ITALIC, false);
	}

	private static Component line(String key, TextColor color, Object... args) {
		return line(Lang.tr(key, args), color);
	}

	private static Component bold(Component c) {
		return c.decorate(TextDecoration.BOLD);
	}

	public static List<Component> build(ItemStack stack) {
		List<Component> lines = new ArrayList<>();
		EvoItem item = Items.of(stack);
		// l'élément donné par le combineur, et ses pouvoirs sur cet objet
		Element element = Elements.get(stack);
		if (element != null) {
			lines.add(bold(line(Lang.tr("tooltip.evotool.element", element.symbol(), Lang.tr(element.translationKey())), element.color())));
			EquipmentSlot slot = Elements.armorSlot(stack);
			if (slot == null) {
				lines.add(line("tooltip.evotool.element.weapon." + element.id(), element.color()));
			} else {
				lines.add(line("tooltip.evotool.element.armor." + element.id(), element.color()));
				String special = Elements.specialKey(element, slot);
				if (special != null) {
					lines.add(line(special, element.color()).decorate(TextDecoration.ITALIC));
				}
			}
		}
		// grimoire élémentaire : ses sorts, le choisi en tête de flèche
		Grimoire grimoire = Grimoire.get(stack);
		if (grimoire != null) {
			Spell selected = grimoire.selectedSpell();
			for (Spell spell : grimoire.spells()) {
				boolean chosen = spell == selected;
				Component text = Component.text((chosen ? "▶ " : "   ") + spell.element().symbol() + " ")
						.append(Lang.tr(spell.translationKey())).append(Lang.tr(spell.kind().translationKey()));
				Component l = line(text, spell.element().color());
				lines.add(chosen ? bold(l) : l);
			}
			lines.add(line("tooltip.evotool.grimoire.cast", NamedTextColor.GRAY));
			lines.add(line("tooltip.evotool.grimoire.switch", NamedTextColor.GRAY));
			lines.add(line("tooltip.evotool.grimoire.more", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
			return lines;
		}
		if (item == null) {
			return lines;
		}
		switch (item) {
			case SPIDER_GLOVE -> {
				lines.add(line("tooltip.evotool.spider_glove.web", NamedTextColor.RED));
				WebPower selected = WebPower.of(stack);
				for (WebPower power : WebPower.values()) {
					boolean chosen = power == selected;
					Component text = Component.text(chosen ? "▶ " : "   ").append(Lang.tr(power.translationKey()))
							.append(Component.text(" : ")).append(Lang.tr(power.translationKey() + ".desc"));
					Component l = line(text, NamedTextColor.RED);
					lines.add(chosen ? bold(l) : l);
				}
				lines.add(line("tooltip.evotool.spider_glove.power", NamedTextColor.GRAY));
				lines.add(line("tooltip.evotool.spider_glove.climb", NamedTextColor.BLUE));
				lines.add(line("tooltip.evotool.spider_glove.sense", NamedTextColor.BLUE));
				lines.add(line("tooltip.evotool.spider_glove.agility", NamedTextColor.BLUE));
				return lines;
			}
			case BOSS_HEART -> {
				lines.add(line("tooltip.evotool.boss_heart", NamedTextColor.LIGHT_PURPLE));
				return lines;
			}
			case EVOTOOL_MANUAL -> {
				lines.add(line("tooltip.evotool.manual", NamedTextColor.GOLD));
				return lines;
			}
			case ELEMENTAL_COMBINER -> {
				lines.add(line("tooltip.evotool.combiner.hint", NamedTextColor.LIGHT_PURPLE));
				return lines;
			}
			case SHIELD_GENERATOR -> {
				long fuel = stack.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.SHIELD_FUEL, PersistentDataType.LONG, 0L);
				int radius = stack.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.SHIELD_RADIUS, PersistentDataType.INTEGER, 8);
				lines.add(line("tooltip.evotool.shield.fuel", NamedTextColor.AQUA, NumberFormat.getIntegerInstance(Locale.FRANCE).format(fuel)));
				lines.add(line("tooltip.evotool.shield.radius", NamedTextColor.AQUA, radius));
				lines.add(line("tooltip.evotool.shield.hint", NamedTextColor.DARK_GRAY).decorate(TextDecoration.ITALIC));
				return lines;
			}
			default -> {
			}
		}
		EvoKind kind = item.kind();
		if (kind == null) {
			return lines;
		}
		// armes moddées et magiques : pouvoirs propres, puis les lignes d'évolution communes
		WeaponKind weapon = Items.weaponKind(kind);
		if (weapon != null) {
			lines.add(line("tooltip.evotool.weapon.passive", NamedTextColor.GOLD, Lang.tr(weapon.passiveKey())));
			lines.add(line("tooltip.evotool.weapon.ability", NamedTextColor.LIGHT_PURPLE, Lang.tr(weapon.abilityKey()),
					String.format(Locale.ROOT, "%.1f", WeaponPowers.cooldown(weapon, stack) / 20.0)));
		}
		MagicKind magic = Items.magicKind(kind);
		if (magic != null) {
			lines.add(line("tooltip.evotool.magic.charges", NamedTextColor.AQUA, ArcaneWeapons.charges(stack, magic),
					ArcaneWeapons.maxCharges(stack, magic),
					String.format(Locale.ROOT, "%.1f", ArcaneWeapons.rechargeTicks(stack, magic) / 20.0)));
			lines.add(line("tooltip.evotool.magic.ability", NamedTextColor.LIGHT_PURPLE, Lang.tr(magic.abilityKey())));
			lines.add(line("tooltip.evotool.magic.sneak", NamedTextColor.DARK_PURPLE, Lang.tr(magic.sneakKey())));
		}
		EvoProfile profile = kind.profile();
		EvoData data = EvoData.get(stack);
		int level = data.level();
		lines.add(line("tooltip.evotool.level", NamedTextColor.LIGHT_PURPLE, level, Progression.maxLevel(stack)));
		lines.add(line("tooltip.evotool.form", NamedTextColor.YELLOW, Progression.stageName(stack), Progression.stage(stack),
				Progression.maxStage(stack)));
		if (Progression.isAbyssal(stack)) {
			lines.add(bold(line("tooltip.evotool.abyssal", NamedTextColor.DARK_PURPLE)));
		}
		if (Progression.isReadyToEvolve(stack)) {
			lines.add(bold(line("tooltip.evotool.ready", NamedTextColor.LIGHT_PURPLE, Progression.evolveKey())));
		} else if (level < Progression.maxLevel(stack)) {
			lines.add(line("tooltip.evotool.xp", NamedTextColor.GRAY, data.xp(), Progression.xpToNext(stack)));
		}
		if (profile.modes().size() > 1) {
			lines.add(line("tooltip.evotool.mode", NamedTextColor.AQUA,
					Lang.tr(Progression.selectedMode(profile, data).translationKey())));
		}
		for (Power power : profile.powers().keySet()) {
			if (power.toggleable() && profile.hasPower(power, level)) {
				boolean on = Progression.isActive(profile, data, power);
				lines.add(line(on ? "tooltip.evotool.power_on" : "tooltip.evotool.power_off",
						on ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY, Lang.tr(power.translationKey())));
			}
		}
		if (EvoliteUpgrade.isUpgraded(stack)) {
			EvoliteUpgrade.Upgrade upgrade = EvoliteUpgrade.upgradeFor(kind);
			lines.add(bold(line("tooltip.evotool.upgraded", NamedTextColor.AQUA, Lang.tr(upgrade.translationKey()))));
			lines.add(line(upgrade.descriptionKey(), NamedTextColor.DARK_AQUA));
		}
		lines.add(line(profile.counter().translationKey(), NamedTextColor.DARK_GRAY, data.count()));
		switch (kind) {
			case SWORD -> lines.add(line("tooltip.evotool.sword_hint", NamedTextColor.RED));
			case PICKAXE -> lines.add(line("tooltip.evotool.pickaxe_hint", NamedTextColor.GOLD));
			case BOW -> lines.add(line("tooltip.evotool.bow_hint", NamedTextColor.LIGHT_PURPLE));
			case AXE -> lines.add(line("tooltip.evotool.axe_hint", NamedTextColor.GREEN));
			default -> {
			}
		}
		lines.add(line(profile.isArmor() ? "tooltip.evotool.armor_hint" : "tooltip.evotool.hint", NamedTextColor.DARK_GRAY)
				.decorate(TextDecoration.ITALIC));
		return lines;
	}
}
