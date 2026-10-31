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

sig net.minecraft.entity.Entity 'noClip|isInsideWall|startRiding|getPassengerRidingPos|updatePassengerPosition|getPassengerAttachmentPos|removeAllPassengers|setOnGround|isSpaceEmpty'
sig net.minecraft.entity.player.PlayerEntity 'isMainPlayer|getAbilities|void tick'
sig net.minecraft.entity.player.PlayerAbilities 'fly|Fly|allow|Speed'
method net.minecraft.entity.player.PlayerEntity tick 60
method net.minecraft.entity.Entity move 40
method net.minecraft.entity.Entity isInsideWall 12
method net.minecraft.client.network.ClientPlayerEntity tickMovement 400 | grep -n -E 'noClip|pushOutOfBlocks|flying|isOnGround|getAbilities|isFlyingLocked|sendAbilitiesUpdate|isSpectator' | head -40
method net.minecraft.server.network.ServerPlayNetworkHandler onPlayerMove 600 | grep -n -E 'noClip|isSpectator|allowFlying|floating|isGliding|100\.0|300\.0|isPlayerNotColliding|isEntityNotColliding|moved' | head -40
for c in $(for JAR in $YARN; do unzip -l "$JAR" | awk '{print $4}' | grep -E 'Overlay.*\.class$' | grep -v '\$' ; done); do
	cls=${c%.class}; cls=${cls//\//.}
	JAR=$(jar_of "$cls")
	if javap -p -c -cp "$JAR" "$cls" 2>/dev/null | grep -q noClip; then
		echo "=== $cls utilise noClip"
		javap -p -c -cp "$JAR" "$cls" 2>/dev/null | grep -n -E 'noClip|Method|InWall|inWall' | head -30
	fi
done
sig net.minecraft.entity.decoration.DisplayEntity 'setTeleportDuration|setInterpolationDuration|setBrightness|setViewRange|setShadow|setDisplayWidth|setDisplayHeight'
sig net.minecraft.server.network.ServerPlayerEntity 'sendAbilitiesUpdate|teleportTo|detach|dismount'
sig net.minecraft.world.CollisionView 'isSpaceEmpty|canCollide'
