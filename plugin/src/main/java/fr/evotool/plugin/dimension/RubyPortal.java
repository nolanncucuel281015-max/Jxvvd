package fr.evotool.plugin.dimension;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.input.InputListener;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Portail en blocs de rubis, comme le portail du Nether : un cadre rectangulaire (intérieur de 2x3 à 21x21) allumé
 * avec le briquet d'évolium. Il relie le monde normal et la dimension évolutive (coordonnées 1:1). L'intérieur est
 * fait de blocs de lumière (on passe au travers) habillés du modèle animé du portail du mod.
 */
public final class RubyPortal implements Listener {
	public static final int MIN_WIDTH = 2;
	public static final int MIN_HEIGHT = 3;
	public static final int MAX_SIZE = 21;
	private static final int SEARCH_RADIUS = 16;
	private static final int ENTER_TICKS = 20;
	private static final int ARRIVAL_COOLDOWN = 80;
	private static final NamespacedKey PORTAL_TAG = Keys.key("portal");

	/** Joueur dans un portail : depuis quand ; joueurs qui viennent d'arriver : jusqu'à quand ils ne repartent pas. */
	private static final Map<UUID, Integer> INSIDE = new HashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();

	/** Intérieur d'un cadre : coin en bas « à gauche », axe (vrai : le long de x), largeur et hauteur. */
	public record Frame(Block bottomLeft, boolean alongX, int width, int height) {
		public BlockFace right() {
			return alongX ? BlockFace.EAST : BlockFace.SOUTH;
		}

