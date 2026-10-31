package fr.evotool.upgrade;

import fr.evotool.EvoTool;
import fr.evotool.upgrade.EvoliteUpgrade.Upgrade;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Plastron amélioré : les ailes de l'infini. Le joueur peut voler (double saut, comme en créatif) et plus
 * vite qu'en créatif, sans dégâts de chute. Les ailes sont de vrais modèles 3D (tools/generate_assets.py,
 * gen_wings) portés par des objets d'affichage qui chevauchent le joueur : repliées dans le dos au sol,
 * elles se déploient et battent en vol ; un symbole de l'infini brille entre les deux.
 */
public final class InfinityWings {
	public static final float DEFAULT_FLY_SPEED = 0.05F;
	/** Vitesse de vol avec les ailes (créatif : 0,05). */
	public static final float SPEED = 0.11F;
	/** Tag posé sur le joueur quand ce sont les ailes qui lui donnent le vol (sauvegardé avec le joueur). */
	private static final String TAG = "evotool_infinity_wings";
	/** Ancien tag du nuage magique (bottes), remplacé par la plongée souterraine. */
	private static final String OLD_CLOUD_TAG = "evotool_magic_cloud";

	private static final float WING_SCALE = 0.95F;
	private static final float EMBLEM_SCALE = 0.42F;
	/** Racine de l'aile (épaule) dans son modèle, en blocs depuis le centre du modèle. */
	private static final Vector3f WING_ROOT = new Vector3f(0.6F / 16, 0.0F, 0.0F);
	/** Le rendu des objets d'affichage tourne le modèle d'un demi-tour : on le compense. */
	private static final Quaternionf FLIP = new Quaternionf().rotationY((float) Math.PI);

	/** L'aile gauche chevauche le joueur, la droite chevauche la gauche, le symbole chevauche la droite. */
	private record Wings(DisplayEntity.ItemDisplayEntity left, DisplayEntity.ItemDisplayEntity right,
			DisplayEntity.ItemDisplayEntity emblem) {
		boolean broken(ServerPlayerEntity player) {
			return left.isRemoved() || right.isRemoved() || emblem.isRemoved()
					|| left.getVehicle() != player || right.getVehicle() != left || emblem.getVehicle() != right;
		}

		void discard() {
			for (DisplayEntity.ItemDisplayEntity part : new DisplayEntity.ItemDisplayEntity[] {emblem, right, left}) {
				part.stopRiding();
				GiantItem.discard(part);
			}
		}
	}

	private static final Map<UUID, Wings> WINGS = new HashMap<>();
	/** Ouverture lissée des ailes : 0 = repliées, 1 = déployées. */
	private static final Map<UUID, Float> OPEN = new HashMap<>();

