package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.armor.Tentacles;
import fr.evotool.network.TentacleGrabPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Quaternionf;

/**
 * Notre joueur et ses tentacules (armure abyssale niveau 150), côté client :
 * <ul>
 *   <li>clic droit à main nue : demande au serveur d'attraper le monstre visé (ou de projeter celui qu'on tient) ;</li>
 *   <li>mode déplacement (touche J) : 4 tentacules sortent du dos, filent loin devant, s'accrochent (« tchouf ») et
 *   tirent le joueur d'un coup, chacune à son tour, puis se détachent quand on les a dépassées. Elles sont dessinées
 *   dans le monde : on les voit aussi en vue à la première personne, qui ne dessine pas le corps du joueur.</li>
 * </ul>
 */
public final class TentacleTravel {
	private static final int ARMS = 4;
	/** Ordre de lancer : en diagonale, comme les pattes d'une araignée. */
	private static final int[] ORDER = {0, 3, 1, 2};
	private static final int REACH_TICKS = 3;
	private static final int HOLD_MAX = 8;
	private static final int RETRACT_TICKS = 4;
	private static final int LAUNCH_EVERY = 3;
	/** Élan donné par une tentacule qui s'accroche, et vitesse au-delà de laquelle elle n'accélère plus (blocs/tick). */
	private static final double PULL = 0.6;
	private static final double MAX_SPEED = 3.2;
	private static final int SEGMENTS = 14;
	private static final float MODEL_RADIUS = 3.0F / 16.0F;
	private static final int FULL_BRIGHT = 0xF000F0;

	private static final class Arm {
		/** -1 : au repos (prête) ; sinon nombre de ticks depuis le lancer. */
		int age = -1;
		/** Âge auquel elle s'est détachée (-1 : pas encore). */
		int releaseAge = -1;
		Vec3d anchor = Vec3d.ZERO;
	}

	private static final Arm[] STATE = new Arm[ARMS];
	private static int next;
	private static int cooldown;
	private static boolean wasUsing;
	private static boolean active;
	private static long lastTick = System.nanoTime();

	private static final ItemStack SEGMENT_STACK = stack("tentacle/segment");
	private static final ItemStack TIP_STACK = stack("tentacle/tip");
	private static final ItemRenderState SEGMENT = new ItemRenderState();
	private static final ItemRenderState TIP = new ItemRenderState();
	/** Le gestionnaire de modèles d'objets, fourni par le rendu du joueur (TentacleFeature). */
	static ItemModelManager models;

	static {
		for (int k = 0; k < ARMS; k++) {
			STATE[k] = new Arm();
		}
	}

