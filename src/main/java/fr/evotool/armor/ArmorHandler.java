package fr.evotool.armor;

import fr.evotool.EvoTool;
import fr.evotool.evo.EvoProgression;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** XP de l'armure quand on prend des coups, et application des bonus d'ensemble. */
public final class ArmorHandler {
	/** Une grosse chute ou une explosion ne doit pas tout faire monter d'un coup. */
	private static final int MAX_XP_PER_HIT = 20;
	private static final Identifier HEALTH_MODIFIER_ID = EvoTool.id("armor_hearts");

	/** Effets que l'armure a posés sur chaque joueur, pour savoir quoi retirer quand on l'enlève. */
	private static final Map<UUID, Map<RegistryEntry<StatusEffect>, Integer>> APPLIED = new HashMap<>();

	private ArmorHandler() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!(entity instanceof ServerPlayerEntity player) || blocked || player.isCreative() || baseDamageTaken <= 0
					|| source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return;
			}
			int xp = MathHelper.clamp(Math.round(baseDamageTaken), 1, MAX_XP_PER_HIT);
			gainArmorXp(player, xp, Math.max(1, Math.round(baseDamageTaken)));
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTicks() % 20 != 0) {
				return;
			}
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				applySetBonus(player);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> APPLIED.remove(handler.getPlayer().getUuid()));
	}

	private static void gainArmorXp(ServerPlayerEntity player, int xp, int damage) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int before = ArmorSet.setLevel(player);
		boolean anyLevelUp = false;
		ItemStack slowest = ItemStack.EMPTY;
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack stack = player.getEquippedStack(slot);
			if (!ArmorSet.isEvolvingArmor(stack, slot)) {
				continue;
			}
			int oldLevel = EvoProgression.get(stack).level();
			anyLevelUp |= EvoProgression.gainXp(stack, xp, damage, world) != oldLevel;
			if (slowest.isEmpty() || EvoProgression.get(stack).level() < EvoProgression.get(slowest).level()) {
				slowest = stack;
			}
		}
		if (slowest.isEmpty()) {
			return;
		}

		int after = ArmorSet.setLevel(player);
		if (anyLevelUp) {
			world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
		}
		for (int level = before + 1; level <= after; level++) {
			announce(player, level);
		}

		int pieceLevel = EvoProgression.get(slowest).level();
		int needed = EvoProgression.kindOf(slowest).profile().curve().xpToNext(pieceLevel);
		player.sendMessage(needed == 0
				? Text.translatable("message.evotool.armor_actionbar_max").formatted(Formatting.GOLD)
				: Text.translatable("message.evotool.armor_actionbar", after, slowest.getName(), pieceLevel,
						EvoProgression.get(slowest).xp(), needed).formatted(Formatting.GRAY), true);
	}

	private static void announce(ServerPlayerEntity player, int setLevel) {
		player.sendMessage(Text.translatable("message.evotool.armor_level", setLevel).formatted(Formatting.BLUE, Formatting.BOLD), false);
		if (setLevel % EvoProgression.LEVELS_PER_STAGE == 0) {
			player.sendMessage(Text.translatable("message.evotool.new_form", EvoProgression.stageName(setLevel),
					EvoProgression.stage(setLevel), EvoProgression.MAX_STAGE).formatted(Formatting.AQUA, Formatting.BOLD), false);
		}
		for (Text unlock : ArmorSet.unlocksAt(setLevel)) {
			player.sendMessage(Text.translatable("message.evotool.unlocked", unlock).formatted(Formatting.GOLD), false);
		}
	}

	/** Pose / retire les effets permanents et les cœurs en plus selon le niveau d'ensemble porté. */
	private static void applySetBonus(ServerPlayerEntity player) {
		int setLevel = ArmorSet.setLevel(player);
		Map<RegistryEntry<StatusEffect>, Integer> desired = ArmorSet.effects(setLevel);
		Map<RegistryEntry<StatusEffect>, Integer> applied = APPLIED.computeIfAbsent(player.getUuid(), uuid -> new HashMap<>());

		Set<RegistryEntry<StatusEffect>> all = new HashSet<>(applied.keySet());
		all.addAll(desired.keySet());
		for (RegistryEntry<StatusEffect> effect : all) {
			Integer want = desired.get(effect);
			Integer had = applied.get(effect);
			StatusEffectInstance current = player.getStatusEffect(effect);
			// On ne retire que l'effet infini qu'on a posé nous-mêmes (pas une potion bue entre-temps).
			if (had != null && !had.equals(want) && current != null && current.isInfinite() && current.getAmplifier() == had) {
				player.removeStatusEffect(effect);
				current = null;
			}
			if (want != null && (current == null || current.getAmplifier() < want
					|| (current.getAmplifier() == want && !current.isInfinite()))) {
				player.addStatusEffect(new StatusEffectInstance(effect, StatusEffectInstance.INFINITE, want, true, false, true));
			}
		}
		applied.clear();
		applied.putAll(desired);

		EntityAttributeInstance maxHealth = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
		if (maxHealth != null) {
			double want = ArmorSet.bonusHealth(setLevel);
			EntityAttributeModifier current = maxHealth.getModifier(HEALTH_MODIFIER_ID);
			double have = current == null ? 0.0 : current.value();
			if (have != want) {
				maxHealth.removeModifier(HEALTH_MODIFIER_ID);
				if (want > 0) {
					maxHealth.addPersistentModifier(new EntityAttributeModifier(HEALTH_MODIFIER_ID, want,
							EntityAttributeModifier.Operation.ADD_VALUE));
				}
				if (player.getHealth() > player.getMaxHealth()) {
					player.setHealth(player.getMaxHealth());
				}
			}
		}
	}
}
