package fr.evotool.plugin;

import fr.evotool.plugin.armor.ArmorListener;
import fr.evotool.plugin.block.BlockListener;
import fr.evotool.plugin.command.Commands;
import fr.evotool.plugin.craft.AnvilListener;
import fr.evotool.plugin.craft.Recipes;
import fr.evotool.plugin.evo.Evolution;
import fr.evotool.plugin.input.InputListener;
import fr.evotool.plugin.menu.Menu;
import fr.evotool.plugin.mining.AxeLaser;
import fr.evotool.plugin.mining.MiningListener;
import fr.evotool.plugin.pack.ResourcePack;
import fr.evotool.plugin.upgrade.CelestialBow;
import fr.evotool.plugin.upgrade.MagicPowers;
import fr.evotool.plugin.upgrade.PickaxeSlam;
import fr.evotool.plugin.weapon.ArcaneWeapons;
import fr.evotool.plugin.weapon.CombatListener;
import fr.evotool.plugin.weapon.WeaponPowers;

/** Branche tous les morceaux du plugin (écouteurs, ticks). */
final class Modules {
	private Modules() {
	}

	static void enable(EvoToolPlugin plugin) {
		plugin.saveDefaultConfig();
		ResourcePack.enable(plugin);
		Recipes.register(plugin);
		Commands.register(plugin);
		Commands.add("selftest", (sender, args) -> {
			if (!sender.isOp()) {
				return;
			}
			java.util.List<String> failures = fr.evotool.plugin.test.SelfTest.run();
			sender.sendMessage(failures.isEmpty() ? "Evo Tool : auto-test réussi" : "Evo Tool : " + failures.size() + " échec(s) : " + failures);
		});
		EvoToolPlugin.listen(new Recipes());
		EvoToolPlugin.listen(new AnvilListener());
		EvoToolPlugin.listen(ResourcePack.listener());
		EvoToolPlugin.listen(new Menu.Events());
		EvoToolPlugin.listen(new BlockListener());
		EvoToolPlugin.listen(new MiningListener());
		EvoToolPlugin.listen(new ArmorListener());
		EvoToolPlugin.listen(new CombatListener());
		EvoToolPlugin.listen(new MagicPowers());
		EvoToolPlugin.listen(new InputListener());
		fr.evotool.plugin.world.OrePopulator.enable();
		fr.evotool.plugin.manual.Manual.enable();
		fr.evotool.plugin.upgrade.InfinityWings.enable();
		fr.evotool.plugin.upgrade.Burrow.enable();
		fr.evotool.plugin.spider.SpiderGlove.enable();
		fr.evotool.plugin.machine.ShieldGenerator.enable();
		fr.evotool.plugin.machine.ElementalCombiner.enable();
		fr.evotool.plugin.machine.Machines.enable();
		fr.evotool.plugin.element.ElementalPowers.enable();
		fr.evotool.plugin.element.Spells.enable();

		EvoToolPlugin.ticker(Evolution::tick);
		EvoToolPlugin.ticker(ArmorListener::tick);
		EvoToolPlugin.ticker(tick -> WeaponPowers.tick());
		EvoToolPlugin.ticker(ArcaneWeapons::tick);
		EvoToolPlugin.ticker(MagicPowers::tick);
		EvoToolPlugin.ticker(AxeLaser::tick);
		EvoToolPlugin.ticker(PickaxeSlam::tick);
		EvoToolPlugin.ticker(CelestialBow::tick);
	}

	static void disable() {
		fr.evotool.plugin.upgrade.Burrow.clear();
		fr.evotool.plugin.spider.SpiderGlove.clear();
		fr.evotool.plugin.machine.Machines.disable();
		fr.evotool.plugin.upgrade.InfinityWings.clear();
		ResourcePack.disable();
		Recipes.unregister();
		Evolution.clear();
		WeaponPowers.clear();
		ArcaneWeapons.clear();
		PickaxeSlam.clear();
		CelestialBow.clear();
	}
}
