package fr.evotool.item;

import fr.evotool.component.ModComponents;
import fr.evotool.element.Grimoire;
import fr.evotool.element.Spell;
import fr.evotool.element.Spells;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Grimoire élémentaire : un livre passé au combineur avec un élément (et autant d'autres qu'on veut ensuite).
 * Clic droit : lance le sort choisi. Accroupi + clic droit : passe au sort suivant.
 */
public class GrimoireItem extends Item {
	public GrimoireItem(Settings settings) {
		super(settings);
	}

	public static Grimoire contents(ItemStack stack) {
		return stack.get(ModComponents.GRIMOIRE);
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Grimoire grimoire = contents(stack);
		if (grimoire == null || grimoire.selectedSpell() == null) {
			return ActionResult.PASS;
		}
		if (user.isSneaking()) {
			// choisir le sort suivant
			Grimoire next = grimoire.next();
			stack.set(ModComponents.GRIMOIRE, next);
			if (!world.isClient()) {
				Spell spell = next.selectedSpell();
				user.sendMessage(Text.translatable("message.evotool.spell_selected", spell.element().symbol(),
						Text.translatable(spell.translationKey())).formatted(spell.element().color()), true);
				world.playSound(null, user.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
			return ActionResult.SUCCESS;
		}
		if (user.getItemCooldownManager().isCoolingDown(stack)) {
			return ActionResult.FAIL;
		}
		Spell spell = grimoire.selectedSpell();
		if (user instanceof ServerPlayerEntity player) {
			Spells.cast(player, spell);
		}
		user.getItemCooldownManager().set(stack, spell.cooldown());
		return ActionResult.SUCCESS;
	}

	@Override
	public Text getName(ItemStack stack) {
		Grimoire grimoire = contents(stack);
		if (grimoire == null || grimoire.elements().size() != 1) {
			return super.getName(stack);
		}
		// un seul élément : « Grimoire élémentaire : Feu »
		return Text.translatable("item.evotool.elemental_grimoire.of",
				Text.translatable(grimoire.elements().get(0).translationKey())).formatted(grimoire.elements().get(0).color());
	}
}
