package fr.evotool.plugin.machine;

import fr.evotool.plugin.block.BlockListener;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.item.Lore;
import fr.evotool.plugin.menu.Menu;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Le générateur de bouclier : il brûle 1 évolium toutes les 10 minutes pour tenir un dôme d'énergie autour de lui
 * (rayon réglable de 3 à 64 blocs). Les monstres dedans en sont expulsés, ceux qui approchent sont repoussés (les
 * creepers n'y explosent pas) et les projectiles qui ne viennent pas d'un joueur s'y désintègrent. Sa case avale
 * l'évolium (et les blocs d'évolium, 9 chacun) jusqu'à 1 milliard.
 */
public final class ShieldGenerator implements Machines.Machine {
	public static final long CAPACITY = 1_000_000_000L;
	public static final int TICKS_PER_EVOLIUM = 12_000;
	public static final int MIN_RADIUS = 3;
	public static final int MAX_RADIUS = 64;
	public static final int DEFAULT_RADIUS = 12;
	private static final float MODEL_RADIUS = 15.0F / 16.0F;

	private final Block block;
	private long fuel;
	private int burn;
	private int radius = DEFAULT_RADIUS;
	private boolean enabled = true;
	private int age;
	private boolean wasActive;
	private float spin;
	private ItemDisplay dome;
	private int domeRadius = -1;
	private GeneratorMenu menu;

	private ShieldGenerator(Block block) {
		this.block = block;
		PersistentDataContainer data = Machines.stored(block);
		fuel = Math.max(0, Math.min(CAPACITY, data.getOrDefault(Keys.SHIELD_FUEL, PersistentDataType.LONG, 0L)));
		burn = data.getOrDefault(Keys.key("burn"), PersistentDataType.INTEGER, 0);
		radius = clamp(data.getOrDefault(Keys.SHIELD_RADIUS, PersistentDataType.INTEGER, DEFAULT_RADIUS));
		enabled = data.getOrDefault(Keys.key("enabled"), PersistentDataType.BYTE, (byte) 1) != 0;
		wasActive = isActive();
	}

	public static void enable() {
		Machines.register(CustomBlock.SHIELD_GENERATOR, ShieldGenerator::new);
		BlockListener.register(CustomBlock.SHIELD_GENERATOR, new BlockListener.Handler() {
			@Override
			public void placed(Block placed, Player player, ItemStack item) {
				ShieldGenerator generator = (ShieldGenerator) Machines.at(placed);
				if (generator != null && item.hasItemMeta()) {
					PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
					generator.fuel = Math.max(0, Math.min(CAPACITY, pdc.getOrDefault(Keys.SHIELD_FUEL, PersistentDataType.LONG, 0L)));
					generator.radius = clamp(pdc.getOrDefault(Keys.SHIELD_RADIUS, PersistentDataType.INTEGER, DEFAULT_RADIUS));
					Machines.save(placed, generator);
				}
			}

			@Override
			public void broken(Block broken, List<ItemStack> drops) {
				ShieldGenerator generator = (ShieldGenerator) Machines.at(broken);
				if (generator != null) {
					drops.removeIf(s -> Items.is(s, EvoItem.SHIELD_GENERATOR));
					drops.add(generator.asItem());
					if (generator.menu != null) {
						generator.menu.close();
					}
				}
				Machines.removed(broken);
			}

			@Override
			public boolean used(Block clicked, Player player) {
				ShieldGenerator generator = (ShieldGenerator) Machines.at(clicked);
				if (generator == null) {
					return false;
				}
				generator.open(player);
				return true;
			}
		});
	}

