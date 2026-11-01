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
	javap -p -cp "$JAR" "$1" 2>/dev/null | grep -E "${2:-.}" | head -${3:-40}
}

sigs net.minecraft.block.entity.BlockEntityType 'BlockEntityType\(|interface|Factory|create'
sigs 'net.minecraft.block.entity.BlockEntityType$BlockEntityFactory' '.'
sigs net.minecraft.block.entity.BlockEntity 'writeData|readData|markRemoved|markDirty|addComponents|readComponents|toInitialChunkDataNbt|toUpdatePacket|onBlockReplaced|getWorld|getPos|protected|public BlockEntity'
sigs net.minecraft.storage.WriteView 'put|public'
sigs net.minecraft.storage.ReadView 'get|public'
sigs net.minecraft.block.BlockWithEntity 'validateTicker|getCodec|getTicker|createBlockEntity|onSyncedBlockEvent'
sigs net.minecraft.block.Block 'onBreak|onPlaced|onStateReplaced|createCodec'
sigs net.minecraft.block.AbstractBlock 'onStateReplaced|getRenderType|onUse\('
sigs net.minecraft.screen.ScreenHandlerType 'ScreenHandlerType\(|Factory|public'
sigs 'net.minecraft.screen.ScreenHandlerType$Factory' '.'
sigs net.minecraft.screen.ScreenHandler 'addPlayerSlots|onButtonClick|quickMove|insertItem|addProperties|canUse|ScreenHandler\('
sigs net.minecraft.screen.ArrayPropertyDelegate 'public'
sigs net.minecraft.client.gui.screen.ingame.HandledScreens 'register|Provider'
sigs net.minecraft.client.gui.screen.ingame.HandledScreen 'drawBackground|drawForeground|render\(|drawMouseoverTooltip|protected int|HandledScreen\(|titleX|backgroundWidth'
sigs net.minecraft.client.gui.DrawContext 'public void (fill|drawText|drawItem|drawStackOverlay|drawBorder|drawCenteredTextWithShadow|drawStrokedRectangle)'
sigs net.minecraft.client.network.ClientPlayerInteractionManager 'clickButton'
sigs net.minecraft.entity.mob.CreeperEntity 'setFuseSpeed|isIgnited|getFuseSpeed'
sigs net.minecraft.entity.decoration.DisplayEntity 'DisplayWidth|DisplayHeight|ViewRange'
sigs net.minecraft.inventory.Inventory 'canPlayerUse'

# Accès élargis par Fabric (constructeurs de BlockEntityType / ScreenHandlerType, HandledScreens.register, DisplayEntity)
for J in $(echo "$ALL" | tr ' ' '\n' | grep -i fabric | grep -v sources); do
	unzip -p "$J" '*.accesswidener' '*.classtweaker' 2>/dev/null | grep -E 'BlockEntityType|ScreenHandlerType|HandledScreens|DisplayEntity' | sed "s|^|$(basename $J): |"
done | sort -u | head -40
