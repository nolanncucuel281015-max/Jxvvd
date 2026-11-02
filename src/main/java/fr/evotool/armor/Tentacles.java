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
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
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
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
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
	/** Vitesse sous terre quand les tentacules creusent (créatif : 0,05). */
	public static final float DIG_SPEED = 0.2F;
	/** Au sol, en mode déplacement : +250 % de vitesse et on monte les blocs sans sauter. */
	public static final Identifier SPEED_ID = EvoTool.id("tentacle_speed");
	private static final Identifier STEP_ID = EvoTool.id("tentacle_step");
	private static final double GROUND_SPEED_BONUS = 2.5;
	private static final double STEP_BONUS = 0.9;
	private static final double STRIKE_RANGE = 14.0;
	private static final int STRIKE_COOLDOWN = 20;
	/** Un coup : la tentacule jaillit en 6 ticks (on la voit partir), frappe, reste tendue puis se rétracte. */
	private static final float[] EXTENSION = {0.12F, 0.26F, 0.42F, 0.58F, 0.74F, 0.88F, 1.0F, 1.0F, 1.0F,
			0.88F, 0.74F, 0.58F, 0.42F, 0.26F, 0.12F};
	private static final int HIT_TICK = 6;
	/**
	 * Attraper : la tentacule met 6 ticks à atteindre le monstre, puis le tient aussi longtemps qu'on veut (pour le
	 * ramener à la base) : elle ne le lâche qu'au clic droit suivant.
	 */
	private static final int GRAB_REACH = 6;
	/** Marge autour de la tentacule pour la zone de rendu de ses segments (blocs). */
	private static final float BOX_MARGIN = 3.0F;
	/** Regarder plus bas que ça (degrés) au clic droit : le monstre est posé doucement au lieu d'être projeté. */
	private static final float DROP_PITCH = 50.0F;
	private static final int RELEASE_TICKS = 5;
	/** Distance à laquelle la tentacule tient ce qu'elle a attrapé, devant le joueur (blocs). */
	private static final double HOLD_DISTANCE = 6.0;
	private static final float SEGMENT = 0.5F;
	/** Rayon du modèle de segment (3 pixels) : sert à calculer l'épaisseur voulue. */
	private static final float MODEL_RADIUS = 3.0F / 16.0F;
	private static final Quaternionf FLIP = new Quaternionf().rotationY((float) Math.PI);

	/** Une tentacule faite d'objets d'affichage : un coup (clic gauche) ou une prise (clic droit). */
	private static final class Strike {
		final ServerPlayerEntity player;
		final ServerWorld world;
		/**
		 * Position des entités d'affichage : les segments sont placés par rapport à elle. Elle est juste sous le point
		 * le plus bas de la tentacule (la zone de rendu d'une entité d'affichage monte depuis sa position) : jamais
		 * enfouie dans la roche, où le client ne dessine pas les entités. Elle suit une prise qui bouge.
		 */
		Vec3d anchor;
		/** Taille de la zone de rendu (largeur = hauteur). */
		float box;
		Vec3d origin;
		Vec3d control;
		Vec3d end;
		final List<DisplayEntity.ItemDisplayEntity> segments;
		int age;
		/** Monstre attrapé (clic droit), sinon null. */
		LivingEntity grabbed;
		/** Après le lancer : la tentacule se rétracte pendant quelques ticks. */
		int releasing = -1;

		Strike(ServerPlayerEntity player, ServerWorld world, Vec3d origin, Vec3d control, Vec3d end,
				List<DisplayEntity.ItemDisplayEntity> segments, Vec3d anchor) {
			this.player = player;
			this.world = world;
			this.origin = origin;
			this.control = control;
			this.end = end;
			this.segments = segments;
			this.anchor = anchor;
		}
	}

	/** Un monstre projeté : il se fracasse sur ce qu'il touche. */
	private record Thrown(ServerPlayerEntity player, LivingEntity target, int start) {
	}

	private static final List<Strike> STRIKES = new ArrayList<>();
	private static final List<Thrown> THROWN = new ArrayList<>();
	private static final Map<UUID, Strike> GRABS = new HashMap<>();
	private static final Map<UUID, Integer> COOLDOWN = new HashMap<>();
	private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();

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
		// Un monstre tenu par une tentacule n'étouffe pas (on peut l'emmener sous terre) et ne se blesse pas en chutant.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!((source.isOf(DamageTypes.IN_WALL) || source.isIn(DamageTypeTags.IS_FALL)) && isHeld(entity)));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (handler.getPlayer().getCommandTags().contains(MOVE_TAG)) {
				broadcast(handler.getPlayer(), true);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.getPlayer().getUuid();
			COOLDOWN.remove(id);
			LAST_POS.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Strike strike : STRIKES) {
				strike.segments.forEach(GiantItem::discard);
			}
			STRIKES.clear();
			GRABS.clear();
			THROWN.clear();
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
		Vec3d origin = shoulder(player);
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
		STRIKES.add(chain(player, world, origin, end));
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.2F, 0.6F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	/** D'où part la tentacule : du dos, à droite de la tête (on la voit jaillir même en vue à la première personne). */
	private static Vec3d shoulder(ServerPlayerEntity player) {
		Vec3d look = player.getRotationVector();
		Vec3d right = new Vec3d(-look.z, 0, look.x);
		right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
		return player.getEyePos().add(right.multiply(0.55)).add(0, -0.55, 0).subtract(look.multiply(0.2));
	}

	private static Vec3d curve(Vec3d origin, Vec3d end, double wobble) {
		Vec3d look = end.subtract(origin);
		Vec3d right = new Vec3d(-look.z, 0, look.x);
		right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
		double length = look.length();
		// un bel arc vers le haut et sur le côté : on voit la tentacule s'enrouler, même de derrière le joueur
		return origin.add(end).multiply(0.5).add(0, 0.3 + length * 0.1, 0).add(right.multiply(length * 0.12 + wobble));
	}

	/** Crée une tentacule repliée (ses segments sont des objets d'affichage) d'{@code origin} vers {@code end}. */
	private static Strike chain(ServerPlayerEntity player, ServerWorld world, Vec3d origin, Vec3d end) {
		double length = origin.distanceTo(end);
		int count = Math.max(4, (int) Math.ceil(length / SEGMENT));
		Vec3d control = curve(origin, end, 0);
		Vec3d anchor = anchorFor(origin, control, end);
		float box = boxFor(origin, control, end);
		List<DisplayEntity.ItemDisplayEntity> segments = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ItemStack stack = new ItemStack(Items.INK_SAC);
			stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id(i == count - 1 ? "tentacle/tip" : "tentacle/segment"));
			DisplayEntity.ItemDisplayEntity segment = GiantItem.create(world, stack, ItemDisplayContext.NONE,
					anchor, 0.0F, new Quaternionf(), 0.0F, 1);
			segment.setTeleportDuration(1);
			segment.setDisplayWidth(box);
			segment.setDisplayHeight(box);
			segments.add(segment);
		}
		Strike strike = new Strike(player, world, origin, control, end, segments, anchor);
		strike.box = box;
		pose(strike, 0.0F);
		return strike;
	}

	/** Sous le point le plus bas de la tentacule (la courbe reste entre ses trois points), au milieu à l'horizontale. */
	private static Vec3d anchorFor(Vec3d a, Vec3d b, Vec3d c) {
		double minX = Math.min(a.x, Math.min(b.x, c.x));
		double maxX = Math.max(a.x, Math.max(b.x, c.x));
		double minZ = Math.min(a.z, Math.min(b.z, c.z));
		double maxZ = Math.max(a.z, Math.max(b.z, c.z));
		double minY = Math.min(a.y, Math.min(b.y, c.y));
		return new Vec3d((minX + maxX) / 2, minY - 0.3, (minZ + maxZ) / 2);
	}

	private static float boxFor(Vec3d a, Vec3d b, Vec3d c) {
		double dx = Math.max(a.x, Math.max(b.x, c.x)) - Math.min(a.x, Math.min(b.x, c.x));
		double dy = Math.max(a.y, Math.max(b.y, c.y)) - Math.min(a.y, Math.min(b.y, c.y));
		double dz = Math.max(a.z, Math.max(b.z, c.z)) - Math.min(a.z, Math.min(b.z, c.z));
		return (float) Math.max(dx, Math.max(dy, dz)) + 2 * BOX_MARGIN;
	}

	private static Vec3d bezier(Strike strike, double t) {
		double u = 1 - t;
		return strike.origin.multiply(u * u).add(strike.control.multiply(2 * u * t)).add(strike.end.multiply(t * t));
	}

	/** Place les segments jusqu'à la fraction {@code extension} du trajet (les autres sont repliés à zéro). */
	private static void pose(Strike strike, float extension) {
		int count = strike.segments.size();
		Vec3d base = strike.anchor;
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
				float width = (0.5F - 0.3F * t0) / (2 * MODEL_RADIUS);
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
			boolean done = strike.grabbed != null ? !tickGrab(strike) : strike.age >= EXTENSION.length;
			if (done || strike.player.isRemoved()) {
				strike.segments.forEach(GiantItem::discard);
				if (GRABS.get(strike.player.getUuid()) == strike) {
					GRABS.remove(strike.player.getUuid());
				}
				it.remove();
				continue;
			}
			if (strike.grabbed == null) {
				pose(strike, EXTENSION[strike.age]);
				if (strike.age == HIT_TICK) {
					hit(strike);
				}
			}
		}
		tickThrown();
	}

	// ------------------------------------------------------------------ attraper et projeter (clic droit à main nue)

	/**
	 * Clic droit à main nue : une tentacule attrape le monstre (ou le joueur) visé et le tient devant le joueur, sans jamais le
	 * lâcher tout seul ; un deuxième clic droit le projette là où il regarde, ou le pose doucement si on regarde
	 * par terre.
	 */
	public static boolean grabOrThrow(ServerPlayerEntity player) {
		if (player.isSpectator() || !hasTentacles(player)) {
			return false;
		}
		Strike held = GRABS.get(player.getUuid());
		if (held != null) {
			if (held.releasing < 0 && held.age > GRAB_REACH) {
				if (player.getPitch() > DROP_PITCH) {
					dropGrabbed(held);
				} else {
					throwGrabbed(held);
				}
			}
			return true;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVector().multiply(STRIKE_RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.MISS ? far : block.getPos();
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
				e -> e instanceof LivingEntity living && canGrab(player, living), eye.squaredDistanceTo(end));
		return aimed != null && grabEntity(player, (LivingEntity) aimed.getEntity());
	}

	/** Monstres, animaux… et joueurs : tout ce qui vit, sauf les boss, les spectateurs et ce qui est déjà tenu. */
	private static boolean canGrab(ServerPlayerEntity player, LivingEntity target) {
		if (target == player || !target.isAlive() || fr.evotool.evo.EvoProgression.isBoss(target) || isHeld(target)) {
			return false;
		}
		if (target instanceof PlayerEntity other) {
			// pas un spectateur, ni celui qui nous tient déjà
			Strike theirs = GRABS.get(other.getUuid());
			return !other.isSpectator() && (theirs == null || theirs.grabbed != player);
		}
		return true;
	}

	/** La tentacule file vers cette créature (ou ce joueur) pour l'attraper (public pour l'auto-test). */
	public static boolean grabEntity(ServerPlayerEntity player, LivingEntity target) {
		if (GRABS.containsKey(player.getUuid()) || !canGrab(player, target)) {
			return false;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Strike strike = chain(player, world, shoulder(player), target.getBoundingBox().getCenter());
		strike.grabbed = target;
		STRIKES.add(strike);
		GRABS.put(player.getUuid(), strike);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.2F, 0.5F);
		return true;
	}

	/**
	 * Déplace ce qu'on tient. Un joueur gère lui-même ses déplacements (côté client) : le serveur le téléporte
	 * (sans toucher à sa caméra) et annule sa vitesse, à chaque tick.
	 */
	private static void moveHeld(LivingEntity target, Vec3d to) {
		if (target instanceof ServerPlayerEntity held) {
			held.requestTeleport(to.x, to.y, to.z);
		} else {
			target.setPosition(to.x, to.y, to.z);
		}
	}

	/** Donne une vitesse à ce qu'on tient (envoyée au client si c'est un joueur). */
	private static void setHeldVelocity(LivingEntity target, Vec3d velocity) {
		target.setVelocity(Vec3d.ZERO);
		target.addVelocity(velocity.x, velocity.y, velocity.z);
		if (target instanceof ServerPlayerEntity held) {
			held.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(held));
		}
	}

	/** Un tick d'une prise ; faux quand elle est finie. */
	private static boolean tickGrab(Strike strike) {
		ServerPlayerEntity player = strike.player;
		LivingEntity target = strike.grabbed;
		if (strike.releasing >= 0) {
			strike.releasing--;
			pose(strike, Math.max(0.0F, strike.releasing / (float) RELEASE_TICKS));
			return strike.releasing > 0;
		}
		// elle ne lâche que si le monstre meurt, si le joueur meurt ou s'il change de dimension
		if (!target.isAlive() || target.isRemoved() || !player.isAlive() || target.getEntityWorld() != player.getEntityWorld()) {
			return false;
		}
		strike.origin = shoulder(player);
		if (strike.age < GRAB_REACH) {
			// elle file vers le monstre
			strike.end = target.getBoundingBox().getCenter();
			strike.control = curve(strike.origin, strike.end, 0);
			follow(strike);
			pose(strike, strike.age / (float) GRAB_REACH);
			return true;
		}
		ServerWorld world = strike.world;
		if (strike.age == GRAB_REACH) {
			Vec3d c = target.getBoundingBox().getCenter();
			world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 25, 0.3, 0.4, 0.3, 0.1);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.2F, 0.6F);
			if (target instanceof ServerPlayerEntity held) {
				held.sendMessage(Text.translatable("message.evotool.tentacle_grabbed", player.getDisplayName())
						.formatted(Formatting.DARK_PURPLE), true);
			}
		}
		// elle le ramène devant le joueur et le tient là, en l'air
		Vec3d hold = player.getEyePos().add(player.getRotationVector().multiply(HOLD_DISTANCE)).add(0, -0.3 - target.getHeight() / 2, 0);
		Vec3d now = target.getEntityPos();
		// en regardant vers le bas, le point de prise tombe dans le sol : on le remonte au-dessus
		for (int i = 0; i < 10 && !free(world, target, now, hold); i++) {
			hold = hold.add(0, 0.5, 0);
		}
		Vec3d next = strike.age < GRAB_REACH + 6 ? now.lerp(hold, 0.45) : hold;
		// pas dans un mur : sinon au-dessus de la tête du joueur, sinon à ses pieds ; s'il n'y a de place nulle part
		// (le joueur traverse la roche), il suit quand même, il ne peut pas étouffer
		Vec3d above = player.getEntityPos().add(0, player.getHeight() + 0.2, 0);
		Vec3d placed = null;
		for (Vec3d candidate : new Vec3d[] {next, hold, above, player.getEntityPos()}) {
			if (free(world, target, now, candidate)) {
				placed = candidate;
				break;
			}
		}
		if (placed == null && now.squaredDistanceTo(player.getEntityPos()) > 4 * 4) {
			placed = player.getEntityPos();
		}
		if (placed != null) {
			moveHeld(target, placed);
		}
		setHeldVelocity(target, Vec3d.ZERO);
		target.onLanding();
		strike.end = target.getBoundingBox().getCenter();
		strike.control = curve(strike.origin, strike.end, 0.25 * Math.sin(strike.age * 0.4));
		follow(strike);
		pose(strike, 1.0F);
		return true;
	}

	/** Y a-t-il la place pour {@code target} (actuellement en {@code now}) à la position {@code at} ? */
	private static boolean free(ServerWorld world, LivingEntity target, Vec3d now, Vec3d at) {
		return world.isSpaceEmpty(target, target.getBoundingBox().offset(at.subtract(now)));
	}

	/** Les segments d'une prise suivent la tentacule, que le joueur peut emmener aussi loin qu'il veut. */
	private static void follow(Strike strike) {
		Vec3d anchor = anchorFor(strike.origin, strike.control, strike.end);
		float box = boxFor(strike.origin, strike.control, strike.end);
		boolean moved = anchor.squaredDistanceTo(strike.anchor) > 1.0E-6;
		boolean resized = Math.abs(box - strike.box) > 0.5F;
		strike.anchor = anchor;
		if (resized) {
			strike.box = box;
		}
		for (DisplayEntity.ItemDisplayEntity segment : strike.segments) {
			if (moved) {
				segment.setPosition(anchor.x, anchor.y, anchor.z);
			}
			if (resized) {
				segment.setDisplayWidth(box);
				segment.setDisplayHeight(box);
			}
		}
	}

	/** Regarder par terre + clic droit : la tentacule pose le monstre doucement, là où on regarde (sans dégâts). */
	private static void dropGrabbed(Strike strike) {
		ServerPlayerEntity player = strike.player;
		LivingEntity target = strike.grabbed;
		ServerWorld world = strike.world;
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVector().multiply(HOLD_DISTANCE + 3.0));
		BlockHitResult hit = world.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			Vec3d spot = hit.getPos().subtract(player.getRotationVector().multiply(0.3));
			Vec3d now = target.getEntityPos();
			Vec3d feet = new Vec3d(spot.x, spot.y, spot.z);
			if (world.isSpaceEmpty(target, target.getBoundingBox().offset(feet.subtract(now)))) {
				moveHeld(target, feet);
			}
		}
		setHeldVelocity(target, Vec3d.ZERO);
		target.onLanding();
		Vec3d c = target.getBoundingBox().getCenter();
		world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 12, 0.3, 0.3, 0.3, 0.05);
		world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0F, 1.2F);
		strike.releasing = RELEASE_TICKS;
		GRABS.remove(player.getUuid());
	}

	/** Ce monstre est-il tenu par une tentacule ? */
	private static boolean isHeld(net.minecraft.entity.Entity entity) {
		for (Strike strike : GRABS.values()) {
			if (strike.grabbed == entity) {
				return true;
			}
		}
		return false;
	}

	/** « Tchouf » : le monstre est projeté là où le joueur regarde. */
	private static void throwGrabbed(Strike strike) {
		ServerPlayerEntity player = strike.player;
		LivingEntity target = strike.grabbed;
		Vec3d push = player.getRotationVector().multiply(2.6).add(0, 0.45, 0);
		setHeldVelocity(target, push);
		THROWN.add(new Thrown(player, target, strike.world.getServer().getTicks()));
		Vec3d c = target.getBoundingBox().getCenter();
		strike.world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 30, 0.3, 0.3, 0.3, 0.2);
		strike.world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.5F, 0.4F);
		strike.world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 1.2F, 0.6F);
		strike.releasing = RELEASE_TICKS;
		GRABS.remove(player.getUuid());
	}

	/** Les monstres projetés se fracassent sur le premier obstacle (ou le sol). */
	private static void tickThrown() {
		for (Iterator<Thrown> it = THROWN.iterator(); it.hasNext(); ) {
			Thrown thrown = it.next();
			LivingEntity target = thrown.target();
			ServerWorld world = (ServerWorld) target.getEntityWorld();
			int age = world.getServer().getTicks() - thrown.start();
			if (!target.isAlive() || age > 60) {
				it.remove();
				continue;
			}
			if (age > 3 && (target.horizontalCollision || target.isOnGround())) {
				Vec3d c = target.getBoundingBox().getCenter();
				target.damage(world, thrown.player().getDamageSources().playerAttack(thrown.player()), 10.0F);
				world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.1);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.2F, 0.6F);
				it.remove();
			}
		}
	}

	/** Le monstre que ce joueur tient avec une tentacule (auto-test). */
	public static LivingEntity grabbed(ServerPlayerEntity player) {
		Strike strike = GRABS.get(player.getUuid());
		return strike == null ? null : strike.grabbed;
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

	/**
	 * Mode déplacement : les longues tentacules portent le joueur au-dessus du sol, comme des pattes (TentacleTravel,
	 * côté client, le tient surélevé et le fait tirer par les tentacules) ; il court très vite et suit le relief.
	 * Accroupi, elles creusent : on plonge sous terre (voir {@link Burrow}) et on traverse la roche jusqu'à ressortir.
	 */
	public static void startMoving(ServerPlayerEntity player) {
		player.addCommandTag(MOVE_TAG);
		LAST_POS.put(player.getUuid(), player.getEntityPos());
		groundBoost(player, true);
		PlayerAbilities abilities = player.getAbilities();
		if (!Burrow.isBurrowing(player)) {
			abilities.flying = false;     // porté par les tentacules, pas en vol
			abilities.allowFlying = true; // sinon un serveur prendrait le joueur surélevé pour un tricheur qui vole
			player.sendAbilitiesUpdate();
		}
		broadcast(player, true);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1, player.getZ(), 40, 0.6, 0.6, 0.6, 0.1);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.5F, 0.5F);
		player.sendMessage(Text.translatable("message.evotool.tentacles_on").formatted(Formatting.DARK_PURPLE), true);
	}

	/** Éteint le mode ; avec {@code rescue}, un joueur resté dans la roche est remonté à la surface. */
	public static void stopMoving(ServerPlayerEntity player, boolean rescue) {
		player.removeCommandTag(MOVE_TAG);
		groundBoost(player, false);
		// sous terre : on ressort (sauf si les bottes prennent le relais)
		if (Burrow.isBurrowing(player)) {
			if (!(Burrow.hasBoots(player) && !player.isCreative())) {
				Burrow.stop(player, rescue);
			}
		} else {
			InfinityWings.restoreAbilities(player);
		}
		broadcast(player, false);
		StatusEffectInstance vision = player.getStatusEffect(StatusEffects.NIGHT_VISION);
		if (vision != null && vision.isAmbient() && vision.getDuration() <= 260) {
			player.removeStatusEffect(StatusEffects.NIGHT_VISION);
		}
		player.sendMessage(Text.translatable("message.evotool.tentacles_off").formatted(Formatting.GRAY), true);
	}

	/** Vitesse au sol et marche haute du mode déplacement (modificateurs temporaires : remis à chaque tick). */
	private static void groundBoost(ServerPlayerEntity player, boolean on) {
		modifier(player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED), SPEED_ID, GROUND_SPEED_BONUS,
				EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE, on);
		modifier(player.getAttributeInstance(EntityAttributes.STEP_HEIGHT), STEP_ID, STEP_BONUS,
				EntityAttributeModifier.Operation.ADD_VALUE, on);
	}

	private static void modifier(EntityAttributeInstance attribute, Identifier id, double value,
			EntityAttributeModifier.Operation operation, boolean on) {
		if (attribute == null) {
			return;
		}
		boolean present = attribute.getModifier(id) != null;
		if (on && !present) {
			attribute.addTemporaryModifier(new EntityAttributeModifier(id, value, operation));
		} else if (!on && present) {
			attribute.removeModifier(id);
		}
	}

	private static void moveTick(ServerPlayerEntity player, int tick) {
		if (!player.isAlive() || player.isSpectator() || !hasTentacles(player)) {
			stopMoving(player, true);
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		groundBoost(player, true);
		PlayerAbilities abilities = player.getAbilities();
		if (!abilities.allowFlying && !Burrow.isBurrowing(player)) {
			abilities.allowFlying = true;
			player.sendAbilitiesUpdate();
		}
		player.setAir(player.getMaxAir());
		if (player.isOnFire()) {
			player.extinguish();
		}
		if (tick % 20 == 0) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, false));
			if (player.isTouchingWater()) {
				// sous l'eau, les tentacules nagent comme une pieuvre
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, 40, 0, true, false, false));
			}
		}
		if (tick % 40 == 0) {
			broadcast(player, true);    // pour les joueurs qui arrivent près de lui
		}

		// sous terre, les tentacules creusent devant le joueur… puis « tchouf »
		UUID id = player.getUuid();
		Vec3d last = LAST_POS.getOrDefault(id, player.getEntityPos());
		Vec3d now = player.getEntityPos();
		LAST_POS.put(id, now);
		boolean moving = now.squaredDistanceTo(last) > 0.01;
		boolean inside = Burrow.isBurrowing(player) && !world.isSpaceEmpty(player, player.getBoundingBox().contract(0.01))
				|| player.isSubmergedInWater();
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
