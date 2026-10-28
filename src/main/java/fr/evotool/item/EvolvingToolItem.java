package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import net.minecraft.item.Item;

/**
 * Pioche et épée évolutives (de simples items avec un composant d'outil / d'arme).
 * La logique d'évolution vit dans {@link fr.evotool.evo.EvoProgression}.
 */
public class EvolvingToolItem extends Item implements EvolvingItem {
	private final EvoKind kind;

	public EvolvingToolItem(EvoKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	@Override
	public EvoKind evoKind() {
		return kind;
	}
}