	private InfinityWings() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				if (tick % 5 == 0) {
					abilities(player);
				}
				update(player, tick);
			}
		});
		// des ailes : on plane jusqu'au sol, jamais de dégâts de chute
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FALL) && hasWings(player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> remove(handler.getPlayer().getUuid()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			WINGS.values().forEach(Wings::discard);
			WINGS.clear();
			OPEN.clear();
		});
	}

	public static boolean hasWings(PlayerEntity player) {
		return EvoliteUpgrade.has(player.getEquippedStack(EquipmentSlot.CHEST), Upgrade.INFINITY_WINGS);
	}

	// ------------------------------------------------------------------ vol

	/** Donne (ou retire) le vol rapide selon que le joueur porte les ailes. */
	private static void abilities(ServerPlayerEntity player) {
		if (player.isCreative() || player.isSpectator() || Burrow.isBurrowing(player)) {
			return;
		}
		Set<String> tags = player.getCommandTags();
		PlayerAbilities abilities = player.getAbilities();
		if (hasWings(player)) {
			if (!abilities.allowFlying || abilities.getFlySpeed() != SPEED || !tags.contains(TAG)) {
				restoreAbilities(player);
			}
		} else if (tags.contains(TAG) || tags.contains(OLD_CLOUD_TAG)) {
			restoreAbilities(player);
		}
	}

	/** Remet le vol du joueur dans l'état normal : celui des ailes s'il les porte, sinon celui de son mode de jeu. */
	public static void restoreAbilities(ServerPlayerEntity player) {
		PlayerAbilities abilities = player.getAbilities();
		player.removeCommandTag(OLD_CLOUD_TAG);
		if (player.isCreative() || player.isSpectator()) {
			abilities.allowFlying = true;
			abilities.setFlySpeed(DEFAULT_FLY_SPEED);
			player.removeCommandTag(TAG);
		} else if (hasWings(player)) {
			abilities.allowFlying = true;
			abilities.setFlySpeed(SPEED);
			player.addCommandTag(TAG);
		} else {
			abilities.allowFlying = false;
			abilities.flying = false;
			abilities.setFlySpeed(DEFAULT_FLY_SPEED);
			player.removeCommandTag(TAG);
		}
		player.sendAbilitiesUpdate();
	}

	// ------------------------------------------------------------------ modèle 3D

	/** Fait apparaître, anime ou retire les ailes de ce joueur (public pour l'auto-test, qui utilise un faux joueur). */
	public static void update(ServerPlayerEntity player, int tick) {
		UUID id = player.getUuid();
		boolean show = hasWings(player) && player.isAlive() && !player.isSpectator() && !player.isInvisible()
				&& !Burrow.isBurrowing(player);
		Wings wings = WINGS.get(id);
		if (!show) {
			if (wings != null) {
				remove(id);
			}
			return;
		}
		if (wings == null || wings.broken(player)) {
			if (wings != null) {
				wings.discard();
			}
			wings = spawn(player);
			WINGS.put(id, wings);
		}
		pose(player, wings, tick);
	}

	private static void remove(UUID id) {
		Wings wings = WINGS.remove(id);
		OPEN.remove(id);
		if (wings != null) {
			wings.discard();
		}
	}

	private static Wings spawn(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		DisplayEntity.ItemDisplayEntity left = part(world, player, "infinity_wing");
		DisplayEntity.ItemDisplayEntity right = part(world, player, "infinity_wing_right");
		DisplayEntity.ItemDisplayEntity emblem = part(world, player, "infinity_emblem");
		left.startRiding(player, true, true);
		right.startRiding(left, true, true);
		emblem.startRiding(right, true, true);
		OPEN.put(player.getUuid(), 0.0F);
		// les ailes s'ouvrent dans un éclat d'étoiles
		world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.3, player.getZ(), 30, 0.5, 0.5, 0.5, 0.08);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 0.8F);
		return new Wings(left, right, emblem);
	}

	private static DisplayEntity.ItemDisplayEntity part(ServerWorld world, ServerPlayerEntity player, String model) {
		ItemStack stack = new ItemStack(Items.FEATHER);
		stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id("wings/" + model));
		DisplayEntity.ItemDisplayEntity entity = GiantItem.create(world, stack, ItemDisplayContext.NONE,
				player.getEntityPos().add(0, player.getHeight(), 0), 0.0F, new Quaternionf(), 0.2F, 1);
		entity.setBrightness(null);
		entity.setTeleportDuration(0);
		entity.setViewRange(1.0F);
		return entity;
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	private static void pose(ServerPlayerEntity player, Wings wings, int tick) {
		boolean flying = player.getAbilities().flying || player.isGliding();
		float open = OPEN.getOrDefault(player.getUuid(), 0.0F);
		open += ((flying ? 1.0F : 0.0F) - open) * 0.25F;
		OPEN.put(player.getUuid(), open);

		Quaternionf yaw = new Quaternionf().rotationY(rad(-player.bodyYaw));
		// les objets d'affichage sont assis sur la tête du joueur : on redescend aux omoplates, dans le dos
		float seat = (float) (player.getPassengerRidingPos(wings.left()).y - player.getY());
		boolean sneaking = player.isInSneakingPose();
		float shoulders = player.getHeight() * 0.77F - seat;
		float back = sneaking ? -0.34F : -0.2F;

		float t = tick;
		float flap = open * 24.0F * (float) Math.sin(t * 0.33);
		float breathe = (1.0F - open) * 2.5F * (float) Math.sin(t * 0.08);
		float spread = lerp(open, -35.0F, 8.0F) + flap + breathe;    // repliées : l'aile descend en arrière
		float sweep = lerp(open, 78.0F, 20.0F) - flap * 0.25F;        // repliées : couchée le long du dos
		// repliées, le bas des ailes s'écarte des jambes ; accroupi, elles se penchent en avant avec le dos
		float tilt = lerp(open, 22.0F, 0.0F) + (sneaking ? 25.0F : 0.0F);
		for (int side = -1; side <= 1; side += 2) {
			// côté +1 = gauche du joueur (+x quand il regarde vers +z)
			Quaternionf local = new Quaternionf().rotationX(rad(tilt)).rotateY(rad(side * sweep)).rotateZ(rad(side * spread));
			Vector3f root = new Vector3f(WING_ROOT.x * side, WING_ROOT.y, WING_ROOT.z);
			apply(side > 0 ? wings.left() : wings.right(), yaw, local, new Vector3f(side * 0.1F, shoulders, back),
					root, WING_SCALE);
		}
		// le symbole de l'infini, entre les ailes : il tourne doucement sur lui-même et pulse
		float pulse = EMBLEM_SCALE * (1.0F + 0.06F * (float) Math.sin(t * 0.2));
		Quaternionf spin = new Quaternionf().rotationX(rad(tilt)).rotateZ(rad(6 * Math.sin(t * 0.05)));
		apply(wings.emblem(), yaw, spin, new Vector3f(0.0F, shoulders + 0.02F, back - 0.06F), new Vector3f(), pulse);
	}

	private static void apply(DisplayEntity.ItemDisplayEntity entity, Quaternionf yaw, Quaternionf local, Vector3f hinge,
			Vector3f root, float scale) {
		Quaternionf rotation = new Quaternionf(yaw).mul(local);
		Vector3f pivot = new Vector3f(root).mul(scale).rotate(rotation);
		Vector3f translation = new Vector3f(hinge.x, 0.0F, hinge.z).rotate(yaw).add(0.0F, hinge.y, 0.0F).sub(pivot);
		entity.setTransformation(new AffineTransformation(translation, rotation, new Vector3f(scale), FLIP));
		entity.setInterpolationDuration(2);
		entity.setStartInterpolation(0);
	}
}
