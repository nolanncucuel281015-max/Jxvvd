package fr.evotool.dimension;

import com.mojang.serialization.Codec;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/** Génère un sanctuaire dans le chunk choisi de chaque région ({@link Sanctuary}), après la végétation. */
public class SanctuaryFeature extends Feature<DefaultFeatureConfig> {
	public SanctuaryFeature(Codec<DefaultFeatureConfig> codec) {
		super(codec);
	}

	@Override
	public boolean generate(FeatureContext<DefaultFeatureConfig> context) {
		StructureWorldAccess world = context.getWorld();
		ChunkPos chunk = new ChunkPos(context.getOrigin());
		if (!Sanctuary.isSanctuaryChunk(world.getSeed(), chunk)) {
			return false;
		}
		Sanctuary.build(world, chunk.getCenterX(), chunk.getCenterZ(), Heightmap.Type.WORLD_SURFACE_WG);
		return true;
	}
}
