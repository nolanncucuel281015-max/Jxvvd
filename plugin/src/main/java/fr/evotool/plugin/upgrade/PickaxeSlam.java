package fr.evotool.plugin.upgrade;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.mining.MiningListener;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Pioche Évolutive niveau 100 améliorée à l'évolite, clic droit : la pioche apparaît en géant, se lève au-dessus du
 * joueur puis s'abat vers l'avant. Une onde tranchante creuse alors une tranchée de 100 blocs de long et 7 de large,
 * de 7 blocs au-dessus des pieds jusqu'à la bedrock. Seuls les minerais rapportent quelque chose.
 */
public final class PickaxeSlam {
	public static final int LENGTH = 100;
	public static final int WIDTH = 7;
	public static final int HEIGHT_ABOVE = 7;
	private static final int START_DISTANCE = 2;
	private static final int BLOCKS_PER_TICK = 2;
	public static final int COOLDOWN = 300;
	private static final int GROW_TICKS = 8;
	private static final int SWING_TICK = 12;
	private static final int SWING_TICKS = 4;
	private static final int IMPACT_TICK = SWING_TICK + SWING_TICKS;
	private static final int VANISH_TICK = IMPACT_TICK + 24;
	private static final float SCALE = 5.0F;
	private static final float HANDLE_BOTTOM = 0.8F;

	private static final class Slash {
		final World world;
		final Player player;
		final ItemStack pickaxe;
		final Block origin;
		final Vector forward;
		final Vector right;
		final int startTick;
		final ItemDisplay giant;
		int carved;
		int xp;

		Slash(Player player, ItemStack pickaxe, int startTick, ItemDisplay giant) {
			this.world = player.getWorld();
			this.player = player;
			this.pickaxe = pickaxe;
			this.origin = player.getLocation().getBlock();
			this.forward = Fx.fromYaw(player.getLocation().getYaw());
			this.right = new Vector(-forward.getZ(), 0, forward.getX());
			this.startTick = startTick;
			this.giant = giant;
		}
	}

	private static final List<Slash> SLASHES = new ArrayList<>();

	private PickaxeSlam() {
	}

	public static boolean isReady(ItemStack stack) {
		return Items.kindOf(stack) == EvoKind.PICKAXE && EvoliteUpgrade.isUpgraded(stack)
				&& EvoData.get(stack).level() >= Progression.MAX_LEVEL;
	}

	/** Les colonnes (x, z) de la tranchée situées entre deux distances devant le joueur. */
	public static Set<long[]> trench(Block origin, Vector forward, Vector right, double from, double to) {
		Set<String> seen = new LinkedHashSet<>();
		Set<long[]> columns = new LinkedHashSet<>();
		double half = (WIDTH - 1) / 2.0;
		for (double d = from; d < to; d += 0.5) {
			for (double w = -half - 0.25; w <= half + 0.25; w += 0.5) {
				long x = (long) Math.floor(origin.getX() + 0.5 + forward.getX() * d + right.getX() * w);
				long z = (long) Math.floor(origin.getZ() + 0.5 + forward.getZ() * d + right.getZ() * w);
				if (seen.add(x + "," + z)) {
					columns.add(new long[] {x, z});
				}
			}
		}
		return columns;
	}

	public static void start(Player player, ItemStack pickaxe) {
		ItemStack shown = pickaxe.asOne();
		shown.editMeta(meta -> {
			meta.setEnchantmentGlintOverride(false);
			Items.setFloats(meta, Progression.stage(EvoData.get(pickaxe).level()));
		});
		Vector f = Fx.fromYaw(player.getLocation().getYaw());
		Vector pivot = Fx.feet(player).add(new Vector(f.getX() * 1.5, 2.0, f.getZ() * 1.5));
		ItemDisplay giant = Giant.create(player.getWorld(), pivot, shown, ItemDisplay.ItemDisplayTransform.NONE,
				player.getLocation().getYaw(), pose(0, 0.2F));
		Giant.pose(giant, pose(-35, SCALE), GROW_TICKS);
		Giant.poof(player.getWorld(), pivot, SCALE);
		Fx.sound(player, Sound.BLOCK_BEACON_POWER_SELECT, 1.5F, 0.7F);
		SLASHES.add(new Slash(player, pickaxe, Bukkit.getCurrentTick(), giant));
	}

