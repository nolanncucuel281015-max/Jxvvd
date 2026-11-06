package fr.evotool.plugin.menu;

import fr.evotool.plugin.armor.ArmorSet;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.EvoMode;
import fr.evotool.plugin.evo.EvoProfile;
import fr.evotool.plugin.evo.Evolution;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Le menu de l'objet évolutif (la touche Y du mod ; ici, F avec l'objet en main) : progression, mode de zone,
 * pouvoirs à activer ou couper, bouton « évoluer » et, pour l'armure, les bonus de l'ensemble.
 */
public final class EvoMenu extends Menu {
	private final Player player;
	private final ItemStack stack;

	private EvoMenu(Player player, ItemStack stack) {
		super(5, Lang.tr("screen.evotool.title"));
		this.player = player;
		this.stack = stack;
		draw();
	}

	/** Ouvre le menu de l'objet en main (sinon de l'armure portée) ; faux s'il n'y a rien à montrer. */
	public static boolean show(Player player) {
		ItemStack main = player.getInventory().getItemInMainHand();
		if (Items.kindOf(main) != null) {
			new EvoMenu(player, main).open(player);
			return true;
		}
		for (org.bukkit.inventory.EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack worn = ArmorSet.worn(player, slot);
			if (worn != null) {
				new EvoMenu(player, worn).open(player);
				return true;
			}
		}
		return false;
	}

	/** Le menu de l'armure portée (commande /evotool armure) ; faux sans armure évolutive. */
	public static boolean showArmor(Player player) {
		for (org.bukkit.inventory.EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack worn = ArmorSet.worn(player, slot);
			if (worn != null) {
				new EvoMenu(player, worn).open(player);
				return true;
			}
		}
		return false;
	}

	private void draw() {
		inventory.clear();
		EvoKind kind = Items.kindOf(stack);
		if (kind == null) {
			return;
		}
		EvoProfile profile = kind.profile();
		EvoData data = EvoData.get(stack);
		ItemStack shown = stack.clone();
		Lore.update(shown);
		inventory.setItem(4, shown);

		// modes de zone
		int slot = 10;
		for (EvoProfile.ModeUnlock unlock : profile.modes()) {
			if (profile.modes().size() <= 1) {
				break;
			}
			EvoMode mode = unlock.mode();
			boolean unlocked = data.level() >= unlock.level();
			boolean selected = Progression.selectedMode(profile, data) == mode;
			Material material = !unlocked ? Material.GRAY_DYE : selected ? Material.LIME_DYE : Material.LIGHT_BLUE_DYE;
			List<Component> lore = new ArrayList<>();
			lore.add(unlocked ? Lang.tr(selected ? "plugin.evotool.menu.selected" : "plugin.evotool.menu.choose").color(NamedTextColor.GRAY)
					: Lang.tr("plugin.evotool.menu.locked", unlock.level()).color(NamedTextColor.RED));
			button(slot++, icon(material, Lang.tr(mode.translationKey()).color(NamedTextColor.AQUA), lore), e -> {
				if (unlocked) {
					EvoData now = EvoData.get(stack);
					now.withSettings(mode.ordinal(), now.flags()).write(stack);
					Lore.update(stack);
					click();
					draw();
				}
			});
		}
		// pouvoirs à couper
		slot = 19;
		for (Power power : profile.powers().keySet()) {
			if (!power.toggleable() || !profile.hasPower(power, data.level())) {
				continue;
			}
			boolean on = Progression.isActive(profile, data, power);
			button(slot++, icon(on ? Material.LIME_CANDLE : Material.GRAY_CANDLE, Lang.tr(power.translationKey())
					.color(on ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY), List.of(Lang.tr(on ? "plugin.evotool.menu.on"
					: "plugin.evotool.menu.off").color(NamedTextColor.GRAY))), e -> {
				EvoData now = EvoData.get(stack);
				now.withSettings(now.mode(), now.flags() ^ power.bit()).write(stack);
				Lore.update(stack);
				click();
				draw();
			});
		}
		// évoluer
		if (Progression.isReadyToEvolve(stack)) {
			button(31, icon(Material.NETHER_STAR, Lang.tr("plugin.evotool.menu.evolve").color(NamedTextColor.LIGHT_PURPLE),
					List.of(Lang.tr("tooltip.evotool.ready", Progression.evolveKey()).color(NamedTextColor.GRAY))), e -> {
				player.closeInventory();
				Evolution.start(player);
			});
		}
		// armure : bonus de l'ensemble
		if (profile.isArmor()) {
			int level = ArmorSet.setLevel(player);
			List<Component> lines = new ArrayList<>(ArmorSet.describe(level));
			inventory.setItem(40, icon(Material.NETHERITE_CHESTPLATE, Lang.tr("armor.evotool.set_level", level).color(NamedTextColor.BLUE),
					lines.stream().map(c -> c.color(NamedTextColor.GRAY)).toList()));
		}
		fill();
	}

	private void click() {
		player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.PLAYERS, 0.6F, 1.2F);
	}
}
