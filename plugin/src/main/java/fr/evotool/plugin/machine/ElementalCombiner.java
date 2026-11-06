package fr.evotool.plugin.machine;

import fr.evotool.plugin.block.BlockListener;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.element.Element;
import fr.evotool.plugin.element.Elements;
import fr.evotool.plugin.menu.Menu;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

/**
 * Le combineur élémentaire : à gauche une armure, une arme, un outil ou un livre, au milieu un élément. Le bouton
 * « Combiner » les avale ; pendant 3 secondes ils flottent au-dessus de l'autel, tournent l'un autour de l'autre en se
 * rapprochant, entourés des particules de l'élément, puis fusionnent dans un éclair : l'objet élémentaire attend à
 * droite (clic pour le prendre).
 */
public final class ElementalCombiner implements Machines.Machine {
	public static final int DURATION = 60;
	private static final int MERGE = 46;
	private static final int EQUIPMENT = 10;
	private static final int ELEMENT = 12;
	private static final int BUTTON = 14;
	private static final int OUTPUT = 16;

	private final Block block;
	private final CombinerMenu menu = new CombinerMenu();
	private ItemStack output;
	private int progress = -1;
	private ItemStack working;
	private ItemStack catalyst;
	private Element element;
	private float spin;
	private ItemDisplay shownEquipment;
	private ItemDisplay shownCatalyst;

	private ElementalCombiner(Block block) {
		this.block = block;
		PersistentDataContainer data = Machines.stored(block);
		menu.getInventory().setItem(EQUIPMENT, Machines.getItem(data, "slot_equipment"));
		menu.getInventory().setItem(ELEMENT, Machines.getItem(data, "slot_element"));
		output = Machines.getItem(data, "slot_output");
		working = Machines.getItem(data, "working");
		catalyst = Machines.getItem(data, "catalyst");
		element = Element.byId(data.getOrDefault(Keys.ELEMENT, PersistentDataType.STRING, ""));
		progress = working != null && element != null ? data.getOrDefault(Keys.key("progress"), PersistentDataType.INTEGER, 0) : -1;
		menu.draw();
	}

	public static void enable() {
		Machines.register(CustomBlock.ELEMENTAL_COMBINER, ElementalCombiner::new);
		BlockListener.register(CustomBlock.ELEMENTAL_COMBINER, new BlockListener.Handler() {
			@Override
			public void broken(Block broken, List<ItemStack> drops) {
				ElementalCombiner combiner = (ElementalCombiner) Machines.at(broken);
				if (combiner != null) {
					drops.addAll(combiner.contents());
				}
				Machines.removed(broken);
			}

			@Override
			public boolean used(Block clicked, Player player) {
				ElementalCombiner combiner = (ElementalCombiner) Machines.at(clicked);
				if (combiner == null) {
					return false;
				}
				combiner.menu.draw();
				combiner.menu.open(player);
				return true;
			}
		});
	}

	/** Auto-test : une épée en fer + de la glace donnent une épée de glace. Null si tout va bien. */
	public static String test(Block at) {
		fr.evotool.plugin.block.Blocks.place(at, CustomBlock.ELEMENTAL_COMBINER);
		ElementalCombiner combiner = (ElementalCombiner) Machines.at(at);
		if (combiner == null) {
			return "pas de machine";
		}
		combiner.menu.getInventory().setItem(EQUIPMENT, new ItemStack(Material.IRON_SWORD));
		combiner.menu.getInventory().setItem(ELEMENT, new ItemStack(Material.PACKED_ICE, 3));
		combiner.start();
		String problem = combiner.progress != 0 ? "fusion non lancée" : null;
		for (int i = 0; i <= DURATION && problem == null; i++) {
			combiner.tick(i);
		}
		if (problem == null && (combiner.output == null || Elements.get(combiner.output) != Element.ICE)) {
			problem = "résultat : " + combiner.output;
		}
		ItemStack left = combiner.menu.getInventory().getItem(ELEMENT);
		if (problem == null && (left == null || left.getAmount() != 2)) {
			problem = "élément non consommé une fois";
		}
		List<ItemStack> drops = combiner.contents();
		Machines.removed(at);
		at.setType(Material.AIR, false);
		if (problem == null && drops.size() != 2) {
			problem = "contenu rendu : " + drops.size();
		}
		return problem;
	}

	@Override
	public void save(PersistentDataContainer data) {
		Machines.putItem(data, "slot_equipment", menu.getInventory().getItem(EQUIPMENT));
		Machines.putItem(data, "slot_element", menu.getInventory().getItem(ELEMENT));
		Machines.putItem(data, "slot_output", output);
		Machines.putItem(data, "working", working);
		Machines.putItem(data, "catalyst", catalyst);
		if (element != null) {
			data.set(Keys.ELEMENT, PersistentDataType.STRING, element.id());
		}
		data.set(Keys.key("progress"), PersistentDataType.INTEGER, progress);
	}

	@Override
	public void unload() {
		removeDisplays();
		menu.close();
	}

