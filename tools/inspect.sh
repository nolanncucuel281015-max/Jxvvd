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

# À qui le serveur envoie-t-il les changements de passagers d'une entité (et donc d'un joueur) ?
C=net.minecraft.server.network.EntityTrackerEntry
JAR=$(jar_of $C)
echo "=== $C"
javap -p -c -cp "$JAR" $C 2>/dev/null | grep -n -E '^  [a-z].*\(|EntityPassengersSetS2CPacket|receiver|sendSyncPacket|accept' | head -40
