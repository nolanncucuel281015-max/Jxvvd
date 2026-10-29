package fr.evotool.item;

import fr.evotool.EvoTool;
import fr.evotool.weapon.WeaponKind;
import fr.evotool.weapon.WeaponPowers;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Arme moddée : modèle 3D unique, passif à chaque coup et attaque spéciale au clic droit ({@link WeaponPowers}). */
public class ModdedWeaponItem extends Item {
	private static final Identifier REACH_MODIFIER_ID = EvoTool.id("weapon_reach");

	private final WeaponKind kind;

	public ModdedWeaponItem(WeaponKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public WeaponKind weaponKind() {
		return kind;
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(stack)) {
			return ActionResult.FAIL;
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			WeaponPowers.activate(kind, serverWorld, player);
		}
		user.getItemCooldownManager().set(stack, kind.cooldown());
		return ActionResult.SUCCESS;
	}

	public static AttributeModifiersComponent attributes(WeaponKind kind) {
		AttributeModifiersComponent.Builder builder = AttributeModifiersComponent.builder()
				.add(EntityAttributes.ATTACK_DAMAGE,
						new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, kind.damage() - 1.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.ATTACK_SPEED,
						new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, kind.speed() - 4.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND);
		if (kind.reach() > 0) {
			builder.add(EntityAttributes.ENTITY_INTERACTION_RANGE,
					new EntityAttributeModifier(REACH_MODIFIER_ID, kind.reach(), EntityAttributeModifier.Operation.ADD_VALUE),
					AttributeModifierSlot.MAINHAND);
		}
		return builder.build();
	}
}