	/** Ce qui tombe quand on le casse : ses cases et l'objet en pleine fusion. */
	private List<ItemStack> contents() {
		menu.close();
		List<ItemStack> drops = new ArrayList<>();
		for (ItemStack stack : new ItemStack[] {menu.getInventory().getItem(EQUIPMENT), menu.getInventory().getItem(ELEMENT), output, working}) {
			if (stack != null && !stack.isEmpty()) {
				drops.add(stack.clone());
			}
		}
		menu.getInventory().setItem(EQUIPMENT, null);
		menu.getInventory().setItem(ELEMENT, null);
		output = null;
		working = null;
		removeDisplays();
		return drops;
	}

	private boolean canStart() {
		return progress < 0 && output == null
				&& Elements.canAccept(menu.getInventory().getItem(EQUIPMENT), Element.fromCatalyst(menu.getInventory().getItem(ELEMENT)));
	}

	private void start() {
		if (!canStart()) {
			return;
		}
		working = menu.getInventory().getItem(EQUIPMENT).clone();
		menu.getInventory().setItem(EQUIPMENT, null);
		ItemStack stack = menu.getInventory().getItem(ELEMENT);
		element = Element.fromCatalyst(stack);
		catalyst = stack.asOne();
		if (stack.getType() == Material.WATER_BUCKET) {
			menu.getInventory().setItem(ELEMENT, new ItemStack(Material.BUCKET));   // le seau vide reste
		} else {
			stack.setAmount(stack.getAmount() - 1);
			menu.getInventory().setItem(ELEMENT, stack.getAmount() > 0 ? stack : null);
		}
		progress = 0;
		spin = 0;
		Vector c = center();
		Fx.sound(block.getWorld(), c, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.4F);
		Fx.particle(block.getWorld(), Particle.END_ROD, c, 20, 0.3, 0.3, 0.3, 0.05);
		Machines.save(block, this);
		menu.draw();
	}

	private Vector center() {
		return block.getLocation().toVector().add(new Vector(0.5, 1.15, 0.5));
	}

	@Override
	public void tick(int tick) {
		if (progress < 0) {
			return;
		}
		if (working == null || element == null) {
			reset();
			return;
		}
		animate();
		progress++;
		if (progress >= DURATION) {
			finish();
		}
	}

	private void particle(World world, double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
		Object data = element.particleData();
		if (element.particle() == Particle.ITEM_SLIME || data == null && element.particle().getDataType() != Void.class) {
			Fx.particle(world, Particle.ITEM_SLIME, x, y, z, count, dx, dy, dz, speed);
		} else if (data != null) {
			Fx.particle(world, element.particle(), x, y, z, count, dx, dy, dz, speed, data);
		} else {
			Fx.particle(world, element.particle(), x, y, z, count, dx, dy, dz, speed);
		}
	}

	private void animate() {
		World world = block.getWorld();
		Vector c = center();
		float t = Math.min(1.0F, progress / (float) MERGE);
		spin += 0.15F + 0.5F * t;
		double radius = 0.75 * (1.0 - t);
		double lift = 0.35 * t + 0.05 * Math.sin(progress * 0.3);
		Vector a = c.clone().add(new Vector(Math.cos(spin) * radius, lift, Math.sin(spin) * radius));
		Vector b = c.clone().add(new Vector(Math.cos(spin + Math.PI) * radius, lift, Math.sin(spin + Math.PI) * radius));
		if (shownEquipment == null || !shownEquipment.isValid()) {
			shownEquipment = Giant.spawn(world, working.clone(), ItemDisplay.ItemDisplayTransform.FIXED, a, 0.0F, new Quaternionf(), 0.6F, 6);
			shownCatalyst = Giant.spawn(world, catalyst.clone(), ItemDisplay.ItemDisplayTransform.FIXED, b, 0.0F, new Quaternionf(), 0.45F, 6);
			shownEquipment.setTeleportDuration(1);
			shownCatalyst.setTeleportDuration(1);
		}
		shownEquipment.teleport(new Location(world, a.getX(), a.getY(), a.getZ()));
		shownCatalyst.teleport(new Location(world, b.getX(), b.getY(), b.getZ()));
		float pulse = progress >= MERGE ? 0.15F * (float) Math.sin((progress - MERGE) * 0.9) : 0.0F;
		if (progress > 6) {
			Giant.poseNow(shownEquipment, Giant.transformation(new Quaternionf().rotationY(spin * 2.0F), 0.6F + pulse), 1);
			Giant.poseNow(shownCatalyst, Giant.transformation(new Quaternionf().rotationY(-spin * 2.0F),
					progress >= MERGE ? 0.0F : 0.45F * (1.0F - 0.4F * t)), 1);
		}
		if (progress % 3 == 0) {
			for (int k = 0; k < 4; k++) {
				double cx = block.getX() + 0.5 + (k % 2 == 0 ? 0.35 : -0.35);
				double cz = block.getZ() + 0.5 + (k < 2 ? 0.35 : -0.35);
				double cy = block.getY() + 0.75;
				Vector to = c.clone().add(new Vector(0, lift, 0)).subtract(new Vector(cx, cy, cz)).multiply(0.12);
				particle(world, cx, cy, cz, 0, to.getX(), to.getY(), to.getZ(), 1.0);
			}
		}
		particle(world, a.getX(), a.getY(), a.getZ(), 1, 0.08, 0.08, 0.08, 0.01);
		Fx.particle(world, Particle.ENCHANT, c.getX(), c.getY() + lift, c.getZ(), 3, 0.4, 0.3, 0.4, 0.3);
		if (progress % 10 == 0) {
			Fx.sound(world, c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0F, 0.7F + progress / (float) DURATION);
		}
		if (progress == MERGE) {
			Fx.sound(world, c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0F, 1.2F);
		}
	}

