package fr.evotool;

import fr.evotool.block.ModBlocks;
import fr.evotool.component.ModComponents;
import fr.evotool.item.ModItemGroup;
import fr.evotool.item.ModItems;
import fr.evotool.mining.EvoMiningHandler;
import fr.evotool.network.ModNetworking;
import fr.evotool.weapon.MaceCombatHandler;
import fr.evotool.world.ModWorldGen;
import net.fabricmc.api.ModInitializer;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EvoTool implements ModInitializer {
	public static final String MOD_ID = "evotool";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.of(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// L'ordre compte : les composants doivent exister avant les items qui les utilisent.
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModItemGroup.init();
		ModWorldGen.init();
		ModNetworking.init();
		EvoMiningHandler.init();
		MaceCombatHandler.init();
		LOGGER.info("Evo Tool chargé : que l'évolution commence !");
	}
}
