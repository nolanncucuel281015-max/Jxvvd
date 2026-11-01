package fr.evotool.shield;

import fr.evotool.EvoTool;
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

/** Menu du générateur de bouclier : la case à évolium, le rayon (boutons) et marche / arrêt. */
public class ShieldGeneratorScreenHandler extends ScreenHandler {
	public static final ScreenHandlerType<ShieldGeneratorScreenHandler> TYPE = Registry.register(Registries.SCREEN_HANDLER,
			EvoTool.id("shield_generator"), new ExtendedScreenHandlerType<>(ShieldGeneratorScreenHandler::new, BlockPos.PACKET_CODEC));

	public static final int BUTTON_MINUS_TEN = 0;
	public static final int BUTTON_MINUS_ONE = 1;
	public static final int BUTTON_PLUS_ONE = 2;
	public static final int BUTTON_PLUS_TEN = 3;
	public static final int BUTTON_TOGGLE = 4;
	/** Position de la case à évolium dans le menu. */
	public static final int FUEL_SLOT_X = 152;
	public static final int FUEL_SLOT_Y = 20;

	private final PropertyDelegate properties;
	private final ShieldGeneratorBlockEntity generator;

	/** Côté client : les valeurs arrivent par les propriétés. */
	public ShieldGeneratorScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos) {
		this(syncId, playerInventory, new SimpleInventory(1), new ArrayPropertyDelegate(5), null);
	}

	public ShieldGeneratorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory input, PropertyDelegate properties,
			ShieldGeneratorBlockEntity generator) {
		super(TYPE, syncId);
		this.properties = properties;
		this.generator = generator;
		addSlot(new Slot(input, 0, FUEL_SLOT_X, FUEL_SLOT_Y) {
			@Override
			public boolean canInsert(ItemStack stack) {
				return ShieldGeneratorBlockEntity.fuelValue(stack) > 0;
			}
		});
		addPlayerSlots(playerInventory, 8, 84);
		addProperties(properties);
	}

	public static void init() {
	}

	public long fuel() {
		return ((long) (properties.get(1) & 0xFFFF) << 16) | (properties.get(0) & 0xFFFF);
	}

	public int burnTicks() {
		return properties.get(2);
	}

	public int radius() {
		return properties.get(3);
	}

	public boolean enabled() {
		return properties.get(4) != 0;
	}

	@Override
	public boolean onButtonClick(PlayerEntity player, int id) {
		if (generator != null) {
			generator.press(id);
		}
		return true;
	}

	@Override
	public ItemStack quickMove(PlayerEntity player, int index) {
		Slot slot = slots.get(index);
		if (!slot.hasStack()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getStack();
		ItemStack original = stack.copy();
		if (index == 0) {
			if (!insertItem(stack, 1, slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (ShieldGeneratorBlockEntity.fuelValue(stack) <= 0 || !insertItem(stack, 0, 1, false)) {
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
		return generator == null || Inventory.canPlayerUse(generator, player);
	}
}
