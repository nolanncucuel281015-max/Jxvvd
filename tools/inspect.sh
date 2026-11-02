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

echo "### rendu des entités : sections prêtes ?"
sigs net.minecraft.client.render.WorldRenderer 'Ready|ntit|Section' 40
J=$(jar_of net.minecraft.client.render.WorldRenderer)
javap -c -p -cp "$J" net.minecraft.client.render.WorldRenderer 2>/dev/null | awk '/Entities|ntities\(/{p=1} p{print} /^$/{p=0}' | grep -E "Method|invoke|^  [a-z].*\(" | grep -iE "ready|section|shouldRender|getEntities|^  [a-z]" | head -60
sigs net.minecraft.entity.decoration.DisplayEntity 'Box|isRendered|shouldRender|Visibility' 20
