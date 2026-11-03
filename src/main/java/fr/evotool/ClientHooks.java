package fr.evotool;

import java.util.function.Consumer;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Ce que le code commun demande au client (ouvrir un écran, lancer une toile…). Le client remplace ces actions au
 * démarrage ; sur un serveur, elles ne font rien.
 */
public final class ClientHooks {
	/** Ouvrir le manuel. */
	public static Runnable openManual = () -> {
	};
	/** Lancer (ou lâcher) la toile du gant de Spider-Man pour notre joueur. */
	public static Consumer<PlayerEntity> toggleWeb = player -> {
	};
	/** Pouvoir « traction » du gant visant un bloc : notre joueur file vers lui au bout de sa toile. */
	public static Consumer<PlayerEntity> zipWeb = player -> {
	};

	private ClientHooks() {
	}
}
