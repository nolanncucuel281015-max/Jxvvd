package fr.evotool.plugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Démarrage du plugin, avant le chargement de la configuration du serveur : les blocs du mod sont portés par des
 * blocs musicaux, qui ne doivent plus changer d'état tout seuls (réglage de Paper « disable-noteblock-updates »).
 */
public final class EvoToolBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(BootstrapContext context) {
		Path root = context.getDataDirectory().toAbsolutePath().getParent().getParent();
		try {
			if (NoteBlocks.enable(root)) {
				context.getLogger().info("Evo Tool : blocs musicaux figés (paper-global.yml, block-updates.disable-noteblock-updates)");
			}
		} catch (IOException e) {
			context.getLogger().warn("Evo Tool : impossible de régler paper-global.yml (" + e + ")");
		}
	}

	/** Le réglage de Paper qui empêche les blocs musicaux de changer d'état. */
	public static final class NoteBlocks {
		private NoteBlocks() {
		}

		static Path file(Path root) {
			return root.resolve("config").resolve("paper-global.yml");
		}

		/** Le réglage en vigueur dans le serveur qui tourne (null : illisible). */
		public static Boolean running() {
			try {
				Class<?> global = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
				Object config = global.getMethod("get").invoke(null);
				Object updates = global.getField("blockUpdates").get(config);
				return updates.getClass().getField("disableNoteblockUpdates").getBoolean(updates);
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}

		/** Vrai si le réglage est actif dans le fichier. */
		public static boolean active(Path root) throws IOException {
			Path file = file(root);
			return Files.exists(file) && Files.readString(file, StandardCharsets.UTF_8).contains("disable-noteblock-updates: true");
		}

		/** Active le réglage ; vrai s'il a fallu changer le fichier. */
		public static boolean enable(Path root) throws IOException {
			Path file = file(root);
			if (!Files.exists(file)) {
				return false;
			}
			String text = Files.readString(file, StandardCharsets.UTF_8);
			if (!text.contains("disable-noteblock-updates: false")) {
				return false;
			}
			Files.writeString(file, text.replace("disable-noteblock-updates: false", "disable-noteblock-updates: true"),
					StandardCharsets.UTF_8);
			return true;
		}
	}
}
