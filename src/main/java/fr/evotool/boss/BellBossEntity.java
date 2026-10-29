package fr.evotool.boss;

import fr.evotool.block.ModBlocks;
import fr.evotool.dimension.EvoDimension;
import fr.evotool.item.ModItems;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Le Slime Cloche, boss de la dimension évolutive. Il évolue avec le temps passé dans la dimension :
 * 0 Slime Évolutif (bondit), 10 min Slime Cloche (onde de son), 20 min Cloche de Guerre (invoque des slimes),
 * 30 min Cloche Colosse (bras et jambes musclés, bonds écrasants), 40 min Cloche Titan (rayon sonique),
 * 50 min Cloche Divine (foudre, régénération). Son apparence est faite d'objets d'affichage animés ({@link BossVisual}).
 */
public class BellBossEntity extends HostileEntity {
	public static final int FORMS = 6;
	private static final String FORM_TAG = "evotool_form_";
	private static final TrackedData<Integer> FORM = DataTracker.registerData(BellBossEntity.class, TrackedDataHandlerRegistry.INTEGER);

	private static final float[] WIDTH = {1.0F, 1.1F, 1.3F, 1.4F, 1.7F, 1.9F};
	private static final float[] HEIGHT = {1.0F, 1.3F, 1.5F, 2.0F, 2.4F, 2.7F};
	private static final double[] HEALTH = {120, 200, 300, 450, 650, 900};
	private static final double[] DAMAGE = {6, 9, 12, 16, 20, 26};
	private static final double[] SPEED = {0.25, 0.25, 0.27, 0.3, 0.32, 0.34};
	private static final double[] ARMOR = {2, 6, 10, 12, 16, 20};
	private static final BossBar.Color[] COLORS = {BossBar.Color.GREEN, BossBar.Color.PURPLE, BossBar.Color.RED,
			BossBar.Color.PINK, BossBar.Color.RED, BossBar.Color.YELLOW};

	private final ServerBossBar bossBar = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
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
		setBase(EntityAttributes.ATTACK_DAMAGE, DAMAGE[form]);
		setBase(EntityAttributes.MOVEMENT_SPEED, SPEED[form]);
		setBase(EntityAttributes.ARMOR, ARMOR[form]);
		if (heal) {
			setHealth(getMaxHealth());
		}
		for (int f = 0; f < FORMS; f++) {
			removeCommandTag(FORM_TAG + f);
		}
		addCommandTag(FORM_TAG + form);
		setCustomName(Text.translatable(formKey(form)));
		bossBar.setName(Text.translatable(formKey(form)).formatted(Formatting.BOLD));
		bossBar.setColor(COLORS[form]);
		calculateDimensions();
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
		LivingEntity target = getTarget();
		if (target != null && target.isAlive() && squaredDistanceTo(target) < 48 * 48) {
			if (form <= 2 && --hopCooldown <= 0 && isOnGround()) {
				hop(target, form);
			}
			if (form >= 1 && --ringCooldown <= 0) {
				ring(world, form);
			}
			if (form >= 2 && --summonCooldown <= 0) {
				summon(world, form);
			}
			if (form >= 3 && --leapCooldown <= 0 && isOnGround()) {
				leap(world, target, form);
			}
			if (form >= 4) {
				if (beamCharge >= 0) {
					chargeBeam(world, target, form);
				} else if (--beamCooldown <= 0) {
					beamCharge = 30;
					world.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 3.0F, 1.0F);
				}
			}
			if (form >= 5 && --strikeCooldown <= 0) {
				strike(world, target);
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
