#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary)

for JAR in $YARN; do
	echo "##### $JAR"
	unzip -l "$JAR" | awk '{print $4}' | grep -i -E 'model/json/ModelElement|ElementRotation|model/json/.*Rotation' | head -20
	for c in net.minecraft.client.render.model.json.ModelElementRotation 'net.minecraft.client.render.model.json.ModelElement$Deserializer' \
		net.minecraft.client.render.model.json.ModelElement ; do
		echo "=== $c"
		javap -p -c -cp "$JAR" "$c" 2>/dev/null | grep -E 'Method|ldc|fcmp|invoke|Exception|public|private|static' | grep -v -E 'getstatic' | head -150
	done
	for c in net.minecraft.world.gen.feature.Feature net.minecraft.world.gen.feature.util.FeatureContext \
		net.minecraft.world.gen.feature.DefaultFeatureConfig net.minecraft.text.ClickEvent 'net.minecraft.text.ClickEvent$RunCommand' \
		'net.minecraft.text.ClickEvent$SuggestCommand' net.minecraft.text.HoverEvent ; do
		echo "=== $c"
		javap -cp "$JAR" "$c" 2>/dev/null | head -60
	done
	javap -cp "$JAR" net.minecraft.item.Item 2>/dev/null | grep -i -E 'ItemBar|inventoryTick|appendTooltip|use\(|finishUsing|getMaxUseTime|onStoppedUsing|usageTick|getUseAction'
	javap -cp "$JAR" net.minecraft.block.AbstractBlock 2>/dev/null | grep -i -E 'onUse|onUseWithItem|getOutlineShape|randomDisplayTick'
	javap -cp "$JAR" net.minecraft.server.world.ServerWorld 2>/dev/null | head -3
	javap -cp "$JAR" net.minecraft.entity.Entity 2>/dev/null | grep -i -E 'teleport|requestTeleport|timeUntilRegen|setNoGravity|hasNoGravity'
	javap -cp "$JAR" net.minecraft.entity.decoration.DisplayEntity\$ItemDisplayEntity 2>/dev/null | grep -i -E 'ItemStack|Transformation'
	javap -cp "$JAR" net.minecraft.entity.decoration.DisplayEntity 2>/dev/null | grep -i -E 'Glow|Brightness|ViewRange|Billboard|Shadow'
done
