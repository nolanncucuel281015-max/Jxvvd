package fr.evotool.combiner;

import fr.evotool.block.ModBlocks;
import fr.evotool.element.Element;
import fr.evotool.element.Elements;
import fr.evotool.upgrade.GiantItem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Quaternionf;

/**
 * Le combineur élémentaire. Cases : 0 = armure / arme / outil, 1 = élément, 2 = résultat. Le bouton « Combiner »
 * avale l'équipement et un élément ; pendant 3 secondes ils flottent au-dessus de l'autel, tournent l'un autour de
 * l'autre en se rapprochant, entourés des particules de l'élément, puis fusionnent dans un éclair : l'objet
 * élémentaire apparaît dans la case du résultat.
 */
public class ElementalCombinerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final int EQUIPMENT_SLOT = 0;
	public static final int ELEMENT_SLOT = 1;
	public static final int OUTPUT_SLOT = 2;
	/** Durée de la fusion (ticks). */
	public static final int DURATION = 60;
	/** Moment où les deux objets se rejoignent. */
	private static final int MERGE = 46;

	private final SimpleInventory items = new SimpleInventory(3) {
		@Override
		public void markDirty() {
			super.markDirty();
			ElementalCombinerBlockEntity.this.markDirty();
		}
	};
	/** -1 : au repos ; sinon ticks depuis le début de la fusion. */
	private int progress = -1;
	private ItemStack working = ItemStack.EMPTY;
	private ItemStack catalyst = ItemStack.EMPTY;
	private Element element;
	private float spin;
	private DisplayEntity.ItemDisplayEntity shownEquipment;
	private DisplayEntity.ItemDisplayEntity shownCatalyst;

	/** Valeurs envoyées au menu : 0 = avancement de la fusion (-1 au repos), 1 = élément en cours (+1, 0 = aucun). */
	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> progress;
				case 1 -> element == null ? 0 : element.ordinal() + 1;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return 2;
		}
	};

	public ElementalCombinerBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ELEMENTAL_COMBINER_ENTITY, pos, state);
	}

	public SimpleInventory items() {
		return items;
	}

	public boolean isWorking() {
		return progress >= 0;
	}

	/** Peut-on lancer la fusion avec ce qu'il y a dans les cases ? */
	public boolean canStart() {
		return progress < 0 && Elements.canAccept(items.getStack(EQUIPMENT_SLOT), Element.fromCatalyst(items.getStack(ELEMENT_SLOT)))
				&& items.getStack(OUTPUT_SLOT).isEmpty();
	}

	/** Bouton « Combiner » : l'équipement et un élément sont avalés, l'animation commence. */
	public boolean start() {
		if (!canStart() || !(world instanceof ServerWorld serverWorld)) {
			return false;
		}
		working = items.getStack(EQUIPMENT_SLOT).copy();
		items.setStack(EQUIPMENT_SLOT, ItemStack.EMPTY);
		ItemStack stack = items.getStack(ELEMENT_SLOT);
		element = Element.fromCatalyst(stack);
		catalyst = stack.copyWithCount(1);
		if (stack.isOf(Items.WATER_BUCKET)) {
			items.setStack(ELEMENT_SLOT, new ItemStack(Items.BUCKET));   // le seau vide reste
		} else {
			stack.decrement(1);
			items.markDirty();
		}
		progress = 0;
		spin = 0;
		Vec3d c = center();
		serverWorld.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.BLOCKS, 1.0F, 1.4F);
		serverWorld.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.05);
		markDirty();
		return true;
	}

	/** Au-dessus de l'autel. */
	private Vec3d center() {
		return Vec3d.ofBottomCenter(pos).add(0, 1.15, 0);
	}

	public static void tick(World world, BlockPos pos, BlockState state, ElementalCombinerBlockEntity combiner) {
		if (combiner.progress < 0 || !(world instanceof ServerWorld serverWorld)) {
			return;
		}
		if (combiner.working.isEmpty() || combiner.element == null) {
			combiner.reset();
			return;
		}
		combiner.animate(serverWorld);
		combiner.progress++;
		if (combiner.progress >= DURATION) {
			combiner.finish(serverWorld);
		}
	}

	/** Les deux objets tournent l'un autour de l'autre en se rapprochant, puis ne font plus qu'un. */
	private void animate(ServerWorld world) {
		Vec3d c = center();
		float t = Math.min(1.0F, progress / (float) MERGE);
		spin += 0.15F + 0.5F * t;
		double radius = 0.75 * (1.0 - t);
		double lift = 0.35 * t + 0.05 * Math.sin(progress * 0.3);
		Vec3d a = c.add(Math.cos(spin) * radius, lift, Math.sin(spin) * radius);
		Vec3d b = c.add(Math.cos(spin + Math.PI) * radius, lift, Math.sin(spin + Math.PI) * radius);
		if (shownEquipment == null || shownEquipment.isRemoved()) {
			shownEquipment = GiantItem.create(world, working.copy(), ItemDisplayContext.FIXED, a, 0.0F, new Quaternionf(), 0.6F, 6);
			shownCatalyst = GiantItem.create(world, catalyst.copy(), ItemDisplayContext.FIXED, b, 0.0F, new Quaternionf(), 0.45F, 6);
			shownEquipment.setTeleportDuration(1);
			shownCatalyst.setTeleportDuration(1);
		}
		shownEquipment.setPosition(a.x, a.y, a.z);
		shownCatalyst.setPosition(b.x, b.y, b.z);
		float pulse = progress >= MERGE ? 0.15F * (float) Math.sin((progress - MERGE) * 0.9) : 0.0F;
		GiantItem.setPose(shownEquipment, new Quaternionf().rotationY(spin * 2.0F), 0.6F + pulse, 1);
		GiantItem.setPose(shownCatalyst, new Quaternionf().rotationY(-spin * 2.0F),
				progress >= MERGE ? 0.0F : 0.45F * (1.0F - 0.4F * t), 1);

		// l'élément jaillit des quatre cristaux vers le centre, et entoure les objets
		if (progress % 3 == 0) {
			for (int k = 0; k < 4; k++) {
				double cx = pos.getX() + 0.5 + (k % 2 == 0 ? 0.35 : -0.35);
				double cz = pos.getZ() + 0.5 + (k < 2 ? 0.35 : -0.35);
				double cy = pos.getY() + 0.75;
				Vec3d to = c.add(0, lift, 0).subtract(cx, cy, cz).multiply(0.12);
				world.spawnParticles(element.particle(), cx, cy, cz, 0, to.x, to.y, to.z, 1.0);
			}
		}
		world.spawnParticles(element.particle(), a.x, a.y, a.z, 1, 0.08, 0.08, 0.08, 0.01);
		world.spawnParticles(ParticleTypes.ENCHANT, c.x, c.y + lift, c.z, 3, 0.4, 0.3, 0.4, 0.3);
		if (progress % 10 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1.0F,
					0.7F + progress / (float) DURATION);
		}
		if (progress == MERGE) {
			world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.0F, 1.2F);
		}
	}

	/** L'éclair final : l'objet élémentaire est prêt. */
	private void finish(ServerWorld world) {
		Vec3d c = center().add(0, 0.35, 0);
		items.setStack(OUTPUT_SLOT, Elements.combine(working, element));
		world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 40, 0.1, 0.1, 0.1, 0.25);
		world.spawnParticles(element.particle(), c.x, c.y, c.z, 60, 0.5, 0.5, 0.5, 0.15);
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.4);
		world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		world.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 1.0F, 1.3F);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1.0F, 1.6F);
		reset();
	}

	private void reset() {
		progress = -1;
		working = ItemStack.EMPTY;
		catalyst = ItemStack.EMPTY;
		element = null;
		removeDisplays();
		markDirty();
	}

	private void removeDisplays() {
		if (shownEquipment != null) {
			GiantItem.discard(shownEquipment);
			shownEquipment = null;
		}
		if (shownCatalyst != null) {
			GiantItem.discard(shownCatalyst);
			shownCatalyst = null;
		}
	}

	/** Ce qui tombe quand on casse le combineur : ses cases et l'objet en pleine fusion. */
	public List<ItemStack> drops() {
		List<ItemStack> drops = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			if (!items.getStack(i).isEmpty()) {
				drops.add(items.getStack(i).copy());
			}
		}
		if (!working.isEmpty()) {
			drops.add(working.copy());
		}
		items.clear();
		working = ItemStack.EMPTY;
		removeDisplays();
		return drops;
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		removeDisplays();
	}

	// ------------------------------------------------------------------ menu et sauvegarde

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.evotool.elemental_combiner");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new ElementalCombinerScreenHandler(syncId, playerInventory, items, properties, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		for (int i = 0; i < items.size(); i++) {
			view.put("Slot" + i, ItemStack.OPTIONAL_CODEC, items.getStack(i));
		}
		view.putInt("Progress", progress);
		view.put("Working", ItemStack.OPTIONAL_CODEC, working);
		view.put("Catalyst", ItemStack.OPTIONAL_CODEC, catalyst);
		view.putString("Element", element == null ? "" : element.id());
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		for (int i = 0; i < items.size(); i++) {
			items.setStack(i, view.read("Slot" + i, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
		}
		progress = view.getInt("Progress", -1);
		working = view.read("Working", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		catalyst = view.read("Catalyst", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		String id = view.getString("Element", "");
		element = null;
		for (Element e : Element.values()) {
			if (e.id().equals(id)) {
				element = e;
			}
		}
	}
}