	/** La pioche géante pivote autour du bas de son manche (0 = verticale, positif = le fer bascule vers l'avant). */
	private static Transformation pose(float angle, float scale) {
		Quaternionf rotation = new Quaternionf().rotationX((float) Math.toRadians(angle));
		Vector3f handle = new Vector3f(0, -HANDLE_BOTTOM * scale, 0);
		rotation.transform(handle);
		return new Transformation(handle.negate(), rotation, new Vector3f(scale, scale, scale), new Quaternionf());
	}

	public static void tick(int tick) {
		for (Iterator<Slash> it = SLASHES.iterator(); it.hasNext(); ) {
			Slash slash = it.next();
			int step = tick - slash.startTick;
			if (step == SWING_TICK) {
				Giant.poseNow(slash.giant, pose(115, SCALE), SWING_TICKS);
				Fx.sound(slash.world, slash.origin.getLocation().toVector(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
			}
			if (step == IMPACT_TICK) {
				impact(slash);
			}
			if (step >= IMPACT_TICK && slash.carved < LENGTH) {
				carve(slash);
			}
			if (step == VANISH_TICK) {
				Giant.remove(slash.giant, SCALE);
			}
			if (step >= VANISH_TICK && slash.carved >= LENGTH) {
				if (slash.xp > 0 && slash.player.isOnline() && slash.player.getGameMode() != GameMode.CREATIVE) {
					Progression.addXp(slash.pickaxe, slash.xp, 0, slash.player);
				}
				it.remove();
			}
		}
	}

	private static void impact(Slash slash) {
		Vector c = slash.origin.getLocation().toVector().add(new Vector(0.5, 0, 0.5)).add(slash.forward.clone().multiply(5.0));
		Fx.particle(slash.world, Particle.EXPLOSION_EMITTER, c.getX(), c.getY() + 0.5, c.getZ(), 2, 0.5, 0.2, 0.5, 0.0);
		Fx.particle(slash.world, Particle.SONIC_BOOM, c.getX(), c.getY() + 1.5, c.getZ(), 1, 0, 0, 0, 0);
		Fx.sound(slash.world, c, Sound.ENTITY_GENERIC_EXPLODE, 3.0F, 0.6F);
		Fx.sound(slash.world, c, Sound.BLOCK_ANVIL_LAND, 2.0F, 0.5F);
	}

	private static void carve(Slash slash) {
		World world = slash.world;
		double from = START_DISTANCE + slash.carved;
		int advance = Math.min(BLOCKS_PER_TICK, LENGTH - slash.carved);
		double to = from + advance;
		List<ItemStack> drops = new ArrayList<>();
		int top = slash.origin.getY() + HEIGHT_ABOVE - 1;
		int bottom = world.getMinHeight();
		for (long[] column : trench(slash.origin, slash.forward, slash.right, from, to)) {
			int x = (int) column[0];
			int z = (int) column[1];
			if (!world.isChunkLoaded(x >> 4, z >> 4)) {
				continue;
			}
			for (int y = top; y >= bottom; y--) {
				Block block = world.getBlockAt(x, y, z);
				Material type = block.getType();
				if (type.isAir() || type.getHardness() < 0 || block.isLiquid() || block.getState() instanceof TileState) {
					continue;
				}
				if (Progression.isOre(block) || Progression.xpForBlock(block) >= 15) {
					drops.addAll(MiningListener.dropsOf(block, slash.pickaxe, slash.player));
					slash.xp += Progression.xpForBlock(block);
				}
				block.setType(Material.AIR, false);
			}
		}
		slash.carved += advance;
		Vector front = slash.origin.getLocation().toVector().add(new Vector(0.5, 0, 0.5)).add(slash.forward.clone().multiply(to));
		for (double w = -3.5; w <= 3.5; w += 0.7) {
			Vector p = front.clone().add(slash.right.clone().multiply(w));
			Fx.particle(world, Particle.SWEEP_ATTACK, p.getX(), p.getY() + 3.5, p.getZ(), 1, 0, 2.5, 0, 0);
			Fx.particle(world, Particle.EXPLOSION, p.getX(), p.getY() + 1.0, p.getZ(), 1, 0.2, 1.5, 0.2, 0);
		}
		Fx.sound(world, front, Sound.ENTITY_GENERIC_EXPLODE, 1.2F, 1.2F);
		if (slash.player.isOnline()) {
			MiningListener.deliver(slash.player, slash.player.getLocation(), drops, false,
					EvoliteUpgrade.has(slash.pickaxe, EvoliteUpgrade.Upgrade.TELEKINESIS));
		}
	}

	public static void clear() {
		SLASHES.forEach(s -> s.giant.remove());
		SLASHES.clear();
	}
}
