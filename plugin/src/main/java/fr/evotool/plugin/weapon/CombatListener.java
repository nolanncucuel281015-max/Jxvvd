package fr.evotool.plugin.weapon;

import fr.evotool.plugin.evo.EvoData;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.EvoProfile;
import fr.evotool.plugin.evo.Power;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.EvoliteUpgrade;
import fr.evotool.plugin.util.Fx;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/** Pouvoirs de combat (épée, lance, hache, masse, arc, armes du mod) et gain d'XP quand l'arme tue un monstre. */
public final class CombatListener implements Listener {
	private static final double LIFESTEAL_RATIO = 0.08;
	private static final double SHOCKWAVE_RATIO = 0.4;
	private static final double LIGHTNING_CHANCE = 0.15;

	/** Le joueur et l'arme évolutive qui ont porté le coup. */
	private record Wielded(Player player, ItemStack weapon, boolean melee) {
	}

	private static Wielded wielded(Entity damager) {
		if (damager instanceof Player player) {
			ItemStack main = player.getInventory().getItemInMainHand();
			EvoKind kind = Items.kindOf(main);
			if (kind != null && kind != EvoKind.BOW && kind.profile().gainsKillXp()) {
				return new Wielded(player, main, true);
			}
			return null;
		}
		if (damager instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) {
			ItemStack shotFrom = arrow.getWeapon();
			if (shotFrom != null && Items.kindOf(shotFrom) == EvoKind.BOW) {
				ItemStack bow = findBow(player);
				return bow == null ? null : new Wielded(player, bow, false);
			}
		}
		return null;
	}

	public static ItemStack findBow(Player player) {
		if (Items.kindOf(player.getInventory().getItemInMainHand()) == EvoKind.BOW) {
			return player.getInventory().getItemInMainHand();
		}
		if (Items.kindOf(player.getInventory().getItemInOffHand()) == EvoKind.BOW) {
			return player.getInventory().getItemInOffHand();
		}
		for (ItemStack stack : player.getInventory().getContents()) {
			if (stack != null && Items.kindOf(stack) == EvoKind.BOW) {
				return stack;
			}
		}
		return null;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onHit(EntityDamageByEntityEvent event) {
		if (Fx.applyingBonus() || !(event.getEntity() instanceof LivingEntity entity) || event.getFinalDamage() <= 0) {
			return;
		}
		double damage = event.getFinalDamage();
		// passifs des armes du mod (coup direct à l'arme)
		if (event.getDamager() instanceof Player attacker && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
			ItemStack main = attacker.getInventory().getItemInMainHand();
			EvoKind kind = Items.kindOf(main);
			if (kind != null) {
				WeaponKind weapon = Items.weaponKind(kind);
				MagicKind magic = Items.magicKind(kind);
				if (weapon != null) {
					WeaponPowers.afterHit(attacker, weapon, entity, damage);
				}
				if (magic != null) {
					ArcaneWeapons.afterHit(attacker, magic, main, entity);
				}
			}
		}
		Wielded wielded = wielded(event.getDamager());
		if (wielded == null) {
			return;
		}
		Player player = wielded.player();
		ItemStack weapon = wielded.weapon();
		EvoProfile profile = Items.kindOf(weapon).profile();
		EvoData data = EvoData.get(weapon);
		if (Progression.isActive(profile, data, Power.LIFESTEAL)) {
			ArcaneWeapons.heal(player, damage * LIFESTEAL_RATIO);
		}
		if (Progression.isActive(profile, data, Power.SHOCKWAVE)) {
			double radius = Progression.isActive(profile, data, Power.WIDE_SHOCKWAVE) ? 4.5 : 3.0;
			shockwave(player, entity, damage * SHOCKWAVE_RATIO, radius);
		}
		if (Progression.isActive(profile, data, Power.LIGHTNING) && !entity.isDead()
				&& ThreadLocalRandom.current().nextDouble() < LIGHTNING_CHANCE) {
			lightning(entity, data.level());
		}
		// Lance améliorée à l'évolite : chaque coup porté pendant la charge foudroie la cible.
		if (EvoliteUpgrade.has(weapon, EvoliteUpgrade.Upgrade.LIGHTNING_CHARGE) && player.isHandRaised() && !entity.isDead()) {
			lightning(entity, data.level());
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDeath(EntityDeathEvent event) {
		LivingEntity entity = event.getEntity();
		if (!(entity instanceof Enemy) || !(entity.getLastDamageCause() instanceof EntityDamageByEntityEvent cause)) {
			return;
		}
		Wielded wielded = wielded(cause.getDamager());
		if (wielded == null || wielded.player().getGameMode() == GameMode.CREATIVE) {
			return;
		}
		int countIncrement = Items.kindOf(wielded.weapon()).profile().counter() == EvoProfile.Counter.KILLS ? 1 : 0;
		Progression.addXp(wielded.weapon(), Progression.xpForKill(entity), countIncrement, wielded.player());
	}

	private static void shockwave(Player player, LivingEntity target, double damage, double radius) {
		Location l = target.getLocation();
		Fx.particle(target.getWorld(), Particle.SONIC_BOOM, l.getX(), l.getY() + 0.5, l.getZ(), 1, 0, 0, 0, 0);
		Fx.sound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.6F, 1.4F);
		for (Entity e : target.getWorld().getNearbyEntities(target.getBoundingBox().clone().expand(radius),
				e -> e != target && e != player && e instanceof Enemy && e instanceof LivingEntity l2 && !l2.isDead())) {
			Fx.hurt(player, (LivingEntity) e, damage);
		}
	}

	/** Éclair purement visuel, puis les dégâts de foudre à la main (le joueur à côté n'est pas touché). */
	public static void lightning(LivingEntity target, int level) {
		target.getWorld().strikeLightningEffect(target.getLocation());
		Fx.applyingBonusRun(() -> target.damage(4.0 + level * 0.06, DamageSource.builder(DamageType.LIGHTNING_BOLT).build()));
		target.setFireTicks(Math.max(target.getFireTicks(), 80));
	}
}
