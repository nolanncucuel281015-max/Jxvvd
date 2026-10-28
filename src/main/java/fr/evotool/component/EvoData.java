package fr.evotool.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Données stockées sur chaque outil évolutif.
 *
 * @param level niveau actuel (0 à {@link fr.evotool.evo.EvoProgression#MAX_LEVEL})
 * @param xp    XP accumulée dans le niveau actuel
 * @param mode  index du {@link fr.evotool.evo.EvoMode} sélectionné
 * @param flags pouvoirs activés (un bit par {@link fr.evotool.evo.Power}), tout est activé par défaut
 * @param count compteur affiché (blocs cassés, monstres tués ou cultures selon l'outil)
 */
public record EvoData(int level, int xp, int mode, int flags, long count) {
	public static final EvoData DEFAULT = new EvoData(0, 0, 0, -1, 0L);

	public static final Codec<EvoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(EvoData::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(EvoData::xp),
			Codec.INT.optionalFieldOf("mode", 0).forGetter(EvoData::mode),
			Codec.INT.optionalFieldOf("flags", -1).forGetter(EvoData::flags),
			Codec.LONG.optionalFieldOf("count", 0L).forGetter(EvoData::count)
	).apply(instance, EvoData::new));

	public static final PacketCodec<ByteBuf, EvoData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, EvoData::level,
			PacketCodecs.VAR_INT, EvoData::xp,
			PacketCodecs.VAR_INT, EvoData::mode,
			PacketCodecs.VAR_INT, EvoData::flags,
			PacketCodecs.VAR_LONG, EvoData::count,
			EvoData::new
	);

	public EvoData withProgress(int newLevel, int newXp, long newCount) {
		return new EvoData(newLevel, newXp, mode, flags, newCount);
	}

	public EvoData withSettings(int newMode, int newFlags) {
		return new EvoData(level, xp, newMode, newFlags, count);
	}
}
