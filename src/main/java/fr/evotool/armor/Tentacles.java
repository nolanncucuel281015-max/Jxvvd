package fr.evotool.armor;

import fr.evotool.EvoTool;
import fr.evotool.network.TentacleStatePayload;
import fr.evotool.upgrade.Burrow;
import fr.evotool.upgrade.GiantItem;
import fr.evotool.upgrade.InfinityWings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Les tentacules de l'armure abyssale (ensemble complet au niveau 150).
 * <ul>
 *   <li>Clic gauche à main nue : une tentacule noire jaillit du dos (« tchouf ») jusqu'à 14 blocs et frappe tout
 *   ce qu'elle traverse.</li>
 *   <li>Touche J : mode déplacement. Les tentacules tirent le joueur à toute vitesse, y compris sous l'eau et à
 *   travers la terre (elles creusent devant lui).</li>
 * </ul>
 * Les tentacules du dos sont dessinées par le client (TentacleFeature) ; le coup est fait d'objets d'affichage
 * (modèles 3D tentacle/segment et tentacle/tip), visibles par tout le monde.
 */
public final class Tentacles {
	/** Tag posé sur le joueur tant que le mode déplacement est allumé (sauvegardé avec lui). */
	public static final String MOVE_TAG = "evotool_tentacle_move";
	/** Vitesse de vol en mode déplacement (créatif : 0,05). */
	public static final float MOVE_SPEED = 0.2F;
	private static final double STRIKE_RANGE = 14.0;
	private static final int STRIKE_COOLDOWN = 12;
	/** Durée d'un coup : la tentacule jaillit en 3 ticks, frappe, puis se rétracte. */
	private static final float[] EXTENSION = {0.35F, 0.7F, 1.0F, 1.0F, 0.75F, 0.5F, 0.25F, 0.05F};
	private static final int HIT_TICK = 2;
	private static final float SEGMENT = 0.5F;
	/** Rayon du modèle de segment (3 pixels) : sert à calculer l'épaisseur voulue. */
	private static final float MODEL_RADIUS = 3.0F / 16.0F;
	private static final Quaternionf FLIP = new Quaternionf().rotationY((float) Math.PI);

	private static final class Strike {
		final ServerPlayerEntity player;
		final ServerWorld world;
		final Vec3d origin;
		final Vec3d control;
		final Vec3d end;
		final List<DisplayEntity.ItemDisplayEntity> segments;
		final float height;
		int age;

		Strike(ServerPlayerEntity player, ServerWorld world, Vec3d origin, Vec3d control, Vec3d end,
				List<DisplayEntity.ItemDisplayEntity> segments, float height) {
			this.player = player;
			this.world = world;
			this.origin = origin;
			this.control = control;
			this.end = end;
			this.segments = segments;
			this.height = height;
		}
	}

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
	private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
	private static final Map<UUID, Vec3d> SAFE_POS = new HashMap<>();

