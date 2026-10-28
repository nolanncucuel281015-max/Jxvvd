package fr.evotool.evo;

/** Implémentée par tous les outils évolutifs. */
public interface EvolvingItem {
	EvoKind evoKind();

	default EvoProfile evoProfile() {
		return evoKind().profile();
	}
}
