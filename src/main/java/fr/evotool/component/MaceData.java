package fr.evotool.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;

/**
 * Données stockées sur la masse en évolium.
 *
 * @param level     niveau actuel (0 à {@link fr.evotool.weapon.MaceProgression#MAX_LEVEL})
 * @param xp        XP accumulée dans le niveau actuel
 * @param kills     nombre total de monstres tués
 * @param shockwave onde de choc activée (si débloquée)
 * @param lightning foudre activée (si débloquée)
 */
public record MaceData(int level, int xp, long kills, boolean shockwave, boolean lightning) {
	public static final MaceData DEFAULT = new MaceData(0, 0, 0L, true, true);

	public static final Codec<MaceData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(MaceData::level),
			Codec.INT.optionalFieldOf("xp", 0).forGetter(MaceData::xp),
			Codec.LONG.optionalFieldOf("kills", 0L).forGetter(MaceData::kills),
			Codec.BOOL.optionalFieldOf("shockwave", true).forGetter(MaceData::shockwave),
			Codec.BOOL.optionalFieldOf("lightning", true).forGetter(MaceData::lightning)
	).apply(instance, MaceData::new));

	private static final PacketCodec<ByteBuf, Boolean> BOOL_CODEC =
			PacketCodecs.VAR_INT.xmap(i -> i != 0, b -> b ? 1 : 0);

	public static final PacketCodec<ByteBuf, MaceData> PACKET_CODEC = PacketCodec.tuple(
			PacketCodecs.VAR_INT, MaceData::level,
			PacketCodecs.VAR_INT, MaceData::xp,
			PacketCodecs.VAR_LONG, MaceData::kills,
			BOOL_CODEC, MaceData::shockwave,
			BOOL_CODEC, MaceData::lightning,
			MaceData::new
	);

	public MaceData withLevelXp(int newLevel, int newXp, long newKills) {
		return new MaceData(newLevel, newXp, newKills, shockwave, lightning);
	}

	public MaceData withToggles(boolean newShockwave, boolean newLightning) {
		return new MaceData(level, xp, kills, newShockwave, newLightning);
	}
}
