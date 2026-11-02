package fr.evotool.element;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/** Contenu d'un grimoire élémentaire : ses éléments (dans l'ordre où on les a ajoutés) et le sort choisi. */
public record Grimoire(List<Element> elements, int selected) {
	public static final Codec<Grimoire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Element.CODEC.listOf().fieldOf("elements").forGetter(Grimoire::elements),
			Codec.INT.optionalFieldOf("selected", 0).forGetter(Grimoire::selected)
	).apply(instance, Grimoire::new));
	public static final PacketCodec<ByteBuf, Grimoire> PACKET_CODEC = PacketCodec.tuple(
			Element.PACKET_CODEC.collect(PacketCodecs.toList()), Grimoire::elements,
			PacketCodecs.VAR_INT, Grimoire::selected,
			Grimoire::new);

	public Grimoire {
		elements = List.copyOf(elements);
	}

	/** Tous les sorts appris : deux par élément. */
	public List<Spell> spells() {
		List<Spell> spells = new ArrayList<>();
		for (Element element : elements) {
			spells.addAll(Spell.of(element));
		}
		return spells;
	}

	/** Le sort choisi (null si le grimoire est vide). */
	public Spell selectedSpell() {
		List<Spell> spells = spells();
		return spells.isEmpty() ? null : spells.get(Math.floorMod(selected, spells.size()));
	}

	public Grimoire withElement(Element element) {
		if (elements.contains(element)) {
			return this;
		}
		List<Element> more = new ArrayList<>(elements);
		more.add(element);
		return new Grimoire(more, selected);
	}

	/** Le sort suivant. */
	public Grimoire next() {
		int count = Math.max(1, spells().size());
		return new Grimoire(elements, Math.floorMod(selected + 1, count));
	}
}
