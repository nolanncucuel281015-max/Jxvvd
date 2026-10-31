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

# startRiding : que veulent dire les deux booléens, et refuse-t-il les joueurs comme montures ?
method net.minecraft.entity.Entity startRiding 90 | grep -v -E '^\s+[0-9]+: (aload|iload|dup|pop)' 

# Références remappées des mixins dans le jar final (celui que le joueur installe).
for J in build/libs/*.jar; do
	case "$J" in *sources*) continue;; esac
	echo "=== refmap de $J"
	unzip -p "$J" '*refmap*.json' 2>/dev/null | python3 -c "import json,sys; d=json.load(sys.stdin); m=d.get('mappings',{}); [print(k, json.dumps(v, indent=1)) for k,v in m.items() if 'PlayerEntityMixin' in k]"
done
