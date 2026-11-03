package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.item.ModItems;
import fr.evotool.network.WebPayload;
import fr.evotool.network.WebStatePayload;
import fr.evotool.spider.SpiderGlove;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;

/**
 * Gant de Spider-Man, côté client : la toile pour se balancer (physique d'un pendule, calculée par notre client),
 * l'escalade des murs, et le fil de toile dessiné pour tous les joueurs qui en ont un.
 */
public final class WebClient {
	/** Portée de la toile (blocs). */
	private static final double RANGE = 48.0;
	private static final double MAX_SPEED = 2.4;
	/** Traction vers un bloc : vitesse max (blocs/tick) et durée max. */
	private static final double ZIP_SPEED = 1.8;
	private static final int ZIP_TICKS = 40;
	private static final int FULL_BRIGHT = 0xF000F0;

	/** Toiles des autres joueurs (id d'entité -> point d'accroche), envoyées par le serveur. */
	private static final Map<Integer, Vec3d> OTHERS = new HashMap<>();
	/** Notre toile. */
	private static Vec3d anchor;
	private static double rope;
	private static int groundTicks;
	private static boolean wasJumping;
	/** Notre toile nous tire vers son bout (pouvoir « traction » sur un bloc) au lieu d'un balancement. */
	private static boolean zipping;
	private static int zipTicks;
	private static long nextZip;
	private static long lastTick = System.nanoTime();

	private static final ItemStack STRAND_STACK = strand();
	private static final ItemRenderState STRAND = new ItemRenderState();

	private WebClient() {
	}

