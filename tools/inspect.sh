#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary)
FABRIC=$(echo "$ALL" | tr ' ' '\n' | grep -i fabric | grep -v -i yarn)

dump() { # dump <fichier> : affiche le fichier s'il est dans un des jars
	for JAR in $ALL; do
		if unzip -l "$JAR" "$1" >/dev/null 2>&1; then echo "=== $1"; unzip -p "$JAR" "$1"; echo; return; fi
	done
	echo "=== $1 : ABSENT"
}

for f in \
	data/minecraft/dimension_type/overworld.json data/minecraft/dimension_type/the_end.json \
	data/minecraft/dimension_type/the_nether.json data/minecraft/worldgen/world_preset/normal.json \
	data/minecraft/worldgen/biome/cherry_grove.json data/minecraft/worldgen/biome/end_highlands.json \
	data/minecraft/worldgen/biome/crimson_forest.json \
	data/minecraft/worldgen/noise_settings/floating_islands.json \
	data/minecraft/worldgen/configured_feature/cherry.json data/minecraft/worldgen/placed_feature/cherry_checked.json \
	data/minecraft/worldgen/placed_feature/trees_cherry.json \
	data/minecraft/worldgen/configured_feature/amethyst_geode.json data/minecraft/worldgen/placed_feature/amethyst_geode.json \
	data/minecraft/worldgen/configured_feature/ore_diamond_small.json \
	data/minecraft/worldgen/noise/surface.json \
	assets/minecraft/blockstates/nether_portal.json assets/minecraft/models/block/nether_portal_ns.json \
	assets/minecraft/textures/block/nether_portal.png.mcmeta assets/minecraft/items/flint_and_steel.json \
	assets/minecraft/equipment/netherite.json data/minecraft/recipe/flint_and_steel.json \
	; do dump "$f"; done

for JAR in $ALL; do
	if unzip -l "$JAR" data/minecraft/worldgen/noise_settings/overworld.json >/dev/null 2>&1; then
		echo "=== overworld noise_settings (début)"; unzip -p "$JAR" data/minecraft/worldgen/noise_settings/overworld.json | head -40
		echo "=== density functions"; unzip -l "$JAR" | awk '{print $4}' | grep 'worldgen/density_function/' | tr '\n' ' '; echo
		echo "=== noises"; unzip -l "$JAR" | awk '{print $4}' | grep 'worldgen/noise/' | tr '\n' ' '; echo
		echo "=== environment attributes"; unzip -l "$JAR" | awk '{print $4}' | grep -i 'attribute' | grep -v '^net/' | head -20
		break
	fi
done

