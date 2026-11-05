package fr.evotool.plugin.mining;

import fr.evotool.plugin.block.BlockListener;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.EvoMode;
import fr.evotool.plugin.evo.EvoProfile;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.util.RayTraceResult;

/**
 * Le cassage des blocs avec les outils évolutifs : minage de zone, abattage d'arbres, récolte en zone,
 * replantation, auto-smelt, gain d'XP, labour en zone, et le minage instantané au niveau 100.
 */
public final class MiningListener implements Listener {
	private static final int MAX_BREAK_EFFECTS = 26;
	private static final int MAX_TREE_BLOCKS = 320;
	private static final int CROP_XP = 3;
	/** Vrai pendant qu'on vérifie (avec un faux évènement de casse) qu'un bloc de la zone peut être cassé. */
	private static boolean checkingExtra;
	private static Map<String, ItemStack> smelting;

	// ------------------------------------------------------------------ minage instantané (niveau 100)

	/**
	 * Règle du joueur : au niveau 100, la pioche, la pelle et la hache évolutives cassent tout en un coup (pierre,
	 * ardoise, minerais, obsidienne…). Ça ne doit JAMAIS changer, même lors d'un rééquilibrage.
	 */
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(BlockDamageEvent event) {
		ItemStack tool = event.getItemInHand();
		if (isInstamine(tool) && event.getBlock().getType().getHardness() >= 0) {
			event.setInstaBreak(true);
		}
	}

	/** L'outil a le minage instantané (pioche, pelle ou hache évolutive niveau 100). */
	public static boolean isInstamine(ItemStack tool) {
		EvoKind kind = Items.kindOf(tool);
		return kind != null && Progression.isActive(tool, Power.INSTAMINE);
	}

