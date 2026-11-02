package fr.evotool.combiner;

import fr.evotool.EvoTool;
import fr.evotool.element.Element;
import fr.evotool.element.Elements;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

/** Menu du combineur : équipement + élément → objet élémentaire, et le bouton « Combiner ». */
public class ElementalCombinerScreenHandler extends ScreenHandler {
	public static final ScreenHandlerType<ElementalCombinerScreenHandler> TYPE = Registry.register(Registries.SCREEN_HANDLER,
			EvoTool.id("elemental_combiner"),
			new ExtendedScreenHandlerType<>(ElementalCombinerScreenHandler::new, BlockPos.PACKET_CODEC));

	public static final int BUTTON_COMBINE = 0;
	/** Panneau des ingrédients : bouton 100 + élément = poser son ingrédient (pris dans l'inventaire). */
	public static final int BUTTON_INGREDIENT = 100;
	public static final int EQUIPMENT_X = 27;
	public static final int ELEMENT_X = 76;
	public static final int OUTPUT_X = 134;
	public static final int SLOTS_Y = 24;

	private final Inventory items;
	private final PropertyDelegate properties;
	private final ElementalCombinerBlockEntity combiner;

	/** Côté client : les valeurs arrivent par les propriétés. */
	public ElementalCombinerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleInventory(3), new ArrayPropertyDelegate(2), null);
	}

	private final PlayerInventory playerInventory;

	public ElementalCombinerScreenHandler(int syncId, PlayerInventory playerInventory, Inventory items, PropertyDelegate properties,
			ElementalCombinerBlockEntity combiner) {
		super(TYPE, syncId);
		this.playerInventory = playerInventory;
		this.items = items;
		this.properties = properties;
		this.combiner = combiner;
		addSlot(new Slot(items, ElementalCombinerBlockEntity.EQUIPMENT_SLOT, EQUIPMENT_X, SLOTS_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return Elements.canCombine(stack.copyWithCount(1));
			}

			@Override
			public int getMaxItemCount() {
				return 1;
			}
		});
		addSlot(new Slot(items, ElementalCombinerBlockEntity.ELEMENT_SLOT, ELEMENT_X, SLOTS_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return Element.fromCatalyst(stack) != null;
			}
		});
		addSlot(new Slot(items, ElementalCombinerBlockEntity.OUTPUT_SLOT, OUTPUT_X, SLOTS_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return false;
			}
		});
		addPlayerSlots(playerInventory, 8, 84);
		addProperties(properties);
	}

	public static void init() {
	}

	/** Avancement de la fusion (-1 au repos). */
	public int progress() {
		return properties.get(0);
	}

	/** L'élément de la fusion en cours, sinon celui que donnerait la case de l'élément. */
	public Element element() {
		int working = properties.get(1);
		if (working > 0 && working <= Element.values().length) {
			return Element.values()[working - 1];
		}
		return Element.fromCatalyst(items.getStack(ElementalCombinerBlockEntity.ELEMENT_SLOT));
	}

	/** Les cases permettent-elles de lancer la fusion (côté client, pour le bouton) ? */
	public boolean ready() {
		return progress() < 0 && Elements.canAccept(items.getStack(ElementalCombinerBlockEntity.EQUIPMENT_SLOT),
				Element.fromCatalyst(items.getStack(ElementalCombinerBlockEntity.ELEMENT_SLOT)))
				&& items.getStack(ElementalCombinerBlockEntity.OUTPUT_SLOT).isEmpty();
	}

	public ItemStack equipment() {
		return items.getStack(ElementalCombinerBlockEntity.EQUIPMENT_SLOT);
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (id == BUTTON_COMBINE && combiner != null) {
			return combiner.start();
		}
		int index = id - BUTTON_INGREDIENT;
		if (index >= 0 && index < Element.values().length) {
			return putIngredient(Element.values()[index]);
		}
		return false;
	}

	/** Pose dans la case de l'élément un ingrédient de cet élément pris dans l'inventaire du joueur. */
	private boolean putIngredient(Element element) {
		ItemStack slot = items.getStack(ElementalCombinerBlockEntity.ELEMENT_SLOT);
		if (!slot.isEmpty() && Element.fromCatalyst(slot) != element) {
			// un autre élément est déjà là : il retourne dans l'inventaire
			if (!playerInventory.insertStack(slot)) {
				return false;
			}
			items.setStack(ElementalCombinerBlockEntity.ELEMENT_SLOT, ItemStack.EMPTY);
			slot = ItemStack.EMPTY;
		}
		for (int i = 0; i < playerInventory.size(); i++) {
			ItemStack stack = playerInventory.getStack(i);
			if (!stack.isEmpty() && Element.fromCatalyst(stack) == element && (slot.isEmpty() || ItemStack.areItemsAndComponentsEqual(slot, stack))) {
				int room = slot.isEmpty() ? stack.getMaxCount() : slot.getMaxCount() - slot.getCount();
				int moved = Math.min(room, stack.getCount());
				if (moved <= 0) {
					return false;
				}
				if (slot.isEmpty()) {
					items.setStack(ElementalCombinerBlockEntity.ELEMENT_SLOT, stack.split(moved));
				} else {
					slot.increment(moved);
					stack.decrement(moved);
					items.markDirty();
				}
				playerInventory.markDirty();
				sendContentUpdates();
				return true;
			}
		}
		return false;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index < 3) {
			if (!insertItem(stack, 3, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (Element.fromCatalyst(stack) != null) {
			if (!insertItem(stack, 1, 2, false)) {
				return ItemStack.EMPTY;
			}
		} else if (Elements.canCombine(stack.copyWithCount(1))) {
			if (!insertItem(stack, 0, 1, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (stack.isEmpty()) {
			slot.setStack(ItemStack.EMPTY);
		} else {
			slot.markDirty();
		}
		return original;
	}

	@Override
	public boolean canUse(PlayerEntity player) {
		return combiner == null || Inventory.canPlayerUse(combiner, player);
	}
}
