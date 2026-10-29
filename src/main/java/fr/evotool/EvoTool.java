package fr.evotool;

import fr.evotool.armor.ArmorHandler;
import fr.evotool.block.ModBlocks;
import fr.evotool.boss.ModEntities;
import fr.evotool.command.UpgradeCommand;
import fr.evotool.component.ModComponents;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.item.ModItemGroup;
import fr.evotool.item.ModItems;
import fr.evotool.mining.AxeLaser;
import fr.evotool.mining.EvoMiningHandler;
import fr.evotool.mining.FarmingHandler;
import fr.evotool.network.ModNetworking;
import fr.evotool.upgrade.CelestialBow;
import fr.evotool.upgrade.GiantItem;
import fr.evotool.upgrade.MagicPowers;
import fr.evotool.upgrade.PickaxeSlam;
import fr.evotool.weapon.CombatHandler;
import fr.evotool.weapon.WeaponPowers;
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
		UpgradeCommand.init();
		EvoMiningHandler.init();
		FarmingHandler.init();
		AxeLaser.init();
		CombatHandler.init();
		WeaponPowers.init();
		ArmorHandler.init();
		MagicPowers.init();
		GiantItem.init();
		CelestialBow.init();
		PickaxeSlam.init();
		ModEntities.init();
		EvoDimension.init();
		if (Boolean.getBoolean("evotool.selftest")) {
			SelfTest.register();
		}
		LOGGER.info("Evo Tool chargé : que l'évolution commence !");
	}
}