	private static ItemStack strand() {
		ItemStack stack = new ItemStack(Items.STRING);
		stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id("web/strand"));
		return stack;
	}

	public static void register() {
		fr.evotool.ClientHooks.toggleWeb = WebClient::toggle;
		fr.evotool.ClientHooks.zipWeb = WebClient::zip;
		ClientTickEvents.END_CLIENT_TICK.register(WebClient::tick);
		WorldRenderEvents.BEFORE_ENTITIES.register(WebClient::render);
		ClientPlayNetworking.registerGlobalReceiver(WebStatePayload.ID, (payload, context) -> {
			if (payload.attached()) {
				OTHERS.put(payload.entityId(), new Vec3d(payload.x(), payload.y(), payload.z()));
			} else {
				OTHERS.remove(payload.entityId());
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			OTHERS.clear();
			anchor = null;
			zipping = false;
		});
	}

	private static Vec3d chest(PlayerEntity player) {
		return player.getEntityPos().add(0, 1.2, 0);
	}

	/** Clic droit avec le gant : la toile s'accroche là où l'on vise (ou on la lâche). */
	public static void toggle(PlayerEntity player) {
		if (anchor != null) {
			release(player, false);
			return;
		}
		Vec3d eye = player.getEyePos();
		BlockHitResult hit = player.getEntityWorld().raycast(new RaycastContext(eye, eye.add(player.getRotationVector().multiply(RANGE)),
				RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		anchor = hit.getPos();
		zipping = false;
		rope = Math.max(2.0, chest(player).distanceTo(anchor) * 0.85);
		groundTicks = 0;
		player.playSound(SoundEvents.ENTITY_ARROW_SHOOT, 0.8F, 1.7F);
		ClientPlayNetworking.send(new WebPayload(true, anchor.x, anchor.y, anchor.z));
	}

	/** Traction vers le bloc visé : la toile s'y colle et nous tire tout droit vers lui (un monstre visé : le serveur le tire). */
	public static void zip(PlayerEntity player) {
		long now = player.getEntityWorld().getTime();
		if (now < nextZip) {
			return;
		}
		SpiderGlove.Aim aim = SpiderGlove.aim(player, SpiderGlove.PULL_RANGE);
		if (!aim.onBlock()) {
			return;
		}
		nextZip = now + fr.evotool.spider.WebPower.PULL.cooldown();
		anchor = aim.end();
		zipping = true;
		zipTicks = 0;
		groundTicks = 0;
		player.setVelocity(player.getVelocity().add(0, 0.3, 0));
		ClientPlayNetworking.send(new WebPayload(true, anchor.x, anchor.y, anchor.z));
	}

	private static void release(PlayerEntity player, boolean leap) {
		anchor = null;
		zipping = false;
		if (leap) {
			// on lâche en plein élan : un petit bond en plus
			Vec3d v = player.getVelocity();
			player.setVelocity(v.x * 1.15, Math.max(v.y, 0.0) + 0.45, v.z * 1.15);
		}
		ClientPlayNetworking.send(new WebPayload(false, 0, 0, 0));
	}

	private static void tick(MinecraftClient client) {
		lastTick = System.nanoTime();
		ClientPlayerEntity player = client.player;
		if (player == null) {
			anchor = null;
			return;
		}
		boolean holding = SpiderGlove.holds(player);
		boolean jump = player.input.playerInput.jump();

		// grimper aux murs avec le gant, comme une araignée ; accroupi contre un mur, on y reste collé
		if (holding && player.horizontalCollision && !player.getAbilities().flying) {
			Vec3d v = player.getVelocity();
			if (player.input.playerInput.forward() || jump) {
				player.setVelocity(v.x, 0.22, v.z);
			} else if (player.isSneaking() && !player.isOnGround()) {
				player.setVelocity(v.x, Math.max(v.y, 0.0), v.z);
			}
		}

		if (anchor != null) {
			if (!holding || !player.isAlive() || chest(player).squaredDistanceTo(anchor) > 64 * 64) {
				release(player, false);
			} else if (jump && !wasJumping && !player.isOnGround()) {
				release(player, true);
			} else if (zipping) {
				zipTick(player);
			} else {
				swing(player);
			}
		}
		wasJumping = jump;
	}

	/** La traction : on file tout droit vers le bout de la toile ; arrivé, on lâche avec un petit bond (on se colle au mur). */
	private static void zipTick(ClientPlayerEntity player) {
		Vec3d toAnchor = anchor.subtract(chest(player));
		double distance = toAnchor.length();
		if (distance < 1.6 || ++zipTicks > ZIP_TICKS) {
			Vec3d v = player.getVelocity();
			release(player, false);
			player.setVelocity(v.x * 0.4, 0.55, v.z * 0.4);
			return;
		}
		player.setVelocity(toAnchor.multiply(Math.min(ZIP_SPEED, 0.5 + distance * 0.35) / distance));
	}

	/** Le pendule : la toile retient le joueur à sa longueur, et il prend de l'élan en avançant. */
	private static void swing(ClientPlayerEntity player) {
		if (player.isOnGround()) {
			if (++groundTicks > 10) {
				release(player, false);
				return;
			}
		} else {
			groundTicks = 0;
		}
		if (player.isSneaking()) {
			rope = Math.max(2.0, rope - 0.6);   // accroupi : on remonte le long de la toile
		}
		Vec3d toAnchor = anchor.subtract(chest(player));
		double distance = toAnchor.length();
		Vec3d v = player.getVelocity();
		if (distance > rope && distance > 1.0E-3) {
			Vec3d dir = toAnchor.multiply(1 / distance);
			double radial = v.dotProduct(dir);
			if (radial < 0) {
				v = v.subtract(dir.multiply(radial));   // la toile empêche de s'éloigner
			}
			v = v.add(dir.multiply(Math.min(0.3, (distance - rope) * 0.25)));
		}
		if (player.input.playerInput.forward()) {
			Vec3d look = player.getRotationVector();
			v = v.add(look.x * 0.06, 0.0, look.z * 0.06);   // on pousse pour prendre de l'élan
		}
		if (v.length() > MAX_SPEED) {
			v = v.normalize().multiply(MAX_SPEED);
		}
		player.setVelocity(v);
	}

	// ------------------------------------------------------------------ le fil de toile

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null || TentacleTravel.models == null || (anchor == null && OTHERS.isEmpty())) {
			return;
		}
		float delta = (float) Math.max(0, Math.min(1, (System.nanoTime() - lastTick) / 5.0e7));
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		TentacleTravel.models.clearAndUpdate(STRAND, STRAND_STACK, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, 0);
		MatrixStack matrices = context.matrices();
		for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
			boolean self = player == client.player;
			Vec3d to = self ? anchor : OTHERS.get(player.getId());
			if (to == null) {
				continue;
			}
			Vec3d look = player.getRotationVector();
			Vec3d right = new Vec3d(-look.z, 0, look.x);
			right = right.lengthSquared() < 1.0E-6 ? Vec3d.ZERO : right.normalize();
			Vec3d hand;
			if (self && client.options.getPerspective().isFirstPerson()) {
				hand = camera.add(right.multiply(0.35)).add(look.multiply(0.5)).add(0, -0.3, 0);
			} else {
				hand = player.getLerpedPos(delta).add(0, 1.3, 0).add(right.multiply(0.35));
			}
			Vec3d span = to.subtract(hand);
			double length = span.length();
			if (length < 0.1) {
				continue;
			}
			Vec3d unit = span.multiply(1 / length);
			matrices.push();
			matrices.translate(hand.x - camera.x, hand.y - camera.y, hand.z - camera.z);
			matrices.multiply(new Quaternionf().rotationTo(0, 1, 0, (float) unit.x, (float) unit.y, (float) unit.z));
			matrices.scale(1.0F, (float) length, 1.0F);
			matrices.translate(0.0F, 0.5F, 0.0F);
			STRAND.render(matrices, context.commandQueue(), FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
			matrices.pop();
		}
	}
}