	// ------------------------------------------------------------------ casse

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		if (checkingExtra) {
			return;
		}
		Player player = event.getPlayer();
		ItemStack tool = player.getInventory().getItemInMainHand();
		EvoKind kind = Items.kindOf(tool);
		if (kind == null || !kind.profile().gainsBlockXp()) {
			return;
		}
		mine(event, player, tool, kind);
	}

	private static void mine(BlockBreakEvent event, Player player, ItemStack tool, EvoKind kind) {
		Block origin = event.getBlock();
		EvoProfile profile = kind.profile();
		EvoData data = EvoData.get(tool);
		boolean precise = player.isSneaking();
		boolean smelt = Progression.isActive(profile, data, Power.AUTOSMELT);
		boolean replant = Progression.isActive(profile, data, Power.AUTO_REPLANT);
		boolean creative = player.getGameMode() == GameMode.CREATIVE;

		List<Block> positions;
		boolean felling = false;
		if (precise) {
			positions = List.of(origin);
		} else if (profile.areaMining()) {
			positions = areaPositions(player, origin, Progression.selectedMode(profile, data));
		} else if (kind == EvoKind.AXE && isLog(origin) && Progression.isActive(profile, data, Power.TREE_FELLING)) {
			positions = treePositions(origin, Progression.isActive(profile, data, Power.FELL_LEAVES));
			felling = true;
		} else if (kind == EvoKind.HOE && isMatureCrop(origin)) {
			positions = cropPositions(origin, Progression.selectedMode(profile, data));
		} else {
			positions = List.of(origin);
		}

		List<ItemStack> drops = new ArrayList<>();
		List<Block> harvested = new ArrayList<>();
		List<BlockData> harvestedData = new ArrayList<>();
		int xp = 0;
		int blocks = 0;
		int effects = 0;
		for (Block block : positions) {
			boolean isOrigin = block.equals(origin);
			if (!isOrigin && !canBreakExtra(player, block, tool, kind)) {
				continue;
			}
			BlockData state = block.getBlockData();
			boolean mature = isMatureCrop(block);
			int blockXp = suitable(block, tool) && block.getType().getHardness() > 0 ? Progression.xpForBlock(block) : 0;
			if (!creative) {
				drops.addAll(dropsOf(block, tool, player));
			}
			if (isOrigin) {
				event.setDropItems(false);
				if (Blocks.at(block) != null && !creative) {
					event.setExpToDrop(Blocks.experience(block, tool));
				}
			} else {
				if (Blocks.at(block) != null) {
					BlockListener.machineBroken(block, drops);
				}
				block.setType(Material.AIR);
				if (effects++ < MAX_BREAK_EFFECTS) {
					block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, state);
				}
			}
			blocks++;
			if (mature) {
				xp += CROP_XP;
				harvested.add(block);
				harvestedData.add(state);
			} else {
				xp += blockXp;
			}
		}
		if (replant && kind == EvoKind.HOE) {
			boolean grown = Progression.isActive(profile, data, Power.INSTANT_GROWTH);
			Bukkit.getScheduler().runTask(fr.evotool.plugin.EvoToolPlugin.instance(), () -> {
				for (int i = 0; i < harvested.size(); i++) {
					replantCrop(harvested.get(i), harvestedData.get(i), grown);
				}
			});
		}
		if (replant && felling) {
			Bukkit.getScheduler().runTask(fr.evotool.plugin.EvoToolPlugin.instance(), () -> replantSapling(origin, drops));
		}
		boolean telekinesis = EvoliteUpgrade.has(tool, EvoliteUpgrade.Upgrade.TELEKINESIS);
		deliver(player, origin.getLocation(), drops, smelt, telekinesis);
		if (xp > 0 && !creative) {
			Progression.addXp(tool, xp, blocks, player);
		}
	}

	/** Le butin d'un bloc (bloc du mod ou de Minecraft) avec cet outil. */
	public static List<ItemStack> dropsOf(Block block, ItemStack tool, Player player) {
		if (Blocks.at(block) != null) {
			return Blocks.drops(block, tool);
		}
		return new ArrayList<>(block.getDrops(tool, player));
	}

	/** Donne le butin : fondu si l'auto-smelt est actif, dans l'inventaire avec la télékinésie, sinon par terre. */
	public static void deliver(Player player, Location at, List<ItemStack> drops, boolean smelt, boolean telekinesis) {
		for (ItemStack drop : drops) {
			ItemStack result = smelt ? smelt(drop) : drop;
			if (result == null || result.getType().isAir()) {
				continue;
			}
			List<ItemStack> leftover = new ArrayList<>();
			if (telekinesis) {
				leftover.addAll(player.getInventory().addItem(result).values());
			} else {
				leftover.add(result);
			}
			BlockListener.drop(at, leftover);
		}
	}

	private static boolean canBreakExtra(Player player, Block block, ItemStack tool, EvoKind kind) {
		if (block.getType().isAir() || block.getType().getHardness() < 0 || block.getState() instanceof TileState) {
			return false;
		}
		CustomBlock custom = Blocks.at(block);
		if (custom != null && (custom.drop() == CustomBlock.Drop.MACHINE || custom == CustomBlock.SANCTUARY_ALTAR)) {
			return false;
		}
		boolean ok = switch (kind) {
			case AXE -> isLog(block) || isNaturalLeaves(block);
			case HOE -> isMatureCrop(block);
			default -> suitable(block, tool);
		};
		if (!ok) {
			return false;
		}
		// les protections des autres plugins (zones protégées…) : un faux évènement de casse
		BlockBreakEvent check = new BlockBreakEvent(block, player);
		check.setDropItems(false);
		checkingExtra = true;
		try {
			Bukkit.getPluginManager().callEvent(check);
		} finally {
			checkingExtra = false;
		}
		return !check.isCancelled();
	}

	/** En zone, on ne casse que ce qui se mine avec cet outil (pas de terre à la pioche, pas de pierre à la pelle). */
	private static boolean suitable(Block block, ItemStack tool) {
		CustomBlock custom = Blocks.at(block);
		if (custom != null) {
			return Blocks.isSuitable(custom, tool);
		}
		return block.isPreferredTool(tool);
	}

	private static ItemStack smelt(ItemStack stack) {
		if (smelting == null) {
			smelting = new HashMap<>();
			for (Iterator<Recipe> it = Bukkit.recipeIterator(); it.hasNext(); ) {
				if (it.next() instanceof CookingRecipe<?> recipe && recipe instanceof org.bukkit.inventory.FurnaceRecipe) {
					if (recipe.getInputChoice() instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice materials) {
						for (Material m : materials.getChoices()) {
							smelting.putIfAbsent(m.name(), recipe.getResult());
						}
					} else if (recipe.getInputChoice() instanceof org.bukkit.inventory.RecipeChoice.ExactChoice exact) {
						for (ItemStack choice : exact.getChoices()) {
							fr.evotool.plugin.item.EvoItem item = Items.of(choice);
							if (item != null) {
								smelting.putIfAbsent("evotool:" + item.id(), recipe.getResult());
							}
						}
					}
				}
			}
		}
		fr.evotool.plugin.item.EvoItem item = Items.of(stack);
		ItemStack result = smelting.get(item != null ? "evotool:" + item.id() : stack.getType().name());
		if (result == null || (item == null && stack.hasItemMeta() && stack.getItemMeta().hasCustomName())) {
			return stack;
		}
		ItemStack out = result.clone();
		out.setAmount(Math.min(out.getMaxStackSize() * 4, result.getAmount() * stack.getAmount()));
		return out;
	}

	// ------------------------------------------------------------------ pioche / pelle : zone

	private static List<Block> areaPositions(Player player, Block origin, EvoMode mode) {
		List<Block> positions = new ArrayList<>();
		positions.add(origin);
		if (mode == EvoMode.SINGLE) {
			return positions;
		}
		BlockFace face = hitFace(player, origin);
		int[] u;
		int[] v;
		if (face == BlockFace.UP || face == BlockFace.DOWN) {
			u = new int[] {1, 0, 0};
			v = new int[] {0, 0, 1};
		} else if (face == BlockFace.EAST || face == BlockFace.WEST) {
			u = new int[] {0, 0, 1};
			v = new int[] {0, 1, 0};
		} else {
			u = new int[] {1, 0, 0};
			v = new int[] {0, 1, 0};
		}
		BlockFace inward = face.getOppositeFace();
		int r = mode.radius();
		for (int d = 0; d < mode.depth(); d++) {
			Block layer = origin.getRelative(inward, d);
			for (int a = -r; a <= r; a++) {
				for (int b = -r; b <= r; b++) {
					Block block = layer.getRelative(u[0] * a + v[0] * b, u[1] * a + v[1] * b, u[2] * a + v[2] * b);
					if (!block.equals(origin)) {
						positions.add(block);
					}
				}
			}
		}
		return positions;
	}

	private static BlockFace hitFace(Player player, Block origin) {
		RayTraceResult hit = player.rayTraceBlocks(6.0);
		if (hit != null && hit.getHitBlock() != null && hit.getHitBlock().equals(origin) && hit.getHitBlockFace() != null) {
			return hit.getHitBlockFace();
		}
		float pitch = player.getLocation().getPitch();
		if (pitch > 45) {
			return BlockFace.UP;
		}
		if (pitch < -45) {
			return BlockFace.DOWN;
		}
		return player.getFacing().getOppositeFace();
	}

	// ------------------------------------------------------------------ hache : abattage

	public static boolean isLog(Block block) {
		return Tag.LOGS.isTagged(block.getType()) || Blocks.at(block) == CustomBlock.EVOLVING_LOG;
	}

	public static boolean isNaturalLeaves(Block block) {
		return (block.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) || Blocks.at(block) == CustomBlock.EVOLVING_LEAVES;
	}

	private static List<Block> treePositions(Block origin, boolean withLeaves) {
		Set<Block> found = new LinkedHashSet<>();
		ArrayDeque<Block> queue = new ArrayDeque<>();
		found.add(origin);
		queue.add(origin);
		while (!queue.isEmpty() && found.size() < MAX_TREE_BLOCKS) {
			Block current = queue.poll();
			boolean currentIsLog = isLog(current) || current.equals(origin);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						Block next = current.getRelative(dx, dy, dz);
						if (found.contains(next) || next.getY() < origin.getY()) {
							continue;
						}
						boolean log = isLog(next);
						boolean leaves = withLeaves && currentIsLog && isNaturalLeaves(next);
						if (log || leaves) {
							found.add(next);
							if (log) {
								queue.add(next);
							} else {
								addLeafCluster(next, found, origin);
							}
						}
					}
				}
			}
		}
		return new ArrayList<>(found);
	}

	private static void addLeafCluster(Block start, Set<Block> found, Block origin) {
		ArrayDeque<Block> queue = new ArrayDeque<>();
		queue.add(start);
		BlockFace[] faces = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
		while (!queue.isEmpty() && found.size() < MAX_TREE_BLOCKS) {
			Block current = queue.poll();
			for (BlockFace face : faces) {
				Block next = current.getRelative(face);
				if (!found.contains(next) && next.getY() >= origin.getY() && next.getLocation().distanceSquared(start.getLocation()) <= 16.0
						&& isNaturalLeaves(next)) {
					found.add(next);
					queue.add(next);
				}
			}
		}
	}

	private static void replantSapling(Block origin, List<ItemStack> drops) {
		if (!origin.getType().isAir() || !Tag.DIRT.isTagged(origin.getRelative(BlockFace.DOWN).getType())) {
			return;
		}
		for (ItemStack stack : drops) {
			if (Tag.SAPLINGS.isTagged(stack.getType()) && stack.getAmount() > 0) {
				origin.setType(stack.getType());
				stack.subtract();
				return;
			}
		}
	}

	// ------------------------------------------------------------------ houe : récolte et labour

	private static boolean isMatureCrop(Block block) {
		return Tag.CROPS.isTagged(block.getType()) && block.getBlockData() instanceof Ageable age && age.getAge() >= age.getMaximumAge();
	}

	private static List<Block> cropPositions(Block origin, EvoMode mode) {
		List<Block> positions = new ArrayList<>();
		positions.add(origin);
		int r = mode.radius();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx != 0 || dz != 0) {
					positions.add(origin.getRelative(dx, 0, dz));
				}
			}
		}
		return positions;
	}

	private static void replantCrop(Block block, BlockData harvested, boolean grown) {
		if (!block.getType().isAir() || !(harvested.clone() instanceof Ageable crop)) {
			return;
		}
		crop.setAge(grown ? crop.getMaximumAge() : 0);
		if (crop.isSupported(block)) {
			block.setBlockData(crop);
		}
	}

	/** Labour en zone avec la houe évolutive (le bloc visé, lui, est labouré par Minecraft). */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void onTill(PlayerInteractEvent event) {
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock() == null
				|| event.getBlockFace() == BlockFace.DOWN || event.useItemInHand() == org.bukkit.event.Event.Result.DENY) {
			return;
		}
		Player player = event.getPlayer();
		ItemStack hoe = player.getInventory().getItemInMainHand();
		if (Items.kindOf(hoe) != EvoKind.HOE) {
			return;
		}
		EvoProfile profile = EvoKind.HOE.profile();
		EvoMode mode = player.isSneaking() ? EvoMode.SINGLE : Progression.selectedMode(profile, EvoData.get(hoe));
		Block center = event.getClickedBlock();
		int tilled = tilled(center) != null ? 1 : 0;
		int r = mode.radius();
		for (int dx = -r; dx <= r; dx++) {
			for (int dz = -r; dz <= r; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				Block block = center.getRelative(dx, 0, dz);
				Material result = tilled(block);
				if (result != null) {
					block.setType(result);
					tilled++;
				}
			}
		}
		if (tilled > 1) {
			center.getWorld().playSound(center.getLocation(), Sound.ITEM_HOE_TILL, SoundCategory.BLOCKS, 1.0F, 0.8F);
		}
		if (tilled > 0 && player.getGameMode() != GameMode.CREATIVE) {
			Progression.addXp(hoe, tilled, tilled, player);
		}
	}

	private static Material tilled(Block block) {
		if (!block.getRelative(BlockFace.UP).getType().isAir()) {
			return null;
		}
		return switch (block.getType()) {
			case DIRT, GRASS_BLOCK, DIRT_PATH -> Material.FARMLAND;
			case COARSE_DIRT -> Material.DIRT;
			default -> null;
		};
	}
}
