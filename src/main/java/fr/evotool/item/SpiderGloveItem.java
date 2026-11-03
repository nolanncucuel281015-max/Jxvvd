package fr.evotool.item;

import fr.evotool.ClientHooks;
import fr.evotool.spider.SpiderGlove;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Gant de Spider-Man. Clic droit : une toile s'accroche là où l'on vise et on s'y balance (clic droit encore ou
 * saut pour lâcher, accroupi pour remonter la toile). Accroupi + clic droit : tir de toile qui piège ce qu'il
 * touche. Tenu en main : on grimpe aux murs et on ne prend plus de dégâts de chute.
 */
public class SpiderGloveItem extends Item {
	public SpiderGloveItem(Settings settings) {
		super(settings);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		if (user.isSneaking()) {
			if (user instanceof ServerPlayerEntity player) {
				SpiderGlove.webShot(player);
			}
			return ActionResult.SUCCESS;
		}
		if (world.isClient()) {
			ClientHooks.toggleWeb.accept(user);
		}
		return ActionResult.SUCCESS;
	}
}
