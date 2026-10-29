package fr.evotool.boss;

import fr.evotool.block.ModBlocks;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.item.ModItems;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Le Slime Cloche, boss de la dimension évolutive. Il évolue avec le temps passé dans la dimension :
 * 0 Slime Évolutif (bondit), 10 min Slime Cloche (onde de son), 20 min Cloche de Guerre (invoque des slimes),
 * 30 min Cloche Colosse (bras et jambes musclés, bonds écrasants), 40 min Cloche Titan (rayon sonique),
 * 50 min Cloche Divine (foudre, régénération). Son apparence est faite d'objets d'affichage animés ({@link BossVisual}).
 * <p>
 * Chaque combat a 3 phases : à 2/3 de sa vie, le boss se transforme (invulnérable quelques secondes, onde de choc)
 * et entre en colère (rouge et or, attaques plus fréquentes) ; à 1/3, c'est l'ascension dorée (tout en or,
 * encore plus rapide, et des colonnes de lumière tombent autour du joueur).
 */
public class BellBossEntity extends HostileEntity {
	public static final int FORMS = 6;
	public static final int PHASES = 3;
	private static final String FORM_TAG = "evotool_form_";
	private static final String PHASE_TAG = "evotool_phase_";
	/** Durée de la transformation entre deux phases (invulnérable). */
	private static final int TRANSITION_TICKS = 50;
	private static final TrackedData<Integer> FORM = DataTracker.registerData(BellBossEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final float[] WIDTH = {1.0F, 1.1F, 1.3F, 1.4F, 1.7F, 1.9F};
	private static final float[] HEIGHT = {1.0F, 1.3F, 1.5F, 2.0F, 2.4F, 2.7F};
	private static final double[] HEALTH = {120, 200, 300, 450, 650, 900};
	private static final double[] DAMAGE = {6, 9, 12, 16, 20, 26};
	private static final double[] SPEED = {0.25, 0.25, 0.27, 0.3, 0.32, 0.34};
	private static final double[] ARMOR = {2, 6, 10, 12, 16, 20};
	private static final BossBar.Color[] COLORS = {BossBar.Color.GREEN, BossBar.Color.PURPLE, BossBar.Color.RED,
			BossBar.Color.PINK, BossBar.Color.RED, BossBar.Color.YELLOW};

	// 6 crans : les phases changent aux 2e et 4e crans
	private final ServerBossBar bossBar = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_6);
	private BossVisual visual;
	private boolean formRestored;
	private int hopCooldown = 20;
	private int ringCooldown = 100;
	private int summonCooldown = 260;
	private int leapCooldown = 140;
	private int beamCooldown = 200;
	private int beamCharge = -1;
	private int strikeCooldown = 120;
	private boolean leaping;
	private int leapStart;
	private int phase = 1;
	private int pillarCooldown = 60;
	/** Colonnes de lumière annoncées au sol (phase 3) : elles frappent à l'âge strikeAge du boss. */
	private record Pillar(double x, double y, double z, int strikeAge) {
	}
	private final List<Pillar> pillars = new ArrayList<>();
	int transitionTicks;
	int ringTicks;
	int punchTicks;
	int hurtTicks;

