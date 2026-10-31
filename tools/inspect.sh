#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary | grep -i minecraft)

jar_of() {
	local path="${1//.//}.class"
	for JAR in $YARN; do
		if unzip -l "$JAR" "$path" >/dev/null 2>&1; then echo "$JAR"; return; fi
	done
}

method() {
	local JAR=$(jar_of "$1")
	echo "=== $1.$2"
	javap -p -c -cp "$JAR" "$1" 2>/dev/null | awk -v n="$2" '
		/^  [a-z].*\(.*\)/ { inside = index($0, " " n "(") > 0 }
		inside { print }
	' | head -${3:-120}
}

# Cibles des mixins dans le jar final (noms intermédiaires, ceux du jeu installé par le joueur).
for J in build/libs/*.jar; do
	case "$J" in *sources*) continue;; esac
	echo "=== $J"
	unzip -l "$J" | grep -i -E 'refmap|mixins.json'
	for R in $(unzip -l "$J" | awk '{print $4}' | grep -i refmap); do
		echo "--- $R"
		unzip -p "$J" "$R" | head -c 3000
		echo
	done
	for c in fr.evotool.mixin.PlayerEntityMixin fr.evotool.mixin.EntityMixin; do
		echo "--- $c"
		javap -v -cp "$J" "$c" 2>/dev/null | grep -E 'Utf8 .*(class_|field_|method_|intermediary|net/minecraft)' | head -20
	done
done
