package fr.evotool.plugin.boss;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.dimension.EvoDimension;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Le Slime Cloche, boss de la dimension évolutive. Il évolue avec le temps : Slime Évolutif (bondit), Slime Cloche
 * (onde de son), Cloche de Guerre (invoque des slimes), Cloche Colosse (bonds écrasants), Cloche Titan (rayon
 * sonique), Cloche Divine (foudre, régénération). Chaque combat a 3 phases : à 2/3 de sa vie il se transforme et
 * entre en colère, à 1/3 c'est l'ascension dorée (colonnes de lumière autour du joueur).
 *
 * <p>Sans mod côté joueur, le boss est un ravageur invisible (il marche et frappe au corps à corps) habillé des
 * modèles 3D du mod (voir {@link BossVisual}).
 */
public final class BellBoss implements Listener {
	public static final int FORMS = 6;
	public static final int PHASES = 3;
	private static final int TRANSITION_TICKS = 50;
	private static final float[] HEIGHT = {1.0F, 1.3F, 1.5F, 2.0F, 2.4F, 2.7F};
	private static final double[] HEALTH = {120, 200, 300, 450, 650, 900};
	private static final double[] DAMAGE = {6, 9, 12, 16, 20, 26};
	private static final double[] SPEED = {0.25, 0.25, 0.27, 0.3, 0.32, 0.34};
	private static final double[] ARMOR = {2, 6, 10, 12, 16, 20};
	private static final BossBar.Color[] COLORS = {BossBar.Color.GREEN, BossBar.Color.PURPLE, BossBar.Color.RED,
			BossBar.Color.PINK, BossBar.Color.RED, BossBar.Color.YELLOW};
	private static final NamespacedKey PHASE = Keys.key("boss_phase");
	private static final NamespacedKey TIMER = Keys.key("boss_evolve_timer");
	/** Le ravageur fait 2,2 blocs de haut : l'échelle donne la taille de chaque forme. */
	private static final double BASE_HEIGHT = 2.2;

	private record Pillar(double x, double y, double z, int strikeAge) {
	}

	/** Un boss chargé : son état de combat (le reste est rangé dans ses données). */
	static final class Boss {
		final Ravager entity;
		final BossBar bar = BossBar.bossBar(Component.empty(), 1, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_6);
		final BossVisual visual;
		final List<Pillar> pillars = new ArrayList<>();
		int age;
		int hopCooldown = 20;
		int ringCooldown = 100;
		int summonCooldown = 260;
		int leapCooldown = 140;
		int beamCooldown = 200;
		int beamCharge = -1;
		int strikeCooldown = 120;
		int pillarCooldown = 60;
		boolean leaping;
		int leapStart;
		int transitionTicks;
		int ringTicks;
		int punchTicks;
		int hurtTicks;

		Boss(Ravager entity) {
			this.entity = entity;
			this.visual = new BossVisual(this);
		}

		int form() {
			return Math.max(0, Math.min(FORMS - 1, entity.getPersistentDataContainer().getOrDefault(Keys.BOSS_FORM, PersistentDataType.INTEGER, 0)));
		}

		int phase() {
			return Math.max(1, Math.min(PHASES, entity.getPersistentDataContainer().getOrDefault(PHASE, PersistentDataType.INTEGER, 1)));
		}

		boolean transforming() {
			return transitionTicks > 0;
		}
	}

	private static final Map<UUID, Boss> BOSSES = new HashMap<>();

	public static void enable() {
		EvoToolPlugin.listen(new BellBoss());
		EvoToolPlugin.ticker(BellBoss::tick);
		for (World world : Bukkit.getWorlds()) {
			for (Entity e : world.getEntities()) {
				track(e);
			}
		}
	}

	public static void disable() {
		for (Boss boss : BOSSES.values()) {
			boss.visual.discard(false);
			boss.bar.viewers().forEach(v -> ((net.kyori.adventure.audience.Audience) v).hideBossBar(boss.bar));
		}
		BOSSES.clear();
	}

	public static String formKey(int form) {
		return "boss.evotool.form." + Math.max(0, Math.min(FORMS - 1, form));
	}

	public static boolean isBoss(Entity e) {
		return e instanceof Ravager && e.getPersistentDataContainer().has(Keys.BOSS);
	}