for JAR in $YARN; do
	echo "##### $JAR"
	for c in net.minecraft.block.Portal 'net.minecraft.block.Portal$Effect' net.minecraft.block.NetherPortalBlock \
		net.minecraft.world.TeleportTarget 'net.minecraft.world.TeleportTarget$PostDimensionTransition' \
		net.minecraft.item.FlintAndSteelItem net.minecraft.entity.mob.HostileEntity 'net.minecraft.entity.EntityType$Builder' \
		net.minecraft.entity.EntityDimensions net.minecraft.entity.boss.ServerBossBar net.minecraft.entity.boss.BossBar \
		net.minecraft.state.property.IntProperty 'net.minecraft.world.Heightmap$Type' \
		net.minecraft.client.render.entity.EmptyEntityRenderer net.minecraft.client.render.BlockRenderLayer \
		net.minecraft.client.render.entity.feature.FeatureRenderer net.minecraft.client.render.entity.feature.ArmorFeatureRenderer \
		net.minecraft.client.render.entity.equipment.EquipmentRenderer net.minecraft.entity.ai.goal.ActiveTargetGoal \
		net.minecraft.entity.ai.goal.MeleeAttackGoal net.minecraft.entity.ai.goal.LookAtEntityGoal \
		net.minecraft.entity.ai.goal.WanderAroundFarGoal net.minecraft.entity.ai.goal.RevengeGoal \
		net.minecraft.entity.ai.goal.GoalSelector net.minecraft.world.dimension.DimensionType ; do
		javap -p -cp "$JAR" "$c" 2>/dev/null | grep -v -E '^\s*(private static final|static \{\};)' | head -80
	done
	javap -cp "$JAR" net.minecraft.entity.Entity 2>/dev/null | grep -i -E 'portal|dimension|teleport|calculateDim|baseDim|getDimensions|setInvisible|startRiding|getPassengerList|addPassenger|isAlive|setNoGravity|getBlockPos|discard|kill\('
	javap -cp "$JAR" net.minecraft.entity.LivingEntity 2>/dev/null | grep -i -E ' damage\(|onDeath|dropLoot|dropEquipment|getBaseDimensions|tryAttack|createLivingAttributes|jump\(|setHealth|getMaxHealth|heal\(|isDead|getAttributeInstance|takeKnockback|onDamaged|tickMovement|updatePostDeath|drop\('
	javap -cp "$JAR" net.minecraft.entity.mob.MobEntity 2>/dev/null | grep -i -E 'initGoals|createMobAttributes|tryAttack|persistent|despawn|Target|goalSelector|targetSelector|onStartPathfinding|getMoveControl|getNavigation|isPersistent|setAiDisabled|lookAt|mobTick'
	javap -cp "$JAR" net.minecraft.entity.attribute.EntityAttributes 2>/dev/null | grep -E 'MAX_HEALTH|MOVEMENT_SPEED|ATTACK_DAMAGE|FOLLOW_RANGE|KNOCKBACK_RESISTANCE|ARMOR|SCALE|GRAVITY|JUMP_STRENGTH|STEP_HEIGHT|SAFE_FALL|ATTACK_KNOCKBACK'
	javap -cp "$JAR" net.minecraft.block.AbstractBlock 2>/dev/null | grep -i -E 'randomTick|hasRandomTicks|onEntityCollision|getOutlineShape|getCollisionShape|scheduledTick|getStateForNeighborUpdate|onUse|onStateReplaced|getPickStack|onSteppedOn|getAmbientOcclusion|isTransparent|isSideInvisible'
	javap -cp "$JAR" net.minecraft.block.Block 2>/dev/null | grep -i -E 'appendProperties|setDefaultState|randomDisplayTick|onBreak|onPlaced|getPlacementState|onSteppedOn'
	javap -cp "$JAR" 'net.minecraft.block.AbstractBlock$Settings' 2>/dev/null | grep -i -E 'noCollision|luminance|ticksRandomly|nonOpaque|strength|sounds|dropsNothing|pistonBehavior|replaceable|solidBlock|blockVision|suffocates|mapColor|emissive|postProcess|noBlockBreakParticles'
	javap -cp "$JAR" net.minecraft.world.WorldView 2>/dev/null | grep -i -E 'getTopY|getTopPosition'
	javap -cp "$JAR" net.minecraft.world.HeightLimitView 2>/dev/null | grep -i -E 'getTopY|getBottomY|getHeight'
	javap -cp "$JAR" net.minecraft.server.world.ServerWorld 2>/dev/null | grep -i -E 'getPlayers|spawnParticles|getRegistryKey|getEntitiesByType|playSound|getTopY|getRandomAlivePlayer|setBlockState'
	javap -cp "$JAR" net.minecraft.server.network.ServerPlayerEntity 2>/dev/null | grep -i -E 'teleport|getEntityWorld|getServerWorld|sendMessage'
	javap -cp "$JAR" net.minecraft.client.render.RenderLayer 2>/dev/null | grep -i -E 'eyes|emissive|translucent\(|getArmor'
	javap -cp "$JAR" net.minecraft.client.render.entity.EntityRendererFactory 2>/dev/null
	javap -cp "$JAR" net.minecraft.client.render.entity.EntityRendererFactories 2>/dev/null | grep -i register
	unzip -l "$JAR" | awk '{print $4}' | grep -E 'RenderCommandQueue|PlayerEntityRenderer|EquipmentModelData|BipedEntityRenderState|ModelElement' | head -20
	javap -p -c -cp "$JAR" 'net.minecraft.client.render.model.json.ModelElement$Deserializer' 2>/dev/null | grep -i -E 'light|ldc' | head -30
done

for JAR in $FABRIC; do
	for c in net/fabricmc/fabric/api/client/rendering/v1/BlockRenderLayerMap \
		net/fabricmc/fabric/api/client/rendering/v1/EntityRendererRegistry \
		net/fabricmc/fabric/api/client/rendering/v1/LivingEntityFeatureRendererRegistrationCallback \
		net/fabricmc/fabric/api/attachment/v1/AttachmentRegistry 'net/fabricmc/fabric/api/attachment/v1/AttachmentRegistry$Builder' \
		net/fabricmc/fabric/api/attachment/v1/AttachmentTarget \
		net/fabricmc/fabric/api/object/builder/v1/entity/FabricDefaultAttributeRegistry \
		net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents \
		'net/fabricmc/fabric/api/entity/event/v1/ServerEntityWorldChangeEvents$AfterPlayerChange' \
		net/fabricmc/fabric/api/entity/event/v1/ServerPlayerEvents \
		net/fabricmc/fabric/api/object/builder/v1/entity/FabricEntityTypeBuilder ; do
		if unzip -l "$JAR" "$c.class" >/dev/null 2>&1; then
			echo "### $c ($JAR)"; javap -cp "$JAR" "$(echo "$c" | tr '/' '.')" 2>/dev/null
		fi
	done
done
