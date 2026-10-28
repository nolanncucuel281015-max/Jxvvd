package fr.evotool.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.evotool.component.EvoData;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * {@code /upgrade <outil> <niveau>} : met l'outil évolutif au niveau voulu (0 à 100).
 * {@code /upgrade armor <niveau>} règle les 4 pièces d'armure d'un coup.
 * Prend l'outil en main, sinon le premier trouvé dans l'inventaire, sinon en donne un neuf.
 * Réservée aux opérateurs (comme /give).
 */
public final class UpgradeCommand {
	private static final String[][] NAMES = {
			{"pickaxe", "pioche"},
			{"shovel", "pelle"},
			{"axe", "hache"},
			{"hoe", "houe"},
			{"sword", "epee"},
			{"mace", "masse"},
			{"helmet", "casque"},
			{"chestplate", "plastron"},
			{"leggings", "jambieres"},
			{"boots", "bottes"},
	};
	private static final EvoKind[] ARMOR = {EvoKind.HELMET, EvoKind.CHESTPLATE, EvoKind.LEGGINGS, EvoKind.BOOTS};

	private UpgradeCommand() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("upgrade")
				.requires(source -> source.getServer() != null); // TEMPORAIRE : vérification opérateur à remettre
		for (EvoKind kind : EvoKind.values()) {
			for (String name : NAMES[kind.ordinal()]) {
				root.then(CommandManager.literal(name)
						.then(CommandManager.argument("level", IntegerArgumentType.integer(0, EvoProgression.MAX_LEVEL))
								.executes(context -> upgrade(context.getSource(), kind,
										IntegerArgumentType.getInteger(context, "level")))));
			}
		}
		// /upgrade armor <niveau> : les 4 pièces d'un coup.
		for (String name : new String[] {"armor", "armure"}) {
			root.then(CommandManager.literal(name)
					.then(CommandManager.argument("level", IntegerArgumentType.integer(0, EvoProgression.MAX_LEVEL))
							.executes(context -> {
								int level = IntegerArgumentType.getInteger(context, "level");
								for (EvoKind kind : ARMOR) {
									upgrade(context.getSource(), kind, level);
								}
								return level;
							})));
		}
		dispatcher.register(root);
	}

	private static int upgrade(ServerCommandSource source, EvoKind kind, int level) throws CommandSyntaxException {
		ServerPlayerEntity player = source.getPlayerOrThrow();
		ItemStack stack = findTool(player, kind);
		boolean given = stack == null;
		if (given) {
			stack = new ItemStack(ModItems.itemFor(kind));
		}

		EvoData data = EvoProgression.get(stack);
		stack.set(ModComponents.EVO_DATA, new EvoData(level, 0, data.mode(), data.flags(), data.count()));
		// Niveau imposé : les enchantements d'évolution suivent exactement le niveau, même vers le bas.
		EvoProgression.refresh(stack, source.getRegistryManager(), true);

		if (given && !player.getInventory().insertStack(stack)) {
			player.dropItem(stack, false);
		}
		Text name = new ItemStack(ModItems.itemFor(kind)).getName();
		source.sendFeedback(() -> Text.translatable(given ? "command.evotool.upgrade.given" : "command.evotool.upgrade.done",
				name, level, EvoProgression.stageName(level)), true);
		return level;
	}

	/** Main principale, seconde main et armure portée, puis le reste de l'inventaire. */
	private static ItemStack findTool(ServerPlayerEntity player, EvoKind kind) {
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (EvoProgression.kindOf(player.getEquippedStack(slot)) == kind) {
				return player.getEquippedStack(slot);
			}
		}
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			if (EvoProgression.kindOf(inventory.getStack(i)) == kind) {
				return inventory.getStack(i);
			}
		}
		return null;
	}
}
