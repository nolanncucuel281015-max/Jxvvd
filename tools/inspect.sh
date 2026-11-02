#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary | grep -i minecraft)

jar_of() {
	local path="${1//.//}.class"
	for JAR in $YARN $ALL; do
		if unzip -l "$JAR" "$path" >/dev/null 2>&1; then echo "$JAR"; return; fi
	done
}

sigs() {
	local JAR=$(jar_of "$1")
	echo "=== $1 ~ ${2:-.} ($(basename "$JAR"))"
	javap -p -cp "$JAR" "$1" 2>/dev/null | grep -E "${2:-.}" | head -${3:-50}
}

echo "### classes de rendu du monde (Fabric)"
for J in $(echo "$ALL" | tr ' ' '\n' | grep -i 'fabric-rendering-v1' | grep -v sources | head -2); do
	unzip -l "$J" | awk '{print $4}' | grep -i -E 'world|Render(Context|Events)' | head -30
done
for c in net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents \
	net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext \
	net.fabricmc.fabric.api.client.rendering.v1.world.WorldTerrainRenderContext \
	net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents \
	net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext; do
	sigs "$c" '.' 60
done
for c in $(for J in $(echo "$ALL" | tr ' ' '\n' | grep -i 'fabric-rendering-v1' | grep -v sources | head -1); do unzip -l "$J" | awk '{print $4}' | grep -E 'world/WorldRenderEvents\$.*\.class$|WorldRenderEvents\$.*\.class$'; done); do
	cls=${c%.class}; cls=${cls//\//.}
	sigs "$cls" 'abstract|interface' 10
done
sigs net.minecraft.client.input.Input 'playerInput|movement|public'
sigs net.minecraft.util.PlayerInput 'forward|public'
sigs net.minecraft.client.render.Camera 'getPos|getCameraPos|public net.minecraft.util.math.Vec3d'
sigs net.minecraft.client.render.state.CameraRenderState '.'
sigs net.minecraft.entity.Entity 'velocityModified|velocityDirty|public void setVelocity'
