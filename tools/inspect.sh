#!/usr/bin/env bash
# Inspection ponctuelle de Minecraft / Fabric API (lancée par la CI quand le commit contient [inspect]).
set +e
ALL=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' ! -name '*sources*' 2>/dev/null)
YARN=$(echo "$ALL" | tr ' ' '\n' | grep -i yarn | grep -v intermediary | grep -i minecraft)
echo "jars: $YARN"

# jar qui contient la classe
jar_of() {
	local path="${1//.//}.class"
	for JAR in $YARN; do
		if unzip -l "$JAR" "$path" >/dev/null 2>&1; then echo "$JAR"; return; fi
	done
}

# bytecode d'une méthode : method <classe> <nom> [lignes]
method() {
	local JAR=$(jar_of "$1")
	echo "=== $1.$2 ($JAR)"
	javap -p -c -cp "$JAR" "$1" 2>/dev/null | awk -v n="$2" '
		/^  [a-z].*\(.*\)/ { inside = index($0, " " n "(") > 0 }
		inside { print }
	' | head -${3:-120}
}

# signatures qui correspondent à un motif : sig <classe> <regex>
sig() {
	local JAR=$(jar_of "$1")
	echo "=== $1 ~ $2"
	javap -p -cp "$JAR" "$1" 2>/dev/null | grep -E "$2" | head -40
}

for c in 'net.minecraft.client.render.entity.DisplayEntityRenderer$ItemDisplayEntityRenderer' net.minecraft.client.render.entity.DisplayEntityRenderer; do
	JAR=$(jar_of "$c")
	echo "=== $c ($JAR)"
	javap -p -c -cp "$JAR" "$c" 2>/dev/null | grep -n -E '^  [a-z].*\(|ldc|RotationAxis|rotation|multiply|Billboard|getYaw|getPitch|rotationYXZ|PI' | head -60
done
