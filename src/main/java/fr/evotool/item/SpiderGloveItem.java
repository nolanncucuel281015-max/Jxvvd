package fr.evotool.item;

import fr.evotool.ClientHooks;
import fr.evotool.EvoTool;
import fr.evotool.spider.SpiderGlove;
import fr.evotool.spider.WebPower;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;

/**
 * Gant de Spider-Man. Clic droit : une toile s'accroche là où l'on vise et on s'y balance (clic droit encore ou
 * saut pour lâcher, accroupi pour remonter la toile). Accroupi + clic droit : le pouvoir de toile choisi avec la
 * touche G (tir, traction, bombe, mur). Tenu en main : on grimpe aux murs, plus de dégâts de chute, sens
 * d'araignée, agilité, et des coups de poing qui envoient voler.
 */
public class SpiderGloveItem extends Item {
	public SpiderGloveItem(Settings settings) {
		super(settings);
	}

	/** Force d'araignée : un coup de poing à 7 dégâts qui repousse loin (2 coups par seconde). */
	public static AttributeModifiersComponent attributes() {
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.ATTACK_DAMAGE,
						new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, 6.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.ATTACK_SPEED,
						new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, -2.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.ATTACK_KNOCKBACK,
						new EntityAttributeModifier(EvoTool.id("spider_strength"), 1.5, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build();
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.isSneaking()) {
			WebPower power = WebPower.of(stack);
			if (user instanceof ServerPlayerEntity player) {
				SpiderGlove.usePower(player, power);
			} else if (world.isClient() && power == WebPower.PULL) {
				// traction vers un bloc : c'est notre client qui nous fait filer (fluide)
				ClientHooks.zipWeb.accept(user);
			}
			return ActionResult.SUCCESS;
		}
		if (world.isClient()) {
			ClientHooks.toggleWeb.accept(user);
		}
		return ActionResult.SUCCESS;
	}
}
