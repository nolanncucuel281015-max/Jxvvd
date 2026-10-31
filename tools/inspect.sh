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
	echo "=== $1 ~ ${2:-.}"
	javap -p -cp "$JAR" "$1" 2>/dev/null | grep -E "${2:-.}" | head -40
}

# animation de l'objet qui jaillit à l'écran (comme le totem d'immortalité)
sigs net.minecraft.client.render.GameRenderer 'loat|Overlay|overlay'
sigs net.minecraft.client.gui.hud.InGameOverlayRenderer 'loat|public'
sigs net.minecraft.client.network.ClientPlayNetworkHandler 'Totem|totem|EntityStatus'
sigs net.minecraft.client.option.KeyBinding 'public'
sigs net.minecraft.client.MinecraftClient 'gameRenderer|getOverlay'