	/** Auto-test : un générateur posé là, avec de l'évolium, tient son dôme et repousse un zombie. Null si tout va bien. */
	public static String test(Block at) {
		fr.evotool.plugin.block.Blocks.place(at, CustomBlock.SHIELD_GENERATOR);
		ShieldGenerator generator = (ShieldGenerator) Machines.at(at);
		if (generator == null) {
			return "pas de machine";
		}
		generator.fuel = 5;
		for (int i = 0; i < 5; i++) {
			generator.tick(i);
		}
		String problem = !generator.isActive() ? "éteint" : generator.dome == null || !generator.dome.isValid() ? "pas de dôme"
				: generator.fuel != 4 ? "évolium non brûlé (" + generator.fuel + ")" : null;
		ItemStack item = generator.asItem();
		long stored = item.getItemMeta().getPersistentDataContainer().getOrDefault(Keys.SHIELD_FUEL, PersistentDataType.LONG, 0L);
		if (problem == null && stored != 5) {
			problem = "évolium ramassé : " + stored;
		}
		Machines.save(at, generator);
		if (problem == null && Machines.stored(at).getOrDefault(Keys.SHIELD_FUEL, PersistentDataType.LONG, -1L) != 4) {
			problem = "état non sauvegardé";
		}
		Machines.removed(at);
		at.setType(Material.AIR, false);
		return problem;
	}