	private Tentacles() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (player.getCommandTags().contains(MOVE_TAG)) {
					moveTick(player, tick);
				}
			}
			tickStrikes();
		});
		// En mode déplacement : ni étouffement, ni noyade, ni lave, ni chute.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player && player.getCommandTags().contains(MOVE_TAG)
						&& (source.isOf(DamageTypes.IN_WALL) || source.isIn(DamageTypeTags.IS_DROWNING)
						|| source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_FALL))));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.getPlayer().getCommandTags().contains(MOVE_TAG)) {
				Burrow.syncClient(handler.getPlayer());
				broadcast(handler.getPlayer(), true);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			COOLDOWN.remove(id);
			LAST_POS.remove(id);
			SAFE_POS.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Strike strike : STRIKES) {
				strike.segments.forEach(GiantItem::discard);
			}
			STRIKES.clear();
		});
	}

	/** L'ensemble abyssal complet au niveau 150 donne les tentacules. */
	public static boolean hasTentacles(PlayerEntity player) {
		return ArmorSet.setLevel(player) >= ArmorSet.TENTACLE_LEVEL;
	}

	// ------------------------------------------------------------------ coup de tentacule (clic gauche à main nue)

	/** Le joueur a balancé le bras (clic gauche) : à main nue, une tentacule frappe. */
	public static void onSwing(ServerPlayerEntity player) {
		if (!player.getMainHandStack().isEmpty() || player.isSpectator() || !hasTentacles(player)) {
			return;
		}
		strike(player);
	}

	/** Lance une tentacule droit devant (public pour l'auto-test). Faux si elle recharge encore. */
	public static boolean strike(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		Integer ready = COOLDOWN.get(player.getUuid());
		if (ready != null && now < ready) {
			return false;
		}
		COOLDOWN.put(player.getUuid(), now + STRIKE_COOLDOWN);

		Vec3d look = player.getRotationVector();
		Vec3d eye = player.getEyePos();
		Vec3d right = new Vec3d(-look.z, 0, look.x).normalize();
		if (!Double.isFinite(right.x)) {
			right = new Vec3d(1, 0, 0);
		}
		// elle part du dos, à droite de la tête : on la voit jaillir même en vue à la première personne
		Vec3d origin = eye.add(right.multiply(0.4)).add(0, -0.45, 0).subtract(look.multiply(0.25));
		Vec3d far = eye.add(look.multiply(STRIKE_RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.MISS ? far : block.getPos();
		// la tentacule file droit sur la créature visée
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
				e -> e instanceof LivingEntity && e != player && e.isAlive(), eye.squaredDistanceTo(end));
		if (aimed != null) {
			end = aimed.getEntity().getBoundingBox().getCenter();
		}
		double length = origin.distanceTo(end);
		Vec3d mid = origin.add(end).multiply(0.5);
		Vec3d control = mid.add(0, 0.15 + length * 0.04, 0).add(right.multiply(length * 0.03));

		int count = Math.max(3, (int) Math.ceil(length / SEGMENT));
		float height = (float) (length * 2 + 4);
		List<DisplayEntity.ItemDisplayEntity> segments = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ItemStack stack = new ItemStack(Items.INK_SAC);
			stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id(i == count - 1 ? "tentacle/tip" : "tentacle/segment"));
			DisplayEntity.ItemDisplayEntity segment = GiantItem.create(world, stack, ItemDisplayContext.NONE,
					origin.add(0, -height / 2, 0), 0.0F, new Quaternionf(), 0.0F, 1);
			segment.setBrightness(null);
			segment.setTeleportDuration(0);
			segment.setDisplayWidth(height);
			segment.setDisplayHeight(height);
			segments.add(segment);
		}
		Strike strike = new Strike(player, world, origin, control, end, segments, height);
		pose(strike, 0.0F);
		STRIKES.add(strike);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.2F, 0.6F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	private static Vec3d bezier(Strike strike, double t) {
		double u = 1 - t;
		return strike.origin.multiply(u * u).add(strike.control.multiply(2 * u * t)).add(strike.end.multiply(t * t));
	}

	/** Place les segments jusqu'à la fraction {@code extension} du trajet (les autres sont repliés à zéro). */
	private static void pose(Strike strike, float extension) {
		int count = strike.segments.size();
		Vec3d base = strike.origin.add(0, -strike.height / 2, 0);
		for (int i = 0; i < count; i++) {
			float t0 = (float) i / count;
			float t1 = (float) (i + 1) / count;
			DisplayEntity.ItemDisplayEntity segment = strike.segments.get(i);
			if (t0 >= extension) {
				segment.setTransformation(new AffineTransformation(new Vector3f(), new Quaternionf(), new Vector3f(0), FLIP));
			} else {
				Vec3d a = bezier(strike, t0);
				Vec3d b = bezier(strike, Math.min(t1, extension));
				Vec3d dir = b.subtract(a);
				float length = (float) dir.length() + 0.04F;
				Vec3d unit = dir.lengthSquared() < 1.0E-6 ? new Vec3d(0, 1, 0) : dir.normalize();
				// épaisse au départ, fine au bout
				float width = (0.32F - 0.2F * t0) / (2 * MODEL_RADIUS);
				Quaternionf rotation = new Quaternionf().rotationTo(0, 1, 0, (float) unit.x, (float) unit.y, (float) unit.z);
				Vec3d center = a.add(unit.multiply(length / 2)).subtract(base);
				segment.setTransformation(new AffineTransformation(new Vector3f((float) center.x, (float) center.y, (float) center.z),
						rotation, new Vector3f(width, length, width), FLIP));
			}
			segment.setInterpolationDuration(1);
			segment.setStartInterpolation(0);
		}
	}

	private static void tickStrikes() {
		for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
			Strike strike = it.next();
			strike.age++;
			if (strike.age >= EXTENSION.length || strike.player.isRemoved()) {
				strike.segments.forEach(GiantItem::discard);
				it.remove();
				continue;
			}
			pose(strike, EXTENSION[strike.age]);
			if (strike.age == HIT_TICK) {
				hit(strike);
			}
		}
	}

	/** Au bout de sa course, la tentacule frappe tout ce qu'elle traverse. */
	private static void hit(Strike strike) {
		ServerPlayerEntity player = strike.player;
		ServerWorld world = strike.world;
		Vec3d dir = strike.end.subtract(strike.origin).normalize();
		float damage = (float) (player.getAttributeValue(EntityAttributes.ATTACK_DAMAGE) * 1.5 + 6.0);
		Box area = new Box(strike.origin, strike.end).expand(1.5);
		boolean touched = false;
		for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
			Vec3d center = target.getBoundingBox().getCenter();
			if (distanceToPath(strike, center) > 0.9 + target.getWidth() / 2) {
				continue;
			}
			target.damage(world, player.getDamageSources().playerAttack(player), damage);
			target.takeKnockback(1.6, -dir.x, -dir.z);
			world.spawnParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 20, 0.3, 0.3, 0.3, 0.1);
			world.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x, center.y, center.z, 1, 0, 0, 0, 0);
			touched = true;
		}
		Vec3d end = strike.end;
		world.spawnParticles(ParticleTypes.SQUID_INK, end.x, end.y, end.z, 12, 0.2, 0.2, 0.2, 0.05);
		BlockState block = world.getBlockState(BlockPos.ofFloored(end.subtract(dir.multiply(0.1)).add(dir.multiply(0.2))));
		if (!block.isAir()) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block), end.x, end.y, end.z, 20, 0.2, 0.2, 0.2, 0.15);
		}
		world.playSound(null, BlockPos.ofFloored(end), touched ? SoundEvents.ENTITY_PLAYER_ATTACK_STRONG : SoundEvents.ENTITY_SQUID_SQUIRT,
				SoundCategory.PLAYERS, 1.2F, 0.7F);
	}

	private static double distanceToPath(Strike strike, Vec3d point) {
		double best = Double.MAX_VALUE;
		Vec3d previous = strike.origin;
		for (int i = 1; i <= 16; i++) {
			Vec3d next = bezier(strike, i / 16.0);
			Vec3d segment = next.subtract(previous);
			double len2 = segment.lengthSquared();
			double t = len2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, point.subtract(previous).dotProduct(segment) / len2));
			best = Math.min(best, previous.add(segment.multiply(t)).distanceTo(point));
			previous = next;
		}
		return best;
	}

	// ------------------------------------------------------------------ mode déplacement (touche J)

	/** Touche J : allume ou éteint le mode déplacement. */
	public static void toggle(ServerPlayerEntity player) {
		if (player.getCommandTags().contains(MOVE_TAG)) {
			stopMoving(player, true);
			return;
		}
		if (!hasTentacles(player)) {
			player.sendMessage(Text.translatable("message.evotool.tentacles_locked").formatted(Formatting.DARK_PURPLE), true);
			return;
		}
		startMoving(player);
	}

	public static void startMoving(ServerPlayerEntity player) {
		if (Burrow.isBurrowing(player)) {
			Burrow.stop(player, false);
		}
		player.addCommandTag(MOVE_TAG);
		LAST_POS.put(player.getUuid(), player.getEntityPos());
		SAFE_POS.put(player.getUuid(), player.getEntityPos());
		Burrow.syncClient(player);
		PlayerAbilities abilities = player.getAbilities();
		abilities.allowFlying = true;
		abilities.flying = true;
		abilities.setFlySpeed(MOVE_SPEED);
		player.sendAbilitiesUpdate();
		broadcast(player, true);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.1);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.5F, 0.5F);
		player.sendMessage(Text.translatable("message.evotool.tentacles_on").formatted(Formatting.DARK_PURPLE), true);
	}

	/** Éteint le mode ; avec {@code rescue}, un joueur resté dans la roche est remonté à la surface. */
	public static void stopMoving(ServerPlayerEntity player, boolean rescue) {
		player.removeCommandTag(MOVE_TAG);
		Burrow.syncClient(player);
		if (!player.isCreative() && !player.isSpectator()) {
			player.getAbilities().flying = false;
		}
		InfinityWings.restoreAbilities(player);
		broadcast(player, false);
		StatusEffectInstance vision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= 260) {
			player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (rescue && !world.isSpaceEmpty(player, player.getBoundingBox().contract(0.01))) {
			int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
			player.requestTeleport(player.getX(), top + 0.1, player.getZ());
		}
		player.sendMessage(Text.translatable("message.evotool.tentacles_off").formatted(Formatting.GRAY), true);
	}

	private static void moveTick(ServerPlayerEntity player, int tick) {
		if (!player.isAlive() || player.isSpectator() || !hasTentacles(player)) {
			stopMoving(player, true);
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		PlayerAbilities abilities = player.getAbilities();
		if (!abilities.allowFlying || abilities.getFlySpeed() != MOVE_SPEED) {
			abilities.allowFlying = true;
			abilities.flying = true;
			abilities.setFlySpeed(MOVE_SPEED);
			player.sendAbilitiesUpdate();
		}
		// garde-fous : ni la bedrock (ni aucun bloc incassable), ni le fond du monde
		Box box = player.getBoundingBox();
		UUID id = player.getUuid();
		if (player.getY() < world.getBottomY() + 1 || Burrow.touchesUnbreakable(world, box)) {
			Vec3d safe = SAFE_POS.getOrDefault(id, player.getEntityPos());
			player.requestTeleport(safe.x, safe.y, safe.z);
			return;
		}
		SAFE_POS.put(id, player.getEntityPos());
		player.setAir(player.getMaxAir());
		if (player.isOnFire()) {
			player.extinguish();
		}
		if (tick % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
		}
		if (tick % 40 == 0) {
			broadcast(player, true);    // pour les joueurs qui arrivent près de lui
		}

		// les tentacules creusent devant le joueur… puis « tchouf »
		Vec3d last = LAST_POS.getOrDefault(id, player.getEntityPos());
		Vec3d now = player.getEntityPos();
		LAST_POS.put(id, now);
		boolean moving = now.squaredDistanceTo(last) > 0.01;
		boolean inside = !world.isSpaceEmpty(player, box.contract(0.01)) || player.isTouchingWater();
		if (moving && inside && tick % 3 == 0) {
			Vec3d ahead = player.getEyePos().add(player.getRotationVector().multiply(1.2));
			BlockState block = world.getBlockState(BlockPos.ofFloored(ahead));
			if (!block.isAir()) {
				world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, block), ahead.x, ahead.y, ahead.z,
						8, 0.4, 0.4, 0.4, 0.15);
			}
			world.spawnParticles(ParticleTypes.SQUID_INK, ahead.x, ahead.y, ahead.z, 2, 0.3, 0.3, 0.3, 0.02);
			if (tick % 9 == 0) {
				world.playSound(null, BlockPos.ofFloored(ahead), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 0.8F,
						0.7F + world.getRandom().nextFloat() * 0.3F);
				if (!block.isAir()) {
					world.playSound(null, BlockPos.ofFloored(ahead), block.getSoundGroup().getBreakSound(), SoundCategory.PLAYERS, 0.7F, 0.8F);
				}
			}
		}
	}

	/** Prévient le joueur et ceux qui le voient : ses tentacules passent (ou non) en mode déplacement. */
	private static void broadcast(ServerPlayerEntity player, boolean moving) {
		TentacleStatePayload payload = new TentacleStatePayload(player.getId(), moving);
		List<ServerPlayerEntity> targets = new ArrayList<>(PlayerLookup.tracking(player));
		targets.add(player);
		for (ServerPlayerEntity target : targets) {
			if (ServerPlayNetworking.canSend(target, TentacleStatePayload.ID)) {
				ServerPlayNetworking.send(target, payload);
			}
		}
	}

	/** Nombre de coups de tentacule en cours (auto-test). */
	public static int activeStrikes() {
		return STRIKES.size();
	}

	/** Fait avancer les coups de tentacule d'un tick (auto-test). */
	public static void tickForTest() {
		tickStrikes();
	}
}
