package fr.evotool.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Données stockées sur la pioche évolutive.
 *
 * @param level       niveau actuel (0 à {@link fr.evotool.mining.EvoProgression#MAX_LEVEL})
 * @param xp          XP accumulée dans le niveau actuel
 * @param mode        index du {@link fr.evotool.mining.EvoMode} sélectionné
 * @param autosmelt   auto-smelt activé (si débloqué)
 * @param totalBlocks nombre total de blocs cassés
 */
public record EvoData(int level, int xp, int mode, boolean autosmelt, long totalBlocks) {
	public static final EvoData DEFAULT = new EvoData(0, 0, 0, true, 0L);

	public static final Codec<EvoData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(EvoData::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(EvoData::xp),
			Codec.INT.optionalFieldOf("mode", 0).forGetter(EvoData::mode),
			Codec.BOOL.optionalFieldOf("autosmelt", true).forGetter(EvoData::autosmelt),
			Codec.LONG.optionalFieldOf("total_blocks", 0L).forGetter(EvoData::totalBlocks)
	).apply(instance, EvoData::new));

	private static final PacketCodec<ByteBuf, Boolean> BOOL_CODEC =
			PacketCodecs.VAR_INT.xmap(i -> i != 0, b -> b ? 1 : 0);

	public static final PacketCodec<ByteBuf, EvoData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, EvoData::level,
			PacketCodecs.VAR_INT, EvoData::xp,
			PacketCodecs.VAR_INT, EvoData::mode,
			BOOL_CODEC, EvoData::autosmelt,
			PacketCodecs.VAR_LONG, EvoData::totalBlocks,
			EvoData::new
	);

	public EvoData withLevelXp(int newLevel, int newXp, long newTotal) {
		return new EvoData(newLevel, newXp, mode, autosmelt, newTotal);
	}

	public EvoData withMode(int newMode) {
		return new EvoData(level, xp, newMode, autosmelt, totalBlocks);
	}

	public EvoData withAutosmelt(boolean enabled) {
		return new EvoData(level, xp, mode, enabled, totalBlocks);
	}
}
