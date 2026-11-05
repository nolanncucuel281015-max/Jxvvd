package fr.evotool.plugin;

import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Evo Tool en plugin Paper : le mod converti pour un serveur, les joueurs n'installent rien (le serveur leur envoie
 * le pack de ressources avec les modèles 3D et les textures du mod).
 */
public final class EvoToolPlugin extends JavaPlugin {
	private static EvoToolPlugin instance;
	/** Ce qui tourne à chaque tick (pouvoirs, projectiles, animations…), dans l'ordre d'inscription. */
	private static final List<IntConsumer> TICKERS = new ArrayList<>();

	public static EvoToolPlugin instance() {
		return instance;
	}

	public static void later(long ticks, Runnable task) {
		Bukkit.getScheduler().runTaskLater(instance, task, Math.max(1, ticks));
	}

	public static void ticker(IntConsumer tick) {
		TICKERS.add(tick);
	}

	/** Le fichier .jar du plugin (le pack de ressources et les recettes y sont lus). */
	public java.io.File jarFile() {
		return getFile();
	}

	public static void listen(Listener listener) {
		Bukkit.getPluginManager().registerEvents(listener, instance);
	}

	@Override
	public void onEnable() {
		instance = this;
		Lang.load(getClassLoader());
		// restes d'un arrêt brutal (objets d'affichage du plugin)
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (Giant.isTemporary(entity)) {
					entity.remove();
				}
			}
		}
		checkNoteBlocks();
		Modules.enable(this);
		Bukkit.getScheduler().runTaskTimer(this, () -> {
			int tick = Bukkit.getCurrentTick();
			for (IntConsumer ticker : TICKERS) {
				try {
					ticker.accept(tick);
				} catch (RuntimeException e) {
					getLogger().warning("Evo Tool : erreur dans un pouvoir (" + e + ")");
				}
			}
		}, 1L, 1L);
		getLogger().info("Evo Tool chargé : que l'évolution commence !");
		if (Boolean.getBoolean("evotool.selftest")) {
			// CI : auto-test sur un vrai serveur, puis arrêt
			later(40, () -> {
				fr.evotool.plugin.test.SelfTest.run();
				Bukkit.shutdown();
			});
		}
	}

	/** Les blocs musicaux qui portent les blocs du mod ne doivent pas changer d'état : réglage de Paper, tout de suite et pour la suite. */
	private void checkNoteBlocks() {
		java.nio.file.Path root = Bukkit.getPluginsFolder().toPath().toAbsolutePath().getParent();
		try {
			EvoToolBootstrap.NoteBlocks.enable(root);   // pour les prochains démarrages
			EvoToolBootstrap.NoteBlocks.enableRunning();   // et tout de suite
			if (!Boolean.TRUE.equals(EvoToolBootstrap.NoteBlocks.running())) {
				needsRestart = true;
				getLogger().warning("Evo Tool : redémarre le serveur une fois pour finir l'installation (blocs du mod).");
			}
		} catch (java.io.IOException e) {
			getLogger().warning("Evo Tool : paper-global.yml illisible (" + e + ")");
		}
	}

	private static boolean needsRestart;

	/** Vrai si un redémarrage est nécessaire pour finir l'installation. */
	public static boolean needsRestart() {
		return needsRestart;
	}

	@Override
	public void onDisable() {
		Modules.disable();
		TICKERS.clear();
		for (World world : Bukkit.getWorlds()) {
			for (Entity entity : world.getEntities()) {
				if (Giant.isTemporary(entity)) {
					entity.remove();
				}
			}
		}
	}
}
