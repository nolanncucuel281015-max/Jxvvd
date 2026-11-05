package fr.evotool.plugin.evo;

import fr.evotool.plugin.armor.ArmorSet;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Évolution (accroupi + F ; la touche R du mod) : quand un objet a toute l'XP de sa prochaine forme, il attend.
 * On le fait apparaître en géant devant le joueur : il monte en tournant dans un tourbillon d'étoiles, au son de
 * carillons de plus en plus aigus, puis éclate dans une gerbe de lumière sous sa nouvelle forme.
 */
public final class Evolution {
	private static final int CLIMAX = 40;
	private static final int END = 62;
	private static final float SIZE = 1.3F;

	private static final class Running {
		final Player player;
		final List<ItemStack> stacks;
		final ItemDisplay display;
		final Vector center;
		final int start;
		float angle;

		Running(Player player, List<ItemStack> stacks, ItemDisplay display, Vector center, int start) {
			this.player = player;
			this.stacks = stacks;
			this.display = display;
			this.center = center;
			this.start = start;
		}
	}

	private static final Map<UUID, Running> RUNNING = new HashMap<>();

	private Evolution() {
	}

	/** Objets prêts à évoluer : celui en main, sinon dans l'autre main, sinon les pièces d'armure portées. */
	public static List<ItemStack> candidates(Player player) {
		for (ItemStack stack : new ItemStack[] {player.getInventory().getItemInMainHand(), player.getInventory().getItemInOffHand()}) {
			if (Progression.isReadyToEvolve(stack)) {
				return List.of(stack);
			}
		}
		List<ItemStack> armor = new ArrayList<>();
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack stack = ArmorSet.worn(player, slot);
			if (stack != null && Progression.isReadyToEvolve(stack)) {
				armor.add(stack);
			}
		}
		return armor;
	}

	/** Lance l'animation d'évolution (l'objet n'évolue vraiment qu'à son apogée). */
	public static boolean start(Player player) {
		if (RUNNING.containsKey(player.getUniqueId()) || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
			return false;
		}
		List<ItemStack> stacks = candidates(player);
		if (stacks.isEmpty()) {
			player.sendActionBar(Lang.tr("message.evotool.nothing_to_evolve").color(NamedTextColor.GRAY));
			return false;
		}
		World world = player.getWorld();
		Vector forward = Fx.fromYaw(player.getLocation().getYaw());
		Vector center = Fx.eye(player).add(forward.multiply(2.2)).add(new Vector(0.0, -0.3, 0.0));
		ItemDisplay display = Giant.spawn(world, stacks.get(0).clone(), ItemDisplay.ItemDisplayTransform.FIXED, center,
				player.getLocation().getYaw(), new Quaternionf(), SIZE, 10);
		RUNNING.put(player.getUniqueId(), new Running(player, stacks, display, center, Bukkit.getCurrentTick()));
		Fx.sound(world, center, Sound.BLOCK_BEACON_ACTIVATE, 1.2F, 1.3F);
		Fx.sound(world, center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
		player.sendActionBar(Lang.tr("message.evotool.evolving", Progression.name(stacks.get(0))).color(NamedTextColor.LIGHT_PURPLE)
				.decorate(TextDecoration.ITALIC));
		return true;
	}

	/** Termine tout de suite l'animation de ce joueur (auto-test) : vrai si l'objet a évolué. */
	public static boolean completeNow(Player player) {
		Running running = RUNNING.remove(player.getUniqueId());
		if (running == null) {
			return false;
		}
		boolean evolved = climax(running);
		running.display.remove();
		return evolved;
	}

	public static void cancel(UUID player) {
		Running running = RUNNING.remove(player);
		if (running != null) {
			running.display.remove();
		}
	}

	public static void clear() {
		RUNNING.values().forEach(r -> r.display.remove());
		RUNNING.clear();
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	public static void tick(int tick) {
		for (Iterator<Running> it = RUNNING.values().iterator(); it.hasNext(); ) {
			Running running = it.next();
			int age = tick - running.start;
			if (!running.display.isValid() || !running.player.isOnline()) {
				running.display.remove();
				it.remove();
				continue;
			}
			if (age < CLIMAX) {
				charge(running, age);
			} else if (age == CLIMAX) {
				if (!climax(running)) {
					Giant.remove(running.display, SIZE);
					it.remove();
				}
			} else if (age < END) {
				settle(running, age - CLIMAX);
			} else {
				Giant.remove(running.display, SIZE);
				it.remove();
			}
		}
	}

	private static void charge(Running running, int age) {
		World world = running.display.getWorld();
		float progress = age / (float) CLIMAX;
		running.angle += Math.min(45.0F, 3.0F + age * 1.1F);
		Quaternionf rotation = new Quaternionf().rotationY(rad(running.angle)).rotateZ(rad(8.0 * Math.sin(age * 0.35)));
		Giant.poseNow(running.display, Giant.transformation(rotation, SIZE * (1.0F + 0.15F * progress)), 1);
		Vector c = running.center.clone().add(new Vector(0.0, 0.4 * progress, 0.0));
		running.display.teleport(new Location(world, c.getX(), c.getY(), c.getZ(), running.display.getLocation().getYaw(), 0));
		double radius = 1.4 - 0.7 * progress;
		for (int k = 0; k < 3; k++) {
			double a = age * 0.45 + k * Math.PI * 2 / 3;
			Fx.particle(world, Particle.END_ROD, c.getX() + Math.cos(a) * radius, c.getY() - 0.6 + 1.2 * ((age + k * 7) % 14) / 14.0,
					c.getZ() + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
		Fx.particle(world, Particle.ENCHANT, c.getX(), c.getY() + 0.3, c.getZ(), 8, 1.4, 1.0, 1.4, 1.0);
		if (age > 24) {
			Fx.particle(world, Particle.ELECTRIC_SPARK, c, 4, 0.5, 0.5, 0.5, 0.15);
		}
		if (age % 8 == 0) {
			Fx.sound(world, c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.4F, 0.6F + 1.4F * progress);
		}
		if (age == 20) {
			Fx.sound(world, c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0F, 1.2F);
		}
		if (age == 34) {
			Fx.sound(world, c, Sound.BLOCK_BEACON_POWER_SELECT, 1.2F, 1.5F);
		}
	}

	/** Apogée : l'objet évolue vraiment, éclate en lumière et montre sa nouvelle forme. Faux s'il n'est plus là. */
	private static boolean climax(Running running) {
		Player player = running.player;
		ItemStack shown = null;
		for (ItemStack stack : running.stacks) {
			if (Items.kindOf(stack) != null && Progression.evolve(stack, player)) {
				if (shown == null) {
					shown = stack;
				}
				player.sendMessage(Lang.tr("message.evotool.evolved", Progression.name(stack), EvoData.get(stack).level())
						.color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
			}
		}
		World world = running.display.getWorld();
		Vector c = running.display.getLocation().toVector();
		if (shown == null) {
			Fx.particle(world, Particle.LARGE_SMOKE, c, 20, 0.4, 0.4, 0.4, 0.02);
			player.sendActionBar(Lang.tr("message.evotool.evolution_cancelled").color(NamedTextColor.RED));
			return false;
		}
		running.display.setItemStack(shown.clone());
		Giant.poseNow(running.display, Giant.transformation(new Quaternionf().rotationY(rad(running.angle)), SIZE * 1.4F), 2);
		Fx.particle(world, Particle.TOTEM_OF_UNDYING, c, 90, 0.5, 0.6, 0.5, 0.6);
		Fx.particle(world, Particle.END_ROD, c, 50, 0.2, 0.2, 0.2, 0.35);
		Fx.particle(world, Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
		Fx.sound(world, c, Sound.ITEM_TOTEM_USE, 0.7F, 1.3F);
		Fx.sound(world, c, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
		Fx.sound(player, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.2F);
		player.showTitle(net.kyori.adventure.title.Title.title(Progression.name(shown).color(NamedTextColor.LIGHT_PURPLE),
				Lang.tr("tooltip.evotool.form", Progression.stageName(shown), Progression.stage(shown), Progression.maxStage(shown))
						.color(NamedTextColor.YELLOW)));
		return true;
	}

	private static void settle(Running running, int age) {
		float progress = age / (float) (END - CLIMAX);
		running.angle += Math.max(4.0F, 30.0F - age * 1.4F);
		Giant.poseNow(running.display, Giant.transformation(new Quaternionf().rotationY(rad(running.angle)), SIZE * (1.4F - 0.3F * progress)), 2);
		if (age % 3 == 0) {
			Fx.particle(running.display.getWorld(), Particle.END_ROD, running.display.getLocation().toVector(), 3, 0.6, 0.6, 0.6, 0.02);
		}
	}

	/** Remet les objets évolutifs du joueur aux règles actuelles (enchantements plafonnés, bonus). */
	public static void rebalance(Player player) {
		for (ItemStack stack : player.getInventory().getContents()) {
			if (stack != null && Items.kindOf(stack) != null) {
				Progression.refresh(stack, false);
			}
		}
	}
}