		public void fill() {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++) {
					place(bottomLeft.getRelative(right(), x).getRelative(BlockFace.UP, y), alongX);
				}
			}
		}
	}

	public static void enable() {
		EvoToolPlugin.listen(new RubyPortal());
		EvoToolPlugin.ticker(RubyPortal::tick);
		InputListener.onUse((player, stack, clicked, hand) -> {
			if (!Items.is(stack, EvoItem.EVOLIUM_LIGHTER)) {
				return false;
			}
			RayTraceResult hit = player.rayTraceBlocks(5.0);
			if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
				return false;
			}
			Block inside = hit.getHitBlock().getRelative(hit.getHitBlockFace());
			if (!tryLight(inside, true)) {
				return false;   // pas de cadre de rubis : le briquet allume un feu, comme un briquet normal
			}
			Fx.sound(player, Sound.ITEM_FLINTANDSTEEL_USE, 1.0F, 1.0F);
			Fx.sound(inside.getWorld(), inside.getLocation().toVector(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6F, 1.6F);
			if (player.getGameMode() != GameMode.CREATIVE && stack.getItemMeta() instanceof Damageable damageable) {
				stack.damage(1, player);
			}
			return true;
		});
	}

	// ------------------------------------------------------------------ cadres

	public static boolean tryLight(Block inside, boolean light) {
		for (boolean alongX : new boolean[] {true, false}) {
			Frame frame = findFrame(inside, alongX);
			if (frame != null) {
				if (light) {
					frame.fill();
				}
				return true;
			}
		}
		return false;
	}

	private static boolean isInside(Block block) {
		Material type = block.getType();
		return type.isAir() || type == Material.FIRE || isPortal(block);
	}

	private static boolean isFrame(Block block) {
		return Blocks.at(block) == CustomBlock.RUBY_BLOCK;
	}

	public static Frame findFrame(Block start, boolean alongX) {
		if (!isInside(start)) {
			return null;
		}
		BlockFace right = alongX ? BlockFace.EAST : BlockFace.SOUTH;
		BlockFace left = right.getOppositeFace();
		Block p = start;
		for (int n = 0; n < MAX_SIZE && isInside(p.getRelative(BlockFace.DOWN)); n++) {
			p = p.getRelative(BlockFace.DOWN);
		}
		if (!isFrame(p.getRelative(BlockFace.DOWN))) {
			return null;
		}
		for (int n = 0; n < MAX_SIZE && isInside(p.getRelative(left)); n++) {
			p = p.getRelative(left);
		}
		if (!isFrame(p.getRelative(left))) {
			return null;
		}
		Block bottomLeft = p;
		int width = 0;
		while (width <= MAX_SIZE && isInside(bottomLeft.getRelative(right, width))) {
			width++;
		}
		if (width < MIN_WIDTH || width > MAX_SIZE || !isFrame(bottomLeft.getRelative(right, width))) {
			return null;
		}
		for (int x = 0; x < width; x++) {
			if (!isFrame(bottomLeft.getRelative(right, x).getRelative(BlockFace.DOWN))) {
				return null;
			}
		}
		int height = 0;
		rows:
		while (height <= MAX_SIZE) {
			for (int x = 0; x < width; x++) {
				if (!isInside(bottomLeft.getRelative(right, x).getRelative(BlockFace.UP, height))) {
					break rows;
				}
			}
			if (!isFrame(bottomLeft.getRelative(left).getRelative(BlockFace.UP, height))
					|| !isFrame(bottomLeft.getRelative(right, width).getRelative(BlockFace.UP, height))) {
				return null;
			}
			height++;
		}
		if (height < MIN_HEIGHT || height > MAX_SIZE) {
			return null;
		}
		for (int x = 0; x < width; x++) {
			if (!isFrame(bottomLeft.getRelative(right, x).getRelative(BlockFace.UP, height))) {
				return null;
			}
		}
		return new Frame(bottomLeft, alongX, width, height);
	}

	// ------------------------------------------------------------------ cases de portail

	private static NamespacedKey key(Block block) {
		return Keys.key("portal_" + block.getX() + "_" + block.getY() + "_" + block.getZ());
	}

	public static boolean isPortal(Block block) {
		return block.getType() == Material.LIGHT && block.getChunk().getPersistentDataContainer().has(key(block));
	}

	private static void place(Block block, boolean alongX) {
		if (isPortal(block)) {
			return;
		}
		Light light = (Light) Material.LIGHT.createBlockData();
		light.setLevel(11);
		block.setBlockData(light, false);
		block.getChunk().getPersistentDataContainer().set(key(block), PersistentDataType.BYTE, (byte) (alongX ? 1 : 0));
		Location at = block.getLocation().add(0.5, 0.5, 0.5);
		block.getWorld().spawn(at, ItemDisplay.class, d -> {
			d.setItemStack(Items.display("evolution_portal_" + (alongX ? "ns" : "ew")));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setPersistent(true);
			d.getPersistentDataContainer().set(PORTAL_TAG, PersistentDataType.BYTE, (byte) 1);
			d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.0F), new Quaternionf().rotationY((float) Math.PI)));
		});
	}

	private static void remove(Block block) {
		block.getChunk().getPersistentDataContainer().remove(key(block));
		if (block.getType() == Material.LIGHT) {
			block.setType(Material.AIR, false);
		}
		for (Entity e : block.getWorld().getNearbyEntities(block.getLocation().add(0.5, 0.5, 0.5), 0.3, 0.3, 0.3,
				e -> e instanceof ItemDisplay && e.getPersistentDataContainer().has(PORTAL_TAG))) {
			e.remove();
		}
	}

	/** Le portail s'éteint (cadre cassé, case remplacée) : toutes les cases reliées à celle-ci. */
	private static void collapse(Block from) {
		ArrayDeque<Block> queue = new ArrayDeque<>();
		Set<Block> seen = new HashSet<>();
		for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.SELF}) {
			Block next = from.getRelative(face);
			if (isPortal(next) || next.equals(from) && from.getChunk().getPersistentDataContainer().has(key(from))) {
				queue.add(next);
				seen.add(next);
			}
		}
		int removed = 0;
		while (!queue.isEmpty() && removed < MAX_SIZE * MAX_SIZE) {
			Block block = queue.poll();
			remove(block);
			removed++;
			for (BlockFace face : new BlockFace[] {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
				Block next = block.getRelative(face);
				if (!seen.contains(next) && isPortal(next)) {
					seen.add(next);
					queue.add(next);
				}
			}
		}
		if (removed > 0) {
			Fx.sound(from.getWorld(), from.getLocation().toVector(), Sound.BLOCK_GLASS_BREAK, 1.0F, 0.6F);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent event) {
		Block block = event.getBlock();
		if (isFrame(block)) {
			EvoToolPlugin.later(1, () -> collapse(block));
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent event) {
		Block block = event.getBlock();
		if (block.getChunk().getPersistentDataContainer().has(key(block))) {
			block.getChunk().getPersistentDataContainer().remove(key(block));
			collapse(block);
		}
	}

	// ------------------------------------------------------------------ passage

	@EventHandler(ignoreCancelled = true)
	public void onMove(PlayerMoveEvent event) {
		if (event.getTo().getBlockX() == event.getFrom().getBlockX() && event.getTo().getBlockY() == event.getFrom().getBlockY()
				&& event.getTo().getBlockZ() == event.getFrom().getBlockZ()) {
			return;
		}
		Player player = event.getPlayer();
		if (isPortal(event.getTo().getBlock()) || isPortal(event.getTo().clone().add(0, 1, 0).getBlock())) {
			INSIDE.putIfAbsent(player.getUniqueId(), Bukkit.getCurrentTick());
		} else {
			INSIDE.remove(player.getUniqueId());
		}
	}

	private static void tick(int tick) {
		for (Map.Entry<UUID, Integer> e : new HashMap<>(INSIDE).entrySet()) {
			Player player = Bukkit.getPlayer(e.getKey());
			if (player == null || !(isPortal(player.getLocation().getBlock()) || isPortal(player.getEyeLocation().getBlock()))) {
				INSIDE.remove(e.getKey());
				continue;
			}
			Integer until = COOLDOWN.get(e.getKey());
			if (until != null && tick < until) {
				INSIDE.put(e.getKey(), tick);
				continue;
			}
			int inside = tick - e.getValue();
			if (tick % 4 == 0) {
				Fx.particle(player.getWorld(), Particle.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
			}
			if (inside >= (player.getGameMode() == GameMode.CREATIVE ? 1 : ENTER_TICKS)) {
				INSIDE.remove(e.getKey());
				travel(player);
			}
		}
	}

	/** Le joueur passe dans l'autre monde, devant un portail (trouvé ou construit). */
	public static boolean travel(Player player) {
		World from = player.getWorld();
		World destination = EvoDimension.isDimension(from) ? Bukkit.getWorlds().get(0) : EvoDimension.world();
		if (destination == null) {
			player.sendActionBar(Lang.tr("plugin.evotool.dimension_missing").color(NamedTextColor.RED));
			COOLDOWN.put(player.getUniqueId(), Bukkit.getCurrentTick() + ARRIVAL_COOLDOWN);
			return false;
		}
		Block portal = findOrBuild(destination, player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
		Byte alongX = portal.getChunk().getPersistentDataContainer().get(key(portal), PersistentDataType.BYTE);
		Block front = alongX == null || alongX == 1 ? portal.getRelative(BlockFace.SOUTH) : portal.getRelative(BlockFace.EAST);
		Location arrival = front.getLocation().add(0.5, 0, 0.5);
		arrival.setYaw(player.getLocation().getYaw());
		arrival.setPitch(player.getLocation().getPitch());
		COOLDOWN.put(player.getUniqueId(), Bukkit.getCurrentTick() + ARRIVAL_COOLDOWN);
		player.teleport(arrival);
		Fx.sound(player, Sound.BLOCK_PORTAL_TRAVEL, 0.4F, 1.4F);
		return true;
	}

	/** Bas d'un portail près de (x, y, z) dans ce monde ; en construit un s'il n'y en a pas. */
	public static Block findOrBuild(World world, int x0, int y0, int z0) {
		Block best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int cx = (x0 - SEARCH_RADIUS) >> 4; cx <= (x0 + SEARCH_RADIUS) >> 4; cx++) {
			for (int cz = (z0 - SEARCH_RADIUS) >> 4; cz <= (z0 + SEARCH_RADIUS) >> 4; cz++) {
				PersistentDataContainer pdc = world.getChunkAt(cx, cz).getPersistentDataContainer();
				for (NamespacedKey key : pdc.getKeys()) {
					if (!key.getNamespace().equals(Keys.NAMESPACE) || !key.getKey().startsWith("portal_")) {
						continue;
					}
					String[] xyz = key.getKey().substring("portal_".length()).split("_");
					try {
						Block block = world.getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
						if (!isPortal(block) || isPortal(block.getRelative(BlockFace.DOWN))) {
							continue;
						}
						double d = Math.pow(block.getX() - x0, 2) + Math.pow(block.getY() - y0, 2) + Math.pow(block.getZ() - z0, 2);
						if (d < bestDistance) {
							bestDistance = d;
							best = block;
						}
					} catch (RuntimeException e) {
						// clé illisible : ignorée
					}
				}
			}
		}
		return best != null ? best : build(world, x0, z0);
	}

	/** Construit un cadre de rubis 4x5 allumé (le long de x), sur une petite plateforme si le sol manque. */
	public static Block build(World world, int x, int z) {
		world.getChunkAt(x >> 4, z >> 4);
		int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
		if (y <= world.getMinHeight() + 2 || y > world.getMaxHeight() - 10) {
			y = 70;
		}
		boolean dimension = EvoDimension.isDimension(world);
		Block base = world.getBlockAt(x, y, z);
		for (int dx = -2; dx <= 3; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				Block ground = base.getRelative(dx, -1, dz);
				if (!ground.getType().isSolid()) {
					if (dimension) {
						Blocks.place(ground, CustomBlock.EVOLVING_STONE);
					} else {
						ground.setType(Material.STONE, false);
					}
				}
				for (int dy = 0; dy <= 4; dy++) {
					base.getRelative(dx, dy, dz).setType(Material.AIR, false);
				}
			}
		}
		for (int dx = -1; dx <= 2; dx++) {
			Blocks.place(base.getRelative(dx, -1, 0), CustomBlock.RUBY_BLOCK);
			Blocks.place(base.getRelative(dx, 3, 0), CustomBlock.RUBY_BLOCK);
		}
		for (int dy = 0; dy < 3; dy++) {
			Blocks.place(base.getRelative(-1, dy, 0), CustomBlock.RUBY_BLOCK);
			Blocks.place(base.getRelative(2, dy, 0), CustomBlock.RUBY_BLOCK);
		}
		new Frame(base, true, 2, 3).fill();
		return base;
	}

	/** Auto-test : un cadre posé à la main s'allume au briquet, puis s'éteint quand on casse le rubis. */
	public static String test(World world, int x, int y, int z) {
		Block base = world.getBlockAt(x, y, z);
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				base.getRelative(dx, dy, 0).setType(Material.AIR, false);
			}
		}
		for (int dx = -1; dx <= 2; dx++) {
			Blocks.place(base.getRelative(dx, -1, 0), CustomBlock.RUBY_BLOCK);
			Blocks.place(base.getRelative(dx, 3, 0), CustomBlock.RUBY_BLOCK);
		}
		for (int dy = 0; dy < 3; dy++) {
			Blocks.place(base.getRelative(-1, dy, 0), CustomBlock.RUBY_BLOCK);
			Blocks.place(base.getRelative(2, dy, 0), CustomBlock.RUBY_BLOCK);
		}
		String problem = null;
		if (!tryLight(base.getRelative(1, 1, 0), true)) {
			problem = "le cadre ne s'allume pas";
		} else if (!isPortal(base) || !isPortal(base.getRelative(1, 2, 0))) {
			problem = "intérieur non rempli";
		}
		Block found = findOrBuild(world, x + 3, y, z + 2);
		if (problem == null && (found.getY() != base.getY() || found.getZ() != base.getZ() || Math.abs(found.getX() - base.getX() - 0.5) > 1)) {
			problem = "portail existant non trouvé (" + found + ")";
		}
		base.getRelative(-1, 0, 0).setType(Material.AIR, false);
		collapse(base.getRelative(-1, 0, 0));
		if (problem == null && isPortal(base)) {
			problem = "le portail ne s'éteint pas";
		}
		for (int dx = -1; dx <= 2; dx++) {
			for (int dy = -1; dy <= 3; dy++) {
				base.getRelative(dx, dy, 0).setType(Material.AIR, false);
			}
		}
		return problem;
	}
}