	private static void track(Entity e) {
		if (isBoss(e) && !e.isDead() && !BOSSES.containsKey(e.getUniqueId())) {
			Boss boss = new Boss((Ravager) e);
			BOSSES.put(e.getUniqueId(), boss);
			applyStats(boss);
		}
	}

	/** Les boss vivants dans ce rayon autour de ce point. */
	public static List<Ravager> near(Location at, double radius) {
		List<Ravager> list = new ArrayList<>();
		for (Boss boss : BOSSES.values()) {
			if (boss.entity.isValid() && boss.entity.getWorld().equals(at.getWorld()) && boss.entity.getLocation().distanceSquared(at) < radius * radius) {
				list.add(boss.entity);
			}
		}
		return list;
	}

	// ------------------------------------------------------------------ apparition et formes

	public static Ravager spawn(Location at, int form) {
		World world = at.getWorld();
		Ravager ravager = world.spawn(at, Ravager.class, r -> {
			r.getPersistentDataContainer().set(Keys.BOSS, PersistentDataType.BYTE, (byte) 1);
			r.getPersistentDataContainer().set(Keys.BOSS_FORM, PersistentDataType.INTEGER, form);
			r.getPersistentDataContainer().set(PHASE, PersistentDataType.INTEGER, 1);
			r.setPersistent(true);
			r.setRemoveWhenFarAway(false);
			r.setSilent(true);
			r.setCanJoinRaid(false);
			r.setPatrolLeader(false);
			r.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false, false));
			r.customName(Lang.tr(formKey(form)));
			r.setCustomNameVisible(false);
		});
		Boss boss = new Boss(ravager);
		BOSSES.put(ravager.getUniqueId(), boss);
		applyForm(boss, form, true);
		Fx.sound(world, at.toVector(), Sound.BLOCK_BELL_USE, 4.0F, 0.5F);
		Fx.particle(world, Particle.EXPLOSION_EMITTER, at.getX(), at.getY() + 1, at.getZ(), 1, 0, 0, 0, 0);
		return ravager;
	}

	private static void setBase(LivingEntity e, Attribute attribute, double value) {
		var instance = e.getAttribute(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	static void applyForm(Boss boss, int form, boolean heal) {
		form = Math.max(0, Math.min(FORMS - 1, form));
		PersistentDataContainer pdc = boss.entity.getPersistentDataContainer();
		pdc.set(Keys.BOSS_FORM, PersistentDataType.INTEGER, form);
		if (heal) {
			pdc.set(PHASE, PersistentDataType.INTEGER, 1);
			boss.transitionTicks = 0;
			boss.pillars.clear();
		}
		boss.entity.customName(Lang.tr(formKey(form)));
		applyStats(boss);
		if (heal) {
			boss.entity.setHealth(boss.entity.getAttribute(Attribute.MAX_HEALTH).getValue());
		}
	}

	private static void applyStats(Boss boss) {
		Ravager e = boss.entity;
		int form = boss.form();
		int phase = boss.phase();
		setBase(e, Attribute.MAX_HEALTH, HEALTH[form]);
		setBase(e, Attribute.ATTACK_DAMAGE, DAMAGE[form] * (1.0 + 0.2 * (phase - 1)));
		setBase(e, Attribute.MOVEMENT_SPEED, SPEED[form] * (1.0 + 0.15 * (phase - 1)));
		setBase(e, Attribute.ARMOR, ARMOR[form] + 4.0 * (phase - 1));
		setBase(e, Attribute.FOLLOW_RANGE, 64.0);
		setBase(e, Attribute.KNOCKBACK_RESISTANCE, 0.9);
		setBase(e, Attribute.STEP_HEIGHT, 1.5);
		setBase(e, Attribute.SAFE_FALL_DISTANCE, 64.0);
		setBase(e, Attribute.SCALE, HEIGHT[form] / BASE_HEIGHT);
		updateBarName(boss);
		boss.bar.color(phase == 1 ? COLORS[form] : phase == 2 ? BossBar.Color.RED : BossBar.Color.YELLOW);
	}

	static int timer(Boss boss) {
		return boss.entity.getPersistentDataContainer().getOrDefault(TIMER, PersistentDataType.INTEGER, 0);
	}

	private static void updateBarName(Boss boss) {
		int form = boss.form();
		Component name = Lang.tr(formKey(form));
		if (form < FORMS - 1) {
			int left = Math.max(0, EvoDimension.intervalTicks() - timer(boss)) / 20;
			boss.bar.name(Lang.tr("boss.evotool.phase_timer", name, boss.phase(), String.format("%d:%02d", left / 60, left % 60))
					.decorate(TextDecoration.BOLD));
		} else {
			boss.bar.name(Lang.tr("boss.evotool.phase", name, boss.phase()).decorate(TextDecoration.BOLD));
		}
	}

	/** Le boss prend la forme suivante (commande /dimension evolve, ou au bout du minuteur). */
	public static boolean evolve(Ravager entity) {
		Boss boss = BOSSES.get(entity.getUniqueId());
		if (boss == null || boss.form() >= FORMS - 1) {
			return false;
		}
		evolve(boss, boss.form() + 1);
		return true;
	}

	private static void evolve(Boss boss, int form) {
		boss.entity.getPersistentDataContainer().set(TIMER, PersistentDataType.INTEGER, 0);
		applyForm(boss, form, true);
		World world = boss.entity.getWorld();
		Vector at = Fx.feet(boss.entity);
		Fx.sound(world, at, Sound.BLOCK_BELL_RESONATE, 5.0F, 0.5F);
		Fx.sound(world, at, Sound.ENTITY_WITHER_SPAWN, 2.0F, 1.4F);
		Fx.particle(world, Particle.EXPLOSION_EMITTER, at.getX(), at.getY() + 1, at.getZ(), 2, 0.5, 0.5, 0.5, 0);
		Fx.particle(world, Particle.END_ROD, at.getX(), at.getY() + HEIGHT[form] / 2, at.getZ(), 150, 1.5, 1.5, 1.5, 0.3);
		for (Player player : players(boss, 96)) {
			player.sendMessage(Lang.tr("message.evotool.boss_evolves", Lang.tr(formKey(form))).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
		}
	}

	private static List<Player> players(Boss boss, double radius) {
		List<Player> list = new ArrayList<>();
		for (Player p : boss.entity.getWorld().getPlayers()) {
			if (p.getLocation().distanceSquared(boss.entity.getLocation()) < radius * radius) {
				list.add(p);
			}
		}
		return list;
	}

	private static List<Player> victims(Boss boss, double radius) {
		List<Player> list = new ArrayList<>();
		for (Player p : players(boss, radius)) {
			if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE) {
				list.add(p);
			}
		}
		return list;
	}

	private static void hurt(Boss boss, Player player, double amount) {
		player.damage(amount, DamageSource.builder(DamageType.MOB_ATTACK).withCausingEntity(boss.entity).withDirectEntity(boss.entity).build());
	}

	private static void push(Entity target, double x, double y, double z) {
		target.setVelocity(target.getVelocity().add(new Vector(x, y, z)));
	}

	private static Vector away(Boss boss, Entity from) {
		Vector v = new Vector(from.getX() - boss.entity.getX(), 0, from.getZ() - boss.entity.getZ());
		return v.lengthSquared() < 1.0E-6 ? new Vector(1, 0, 0) : v.normalize();
	}

	// ------------------------------------------------------------------ chaque tick

	private static void tick(int tick) {
		for (Iterator<Boss> it = BOSSES.values().iterator(); it.hasNext(); ) {
			Boss boss = it.next();
			if (!boss.entity.isValid() || boss.entity.isDead()) {
				boss.visual.discard(false);
				hideBar(boss);
				it.remove();
				continue;
			}
			tickBoss(boss);
		}
	}

	private static void hideBar(Boss boss) {
		for (Player p : Bukkit.getOnlinePlayers()) {
			p.hideBossBar(boss.bar);
		}
	}

	private static void tickBoss(Boss boss) {
		boss.age++;
		Ravager e = boss.entity;
		World world = e.getWorld();
		int form = boss.form();
		if (boss.age % 20 == 0) {
			for (Player p : Bukkit.getOnlinePlayers()) {
				boolean near = p.getWorld().equals(world) && p.getLocation().distanceSquared(e.getLocation()) < 64 * 64;
				if (near) {
					p.showBossBar(boss.bar);
				} else {
					p.hideBossBar(boss.bar);
				}
			}
			updateBarName(boss);
			if (!e.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
				e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, true, false, false));
			}
		}
		boss.bar.progress((float) Math.max(0, Math.min(1, e.getHealth() / e.getAttribute(Attribute.MAX_HEALTH).getValue())));
		if (boss.ringTicks > 0) {
			boss.ringTicks--;
		}
		if (boss.punchTicks > 0) {
			boss.punchTicks--;
		}
		if (boss.hurtTicks > 0) {
			boss.hurtTicks--;
		}
		checkPhase(boss);
		advanceEvolution(boss);
		tickPillars(boss, form);
		if (boss.transitionTicks > 0) {
			tickTransition(boss, form);
			boss.visual.tick();
			return;
		}
		aura(boss);
		int phase = boss.phase();
		int rate = phase == 1 ? 1 : phase == 2 ? 1 + (boss.age & 1) : 2;
		LivingEntity target = e.getTarget();
		if (target != null && !target.isDead() && target.getWorld().equals(world) && target.getLocation().distanceSquared(e.getLocation()) < 48 * 48) {
			if (form <= 2 && (boss.hopCooldown -= rate) <= 0 && e.isOnGround()) {
				hop(boss, target, form);
			}
			if ((form >= 1 || phase >= 2) && (boss.ringCooldown -= rate) <= 0) {
				ring(boss, form);
			}
			if (form >= 2 && (boss.summonCooldown -= rate) <= 0) {
				summon(boss, form);
			}
			if (form >= 3 && (boss.leapCooldown -= rate) <= 0 && e.isOnGround()) {
				leap(boss, target, form);
			}
			if (form >= 4) {
				if (boss.beamCharge >= 0) {
					chargeBeam(boss, target, form);
				} else if ((boss.beamCooldown -= rate) <= 0) {
					boss.beamCharge = 30;
					Fx.sound(world, Fx.feet(e), Sound.ENTITY_WARDEN_SONIC_CHARGE, 3.0F, 1.0F);
				}
			}
			if (form >= 5 && (boss.strikeCooldown -= rate) <= 0) {
				strike(boss, target);
			}
			if (phase >= 3 && (boss.pillarCooldown -= rate) <= 0) {
				callPillars(boss, target, form);
			}
		}
		if (boss.leaping && e.isOnGround() && boss.age - boss.leapStart > 6) {
			slam(boss, form);
		}
		if (form >= 5 && boss.age % 40 == 0) {
			e.heal(Math.min(6.0, e.getAttribute(Attribute.MAX_HEALTH).getValue() - e.getHealth()));
		}
		boss.visual.tick();
	}

	private static void advanceEvolution(Boss boss) {
		if (boss.form() >= FORMS - 1) {
			return;
		}
		int t = timer(boss) + 1;
		if (t >= EvoDimension.intervalTicks()) {
			evolve(boss, boss.form() + 1);
		} else {
			boss.entity.getPersistentDataContainer().set(TIMER, PersistentDataType.INTEGER, t);
		}
	}

	private static void checkPhase(Boss boss) {
		if (boss.transitionTicks > 0) {
			return;
		}
		double ratio = boss.entity.getHealth() / boss.entity.getAttribute(Attribute.MAX_HEALTH).getValue();
		int wanted = ratio <= 1.0 / 3.0 ? 3 : ratio <= 2.0 / 3.0 ? 2 : 1;
		if (wanted > boss.phase()) {
			startPhase(boss, wanted);
		}
	}

	private static void startPhase(Boss boss, int newPhase) {
		boss.entity.getPersistentDataContainer().set(PHASE, PersistentDataType.INTEGER, newPhase);
		applyStats(boss);
		boss.transitionTicks = TRANSITION_TICKS;
		boss.leaping = false;
		boss.beamCharge = -1;
		boss.entity.getPathfinder().stopPathfinding();
		World world = boss.entity.getWorld();
		Vector at = Fx.feet(boss.entity);
		Fx.sound(world, at, Sound.BLOCK_BELL_RESONATE, 5.0F, newPhase == 3 ? 1.2F : 0.6F);
		Fx.sound(world, at, Sound.BLOCK_BEACON_ACTIVATE, 4.0F, newPhase == 3 ? 1.4F : 0.7F);
		Fx.sound(world, at, Sound.ENTITY_WITHER_SPAWN, 1.5F, newPhase == 3 ? 1.6F : 1.0F);
		NamedTextColor color = newPhase == 3 ? NamedTextColor.GOLD : NamedTextColor.RED;
		for (Player player : players(boss, 96)) {
			player.showTitle(Title.title(Lang.tr("title.evotool.phase." + newPhase).color(color).decorate(TextDecoration.BOLD),
					Lang.tr("subtitle.evotool.phase." + newPhase).color(color),
					Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(2500), Duration.ofMillis(750))));
			player.sendMessage(Lang.tr("message.evotool.boss_phase", Lang.tr(formKey(boss.form())), newPhase).color(color).decorate(TextDecoration.BOLD));
		}
	}

	private static void tickTransition(Boss boss, int form) {
		Ravager e = boss.entity;
		World world = e.getWorld();
		boss.transitionTicks--;
		e.getPathfinder().stopPathfinding();
		e.setVelocity(new Vector(0, Math.min(0, e.getVelocity().getY()), 0));
		double h = HEIGHT[form];
		double r = h * 0.6 + 0.5;
		for (int i = 0; i < 3; i++) {
			double a = boss.age * 0.45 + i * Math.PI * 2 / 3;
			double y = e.getY() + h * ((TRANSITION_TICKS - boss.transitionTicks + i * 15) % TRANSITION_TICKS) / TRANSITION_TICKS;
			Fx.particle(world, boss.phase() == 3 ? Particle.TOTEM_OF_UNDYING : Particle.FLAME, e.getX() + Math.cos(a) * r, y,
					e.getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.01);
			Fx.particle(world, Particle.END_ROD, e.getX() + Math.cos(a + 1) * r, y, e.getZ() + Math.sin(a + 1) * r, 1, 0, 0, 0, 0.02);
		}
		if (boss.transitionTicks % 10 == 0) {
			Fx.sound(world, Fx.feet(e), Sound.BLOCK_BELL_USE, 3.0F, 0.5F + boss.phase() * 0.2F);
		}
		if (boss.transitionTicks > 0) {
			return;
		}
		double radius = 8 + form;
		Fx.sound(world, Fx.feet(e), Sound.ENTITY_GENERIC_EXPLODE, 4.0F, 0.5F);
		Fx.particle(world, Particle.EXPLOSION_EMITTER, e.getX(), e.getY() + 1, e.getZ(), 2, 1, 0.5, 1, 0);
		for (int i = 0; i < 72; i++) {
			double a = Math.PI * 2 * i / 72;
			Fx.particle(world, boss.phase() == 3 ? Particle.TOTEM_OF_UNDYING : Particle.FLAME, e.getX() + Math.cos(a) * radius, e.getY() + 0.4,
					e.getZ() + Math.sin(a) * radius, 2, 0.1, 0.2, 0.1, 0.05);
		}
		for (Player player : victims(boss, radius)) {
			hurt(boss, player, 6.0 + form * 2.0);
			Vector away = away(boss, player);
			push(player, away.getX() * 2.0, 0.8, away.getZ() * 2.0);
		}
	}

	private static void aura(Boss boss) {
		int phase = boss.phase();
		if (phase == 1 || boss.age % 4 != 0) {
			return;
		}
		Ravager e = boss.entity;
		double r = HEIGHT[boss.form()] * 0.5;
		double a = boss.age * 0.3;
		double y = e.getY() + HEIGHT[boss.form()] * (0.2 + 0.6 * ThreadLocalRandom.current().nextDouble());
		if (phase == 2) {
			Fx.particle(e.getWorld(), Particle.FLAME, e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.01);
			Fx.particle(e.getWorld(), Particle.SMOKE, e.getX() - Math.cos(a) * r, y, e.getZ() - Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.01);
		} else {
			Fx.particle(e.getWorld(), Particle.TOTEM_OF_UNDYING, e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 2, 0.1, 0.2, 0.1, 0.05);
			Fx.particle(e.getWorld(), Particle.END_ROD, e.getX() - Math.cos(a) * r, y, e.getZ() - Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.01);
		}
	}

	private static void callPillars(Boss boss, LivingEntity target, int form) {
		boss.pillarCooldown = 110;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 4 + form; i++) {
			double x = target.getX();
			double z = target.getZ();
			if (i > 0) {
				double a = random.nextDouble() * Math.PI * 2;
				double d = 2.0 + random.nextDouble() * 6.0;
				x += Math.cos(a) * d;
				z += Math.sin(a) * d;
			}
			boss.pillars.add(new Pillar(x, target.getY(), z, boss.age + 30 + i * 3));
		}
		Fx.sound(target, Sound.BLOCK_BEACON_POWER_SELECT, 2.0F, 0.6F);
	}

	private static void tickPillars(Boss boss, int form) {
		World world = boss.entity.getWorld();
		for (Iterator<Pillar> it = boss.pillars.iterator(); it.hasNext(); ) {
			Pillar p = it.next();
			if (boss.age < p.strikeAge()) {
				if (boss.age % 2 == 0) {
					for (int i = 0; i < 10; i++) {
						double a = Math.PI * 2 * i / 10 + boss.age * 0.1;
						Fx.particle(world, Particle.END_ROD, p.x() + Math.cos(a) * 1.4, p.y() + 0.1, p.z() + Math.sin(a) * 1.4, 1, 0, 0, 0, 0);
					}
					Fx.particle(world, Particle.WAX_ON, p.x(), p.y() + 0.2, p.z(), 2, 0.4, 0, 0.4, 0);
				}
				continue;
			}
			it.remove();
			for (double dy = 0; dy < 14; dy += 0.5) {
				Fx.particle(world, Particle.END_ROD, p.x(), p.y() + dy, p.z(), 2, 0.25, 0.1, 0.25, 0.01);
			}
			Fx.particle(world, Particle.TOTEM_OF_UNDYING, p.x(), p.y() + 0.5, p.z(), 30, 0.6, 0.4, 0.6, 0.3);
			Fx.sound(world, new Vector(p.x(), p.y(), p.z()), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.5F, 1.4F);
			for (Player player : victims(boss, 64)) {
				double dx = player.getX() - p.x();
				double dz = player.getZ() - p.z();
				if (dx * dx + dz * dz < 1.7 * 1.7 && Math.abs(player.getY() - p.y()) < 3.0) {
					player.damage(10.0 + form * 2.0, DamageSource.builder(DamageType.INDIRECT_MAGIC).withCausingEntity(boss.entity).build());
					push(player, 0, 0.6, 0);
				}
			}
		}
	}

	private static void hop(Boss boss, LivingEntity target, int form) {
		boss.hopCooldown = 30 - form * 4;
		Vector dir = new Vector(target.getX() - boss.entity.getX(), 0, target.getZ() - boss.entity.getZ());
		if (dir.lengthSquared() > 1.0E-6) {
			dir.normalize();
		}
		boss.entity.setVelocity(new Vector(dir.getX() * 0.6, 0.65, dir.getZ() * 0.6));
		Fx.sound(boss.entity.getWorld(), Fx.feet(boss.entity), Sound.ENTITY_SLIME_JUMP, 2.0F, 0.5F);
	}

	private static void ring(Boss boss, int form) {
		Ravager e = boss.entity;
		World world = e.getWorld();
		boss.ringCooldown = 140 - form * 12;
		boss.ringTicks = 20;
		Fx.sound(world, Fx.feet(e), Sound.BLOCK_BELL_USE, 5.0F, 0.45F + form * 0.05F);
		Fx.sound(world, Fx.feet(e), Sound.BLOCK_BELL_RESONATE, 3.0F, 0.7F);
		double radius = 6 + form * 1.5;
		for (int i = 0; i < 64; i++) {
			double a = Math.PI * 2 * i / 64;
			Fx.particle(world, Particle.END_ROD, e.getX() + Math.cos(a) * radius, e.getY() + 0.3, e.getZ() + Math.sin(a) * radius, 1, 0, 0.1, 0, 0.01);
			Fx.particle(world, Particle.NOTE, e.getX() + Math.cos(a) * radius * 0.5, e.getY() + 1.0, e.getZ() + Math.sin(a) * radius * 0.5, 1, 0, 0, 0, 0);
		}
		Fx.particle(world, Particle.SONIC_BOOM, e.getX(), e.getY() + HEIGHT[form] * 0.5, e.getZ(), 1, 0, 0, 0, 0);
		for (Player player : victims(boss, radius)) {
			hurt(boss, player, 4.0 + form * 2.0);
			Vector away = away(boss, player);
			push(player, away.getX() * 1.2, 0.5, away.getZ() * 1.2);
		}
	}

	private static void summon(Boss boss, int form) {
		Ravager e = boss.entity;
		boss.summonCooldown = 320 - form * 20;
		Fx.sound(e.getWorld(), Fx.feet(e), Sound.ENTITY_SLIME_SQUISH, 3.0F, 0.5F);
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 2 + form / 2; i++) {
			double a = random.nextDouble() * Math.PI * 2;
			Location at = e.getLocation().add(Math.cos(a) * 3, 0.5, Math.sin(a) * 3);
			Slime slime = e.getWorld().spawn(at, Slime.class, s -> s.setSize(1 + random.nextInt(2)));
			Fx.particle(e.getWorld(), Particle.ITEM_SLIME, slime.getX(), slime.getY() + 0.5, slime.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
		}
	}

	private static void leap(Boss boss, LivingEntity target, int form) {
		Ravager e = boss.entity;
		boss.leapCooldown = 180 - form * 15;
		boss.leaping = true;
		boss.leapStart = boss.age;
		Vector d = new Vector(target.getX() - e.getX(), 0, target.getZ() - e.getZ());
		double dist = Math.max(1.0, d.length());
		double speed = Math.min(1.6, dist * 0.11);
		e.setVelocity(new Vector(d.getX() / dist * speed, 1.1, d.getZ() / dist * speed));
		Fx.sound(e.getWorld(), Fx.feet(e), Sound.ENTITY_RAVAGER_ROAR, 3.0F, 0.6F);
	}

	private static void slam(Boss boss, int form) {
		Ravager e = boss.entity;
		World world = e.getWorld();
		boss.leaping = false;
		double radius = 6 + form;
		Fx.sound(world, Fx.feet(e), Sound.ENTITY_GENERIC_EXPLODE, 3.0F, 0.6F);
		for (int i = 0; i < 40; i++) {
			double a = Math.PI * 2 * i / 40;
			Fx.particle(world, Particle.EXPLOSION, e.getX() + Math.cos(a) * radius * 0.7, e.getY() + 0.2, e.getZ() + Math.sin(a) * radius * 0.7,
					1, 0.2, 0, 0.2, 0);
		}
		for (Player player : victims(boss, radius)) {
			hurt(boss, player, 8.0 + form * 3.0);
			Vector away = away(boss, player);
			push(player, away.getX() * 1.5, 0.9, away.getZ() * 1.5);
		}
	}

	private static void chargeBeam(Boss boss, LivingEntity target, int form) {
		Ravager e = boss.entity;
		World world = e.getWorld();
		Vector eye = new Vector(e.getX(), e.getY() + HEIGHT[form] * 0.6, e.getZ());
		if (boss.beamCharge-- > 0) {
			Fx.particle(world, Particle.END_ROD, eye, 4, 1.5, 1.5, 1.5, -0.15);
			e.lookAt(target);
			return;
		}
		boss.beamCharge = -1;
		boss.beamCooldown = 220 - form * 20;
		Fx.sound(world, Fx.feet(e), Sound.ENTITY_WARDEN_SONIC_BOOM, 4.0F, 1.0F);
		Vector to = Fx.center(target);
		Vector dir = to.clone().subtract(eye).normalize();
		for (double s = 1; s < 40; s += 0.5) {
			Vector p = eye.clone().add(dir.clone().multiply(s));
			if (((int) (s * 2)) % 6 == 0) {
				Fx.particle(world, Particle.SONIC_BOOM, p, 1, 0, 0, 0, 0);
			}
			Fx.particle(world, Particle.END_ROD, p, 1, 0.05, 0.05, 0.05, 0);
		}
		for (Player player : victims(boss, 40)) {
			Vector c = Fx.center(player);
			double along = c.clone().subtract(eye).dot(dir);
			if (along > 0 && c.clone().subtract(eye.clone().add(dir.clone().multiply(along))).length() < 1.6) {
				player.damage(12.0 + form * 3.0, DamageSource.builder(DamageType.SONIC_BOOM).withCausingEntity(e).withDirectEntity(e).build());
				push(player, dir.getX() * 1.8, 0.5, dir.getZ() * 1.8);
			}
		}
	}

	private static void strike(Boss boss, LivingEntity target) {
		boss.strikeCooldown = 100;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < 3; i++) {
			double x = target.getX() + (random.nextDouble() - 0.5) * (i == 0 ? 0 : 6);
			double z = target.getZ() + (random.nextDouble() - 0.5) * (i == 0 ? 0 : 6);
			target.getWorld().strikeLightningEffect(new Location(target.getWorld(), x, target.getY(), z));
		}
		target.damage(8.0, DamageSource.builder(DamageType.LIGHTNING_BOLT).withCausingEntity(boss.entity).build());
		target.setFireTicks(Math.max(target.getFireTicks(), 60));
	}

	// ------------------------------------------------------------------ événements

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		Boss boss = BOSSES.get(event.getEntity().getUniqueId());
		if (boss == null) {
			return;
		}
		EntityDamageEvent.DamageCause cause = event.getCause();
		if (cause == EntityDamageEvent.DamageCause.FALL || cause == EntityDamageEvent.DamageCause.SUFFOCATION
				|| cause == EntityDamageEvent.DamageCause.LIGHTNING) {
			event.setCancelled(true);
			return;
		}
		if (boss.transitionTicks > 0 && cause != EntityDamageEvent.DamageCause.KILL && cause != EntityDamageEvent.DamageCause.VOID) {
			if (boss.hurtTicks == 0) {
				Fx.sound(boss.entity.getWorld(), Fx.feet(boss.entity), Sound.BLOCK_ANVIL_LAND, 0.6F, 1.8F);
				boss.hurtTicks = 6;
			}
			event.setCancelled(true);
			return;
		}
		boss.hurtTicks = 6;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onAttack(EntityDamageByEntityEvent event) {
		Boss boss = BOSSES.get(event.getDamager().getUniqueId());
		if (boss == null || !(event.getEntity() instanceof LivingEntity living)) {
			return;
		}
		boss.punchTicks = 8;
		Vector away = away(boss, living);
		double k = 0.6 + boss.form() * 0.25;
		EvoToolPlugin.later(1, () -> push(living, away.getX() * k, 0.35, away.getZ() * k));
	}

	@EventHandler
	public void onDeath(EntityDeathEvent event) {
		Boss boss = BOSSES.remove(event.getEntity().getUniqueId());
		if (boss == null) {
			return;
		}
		int form = boss.form();
		World world = boss.entity.getWorld();
		Vector at = Fx.feet(boss.entity);
		event.getDrops().clear();
		event.getDrops().add(Items.create(EvoItem.EVOLIUM, 3 + form * 3));
		event.getDrops().add(Items.create(EvoItem.BOSS_HEART));
		event.getDrops().add(Items.create(EvoItem.of(CustomBlock.RUBY_BLOCK), 2));
		if (form >= 1) {
			event.getDrops().add(new ItemStack(Material.BELL));
		}
		if (form >= 2) {
			event.getDrops().add(Items.create(EvoItem.EVOLITE_INGOT, form - 1));
		}
		if (form >= 4) {
			event.getDrops().add(Items.create(EvoItem.of(CustomBlock.EVOLIUM_BLOCK), form - 3));
		}
		event.setDroppedExp(80 * (form + 1));
		Fx.sound(world, at, Sound.UI_TOAST_CHALLENGE_COMPLETE, 3.0F, 1.0F);
		Fx.particle(world, Particle.EXPLOSION_EMITTER, at.getX(), at.getY() + 1, at.getZ(), 3, 1, 1, 1, 0);
		for (Player player : players(boss, 96)) {
			player.sendMessage(Lang.tr("message.evotool.boss_defeated", Lang.tr(formKey(form))).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
		}
		hideBar(boss);
		boss.visual.discard(true);
	}

	@EventHandler
	public void onLoad(EntitiesLoadEvent event) {
		for (Entity e : event.getEntities()) {
			track(e);
		}
	}

	@EventHandler
	public void onUnload(EntitiesUnloadEvent event) {
		for (Entity e : event.getEntities()) {
			Boss boss = BOSSES.remove(e.getUniqueId());
			if (boss != null) {
				boss.visual.discard(false);
				hideBar(boss);
			}
		}
	}
}
