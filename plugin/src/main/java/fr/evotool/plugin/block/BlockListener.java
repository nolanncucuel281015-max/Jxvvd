package fr.evotool.plugin.block;

import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Fait vivre les blocs du mod : pose avec leur objet, casse avec leur butin, et protège leur état (les blocs
 * musicaux qui les portent ne doivent ni jouer de note, ni changer d'instrument, ni être poussés).
 */
public final class BlockListener implements Listener {
	/** Ce qu'une machine (générateur, combineur, autel) fait de plus quand on la pose, la casse ou l'utilise. */
	public interface Handler {
		default void placed(Block block, Player player, ItemStack item) {
		}

		/** Le bloc va disparaître : ajoute ce qu'il contient au butin. */
		default void broken(Block block, List<ItemStack> drops) {
		}

		/** Clic droit sur le bloc ; vrai si c'est géré. */
		default boolean used(Block block, Player player) {
			return false;
		}
	}

	private static final Map<CustomBlock, Handler> HANDLERS = new EnumMap<>(CustomBlock.class);

	public static void register(CustomBlock block, Handler handler) {
		HANDLERS.put(block, handler);
	}

	// ------------------------------------------------------------------ pose

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
			return;
		}
		Block clicked = event.getClickedBlock();
		Player player = event.getPlayer();
		CustomBlock custom = Blocks.at(clicked);
		if (custom != null && event.getHand() == EquipmentSlot.HAND && !player.isSneaking()) {
			Handler handler = HANDLERS.get(custom);
			if (handler != null && handler.used(clicked, player)) {
				event.setUseInteractedBlock(Event.Result.DENY);
				event.setUseItemInHand(Event.Result.DENY);
				return;
			}
		}
		if (custom != null) {
			// pas de note jouée, pas de changement de note : le bloc garde son apparence
			event.setUseInteractedBlock(Event.Result.DENY);
		}
		ItemStack item = event.getItem();
		EvoItem evo = Items.of(item);
		if (evo == null || evo.block() == null || event.useItemInHand() == Event.Result.DENY) {
			return;
		}
		if (custom == null && !player.isSneaking() && clicked.getType().isInteractable()) {
			return;   // clic sur un coffre, une porte… : on les utilise d'abord
		}
		event.setCancelled(true);
		Block target = clicked.getType().isSolid() && !clicked.isReplaceable() ? clicked.getRelative(event.getBlockFace()) : clicked;
		if (!target.isReplaceable() || !target.getWorld().getNearbyEntities(target.getBoundingBox(),
				e -> e instanceof org.bukkit.entity.LivingEntity).isEmpty()) {
			return;
		}
		BlockState before = target.getState();
		CustomBlock placing = evo.block();
		Blocks.place(target, placing, placing.variant(0, Blocks.axis(event.getBlockFace())));
		BlockPlaceEvent place = new BlockPlaceEvent(target, before, clicked, item, player, true, event.getHand());
		org.bukkit.Bukkit.getPluginManager().callEvent(place);
		if (place.isCancelled() || !place.canBuild()) {
			before.update(true, false);
			return;
		}
		Handler handler = HANDLERS.get(placing);
		if (handler != null) {
			handler.placed(target, player, item);
		}
		target.getWorld().playSound(target.getLocation().add(0.5, 0.5, 0.5),
				placing == CustomBlock.EVOLVING_LEAVES ? Sound.BLOCK_CHERRY_LEAVES_PLACE : Sound.BLOCK_STONE_PLACE,
				SoundCategory.BLOCKS, 1.0F, 0.9F);
		player.swingHand(event.getHand());
		if (player.getGameMode() != GameMode.CREATIVE) {
			item.subtract();
		}
	}

	// ------------------------------------------------------------------ casse

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		CustomBlock custom = Blocks.at(block);
		if (custom == null) {
			return;
		}
		Player player = event.getPlayer();
		ItemStack tool = player.getInventory().getItemInMainHand();
		Handler handler = HANDLERS.get(custom);
		if (!event.isDropItems()) {
			// déjà ramassé par un outil évolutif : seulement le contenu des machines
			if (handler != null) {
				List<ItemStack> extra = new java.util.ArrayList<>();
				handler.broken(block, extra);
				drop(block.getLocation(), extra);
			}
			return;
		}
		event.setDropItems(false);
		List<ItemStack> drops = player.getGameMode() == GameMode.CREATIVE ? new java.util.ArrayList<>() : Blocks.drops(block, tool);
		if (handler != null) {
			handler.broken(block, drops);
		}
		drop(block.getLocation(), drops);
		event.setExpToDrop(player.getGameMode() == GameMode.CREATIVE ? 0 : Blocks.experience(block, tool));
	}

	/** Le contenu d'une machine cassée autrement (outil évolutif, explosion…). */
	public static void machineBroken(Block block, List<ItemStack> drops) {
		CustomBlock custom = Blocks.at(block);
		Handler handler = custom == null ? null : HANDLERS.get(custom);
		if (handler != null) {
			handler.broken(block, drops);
		}
	}

	public static void drop(Location at, List<ItemStack> drops) {
		Location center = at.clone().add(0.5, 0.5, 0.5);
		for (ItemStack stack : drops) {
			if (stack != null && !stack.getType().isAir()) {
				at.getWorld().dropItemNaturally(center, stack);
			}
		}
	}

	// ------------------------------------------------------------------ protection de l'état

	@EventHandler(ignoreCancelled = true)
	public void onPhysics(BlockPhysicsEvent event) {
		if (event.getBlock().getType() == Material.NOTE_BLOCK && Blocks.at(event.getBlock()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onNote(NotePlayEvent event) {
		if (Blocks.at(event.getBlock()) != null) {
			event.setCancelled(true);
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent event) {
		for (Block block : event.getBlocks()) {
			if (Blocks.at(block) != null) {
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent event) {
		for (Block block : event.getBlocks()) {
			if (Blocks.at(block) != null) {
				event.setCancelled(true);
				return;
			}
		}
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		explode(event.blockList());
	}

	@EventHandler(ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent event) {
		explode(event.blockList());
	}

	/** Explosion : les machines résistent, les autres blocs du mod lâchent leur objet (pas un bloc musical). */
	private static void explode(List<Block> blocks) {
		for (Iterator<Block> it = blocks.iterator(); it.hasNext(); ) {
			Block block = it.next();
			CustomBlock custom = Blocks.at(block);
			if (custom == null) {
				continue;
			}
			it.remove();
			if (custom.drop() == CustomBlock.Drop.MACHINE || custom == CustomBlock.SANCTUARY_ALTAR) {
				continue;
			}
			List<ItemStack> drops = Blocks.drops(block, new ItemStack(Material.NETHERITE_PICKAXE));
			block.setType(Material.AIR, false);
			drop(block.getLocation(), drops);
		}
	}
}
