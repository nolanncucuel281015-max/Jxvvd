package fr.evotool.item;

import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.MaceItem;

/**
 * Masse en évolium : garde l'attaque écrasante de la mace vanilla (dégâts bonus en chute)
 * et évolue en tuant des monstres (voir {@link fr.evotool.weapon.MaceProgression}).
 */
public class EvoliumMaceItem extends MaceItem {
	public EvoliumMaceItem(Settings settings) {
		super(settings);
	}

	public static AttributeModifiersComponent createEvoliumAttributeModifiers() {
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.ATTACK_DAMAGE,
						new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, 7.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.ATTACK_SPEED,
						new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, -3.2, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build();
	}
}
