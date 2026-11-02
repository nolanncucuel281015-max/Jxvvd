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

echo "### client gametest : délais"
for J in $(echo "$ALL" | tr ' ' '\n' | grep -i 'fabric-client-gametest' | grep -v sources | head -1); do
	echo "jar: $J"
	for c in $(unzip -l "$J" | awk '{print $4}' | grep '\.class$' | grep -iE 'World|Context|Timeout|Constants' | head -20); do
		cls=${c%.class}; cls=${cls//\//.}
		javap -c -p -constants -cp "$J" "$cls" 2>/dev/null | grep -iE "timeout|Timeout loading|ldc|sipush|bipush|class |static final" | grep -iE "timeout|class |TIMEOUT|loading world" | head -12
	done
done
