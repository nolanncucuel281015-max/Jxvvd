package fr.evotool.plugin.input;

import fr.evotool.plugin.armor.ArmorSet;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Evolution;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.menu.EvoMenu;
import fr.evotool.plugin.mining.AxeLaser;
import fr.evotool.plugin.upgrade.CelestialBow;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.upgrade.MagicPowers;
import fr.evotool.plugin.upgrade.PickaxeSlam;
import fr.evotool.plugin.util.Lang;
import fr.evotool.plugin.weapon.ArcaneWeapons;
import fr.evotool.plugin.weapon.MagicKind;
import fr.evotool.plugin.weapon.WeaponKind;
import fr.evotool.plugin.weapon.WeaponPowers;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Les touches du mod, version serveur (le joueur n'a rien à installer) :
 * <ul>
 * <li>clic droit : attaque spéciale de l'arme, sort, laser de la hache, tranchée de la pioche, arc céleste… ;</li>
 * <li>F (échanger de main) : menu de l'objet évolutif en main (ou de l'armure, main vide) ;</li>
 * <li>accroupi + F : faire évoluer l'objet prêt ;</li>
 * <li>coup dans le vide : slash de la Lame Détonante.</li>
 * </ul>
 */
public final class InputListener implements Listener {
	/** Une action de plus (gant, grimoire, tentacules…) branchée par un autre module. */
	public interface Action2 {
		/** Vrai si l'action a eu lieu (l'action normale de l'objet est alors annulée). */
		boolean run(Player player, ItemStack stack, Block clicked, EquipmentSlot hand);
	}

	/** Une action de la touche F ; vrai si elle a eu lieu. */
	public interface SwapAction {
		boolean run(Player player, ItemStack main);
	}

	private static final List<Action2> USE = new ArrayList<>();
	private static final List<SwapAction> SWAP = new ArrayList<>();
	private static final List<java.util.function.Consumer<Player>> SWING = new ArrayList<>();

	/** Branche une action du clic droit (essayée avant celles des outils et armes). */
	public static void onUse(Action2 action) {
		USE.add(action);
	}

	/** Branche une action de la touche F (essayée avant le menu). */
	public static void onSwap(SwapAction action) {
		SWAP.add(action);
	}

