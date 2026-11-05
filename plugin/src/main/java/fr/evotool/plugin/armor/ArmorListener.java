package fr.evotool.plugin.armor;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** XP de l'armure quand on prend des coups, et application des bonus d'ensemble. */
public final class ArmorListener implements Listener {
	/** Une grosse chute ou une explosion ne doit pas tout faire monter d'un coup. */
	private static final int MAX_XP_PER_HIT = 20;
	/** Effets que l'armure a posés sur chaque joueur, pour savoir quoi retirer quand on l'enlève. */
	private static final Map<UUID, Map<PotionEffectType, Integer>> APPLIED = new HashMap<>();

	public static void tick(int tick) {
		if (tick % 20 != 0) {
			return;
		}
		for (Player player : Bukkit.getOnlinePlayers()) {
			applySetBonus(player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || player.getGameMode() == GameMode.CREATIVE || event.getDamage() <= 0
				|| event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.KILL) {
			return;
		}
		int amount = (int) Math.round(event.getDamage());
		int xp = Math.max(1, Math.min(MAX_XP_PER_HIT, amount));
		gainArmorXp(player, xp, Math.max(1, amount));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();
		Player killer = entity.getKiller();
		if (entity instanceof Enemy && killer != null && killer.getGameMode() != GameMode.CREATIVE) {
			gainAbyssKillXp(killer, Progression.xpForKill(entity));
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		APPLIED.remove(event.getPlayer().getUniqueId());
	}

	private static void gainArmorXp(Player player, int xp, int damage) {
		int before = ArmorSet.setLevel(player);
		boolean anyLevelUp = false;
		ItemStack slowest = null;
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack stack = ArmorSet.worn(player, slot);
			if (stack == null) {
				continue;
			}
			int oldLevel = EvoData.get(stack).level();
			boolean wasReady = Progression.isReadyToEvolve(stack);
			anyLevelUp |= Progression.gainXp(stack, xp, damage) != oldLevel;
			if (!wasReady && Progression.isReadyToEvolve(stack)) {
				Progression.announceReady(player, Progression.name(stack));
			}
			if (slowest == null || EvoData.get(stack).level() < EvoData.get(slowest).level()) {
				slowest = stack;
			}
		}
		if (slowest == null) {
			return;
		}
		int after = ArmorSet.setLevel(player);
		if (anyLevelUp) {
			player.getWorld().playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, SoundCategory.PLAYERS, 1.0F, 0.8F);
		}
		for (int level = before + 1; level <= after; level++) {
			announce(player, level);
		}
		int pieceLevel = EvoData.get(slowest).level();
		int needed = Progression.xpToNext(slowest);
		if (Progression.isReadyToEvolve(slowest)) {
			player.sendActionBar(Lang.tr("message.evotool.actionbar_ready", Progression.name(slowest), Progression.evolveKey())
					.color(NamedTextColor.LIGHT_PURPLE));
			return;
		}
		player.sendActionBar(needed == 0
				? Lang.tr("message.evotool.armor_actionbar_max").color(NamedTextColor.GOLD)
				: Lang.tr("message.evotool.armor_actionbar", after, Progression.name(slowest), pieceLevel, EvoData.get(slowest).xp(), needed)
						.color(NamedTextColor.GRAY));
	}

	private static void gainAbyssKillXp(Player player, int xp) {
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack stack = ArmorSet.worn(player, slot);
			if (stack != null && Progression.isAbyssal(stack)) {
				boolean wasReady = Progression.isReadyToEvolve(stack);
				Progression.gainXp(stack, xp, 0);
				if (!wasReady && Progression.isReadyToEvolve(stack)) {
					Progression.announceReady(player, Progression.name(stack));
				}
			}
		}
	}

	private static void announce(Player player, int setLevel) {
		player.sendMessage(Lang.tr("message.evotool.armor_level", setLevel).color(NamedTextColor.BLUE).decorate(TextDecoration.BOLD));
		if (setLevel % Progression.LEVELS_PER_STAGE == 0 && setLevel <= Progression.MAX_LEVEL) {
			player.sendMessage(Lang.tr("message.evotool.new_form", Progression.stageName(setLevel), Progression.stage(setLevel),
					Progression.MAX_STAGE).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
		}
		for (Component unlock : ArmorSet.unlocksAt(setLevel)) {
			player.sendMessage(Lang.tr("message.evotool.unlocked", unlock).color(NamedTextColor.GOLD));
		}
	}

	/** Pose / retire les effets permanents et les cœurs en plus selon le niveau d'ensemble porté. */
	public static void applySetBonus(Player player) {
		int setLevel = ArmorSet.setLevel(player);
		Map<PotionEffectType, Integer> desired = ArmorSet.effects(setLevel);
		Map<PotionEffectType, Integer> applied = APPLIED.computeIfAbsent(player.getUniqueId(), uuid -> new HashMap<>());
		Set<PotionEffectType> all = new HashSet<>(ArmorSet.managedEffects());
		all.addAll(applied.keySet());
		all.addAll(desired.keySet());
		for (PotionEffectType effect : all) {
			Integer want = desired.get(effect);
			Integer had = applied.get(effect);
			PotionEffect current = player.getPotionEffect(effect);
			// On ne retire que l'effet infini et discret qu'on a posé nous-mêmes (pas une potion bue entre-temps).
			boolean ours = current != null && current.isInfinite() && current.isAmbient();
			if (ours && (want == null || current.getAmplifier() != want) && (had == null || current.getAmplifier() == had)) {
				player.removePotionEffect(effect);
				current = null;
			}
			if (want != null && (current == null || current.getAmplifier() < want
					|| (current.getAmplifier() == want && !current.isInfinite()))) {
				player.addPotionEffect(new PotionEffect(effect, PotionEffect.INFINITE_DURATION, want, true, false, true));
			}
		}
		applied.clear();
		applied.putAll(desired);

		AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
		if (maxHealth != null) {
			double want = ArmorSet.bonusHealth(setLevel);
			AttributeModifier current = maxHealth.getModifier(Keys.key("armor_hearts"));
			double have = current == null ? 0.0 : current.getAmount();
			if (have != want) {
				maxHealth.removeModifier(Keys.key("armor_hearts"));
				if (want > 0) {
					maxHealth.addModifier(new AttributeModifier(Keys.key("armor_hearts"), want, AttributeModifier.Operation.ADD_NUMBER));
				}
				if (player.getHealth() > maxHealth.getValue()) {
					player.setHealth(maxHealth.getValue());
				}
			}
		}
	}
}