	private static int clamp(int r) {
		return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, r));
	}

	public static int fuelValue(ItemStack stack) {
		if (Items.is(stack, EvoItem.EVOLIUM)) {
			return 1;
		}
		return Items.is(stack, EvoItem.EVOLIUM_BLOCK) ? 9 : 0;
	}

	public boolean isActive() {
		return enabled && (burn > 0 || fuel > 0);
	}

	/** Le générateur ramassé : lui-même, avec son évolium (l'entamé compte entier) et son rayon. */
	private ItemStack asItem() {
		ItemStack self = Items.create(EvoItem.SHIELD_GENERATOR);
		long stored = Math.min(CAPACITY, fuel + (burn > 0 ? 1 : 0));
		self.editMeta(meta -> {
			if (stored > 0) {
				meta.getPersistentDataContainer().set(Keys.SHIELD_FUEL, PersistentDataType.LONG, stored);
			}
			meta.getPersistentDataContainer().set(Keys.SHIELD_RADIUS, PersistentDataType.INTEGER, radius);
		});
		Lore.update(self);
		return self;
	}

	@Override
	public void save(PersistentDataContainer data) {
		data.set(Keys.SHIELD_FUEL, PersistentDataType.LONG, fuel);
		data.set(Keys.key("burn"), PersistentDataType.INTEGER, burn);
		data.set(Keys.SHIELD_RADIUS, PersistentDataType.INTEGER, radius);
		data.set(Keys.key("enabled"), PersistentDataType.BYTE, (byte) (enabled ? 1 : 0));
	}

	@Override
	public void unload() {
		removeDome();
		if (menu != null) {
			menu.close();
		}
	}

	@Override
	public void tick(int tick) {
		age++;
		if (enabled) {
			if (burn <= 0 && fuel > 0) {
				fuel--;
				burn = TICKS_PER_EVOLIUM;
			}
			if (burn > 0) {
				burn--;
			}
		}
		boolean active = isActive();
		Vector c = center();
		if (active != wasActive) {
			Fx.sound(block.getWorld(), c, active ? Sound.BLOCK_BEACON_ACTIVATE : Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 1.2F);
			wasActive = active;
			refreshMenu();
		}
		if (active) {
			updateDome();
			if (age % 4 == 0) {
				protect();
			}
		} else {
			removeDome();
		}
		if (menu != null && age % 20 == 0) {
			refreshMenu();
		}
	}

	private Vector center() {
		return block.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
	}

	// ------------------------------------------------------------------ dôme d'énergie

	private void updateDome() {
		World world = block.getWorld();
		Vector c = center();
		if (dome == null || !dome.isValid()) {
			dome = Giant.create(world, c.clone().subtract(new Vector(0, radius, 0)), Items.display("shield/dome"),
					ItemDisplay.ItemDisplayTransform.NONE, 0.0F, Giant.transformation(new Quaternionf(), 0.1F));
			dome.setViewRange(4.0F);
			dome.setTeleportDuration(0);
			domeRadius = -1;
			Fx.particle(world, Particle.END_ROD, c.getX(), c.getY() + 0.7, c.getZ(), 40, 0.3, 0.6, 0.3, 0.3);
		}
		boolean resized = domeRadius != radius;
		if (resized || age % 20 == 0) {
			if (resized) {
				// l'entité est posée en bas de la sphère, avec une boîte de la taille du dôme (pour être vue de loin)
				dome.teleport(new Location(world, c.getX(), c.getY() - radius, c.getZ()));
				dome.setDisplayWidth(radius * 2.0F + 1.0F);
				dome.setDisplayHeight(radius * 2.0F + 1.0F);
				domeRadius = radius;
			}
			spin += 6.0F;
			float scale = radius / MODEL_RADIUS;
			dome.setInterpolationDelay(0);
			dome.setInterpolationDuration(resized ? 10 : 20);
			dome.setTransformation(new Transformation(new Vector3f(0.0F, radius, 0.0F),
					new Quaternionf().rotationY((float) Math.toRadians(spin)), new Vector3f(scale), new Quaternionf()));
		}
	}

	private void removeDome() {
		if (dome != null) {
			dome.remove();
			dome = null;
			domeRadius = -1;
		}
	}

	// ------------------------------------------------------------------ protection

	private void protect() {
		World world = block.getWorld();
		Vector c = center();
		double r = radius;
		BoundingBox area = BoundingBox.of(block).expand(r + 3);
		for (Entity e : world.getNearbyEntities(area, e -> e instanceof Enemy && e instanceof LivingEntity l && !l.isDead())) {
			LivingEntity mob = (LivingEntity) e;
			Vector at = Fx.center(mob);
			double distance = at.distance(c);
			if (distance > r + 2) {
				continue;
			}
			if (mob instanceof Creeper creeper) {
				creeper.setIgnited(false);
				creeper.setFuseTicks(0);
			}
			Vector out = new Vector(at.getX() - c.getX(), 0, at.getZ() - c.getZ());
			if (out.lengthSquared() < 1.0E-4) {
				out = new Vector(1, 0, 0);
			}
			out.normalize();
			if (distance < r - 2 && !Progression.isBoss(mob)) {
				Vector target = c.clone().add(out.clone().multiply(r + 2.5));
				int top = world.getHighestBlockYAt(target.getBlockX(), target.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES);
				Fx.particle(world, Particle.REVERSE_PORTAL, at, 20, 0.3, 0.5, 0.3, 0.05);
				mob.teleport(new Location(world, target.getX(), top + 1, target.getZ(), mob.getLocation().getYaw(), 0));
				Fx.sound(world, Fx.feet(mob), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5F, 1.4F);
			} else {
				mob.knockback(1.2, c.getX() - at.getX(), c.getZ() - at.getZ());
				Vector spark = c.clone().add(at.clone().subtract(c).normalize().multiply(r));
				Fx.particle(world, Particle.ELECTRIC_SPARK, spark, 8, 0.2, 0.3, 0.2, 0.1);
			}
		}
		for (Entity e : world.getNearbyEntities(area, e -> e instanceof Projectile p && !(p.getShooter() instanceof Player))) {
			Vector at = e.getLocation().toVector();
			if (at.distance(c) <= r + 0.5) {
				Fx.particle(world, Particle.ELECTRIC_SPARK, at, 10, 0.1, 0.1, 0.1, 0.2);
				Fx.sound(world, at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1.0F, 1.5F);
				e.remove();
			}
		}
	}

	// ------------------------------------------------------------------ menu

	private void open(Player player) {
		if (menu == null) {
			menu = new GeneratorMenu();
		}
		menu.draw();
		menu.open(player);
	}

	private void refreshMenu() {
		if (menu != null) {
			if (menu.getInventory().getViewers().isEmpty()) {
				menu = null;
			} else {
				menu.draw();
			}
		}
	}

	private static String number(long n) {
		return NumberFormat.getIntegerInstance(Locale.FRANCE).format(n);
	}

	/** L'autonomie restante, en clair. */
	private Component autonomy() {
		long ticks = fuel * (long) TICKS_PER_EVOLIUM + burn;
		long minutes = ticks / 1200;
		long hours = minutes / 60;
		long days = hours / 24;
		if (days >= 365) {
			return Lang.tr("gui.evotool.shield.time.years", number(days / 365));
		}
		if (days > 0) {
			return Lang.tr("gui.evotool.shield.time.days", days, hours % 24);
		}
		if (hours > 0) {
			return Lang.tr("gui.evotool.shield.time.hours", hours, minutes % 60);
		}
		return Lang.tr("gui.evotool.shield.time.minutes", minutes);
	}

	private final class GeneratorMenu extends Menu {
		private static final int FUEL = 13;

		GeneratorMenu() {
			super(3, Lang.tr("block.evotool.shield_generator"));
			freeSlot(FUEL);
		}

		void draw() {
			for (int i = 0; i < inventory.getSize(); i++) {
				if (i != FUEL) {
					inventory.setItem(i, null);
				}
			}
			List<Component> info = new ArrayList<>();
			info.add(Lang.tr("gui.evotool.shield.fuel", number(fuel)).color(NamedTextColor.AQUA));
			info.add(Lang.tr("gui.evotool.shield.autonomy", autonomy()).color(NamedTextColor.GRAY));
			info.add(Lang.tr("gui.evotool.shield.radius", radius).color(NamedTextColor.GRAY));
			Component status = !enabled ? Lang.tr("gui.evotool.shield.status_off").color(NamedTextColor.RED)
					: isActive() ? Lang.tr("gui.evotool.shield.status_on").color(NamedTextColor.GREEN)
					: Lang.tr("gui.evotool.shield.status_empty").color(NamedTextColor.GOLD);
			inventory.setItem(4, icon(Material.BEACON, status, info));
			radiusButton(10, -10, Material.RED_CONCRETE);
			radiusButton(11, -1, Material.PINK_CONCRETE);
			radiusButton(15, 1, Material.LIME_CONCRETE);
			radiusButton(16, 10, Material.GREEN_CONCRETE);
			button(22, icon(enabled ? Material.REDSTONE_TORCH : Material.LEVER,
					Lang.tr(enabled ? "gui.evotool.shield.on" : "gui.evotool.shield.off").color(enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
					List.of(Lang.tr("plugin.evotool.shield.toggle").color(NamedTextColor.GRAY))), e -> {
						enabled = !enabled;
						click(e.getWhoClicked());
						changedState();
					});
			inventory.setItem(12, icon(Material.LIGHT_BLUE_STAINED_GLASS_PANE, Lang.tr("plugin.evotool.shield.fuel_slot").color(NamedTextColor.AQUA),
					List.of(Lang.tr("tooltip.evotool.shield.hint").color(NamedTextColor.GRAY))));
			inventory.setItem(14, inventory.getItem(12));
			fill();
		}

		private void radiusButton(int slot, int delta, Material material) {
			button(slot, icon(material, Component.text((delta > 0 ? "+" : "") + delta, delta > 0 ? NamedTextColor.GREEN : NamedTextColor.RED),
					List.of(Lang.tr("gui.evotool.shield.radius", radius).color(NamedTextColor.GRAY))), e -> {
						radius = clamp(radius + delta);
						click(e.getWhoClicked());
						changedState();
					});
		}

		private void click(org.bukkit.entity.HumanEntity who) {
			if (who instanceof Player p) {
				Fx.sound(p, Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
			}
		}

		private void changedState() {
			Machines.save(block, ShieldGenerator.this);
			draw();
		}

		@Override
		protected boolean accepts(int slot, ItemStack stack) {
			return fuelValue(stack) > 0;
		}

		/** La case avale l'évolium posé dedans, jusqu'à 1 milliard ; le reste est rendu. */
		@Override
		protected void changed() {
			ItemStack stack = inventory.getItem(FUEL);
			int value = fuelValue(stack);
			if (stack == null || value <= 0) {
				return;
			}
			long room = (CAPACITY - fuel) / value;
			int taken = (int) Math.min(stack.getAmount(), room);
			if (taken > 0) {
				fuel += (long) taken * value;
				stack.setAmount(stack.getAmount() - taken);
				inventory.setItem(FUEL, stack.getAmount() > 0 ? stack : null);
				Fx.sound(block.getWorld(), center(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8F, 1.6F);
				changedState();
			}
		}

		@Override
		protected void closed(Player player) {
			ItemStack rest = inventory.getItem(FUEL);
			if (rest != null && !rest.isEmpty()) {
				inventory.setItem(FUEL, null);
				for (ItemStack left : player.getInventory().addItem(rest).values()) {
					player.getWorld().dropItem(player.getLocation(), left);
				}
			}
		}

		void close() {
			for (org.bukkit.entity.HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
				viewer.closeInventory();
			}
		}
	}
}
