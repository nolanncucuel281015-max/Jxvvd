#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary)

for JAR in $YARN; do
	if ! unzip -l "$JAR" 'net/minecraft/client/render/model/json/ModelElementRotation$OfEuler.class' >/dev/null 2>&1; then
		continue
	fi
	echo "##### $JAR"
	for c in 'net.minecraft.client.render.model.json.ModelElementRotation$OfEuler' \
		'net.minecraft.client.render.model.json.ModelElementRotation$OfAxisAngle' \
		'net.minecraft.client.render.model.json.ModelElementRotation$RotationValue' ; do
		echo "=== $c"
		javap -p -c -cp "$JAR" "$c" 2>/dev/null | grep -v -E 'hashCode|toString|equals' | head -80
	done
	javap -p -c -cp "$JAR" net.minecraft.client.render.model.BakedQuadFactory 2>/dev/null | grep -E 'Method|rotat|Rotation' | head -40
	javap -p -c -cp "$JAR" net.minecraft.client.render.model.json.ModelElement 2>/dev/null | grep -E 'Method|Rotation' | head -20
	break
done