	/** Branche une action du coup (bras qui frappe, même dans le vide). */
	public static void onSwing(java.util.function.Consumer<Player> action) {
		SWING.add(action);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent event) {
		Action action = event.getAction();
		if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK || event.getHand() == null
				|| event.useItemInHand() == Event.Result.DENY) {
			return;
		}
		Player player = event.getPlayer();
		if (player.getGameMode() == GameMode.SPECTATOR) {
			return;
		}
		ItemStack stack = event.getItem();
		Block clicked = event.getClickedBlock();
		// un coffre, une porte, un levier… : on les utilise d'abord (sauf accroupi)
		if (clicked != null && !player.isSneaking() && clicked.getType().isInteractable()
				&& event.useInteractedBlock() != Event.Result.DENY) {
			return;
		}
		for (Action2 extra : USE) {
			if (extra.run(player, stack, clicked, event.getHand())) {
				deny(event);
				return;
			}
		}
		if (stack == null || Items.of(stack) == null) {
			return;
		}
		if (use(player, stack, clicked)) {
			deny(event);
			player.swingHand(event.getHand());
		}
	}

	private static void deny(PlayerInteractEvent event) {
		event.setUseItemInHand(Event.Result.DENY);
		event.setUseInteractedBlock(Event.Result.DENY);
	}

	/** Clic droit avec un objet du mod ; vrai si un pouvoir a été utilisé (ou est en recharge). */
	static boolean use(Player player, ItemStack stack, Block clicked) {
		EvoKind kind = Items.kindOf(stack);
		if (kind == null) {
			return false;
		}
		// Accroupi + clic droit avec l'épée évolutive : explosion céleste de 15 blocs.
		if (player.isSneaking() && kind == EvoKind.SWORD) {
			if (!Cooldowns.active(player, stack)) {
				Cooldowns.set(player, stack, MagicPowers.EXPLOSION_COOLDOWN);
				MagicPowers.celestialExplosion(player, EvoData.get(stack).level());
			}
			return true;
		}
		if (EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.MAGIC_SLASH) || EvoliteUpgrade.has(stack, EvoliteUpgrade.Upgrade.SOAR)) {
			if (!Cooldowns.active(player, stack)) {
				Cooldowns.set(player, stack, MagicPowers.use(player, stack));
			}
			return true;
		}
		WeaponKind weapon = Items.weaponKind(kind);
		if (weapon != null) {
			if (!Cooldowns.active(player, stack)) {
				WeaponPowers.activate(weapon, player, stack);
				Cooldowns.set(player, stack, WeaponPowers.cooldown(weapon, stack));
			}
			return true;
		}
		MagicKind magic = Items.magicKind(kind);
		if (magic != null) {
			if (!Cooldowns.active(player, stack) && ArcaneWeapons.cast(magic, stack, player)) {
				Cooldowns.set(player, stack, 8);
			}
			return true;
		}
		switch (kind) {
			case AXE -> {
				if (clicked != null && AxeLaser.wouldStrip(clicked)) {
					return false;   // on écorce la bûche, comme une hache normale
				}
				if (!Cooldowns.active(player, stack)) {
					Cooldowns.set(player, stack, AxeLaser.COOLDOWN);
					AxeLaser.start(player, stack);
				}
				return true;
			}
			case PICKAXE -> {
				if (!PickaxeSlam.isReady(stack)) {
					if (clicked == null) {
						player.sendActionBar(Lang.tr("message.evotool.slash_locked").color(NamedTextColor.GRAY));
					}
					return false;
				}
				if (!Cooldowns.active(player, stack)) {
					Cooldowns.set(player, stack, PickaxeSlam.COOLDOWN);
					PickaxeSlam.start(player, stack);
				}
				return true;
			}
			case BOW -> {
				if (!CelestialBow.isReady(stack)) {
					return false;
				}
				if (!Cooldowns.active(player, stack)) {
					Cooldowns.set(player, stack, CelestialBow.COOLDOWN);
					CelestialBow.start(player, stack);
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	// ------------------------------------------------------------------ touche F

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onSwap(PlayerSwapHandItemsEvent event) {
		Player player = event.getPlayer();
		ItemStack main = player.getInventory().getItemInMainHand();
		for (SwapAction extra : SWAP) {
			if (extra.run(player, main)) {
				event.setCancelled(true);
				return;
			}
		}
		boolean evolving = Items.kindOf(main) != null;
		boolean armor = false;
		if (!evolving && main.getType().isAir()) {
			for (EquipmentSlot slot : ArmorSet.SLOTS) {
				armor |= ArmorSet.worn(player, slot) != null;
			}
		}
		if (!evolving && !armor) {
			return;
		}
		event.setCancelled(true);
		if (player.isSneaking()) {
			Evolution.start(player);
		} else {
			EvoMenu.show(player);
		}
	}

	// ------------------------------------------------------------------ coup (bras qui frappe)

	@EventHandler(ignoreCancelled = true)
	public void onSwing(PlayerAnimationEvent event) {
		if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) {
			return;
		}
		Player player = event.getPlayer();
		WeaponPowers.onSwing(player);
		for (java.util.function.Consumer<Player> extra : SWING) {
			extra.accept(player);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		Evolution.cancel(player.getUniqueId());
		WeaponPowers.forget(player);
		MagicPowers.forget(player);
	}

	/** Pour l'auto-test : l'objet est-il un objet du mod ? */
	static boolean isModItem(ItemStack stack) {
		EvoItem item = Items.of(stack);
		return item != null;
	}
}
