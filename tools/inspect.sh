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
	echo "=== $1 ($(basename "$JAR"))"
	javap -p -cp "$JAR" "$1" 2>/dev/null | head -${2:-60}
}

calls() {
	local JAR=$(jar_of "$1")
	echo "=== appels dans $1.$2"
	javap -p -c -cp "$JAR" "$1" 2>/dev/null | awk -v n="$2" '
		/^  [a-z].*\(.*\)/ { inside = index($0, " " n "(") > 0; if (inside) print }
		inside && /(invoke|getfield|getstatic)/ { sub(/^ +[0-9]+: /, ""); print "    " $0 }
	' | head -${3:-60}
}

CLIENT=$(echo "$YARN" | tr ' ' '\n' | grep -i client | head -1)
echo "### classes joueur / avatar"
unzip -l "$CLIENT" | awk '{print $4}' | grep -E 'client/render/entity/(state/)?[A-Za-z]*(Player|Avatar)[A-Za-z]*\.class$|feature/(Cape|Elytra|Head)FeatureRenderer\.class$'

sigs net.minecraft.client.render.entity.feature.FeatureRenderer
sigs net.minecraft.client.render.entity.feature.FeatureRendererContext
sigs net.minecraft.client.render.entity.feature.CapeFeatureRenderer
calls net.minecraft.client.render.entity.feature.CapeFeatureRenderer render 50
calls net.minecraft.client.render.entity.feature.HeadFeatureRenderer render 50
for c in PlayerEntityRenderState AvatarRenderState PlayerLikeEntityRenderState BipedEntityRenderState LivingEntityRenderState EntityRenderState ItemStackRenderState; do
	sigs net.minecraft.client.render.entity.state.$c 70
done
for c in PlayerEntityRenderer AvatarEntityRenderer PlayerLikeEntityRenderer; do
	sigs net.minecraft.client.render.entity.$c 50
done
sigs net.minecraft.client.model.ModelPart 80 | grep -E 'MatrixStack|public .* (pitch|yaw|roll|originX|originY|originZ)|class'
sigs net.minecraft.client.render.entity.model.BipedEntityModel 40
sigs net.minecraft.client.render.entity.model.PlayerEntityModel 40
sigs net.minecraft.client.render.item.ItemRenderState 40
sigs net.minecraft.client.item.ItemModelManager 30
sigs net.minecraft.client.render.entity.EntityRendererFactory\$Context 40
sigs net.minecraft.client.render.command.OrderedRenderCommandQueue 40
sigs net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback 20
sigs net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback\$RegistrationHelper 20
sigs net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState 20
sigs net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey 20
