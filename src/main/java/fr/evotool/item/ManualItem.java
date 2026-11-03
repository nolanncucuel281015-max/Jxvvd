package fr.evotool.item;

import fr.evotool.ClientHooks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/** Le manuel d'Evo Tool : clic droit pour l'ouvrir (11 volumes, avec images). */
public class ManualItem extends Item {
	public ManualItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (world.isClient()) {
			user.playSound(SoundEvents.ITEM_BOOK_PAGE_TURN, 1.0F, 1.0F);
			ClientHooks.openManual.run();
		}
		return ActionResult.SUCCESS;
	}
}
