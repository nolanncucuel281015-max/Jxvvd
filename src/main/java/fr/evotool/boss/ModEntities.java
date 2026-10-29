package fr.evotool.boss;

import fr.evotool.EvoTool;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

public final class ModEntities {
	public static final RegistryKey<EntityType<?>> BELL_BOSS_KEY = RegistryKey.of(RegistryKeys.ENTITY_TYPE, EvoTool.id("bell_boss"));
	/** Le Slime Cloche, boss de la dimension évolutive (taille de base 2x2, agrandie selon la forme). */
	public static final EntityType<BellBossEntity> BELL_BOSS = Registry.register(Registries.ENTITY_TYPE, BELL_BOSS_KEY,
			EntityType.Builder.create(BellBossEntity::new, SpawnGroup.MONSTER)
					.dimensions(2.0F, 2.0F)
					.makeFireImmune()
					.maxTrackingRange(10)
					.build(BELL_BOSS_KEY));

	private ModEntities() {
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(BELL_BOSS, BellBossEntity.createAttributes());
	}
}
