package fr.evotool.spider;

import fr.evotool.component.ModComponents;
import fr.evotool.item.ModItems;
import fr.evotool.network.WebPayload;
import fr.evotool.network.WebStatePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.Direction;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Gant de Spider-Man, côté serveur : l'éveil du rituel (la première fois qu'on le tient), les pouvoirs de toile
 * (tir, traction, bombe, mur), les toiles qui disparaissent toutes seules, le sens d'araignée (les ennemis qui nous
 * visent brillent, un projectile esquivé toutes les 2 s), l'agilité, plus de dégâts de chute, et la toile montrée
 * aux autres joueurs. Le balancement et la traction vers un bloc sont calculés par le client du joueur (fluide).
 */
public final class SpiderGlove {
	/** Portée du tir de toile et de la bombe. */
	public static final double SHOT_RANGE = 24.0;
	/** Portée de la traction (créature tirée vers soi, ou on file vers un bloc). */
	public static final double PULL_RANGE = 32.0;
	/** Les toiles posées par les pouvoirs disparaissent au bout de 10 s. */
	private static final int WEB_LIFE = 200;
	/** Sens d'araignée : il sent les ennemis qui nous visent jusqu'à cette distance… */
	private static final double SENSE_RANGE = 32.0;
	/** … et il esquive un projectile toutes les 2 s. */
	private static final int DODGE_COOLDOWN = 40;
	/** Rayon de la bombe de toile. */
	private static final double BOMB_RADIUS = 4.5;

	private record TempWeb(ServerWorld world, BlockPos pos, int expires) {
	}

	private static final List<TempWeb> WEBS = new ArrayList<>();
	/** Fin de recharge de chaque pouvoir de toile, par joueur (chacun a la sienne : on peut les enchaîner). */
	private static final Map<UUID, int[]> NEXT_USE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_DODGE = new HashMap<>();
	private static final Map<UUID, Set<UUID>> THREATS = new HashMap<>();
	private static final Map<UUID, Vec3d> ANCHORS = new HashMap<>();

	/** Ce que vise le joueur : une créature (ou null), sinon le bloc (ou rien), et le bout du trait. */
	public record Aim(Vec3d eye, Vec3d end, BlockHitResult block, LivingEntity target) {
		public boolean onBlock() {
			return target == null && block.getType() == HitResult.Type.BLOCK;
		}
	}

