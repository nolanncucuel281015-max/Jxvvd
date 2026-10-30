package fr.evotool.item;

import fr.evotool.evo.EvoKind;
import fr.evotool.evo.EvolvingItem;
import fr.evotool.weapon.ArcaneWeapons;
import fr.evotool.weapon.MagicKind;
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
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/** Arme magique à charges : chaque sort coûte une charge, les charges reviennent avec le temps (barre colorée). */
public class MagicWeaponItem extends Item implements EvolvingItem {
	private final MagicKind kind;

	public MagicWeaponItem(MagicKind kind, Settings settings) {
		super(settings);
		this.kind = kind;
	}

	public MagicKind magicKind() {
		return kind;
	}

	@Override
	public EvoKind evoKind() {
		return switch (kind) {
			case ARCANE_WAND -> EvoKind.ARCANE_WAND;
			case THUNDER_GAUNTLET -> EvoKind.THUNDER_GAUNTLET;
			case CELESTIAL_ORB -> EvoKind.CELESTIAL_ORB;
			case VOID_GRIMOIRE -> EvoKind.VOID_GRIMOIRE;
			case SOLAR_CHAKRAM -> EvoKind.SOLAR_CHAKRAM;
			case SPIRIT_LANTERN -> EvoKind.SPIRIT_LANTERN;
			case EVOLIUM_BLASTER -> EvoKind.EVOLIUM_BLASTER;
		};
	}

	@Override
	public ActionResult use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		if (user.getItemCooldownManager().isCoolingDown(stack)) {
			return ActionResult.FAIL;
		}
		if (world instanceof ServerWorld serverWorld && user instanceof ServerPlayerEntity player) {
			if (!ArcaneWeapons.cast(kind, stack, serverWorld, player)) {
				return ActionResult.FAIL;
			}
		}
		user.getItemCooldownManager().set(stack, 8);
		return ActionResult.SUCCESS;
	}

	@Override
	public boolean isItemBarVisible(ItemStack stack) {
		return ArcaneWeapons.charges(stack, kind) < ArcaneWeapons.maxCharges(stack, kind);
	}

	@Override
	public int getItemBarStep(ItemStack stack) {
		return MathHelper.clamp(Math.round(13.0F * ArcaneWeapons.charges(stack, kind) / ArcaneWeapons.maxCharges(stack, kind)), 0, 13);
	}

	@Override
	public int getItemBarColor(ItemStack stack) {
		return kind.color();
	}

	public static AttributeModifiersComponent attributes(MagicKind kind) {
		return AttributeModifiersComponent.builder()
				.add(EntityAttributes.ATTACK_DAMAGE,
						new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, kind.damage() - 1.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.add(EntityAttributes.ATTACK_SPEED,
						new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, kind.speed() - 4.0, EntityAttributeModifier.Operation.ADD_VALUE),
						AttributeModifierSlot.MAINHAND)
				.build();
	}
}