	private void finish() {
		World world = block.getWorld();
		Vector c = center().add(new Vector(0, 0.35, 0));
		output = Elements.combine(working, element);
		Fx.particle(world, Particle.END_ROD, c, 40, 0.1, 0.1, 0.1, 0.25);
		particle(world, c.getX(), c.getY(), c.getZ(), 60, 0.5, 0.5, 0.5, 0.15);
		Fx.particle(world, Particle.TOTEM_OF_UNDYING, c, 30, 0.3, 0.3, 0.3, 0.4);
		Fx.particle(world, Particle.EXPLOSION, c, 1, 0, 0, 0, 0);
		Fx.sound(world, c, Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.3F);
		Fx.sound(world, c, Sound.BLOCK_BEACON_POWER_SELECT, 1.0F, 1.6F);
		reset();
	}

	private void reset() {
		progress = -1;
		working = null;
		catalyst = null;
		element = null;
		removeDisplays();
		Machines.save(block, this);
		menu.draw();
	}

	private void removeDisplays() {
		if (shownEquipment != null) {
			shownEquipment.remove();
			shownEquipment = null;
		}
		if (shownCatalyst != null) {
			shownCatalyst.remove();
			shownCatalyst = null;
		}
	}

	private final class CombinerMenu extends Menu {
		CombinerMenu() {
			super(3, Lang.tr("block.evotool.elemental_combiner"));
			freeSlot(EQUIPMENT);
			freeSlot(ELEMENT);
		}

		void draw() {
			for (int i = 0; i < inventory.getSize(); i++) {
				if (i != EQUIPMENT && i != ELEMENT) {
					inventory.setItem(i, null);
				}
			}
			ItemStack equipment = inventory.getItem(EQUIPMENT);
			Element chosen = Element.fromCatalyst(inventory.getItem(ELEMENT));
			List<Component> lore = new ArrayList<>();
			Material material;
			Component name;
			if (progress >= 0) {
				material = Material.CLOCK;
				name = Lang.tr("gui.evotool.combiner.working").color(NamedTextColor.LIGHT_PURPLE);
			} else {
				material = canStart() ? Material.NETHER_STAR : Material.BARRIER;
				name = Lang.tr("gui.evotool.combiner.combine").color(canStart() ? NamedTextColor.GREEN : NamedTextColor.GRAY);
				if (equipment == null || !Elements.canCombine(equipment)) {
					lore.add(Lang.tr("gui.evotool.combiner.need_equipment").color(NamedTextColor.GRAY));
				}
				if (chosen == null) {
					lore.add(Lang.tr("gui.evotool.combiner.need_element").color(NamedTextColor.GRAY));
				} else {
					lore.add(Lang.tr(chosen.translationKey()).color(chosen.color()));
				}
				if (output != null) {
					lore.add(Lang.tr("plugin.evotool.combiner.take_output").color(NamedTextColor.GOLD));
				}
			}
			button(BUTTON, icon(material, name, lore), e -> start());
			if (output != null) {
				button(OUTPUT, output.clone(), e -> {
					HumanEntity who = e.getWhoClicked();
					ItemStack taken = output;
					output = null;
					for (ItemStack rest : who.getInventory().addItem(taken).values()) {
						who.getWorld().dropItem(who.getLocation(), rest);
					}
					Machines.save(block, ElementalCombiner.this);
					draw();
				});
			} else {
				inventory.setItem(OUTPUT, icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
						Lang.tr("plugin.evotool.combiner.output").color(NamedTextColor.GRAY), null));
			}
			inventory.setItem(11, icon(Material.PURPLE_STAINED_GLASS_PANE, Component.text("+", NamedTextColor.LIGHT_PURPLE), null));
			inventory.setItem(13, icon(Material.PURPLE_STAINED_GLASS_PANE, Component.text("→", NamedTextColor.LIGHT_PURPLE), null));
			inventory.setItem(15, inventory.getItem(13));
			fill();
		}

		@Override
		protected boolean accepts(int slot, ItemStack stack) {
			return slot == EQUIPMENT ? Elements.canCombine(stack) : Element.fromCatalyst(stack) != null;
		}

		@Override
		protected void changed() {
			Machines.save(block, ElementalCombiner.this);
			draw();
		}

		void close() {
			for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
				viewer.closeInventory();
			}
		}
	}
}