	private SpiderGlove() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			if (tick % 10 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					awakenInventory(player);
					if (holds(player) && player.isAlive()) {
						agility(player);
						spiderSense(player);
					} else {
						THREATS.remove(player.getUuid());
					}
				}
			}
			for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
				TempWeb web = it.next();
				if (tick >= web.expires()) {
					if (web.world().getBlockState(web.pos()).isOf(Blocks.COBWEB)) {
						web.world().removeBlock(web.pos(), false);
					}
					it.remove();
				}
			}
		});
		// Spider-Man ne se fait pas mal en tombant, et son sens d'araignée lui fait esquiver les projectiles
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player) || !holds(player)) {
				return true;
			}
			if (source.isIn(DamageTypeTags.IS_FALL)) {
				return false;
			}
			return !(source.isIn(DamageTypeTags.IS_PROJECTILE) && dodge(player));
		});
		ServerPlayNetworking.registerGlobalReceiver(WebPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			Vec3d anchor = new Vec3d(payload.x(), payload.y(), payload.z());
			if (payload.attached() && (!holds(player) || anchor.squaredDistanceTo(player.getEyePos()) > 64 * 64)) {
				return;
			}
			setWeb(player, payload.attached() ? anchor : null);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			ANCHORS.remove(id);
			NEXT_USE.remove(id);
			NEXT_DODGE.remove(id);
			THREATS.remove(id);
		});
	}

	/** Le joueur tient-il le gant (dans une main ou l'autre) ? */
	public static boolean holds(PlayerEntity player) {
		return player.getMainHandStack().isOf(ModItems.SPIDER_GLOVE) || player.getOffHandStack().isOf(ModItems.SPIDER_GLOVE);
	}

	/** Le gant tenu (main principale d'abord), ou une pile vide. */
	public static ItemStack glove(PlayerEntity player) {
		if (player.getMainHandStack().isOf(ModItems.SPIDER_GLOVE)) {
			return player.getMainHandStack();
		}
		return player.getOffHandStack().isOf(ModItems.SPIDER_GLOVE) ? player.getOffHandStack() : ItemStack.EMPTY;
	}

	/** Ce que vise le joueur jusqu'à {@code range} blocs (marche aussi côté client). */
	public static Aim aim(PlayerEntity player, double range) {
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVector().multiply(range));
		BlockHitResult block = player.getEntityWorld().raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.MISS ? far : block.getPos();
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && e != player && !e.isSpectator(), eye.squaredDistanceTo(end));
		LivingEntity target = aimed != null ? (LivingEntity) aimed.getEntity() : null;
		return new Aim(eye, target != null ? target.getBoundingBox().getCenter() : end, block, target);
	}

	// ------------------------------------------------------------------ les pouvoirs de toile

	/** Touche G : le gant tenu passe au pouvoir suivant (renvoie le nouveau, ou null sans gant). */
	public static WebPower cyclePower(ServerPlayerEntity player) {
		ItemStack glove = glove(player);
		if (glove.isEmpty()) {
			return null;
		}
		WebPower next = WebPower.of(glove).next();
		glove.set(ModComponents.WEB_POWER, next.ordinal());
		player.sendMessage(Text.translatable("message.evotool.web_power", Text.translatable(next.translationKey()))
				.formatted(Formatting.RED, Formatting.BOLD), true);
		player.getEntityWorld().playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 0.8F, 1.6F);
		return next;
	}

	/** Accroupi + clic droit : le pouvoir choisi, s'il est rechargé. */
	public static boolean usePower(ServerPlayerEntity player, WebPower power) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		int[] next = NEXT_USE.computeIfAbsent(player.getUuid(), id -> new int[WebPower.values().length]);
		if (now < next[power.ordinal()]) {
			return false;
		}
		next[power.ordinal()] = now + power.cooldown();
		return switch (power) {
			case SHOT -> webShot(player);
			case PULL -> pull(player);
			case BOMB -> webBomb(player) > 0;
			case WALL -> webWall(player) > 0;
		};
	}

	/** Le fil de toile, des yeux du joueur jusqu'à {@code to}. */
	private static void silk(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d step = to.subtract(from);
		int count = (int) (step.length() * 2);
		ItemStackParticleEffect silk = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
		for (int i = 1; i <= count; i++) {
			Vec3d p = from.add(step.multiply(i / (double) count)).add(0, -0.2, 0);
			world.spawnParticles(silk, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** Donne une vitesse à une créature (et la fait vraiment bouger si c'est un joueur : son client décide). */
	private static void launch(LivingEntity target, Vec3d velocity) {
		target.setVelocity(velocity);
		target.fallDistance = 0;
		if (target instanceof ServerPlayerEntity other) {
			other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
		}
	}

	/**
	 * Traction : la créature visée est arrachée du sol et vole vers nous (elle retombe à quelques blocs devant).
	 * Sur un bloc, c'est le client du joueur qui le fait filer vers lui ; ici, juste le fil. Public pour l'auto-test.
	 */
	public static boolean pull(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Aim aim = aim(player, PULL_RANGE);
		silk(world, aim.eye(), aim.end());
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.4F);
		if (aim.target() == null) {
			return aim.onBlock();
		}
		LivingEntity target = aim.target();
		Vec3d d = player.getEntityPos().subtract(target.getEntityPos());
		double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
		Vec3d flat = new Vec3d(d.x, 0, d.z).multiply(0.08);
		if (flat.length() > 2.5) {
			flat = flat.normalize().multiply(2.5);
		}
		launch(target, new Vec3d(flat.x, 0.4 + horizontal * 0.015 + Math.max(0.0, d.y) * 0.08, flat.z));
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.2F, 0.7F);
		return true;
	}

	/** Bombe de toile : elle éclate là où l'on vise ; renvoie le nombre de toiles posées et de créatures prises. */
	public static int webBomb(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		Aim aim = aim(player, SHOT_RANGE);
		Vec3d at = aim.target() != null ? aim.target().getEntityPos().add(0, 0.5, 0)
				: aim.onBlock() ? Vec3d.ofCenter(aim.block().getBlockPos().offset(aim.block().getSide())) : aim.end();
		silk(world, aim.eye(), at);
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
				at.x, at.y, at.z, 90, 1.6, 1.2, 1.6, 0.15);
		world.spawnParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 30, 1.2, 0.8, 1.2, 0.05);
		world.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.5F, 0.6F);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5F, 1.6F);
		int count = 0;
		// les toiles : une boule autour du point d'impact (jamais sur nous)
		BlockPos center = BlockPos.ofFloored(at);
		Box self = player.getBoundingBox().expand(0.6);
		for (BlockPos pos : BlockPos.iterate(center.add(-2, -1, -2), center.add(2, 2, 2))) {
			int dx = pos.getX() - center.getX();
			int dy = pos.getY() - center.getY();
			int dz = pos.getZ() - center.getZ();
			if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2 && !self.intersects(new Box(pos))
					&& placeWeb(world, pos.toImmutable(), now)) {
				count++;
			}
		}
		// ceux qui sont pris dedans : englués, affaiblis, blessés
		for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, Box.of(at, BOMB_RADIUS * 2, BOMB_RADIUS * 2, BOMB_RADIUS * 2),
				e -> e != player && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(at) <= BOMB_RADIUS * BOMB_RADIUS)) {
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 120, 4), player);
			victim.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 120, 1), player);
			victim.damage(world, player.getDamageSources().playerAttack(player), 4.0F);
			placeWeb(world, victim.getBlockPos(), now);
			count++;
		}
		return count;
	}

	/** Mur de toile : 5 blocs de large, 3 de haut, à 3 blocs devant soi ; renvoie le nombre de toiles posées. */
	public static int webWall(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		Direction facing = player.getHorizontalFacing();
		Direction side = facing.rotateYClockwise();
		BlockPos base = player.getBlockPos().offset(facing, 3);
		int count = 0;
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < 3; h++) {
				BlockPos pos = base.offset(side, w).up(h);
				if (placeWeb(world, pos, now)) {
					count++;
					world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
							pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
				}
			}
		}
		silk(world, player.getEyePos(), Vec3d.ofCenter(base.up()));
		world.playSound(null, base, SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.5F, 0.6F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.2F);
		return count;
	}

	// ------------------------------------------------------------------ sens d'araignée, agilité

	/** Agilité d'araignée, gant en main : saut amélioré et vitesse (sans particules). Public pour l'auto-test. */
	public static void agility(ServerPlayerEntity player) {
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 40, 1, true, false, true));
		player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, true, false, true));
	}

	/**
	 * Sens d'araignée : les créatures qui nous visent brillent (on les voit à travers les murs) ; une alerte quand une
	 * nouvelle arrive. Renvoie le nombre de menaces. Public pour l'auto-test.
	 */
	public static int spiderSense(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Set<UUID> threats = new HashSet<>();
		for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(SENSE_RANGE),
				mob -> mob.isAlive() && mob.getTarget() == player)) {
			mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 30, 0, true, false), player);
			threats.add(mob.getUuid());
		}
		Set<UUID> before = THREATS.put(player.getUuid(), threats);
		boolean fresh = threats.stream().anyMatch(id -> before == null || !before.contains(id));
		if (fresh) {
			world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.8F);
			player.sendMessage(Text.translatable("message.evotool.spider_sense", threats.size()).formatted(Formatting.RED), true);
		}
		return threats.size();
	}

	/** Esquive d'un projectile, si le sens d'araignée est prêt (une fois toutes les 2 s). Public pour l'auto-test. */
	public static boolean dodge(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		if (now < NEXT_DODGE.getOrDefault(player.getUuid(), 0)) {
			return false;
		}
		NEXT_DODGE.put(player.getUuid(), now + DODGE_COOLDOWN);
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.4, 0.6, 0.4, 0.05);
		world.spawnParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.8F, 1.6F);
		player.sendMessage(Text.translatable("message.evotool.spider_dodge").formatted(Formatting.RED, Formatting.ITALIC), true);
		return true;
	}

	/** La toile du joueur s'accroche à ce point (null : lâchée) ; ceux qui le voient la voient aussi. */
	public static void setWeb(ServerPlayerEntity player, Vec3d anchor) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (anchor == null) {
			ANCHORS.remove(player.getUuid());
		} else {
			ANCHORS.put(player.getUuid(), anchor);
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
					anchor.x, anchor.y, anchor.z, 12, 0.2, 0.2, 0.2, 0.05);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		}
		WebStatePayload state = anchor == null
				? new WebStatePayload(player.getId(), false, 0, 0, 0)
				: new WebStatePayload(player.getId(), true, anchor.x, anchor.y, anchor.z);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(viewer, WebStatePayload.ID)) {
				ServerPlayNetworking.send(viewer, state);
			}
		}
	}

	/** La toile du joueur (null s'il n'en a pas) — auto-test. */
	public static Vec3d anchor(ServerPlayerEntity player) {
		return ANCHORS.get(player.getUuid());
	}

	// ------------------------------------------------------------------ le rituel

	/** Le gant fraîchement sorti du rituel s'éveille dans l'inventaire : la foudre tombe sur son porteur. */
	private static void awakenInventory(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(ModItems.SPIDER_GLOVE) && !stack.contains(ModComponents.AWAKENED)) {
				awaken(player, stack);
			}
		}
	}

	/** L'éveil du gant (public pour l'auto-test). */
	public static void awaken(ServerPlayerEntity player, ItemStack glove) {
		glove.set(ModComponents.AWAKENED, Unit.INSTANCE);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (lightning != null) {
			lightning.refreshPositionAfterTeleport(player.getEntityPos());
			lightning.setCosmetic(true);
			world.spawnEntity(lightning);
		}
		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), x, y, z, 60, 1.0, 1.0, 1.0, 0.1);
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 60, 0.6, 0.8, 0.6, 0.5);
		world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.8, 1.2, 0.8, 0.1);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.6F, 1.4F);
		player.sendMessage(Text.translatable("message.evotool.spider_glove_awakened").formatted(Formatting.RED, Formatting.BOLD), false);
	}

	// ------------------------------------------------------------------ le tir de toile

	/** Tir de toile : une créature touchée est engluée, sinon une toile se pose sur le bloc (public pour l'auto-test). */
	public static boolean webShot(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		Aim aim = aim(player, SHOT_RANGE);
		silk(world, aim.eye(), aim.end());
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.8F);
		if (aim.target() != null) {
			LivingEntity target = aim.target();
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1), player);
			target.damage(world, player.getDamageSources().playerAttack(player), 2.0F);
			placeWeb(world, target.getBlockPos(), now);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.2F, 0.8F);
			return true;
		}
		if (aim.onBlock()) {
			placeWeb(world, aim.block().getBlockPos().offset(aim.block().getSide()), now);
		}
		return true;
	}

	/** Une toile temporaire (seulement dans l'air ou ce qui se remplace, comme l'herbe) ; vrai si elle est posée. */
	private static boolean placeWeb(ServerWorld world, BlockPos pos, int now) {
		if (world.getBlockState(pos).isReplaceable() && !world.getBlockState(pos).isOf(Blocks.WATER)
				&& !world.getBlockState(pos).isOf(Blocks.LAVA) && !world.getBlockState(pos).isOf(Blocks.COBWEB)) {
			world.setBlockState(pos, Blocks.COBWEB.getDefaultState());
			WEBS.add(new TempWeb(world, pos, now + WEB_LIFE));
			return true;
		}
		return false;
	}
}
