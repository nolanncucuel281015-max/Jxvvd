package fr.evotool.item;

import fr.evotool.dimension.RubyPortal;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Briquet d'évolium : allume un cadre de blocs de rubis en portail évolutif ; sinon, fait du feu comme un briquet. */
public class EvoliumLighterItem extends FlintAndSteelItem {
	public EvoliumLighterItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos inside = context.getBlockPos().offset(context.getSide());
		boolean server = world instanceof ServerWorld;
		if (RubyPortal.tryLight(world, inside, server)) {
			if (server) {
				world.playSound(null, inside, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.2F);
				world.playSound(null, inside, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.BLOCKS, 0.7F, 1.6F);
				PlayerEntity player = context.getPlayer();
				if (player != null) {
					context.getStack().damage(1, player,
							context.getHand() == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				}
			}
			return ActionResult.SUCCESS;
		}
		return super.useOnBlock(context);
	}
}