	private TentacleTravel() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(TentacleTravel::tick);
		WorldRenderEvents.BEFORE_ENTITIES.register(TentacleTravel::render);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
	}

	private static ItemStack stack(String model) {
		ItemStack stack = new ItemStack(Items.INK_SAC);
		stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id(model));
		return stack;
	}

	private static void reset() {
		for (Arm arm : STATE) {
			arm.age = -1;
			arm.releaseAge = -1;
		}
		active = false;
		wasUsing = false;
	}

	/** Les tentacules de notre joueur sont-elles dessinées ici (mode déplacement) plutôt que sur son buste ? */
	public static boolean drawsLocalPlayer() {
		return active;
	}

	// ------------------------------------------------------------------ chaque tick

	private static void tick(MinecraftClient client) {
		lastTick = System.nanoTime();
		ClientPlayerEntity player = client.player;
		if (player == null || client.world == null) {
			reset();
			return;
		}
		boolean tentacles = Tentacles.hasTentacles(player) && !player.isSpectator();

		// clic droit à main nue : attraper / projeter
		boolean using = client.options.useKey.isPressed();
		if (using && !wasUsing && tentacles && client.currentScreen == null && player.getMainHandStack().isEmpty()
				&& !(client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)) {
			ClientPlayNetworking.send(TentacleGrabPayload.INSTANCE);
		}
		wasUsing = using;

		active = tentacles && TentacleFeature.isMoving(player.getId());
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVector();
		for (Arm arm : STATE) {
			if (arm.age < 0) {
				continue;
			}
			arm.age++;
			if (arm.releaseAge < 0) {
				if (arm.age == REACH_TICKS) {
					grip(client, player, arm);
				}
				Vec3d toAnchor = arm.anchor.subtract(eye);
				boolean passed = toAnchor.dotProduct(look) < 0.5 || toAnchor.lengthSquared() > 30 * 30;
				if (!active || arm.age > REACH_TICKS && (passed || arm.age >= REACH_TICKS + HOLD_MAX)) {
					arm.releaseAge = arm.age;
				}
			} else if (arm.age - arm.releaseAge >= RETRACT_TICKS) {
				arm.age = -1;
				arm.releaseAge = -1;
			}
		}
		if (!active) {
			return;
		}
		Vec3d direction = travelDirection(player, look);
		if (--cooldown > 0 || direction == null) {
			return;
		}
		for (int i = 0; i < ARMS; i++) {
			int k = ORDER[(next + i) % ARMS];
			Arm arm = STATE[k];
			if (arm.age < 0) {
				launch(client, player, arm, k, direction);
				next = (next + i + 1) % ARMS;
				cooldown = LAUNCH_EVERY;
				return;
			}
		}
	}

	/** Là où le joueur veut aller : vers où il regarde (avancer / reculer), de côté, vers le haut ou le bas. */
	private static Vec3d travelDirection(ClientPlayerEntity player, Vec3d look) {
		Vec2f input = player.input.getMovementInput();
		Vec3d right = right(look);
		double vertical = (player.input.playerInput.jump() ? 1 : 0) - (player.input.playerInput.sneak() ? 1 : 0);
		Vec3d direction = look.multiply(input.y).add(right.multiply(-input.x)).add(0, vertical, 0);
		return direction.lengthSquared() < 0.01 ? null : direction.normalize();
	}

	private static Vec3d right(Vec3d look) {
		Vec3d right = new Vec3d(-look.z, 0, look.x);
		return right.lengthSquared() < 1.0E-6 ? new Vec3d(-1, 0, 0) : right.normalize();
	}

	/** La tentacule file loin devant (plus loin quand on va vite) et vise un mur si elle en trouve un. */
	private static void launch(MinecraftClient client, ClientPlayerEntity player, Arm arm, int k, Vec3d direction) {
		Vec3d eye = player.getEyePos();
		double speed = player.getVelocity().length();
		double reach = 6.0 + speed * 4.0;
		Vec3d side = right(direction);
		Vec3d up = side.crossProduct(direction).normalize();
		double s = k % 2 == 0 ? 1 : -1;
		double v = k < 2 ? 1 : -1;
		Vec3d aim = eye.add(direction.multiply(reach)).add(side.multiply(s * 1.6)).add(up.multiply(v * 1.1));
		BlockHitResult hit = client.world.raycast(new RaycastContext(eye, aim, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		// dans la roche (le joueur la traverse), elle s'enfonce droit devant : elle creuse
		arm.anchor = hit.getType() == HitResult.Type.BLOCK && hit.getPos().squaredDistanceTo(eye) > 4 ? hit.getPos() : aim;
		arm.age = 0;
		arm.releaseAge = -1;
		player.playSound(SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 0.25F, 1.6F);
	}

	/** « Tchouf » : elle s'accroche et tire le joueur d'un coup vers son point d'accroche. */
	private static void grip(MinecraftClient client, ClientPlayerEntity player, Arm arm) {
		Vec3d pull = arm.anchor.subtract(player.getEyePos());
		if (pull.lengthSquared() < 1.0E-4) {
			return;
		}
		Vec3d velocity = player.getVelocity();
		Vec3d boosted = velocity.add(pull.normalize().multiply(PULL));
		double limit = Math.max(velocity.length(), MAX_SPEED);
		if (boosted.length() > limit) {
			boosted = boosted.normalize().multiply(limit);
		}
		player.setVelocity(boosted);
		player.playSound(SoundEvents.ENTITY_SQUID_SQUIRT, 0.45F, 0.7F + player.getRandom().nextFloat() * 0.4F);
	}

	// ------------------------------------------------------------------ rendu dans le monde

	private static Vec3d lerp(Vec3d a, Vec3d b, double t) {
		return a.add(b.subtract(a).multiply(t));
	}

	private static double ease(double t) {
		t = Math.max(0, Math.min(1, t));
		return 1 - (1 - t) * (1 - t);
	}

	private static void render(WorldRenderContext context) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (!active || player == null || models == null || client.world == null) {
			return;
		}
		float delta = (float) Math.max(0, Math.min(1, (System.nanoTime() - lastTick) / 5.0e7));
		Vec3d camera = client.gameRenderer.getCamera().getCameraPos();
		boolean firstPerson = client.options.getPerspective().isFirstPerson();
		Vec3d eye = firstPerson ? camera : player.getLerpedPos(delta).add(0, player.getEyeY() - player.getY(), 0);
		Vec3d look = player.getRotationVector();
		Vec3d right = right(look);
		Vec3d up = right.crossProduct(look).normalize();
		double time = player.age + delta;

		models.clearAndUpdate(SEGMENT, SEGMENT_STACK, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, player.getId());
		models.clearAndUpdate(TIP, TIP_STACK, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, player.getId());
		MatrixStack matrices = context.matrices();
		OrderedRenderCommandQueue queue = context.commandQueue();

		for (int k = 0; k < ARMS; k++) {
			Arm arm = STATE[k];
			double s = k % 2 == 0 ? 1 : -1;
			boolean upper = k < 2;
			// elles sortent du dos, derrière les épaules : en vue à la première personne, elles entrent par les bords
			Vec3d root = eye.add(right.multiply(s * 0.55)).add(up.multiply(upper ? -0.2 : -0.65)).subtract(look.multiply(0.35));
			// au repos : courtes, pointées vers l'avant et l'extérieur, elles ondulent
			Vec3d ready = root.add(look.multiply(1.0).add(right.multiply(s * 0.75)).add(up.multiply(upper ? 0.35 : -0.35))
					.normalize().multiply(1.3 + 0.12 * Math.sin(time * 0.3 + k)));
			Vec3d tip;
			if (arm.age < 0) {
				tip = ready;
			} else if (arm.releaseAge < 0) {
				tip = arm.age < REACH_TICKS ? lerp(ready, arm.anchor, ease((arm.age + delta) / REACH_TICKS)) : arm.anchor;
			} else {
				tip = lerp(arm.anchor, ready, ease((arm.age - arm.releaseAge + delta) / RETRACT_TICKS));
			}
			drawTentacle(matrices, queue, camera, root, tip, up, time, k);
		}
	}

	/** Une tentacule courbe, épaisse à la racine et fine au bout, qui ondule. */
	private static void drawTentacle(MatrixStack matrices, OrderedRenderCommandQueue queue, Vec3d camera, Vec3d root, Vec3d tip,
			Vec3d up, double time, int k) {
		Vec3d span = tip.subtract(root);
		double length = span.length();
		if (length < 0.05) {
			return;
		}
		Vec3d axis = span.multiply(1 / length);
		Vec3d side = axis.crossProduct(up);
		side = side.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : side.normalize();
		Vec3d bend = side.crossProduct(axis).normalize();
		Vec3d previous = root;
		for (int i = 1; i <= SEGMENTS; i++) {
			double t = (double) i / SEGMENTS;
			// arc vers le haut + vague qui court jusqu'au bout
			double arc = Math.sin(Math.PI * t) * Math.min(1.2, length * 0.12);
			double wave = Math.sin(Math.PI * t) * Math.sin(time * 0.6 - t * 7 + k * 1.7) * Math.min(0.5, length * 0.06);
			Vec3d point = root.add(span.multiply(t)).add(bend.multiply(arc)).add(side.multiply(wave));
			Vec3d dir = point.subtract(previous);
			float segment = (float) dir.length();
			if (segment > 1.0E-4F) {
				float width = (float) (0.24 - 0.17 * (i - 1) / (double) SEGMENTS) / (2 * MODEL_RADIUS);
				Vec3d unit = dir.multiply(1 / segment);
				matrices.push();
				matrices.translate(previous.x - camera.x, previous.y - camera.y, previous.z - camera.z);
				matrices.multiply(new Quaternionf().rotationTo(0, 1, 0, (float) unit.x, (float) unit.y, (float) unit.z));
				matrices.scale(width, segment + 0.02F, width);
				matrices.translate(0.0F, 0.5F, 0.0F);
				(i == SEGMENTS ? TIP : SEGMENT).render(matrices, queue, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0);
				matrices.pop();
			}
			previous = point;
		}
	}
}