	public BellBossEntity(EntityType<? extends BellBossEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createAttributes() {
		return HostileEntity.createHostileAttributes()
				.add(EntityAttributes.MAX_HEALTH, HEALTH[0])
				.add(EntityAttributes.MOVEMENT_SPEED, SPEED[0])
				.add(EntityAttributes.ATTACK_DAMAGE, DAMAGE[0])
				.add(EntityAttributes.ARMOR, ARMOR[0])
				.add(EntityAttributes.FOLLOW_RANGE, 64.0)
				.add(EntityAttributes.KNOCKBACK_RESISTANCE, 0.9)
				.add(EntityAttributes.STEP_HEIGHT, 1.5)
				.add(EntityAttributes.SAFE_FALL_DISTANCE, 64.0);
	}

	public static String formKey(int form) {
		return "boss.evotool.form." + MathHelper.clamp(form, 0, FORMS - 1);
	}

	@Override
	protected void initDataTracker(DataTracker.Builder builder) {
		super.initDataTracker(builder);
		builder.add(FORM, 0);
	}

	public int getForm() {
		return dataTracker == null ? 0 : dataTracker.get(FORM);
	}

	/** Phase du combat en cours (1 à 3). */
	public int getPhase() {
		return phase;
	}

	public boolean isTransforming() {
		return transitionTicks > 0;
	}

	@Override
	public void onTrackedDataSet(TrackedData<?> data) {
		super.onTrackedDataSet(data);
		if (FORM.equals(data)) {
			calculateDimensions();
		}
	}

	@Override
	protected EntityDimensions getBaseDimensions(EntityPose pose) {
		int form = getForm();
		return super.getBaseDimensions(pose).scaled(WIDTH[form], HEIGHT[form]);
	}

	@Override
	protected void initGoals() {
		goalSelector.add(2, new MeleeAttackGoal(this, 1.1, true));
		goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
		goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24.0F));
		goalSelector.add(7, new LookAroundGoal(this));
		targetSelector.add(1, new RevengeGoal(this));
		targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
	}

	/** Prend la forme {@code form} : statistiques, nom, barre de boss (le modèle suit tout seul). */
	public void applyForm(int form, boolean heal) {
		form = MathHelper.clamp(form, 0, FORMS - 1);
		dataTracker.set(FORM, form);
		setBase(EntityAttributes.MAX_HEALTH, HEALTH[form]);
		if (heal) {
			// nouvelle forme, vie pleine : le combat reprend en phase 1
			setPhase(1);
			transitionTicks = 0;
			pillars.clear();
			setHealth(getMaxHealth());
		}
		for (int f = 0; f < FORMS; f++) {
			removeCommandTag(FORM_TAG + f);
		}
		addCommandTag(FORM_TAG + form);
		setCustomName(Text.translatable(formKey(form)));
		applyPhaseStats();
		calculateDimensions();
	}

	private void setPhase(int newPhase) {
		phase = MathHelper.clamp(newPhase, 1, PHASES);
		for (int p = 1; p <= PHASES; p++) {
			removeCommandTag(PHASE_TAG + p);
		}
		addCommandTag(PHASE_TAG + phase);
	}

	/** Statistiques de la forme, renforcées à chaque phase ; nom et couleur de la barre de boss. */
	private void applyPhaseStats() {
		int form = getForm();
		setBase(EntityAttributes.ATTACK_DAMAGE, DAMAGE[form] * (1.0 + 0.2 * (phase - 1)));
		setBase(EntityAttributes.MOVEMENT_SPEED, SPEED[form] * (1.0 + 0.15 * (phase - 1)));
		setBase(EntityAttributes.ARMOR, ARMOR[form] + 4.0 * (phase - 1));
		bossBar.setName(Text.translatable("boss.evotool.phase", Text.translatable(formKey(form)), phase).formatted(Formatting.BOLD));
		bossBar.setColor(phase == 1 ? COLORS[form] : phase == 2 ? BossBar.Color.RED : BossBar.Color.YELLOW);
	}

	/** Passe à la phase suivante quand la vie descend sous 2/3 puis 1/3 (une seule fois par phase). */
	public void checkPhase(ServerWorld world) {
		if (transitionTicks > 0) {
			return;
		}
		float ratio = getHealth() / getMaxHealth();
		int wanted = ratio <= 1.0F / 3.0F ? 3 : ratio <= 2.0F / 3.0F ? 2 : 1;
		if (wanted > phase) {
			startPhase(world, wanted);
		}
	}

	private void startPhase(ServerWorld world, int newPhase) {
		setPhase(newPhase);
		applyPhaseStats();
		transitionTicks = TRANSITION_TICKS;
		leaping = false;
		beamCharge = -1;
		getNavigation().stop();
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 5.0F, newPhase == 3 ? 1.2F : 0.6F);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.HOSTILE, 4.0F, newPhase == 3 ? 1.4F : 0.7F);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.5F, newPhase == 3 ? 1.6F : 1.0F);
		Formatting color = newPhase == 3 ? Formatting.GOLD : Formatting.RED;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(this) < 96 * 96) {
				player.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 50, 15));
				player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.evotool.phase." + newPhase)
						.formatted(color, Formatting.BOLD)));
				player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("subtitle.evotool.phase." + newPhase)
						.formatted(color)));
				player.sendMessage(Text.translatable("message.evotool.boss_phase", Text.translatable(formKey(getForm())), newPhase)
						.formatted(color, Formatting.BOLD), false);
			}
		}
	}

	/** Pendant la transformation : le boss reste sur place dans un tourbillon de lumière, puis libère une onde de choc. */
	private void tickTransition(ServerWorld world, int form) {
		transitionTicks--;
		getNavigation().stop();
		setVelocity(0, Math.min(0, getVelocity().y), 0);
		double h = getHeight();
		double r = getWidth() * 0.9 + 0.5;
		for (int i = 0; i < 3; i++) {
			double a = age * 0.45 + i * Math.PI * 2 / 3;
			double y = getY() + h * ((TRANSITION_TICKS - transitionTicks + i * 15) % TRANSITION_TICKS) / TRANSITION_TICKS;
			world.spawnParticles(phase == 3 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.FLAME,
					getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 2, 0.05, 0.05, 0.05, 0.01);
			world.spawnParticles(ParticleTypes.END_ROD, getX() + Math.cos(a + 1) * r, y, getZ() + Math.sin(a + 1) * r, 1, 0, 0, 0, 0.02);
		}
		if (transitionTicks % 10 == 0) {
			world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 3.0F, 0.5F + phase * 0.2F);
		}
		if (transitionTicks > 0) {
			return;
		}
		double radius = 8 + form;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 4.0F, 0.5F);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 1, 0.5, 1, 0);
		for (int i = 0; i < 72; i++) {
			double a = Math.PI * 2 * i / 72;
			world.spawnParticles(phase == 3 ? ParticleTypes.TOTEM_OF_UNDYING : ParticleTypes.FLAME,
					getX() + Math.cos(a) * radius, getY() + 0.4, getZ() + Math.sin(a) * radius, 2, 0.1, 0.2, 0.1, 0.05);
			world.spawnParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * radius * 0.6, getY() + 0.4,
					getZ() + Math.sin(a) * radius * 0.6, 1, 0, 0.1, 0, 0.02);
		}
		for (ServerPlayerEntity player : victims(world, radius)) {
			player.damage(world, getDamageSources().mobAttack(this), 6.0F + form * 2.0F);
			Vec3d away = new Vec3d(player.getX() - getX(), 0, player.getZ() - getZ()).normalize();
			push(player, away.x * 2.0, 0.8, away.z * 2.0);
		}
	}

	private void setBase(RegistryEntry<EntityAttribute> attribute, double value) {
		EntityAttributeInstance instance = getAttributeInstance(attribute);
		if (instance != null) {
			instance.setBaseValue(value);
		}
	}

	@Override
	public boolean cannotDespawn() {
		return true;
	}

	@Override
	protected void mobTick(ServerWorld world) {
		super.mobTick(world);
		if (!formRestored) {
			formRestored = true;
			// la forme est gardée dans une étiquette (sauvegardée avec l'entité)
			for (String tag : List.copyOf(getCommandTags())) {
				if (tag.startsWith(FORM_TAG)) {
					applyForm(Integer.parseInt(tag.substring(FORM_TAG.length())), false);
				} else if (tag.startsWith(PHASE_TAG)) {
					setPhase(Integer.parseInt(tag.substring(PHASE_TAG.length())));
					applyPhaseStats();
				}
			}
		}
		if (visual == null) {
			visual = new BossVisual(this);
		}
		int form = getForm();
		if (age % 20 == 0) {
			syncWithPlayers(world);
			updateBarPlayers(world);
		}
		bossBar.setPercent(getHealth() / getMaxHealth());
		if (ringTicks > 0) {
			ringTicks--;
		}
		if (punchTicks > 0) {
			punchTicks--;
		}
		if (hurtTicks > 0) {
			hurtTicks--;
		}
		checkPhase(world);
		tickPillars(world, form);
		if (transitionTicks > 0) {
			tickTransition(world, form);
			visual.tick(world);
			return;
		}
		aura(world);
		// en phase 2 les attaques reviennent 1,5 fois plus vite, en phase 3 deux fois plus vite
		int rate = phase == 1 ? 1 : phase == 2 ? 1 + (age & 1) : 2;
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && squaredDistanceTo(target) < 48 * 48) {
			if (form <= 2 && (hopCooldown -= rate) <= 0 && isOnGround()) {
				hop(target, form);
			}
			if ((form >= 1 || phase >= 2) && (ringCooldown -= rate) <= 0) {
				ring(world, form);
			}
			if (form >= 2 && (summonCooldown -= rate) <= 0) {
				summon(world, form);
			}
			if (form >= 3 && (leapCooldown -= rate) <= 0 && isOnGround()) {
				leap(world, target, form);
			}
			if (form >= 4) {
				if (beamCharge >= 0) {
					chargeBeam(world, target, form);
				} else if ((beamCooldown -= rate) <= 0) {
					beamCharge = 30;
					world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 3.0F, 1.0F);
				}
			}
			if (form >= 5 && (strikeCooldown -= rate) <= 0) {
				strike(world, target);
			}
			if (phase >= 3 && (pillarCooldown -= rate) <= 0) {
				callPillars(world, target, form);
			}
		}
		if (leaping && isOnGround() && age - leapStart > 6) {
			slam(world, form);
		}
		if (form >= 5 && age % 40 == 0) {
			heal(6.0F);
		}
		visual.tick(world);
	}

	/** Particules autour du boss : braises en colère (phase 2), aura dorée (phase 3). */
	private void aura(ServerWorld world) {
		if (phase == 1 || age % 4 != 0) {
			return;
		}
		double r = getWidth() * 0.7;
		double a = age * 0.3;
		double y = getY() + getHeight() * (0.2 + 0.6 * world.getRandom().nextDouble());
		if (phase == 2) {
			world.spawnParticles(ParticleTypes.FLAME, getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 2, 0.1, 0.1, 0.1, 0.01);
			world.spawnParticles(ParticleTypes.SMOKE, getX() - Math.cos(a) * r, y, getZ() - Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.01);
		} else {
			world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX() + Math.cos(a) * r, y, getZ() + Math.sin(a) * r, 2, 0.1, 0.2, 0.1, 0.05);
			world.spawnParticles(ParticleTypes.END_ROD, getX() - Math.cos(a) * r, y, getZ() - Math.sin(a) * r, 1, 0.1, 0.1, 0.1, 0.01);
		}
	}

	/** Phase 3 : des cercles de lumière apparaissent sous le joueur et autour, puis des colonnes de lumière y tombent. */
	private void callPillars(ServerWorld world, LivingEntity target, int form) {
		pillarCooldown = 110;
		int count = 4 + form;
		for (int i = 0; i < count; i++) {
			double x = target.getX();
			double z = target.getZ();
			if (i > 0) {
				double a = world.getRandom().nextDouble() * Math.PI * 2;
				double d = 2.0 + world.getRandom().nextDouble() * 6.0;
				x += Math.cos(a) * d;
				z += Math.sin(a) * d;
			}
			pillars.add(new Pillar(x, target.getY(), z, age + 30 + i * 3));
		}
		world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.0F, 0.6F);
	}

	private void tickPillars(ServerWorld world, int form) {
		Iterator<Pillar> it = pillars.iterator();
		while (it.hasNext()) {
			Pillar p = it.next();
			if (age < p.strikeAge()) {
				if (age % 2 == 0) {
					for (int i = 0; i < 10; i++) {
						double a = Math.PI * 2 * i / 10 + age * 0.1;
						world.spawnParticles(ParticleTypes.END_ROD, p.x() + Math.cos(a) * 1.4, p.y() + 0.1, p.z() + Math.sin(a) * 1.4,
								1, 0, 0, 0, 0);
					}
					world.spawnParticles(ParticleTypes.WAX_ON, p.x(), p.y() + 0.2, p.z(), 2, 0.4, 0, 0.4, 0);
				}
				continue;
			}
			it.remove();
			for (double dy = 0; dy < 14; dy += 0.5) {
				world.spawnParticles(ParticleTypes.END_ROD, p.x(), p.y() + dy, p.z(), 2, 0.25, 0.1, 0.25, 0.01);
			}
			world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.x(), p.y() + 0.5, p.z(), 30, 0.6, 0.4, 0.6, 0.3);
			world.playSound(null, BlockPos.ofFloored(p.x(), p.y(), p.z()), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.5F, 1.4F);
			for (ServerPlayerEntity player : victims(world, 64)) {
				double dx = player.getX() - p.x();
				double dz = player.getZ() - p.z();
				if (dx * dx + dz * dz < 1.7 * 1.7 && Math.abs(player.getY() - p.y()) < 3.0) {
					player.damage(world, getDamageSources().indirectMagic(this, this), 10.0F + form * 2.0F);
					push(player, 0, 0.6, 0);
				}
			}
		}
	}

	/** Toutes les 10 minutes passées par un joueur proche dans la dimension, le boss évolue avec lui. */
	private void syncWithPlayers(ServerWorld world) {
		int phase = getForm();
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (!player.isSpectator() && player.squaredDistanceTo(this) < 96 * 96) {
				phase = Math.max(phase, EvoDimension.phase(player));
			}
		}
		if (phase > getForm()) {
			evolve(world, phase);
		}
	}

	public void evolve(ServerWorld world, int form) {
		applyForm(form, true);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 5.0F, 0.5F);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 2.0F, 1.4F);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 2, 0.5, 0.5, 0.5, 0);
		world.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + getHeight() / 2, getZ(), 150, 1.5, 1.5, 1.5, 0.3);
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(this) < 96 * 96) {
				player.sendMessage(Text.translatable("message.evotool.boss_evolves", Text.translatable(formKey(form)))
						.formatted(Formatting.RED, Formatting.BOLD), false);
			}
		}
	}

	private void updateBarPlayers(ServerWorld world) {
		for (ServerPlayerEntity player : List.copyOf(bossBar.getPlayers())) {
			if (player.isRemoved() || player.getEntityWorld() != world || player.squaredDistanceTo(this) > 72 * 72) {
				bossBar.removePlayer(player);
			}
		}
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.squaredDistanceTo(this) < 64 * 64) {
				bossBar.addPlayer(player);
			}
		}
	}

	/** Donne un élan à une entité ; pour un joueur, on lui envoie sa nouvelle vitesse (son client gère son mouvement). */
	static void push(LivingEntity target, double x, double y, double z) {
		target.addVelocity(x, y, z);
		if (target instanceof ServerPlayerEntity player) {
			player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
		}
	}

	private List<ServerPlayerEntity> victims(ServerWorld world, double radius) {
		return world.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < radius * radius);
	}

	private void hop(LivingEntity target, int form) {
		hopCooldown = 30 - form * 4;
		Vec3d dir = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ()).normalize();
		setVelocity(dir.x * 0.6, 0.65, dir.z * 0.6);
		playSound(SoundEvents.ENTITY_SLIME_JUMP, 2.0F, 0.5F);
	}

	private void ring(ServerWorld world, int form) {
		ringCooldown = 140 - form * 12;
		ringTicks = 20;
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 5.0F, 0.45F + form * 0.05F);
		world.playSound(null, getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.HOSTILE, 3.0F, 0.7F);
		double radius = 6 + form * 1.5;
		for (int i = 0; i < 64; i++) {
			double a = Math.PI * 2 * i / 64;
			world.spawnParticles(ParticleTypes.END_ROD, getX() + Math.cos(a) * radius, getY() + 0.3, getZ() + Math.sin(a) * radius,
					1, 0, 0.1, 0, 0.01);
			world.spawnParticles(ParticleTypes.NOTE, getX() + Math.cos(a) * radius * 0.5, getY() + 1.0, getZ() + Math.sin(a) * radius * 0.5,
					1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + getHeight() * 0.5, getZ(), 1, 0, 0, 0, 0);
		for (ServerPlayerEntity player : victims(world, radius)) {
			player.damage(world, getDamageSources().mobAttack(this), 4.0F + form * 2.0F);
			Vec3d away = new Vec3d(player.getX() - getX(), 0, player.getZ() - getZ()).normalize();
			push(player, away.x * 1.2, 0.5, away.z * 1.2);
		}
	}

	private void summon(ServerWorld world, int form) {
		summonCooldown = 320 - form * 20;
		playSound(SoundEvents.ENTITY_SLIME_SQUISH, 3.0F, 0.5F);
		for (int i = 0; i < 2 + form / 2; i++) {
			SlimeEntity slime = EntityType.SLIME.create(world, SpawnReason.MOB_SUMMONED);
			if (slime == null) {
				continue;
			}
			double a = world.getRandom().nextDouble() * Math.PI * 2;
			slime.setSize(1 + world.getRandom().nextInt(2), true);
			slime.refreshPositionAndAngles(getX() + Math.cos(a) * 3, getY() + 0.5, getZ() + Math.sin(a) * 3, 0, 0);
			world.spawnEntity(slime);
			world.spawnParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY() + 0.5, slime.getZ(), 20, 0.4, 0.4, 0.4, 0.1);
		}
	}

	private void leap(ServerWorld world, LivingEntity target, int form) {
		leapCooldown = 180 - form * 15;
		leaping = true;
		leapStart = age;
		Vec3d d = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ());
		double dist = Math.max(1.0, d.length());
		double speed = Math.min(1.6, dist * 0.11);
		setVelocity(d.x / dist * speed, 1.1, d.z / dist * speed);
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.6F);
	}

	private void slam(ServerWorld world, int form) {
		leaping = false;
		double radius = 6 + form;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.HOSTILE, 3.0F, 0.6F);
		for (int i = 0; i < 40; i++) {
			double a = Math.PI * 2 * i / 40;
			world.spawnParticles(ParticleTypes.EXPLOSION, getX() + Math.cos(a) * radius * 0.7, getY() + 0.2,
					getZ() + Math.sin(a) * radius * 0.7, 1, 0.2, 0, 0.2, 0);
		}
		for (ServerPlayerEntity player : victims(world, radius)) {
			player.damage(world, getDamageSources().mobAttack(this), 8.0F + form * 3.0F);
			Vec3d away = new Vec3d(player.getX() - getX(), 0, player.getZ() - getZ()).normalize();
			push(player, away.x * 1.5, 0.9, away.z * 1.5);
		}
	}

	private void chargeBeam(ServerWorld world, LivingEntity target, int form) {
		Vec3d eye = new Vec3d(getX(), getY() + getHeight() * 0.6, getZ());
		if (beamCharge-- > 0) {
			world.spawnParticles(ParticleTypes.END_ROD, eye.x, eye.y, eye.z, 4, 1.5, 1.5, 1.5, -0.15);
			getLookControl().lookAt(target, 30.0F, 30.0F);
			return;
		}
		beamCharge = -1;
		beamCooldown = 220 - form * 20;
		world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4.0F, 1.0F);
		Vec3d to = new Vec3d(target.getX(), target.getY() + target.getHeight() * 0.5, target.getZ());
		Vec3d dir = to.subtract(eye).normalize();
		for (double s = 1; s < 40; s += 0.5) {
			Vec3d p = eye.add(dir.multiply(s));
			if (((int) (s * 2)) % 6 == 0) {
				world.spawnParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0, 0, 0, 0);
			}
			world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
		}
		for (ServerPlayerEntity player : victims(world, 40)) {
			Vec3d c = new Vec3d(player.getX(), player.getY() + player.getHeight() * 0.5, player.getZ());
			double along = c.subtract(eye).dotProduct(dir);
			if (along > 0 && c.subtract(eye.add(dir.multiply(along))).length() < 1.6) {
				player.damage(world, getDamageSources().sonicBoom(this), 12.0F + form * 3.0F);
				push(player, dir.x * 1.8, 0.5, dir.z * 1.8);
			}
		}
	}

	private void strike(ServerWorld world, LivingEntity target) {
		strikeCooldown = 100;
		for (int i = 0; i < 3; i++) {
			LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
			if (bolt == null) {
				continue;
			}
			double x = target.getX() + (world.getRandom().nextDouble() - 0.5) * (i == 0 ? 0 : 6);
			double z = target.getZ() + (world.getRandom().nextDouble() - 0.5) * (i == 0 ? 0 : 6);
			bolt.refreshPositionAfterTeleport(new Vec3d(x, target.getY(), z));
			bolt.setCosmetic(true);
			world.spawnEntity(bolt);
		}
		target.damage(world, world.getDamageSources().lightningBolt(), 8.0F);
		target.setOnFireFor(3.0F);
	}

	@Override
	public boolean tryAttack(ServerWorld world, Entity target) {
		boolean hit = super.tryAttack(world, target);
		if (hit) {
			punchTicks = 8;
			if (target instanceof LivingEntity living) {
				Vec3d away = new Vec3d(target.getX() - getX(), 0, target.getZ() - getZ()).normalize();
				push(living, away.x * (0.6 + getForm() * 0.25), 0.35, away.z * (0.6 + getForm() * 0.25));
			}
		}
		return hit;
	}

	@Override
	public boolean damage(ServerWorld world, DamageSource source, float amount) {
		if (transitionTicks > 0 && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			// invulnérable pendant la transformation
			if (hurtTicks == 0) {
				world.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 0.6F, 1.8F);
				hurtTicks = 6;
			}
			return false;
		}
		boolean damaged = super.damage(world, source, amount);
		if (damaged) {
			hurtTicks = 6;
		}
		return damaged;
	}

	@Override
	public void onDeath(DamageSource source) {
		super.onDeath(source);
		if (getEntityWorld() instanceof ServerWorld world) {
			EvoDimension.onBossDefeated(world, getBlockPos());
			world.playSound(null, getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.HOSTILE, 3.0F, 1.0F);
			world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 3, 1, 1, 1, 0);
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.squaredDistanceTo(this) < 96 * 96) {
					player.sendMessage(Text.translatable("message.evotool.boss_defeated", Text.translatable(formKey(getForm())))
							.formatted(Formatting.GOLD, Formatting.BOLD), false);
				}
			}
			bossBar.clearPlayers();
			if (visual != null) {
				visual.discard(world, true);
			}
		}
	}

	@Override
	protected void dropLoot(ServerWorld world, DamageSource source, boolean causedByPlayer) {
		super.dropLoot(world, source, causedByPlayer);
		int form = getForm();
		dropStack(world, new ItemStack(ModItems.EVOLIUM, 3 + form * 3));
		dropStack(world, new ItemStack(ModBlocks.RUBY_BLOCK, 2));
		if (form >= 1) {
			dropStack(world, new ItemStack(Items.BELL));
		}
		if (form >= 2) {
			dropStack(world, new ItemStack(ModItems.EVOLITE_INGOT, form - 1));
		}
		if (form >= 4) {
			dropStack(world, new ItemStack(ModBlocks.EVOLIUM_BLOCK, form - 3));
		}
	}

	@Override
	protected int getExperienceToDrop(ServerWorld world) {
		return 80 * (getForm() + 1);
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		bossBar.clearPlayers();
		if (visual != null && getEntityWorld() instanceof ServerWorld world) {
			visual.discard(world, false);
		}
	}
}
