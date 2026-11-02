package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.armor.ArmorSet;
import fr.evotool.network.TentacleStatePayload;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.HeldItemContext;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Les 4 tentacules noires de l'armure abyssale (ensemble niveau 150), dessinées sur le buste du joueur comme les
 * ailes : au repos elles ondulent dans le dos ; en mode déplacement (touche J) elles se tendent vers l'avant et
 * tirent le joueur, chacune à son rythme, comme si elles s'accrochaient à la roche.
 */
public class TentacleFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private static final int SEGMENTS = 9;
	private static final float SEGMENT_LENGTH = 0.2F;
	/** Rayon du modèle de segment (3 pixels). */
	private static final float MODEL_RADIUS = 3.0F / 16.0F;

	/** Joueurs (id d'entité) dont les tentacules sont en mode déplacement. */
	private static final Map<Integer, Boolean> MOVING = new HashMap<>();
	private static final Map<Integer, Reach> REACH = new HashMap<>();

	private static final class Reach {
		float value;
		long last = System.nanoTime();
	}

	private final ItemModelManager itemModelManager;
	private final ItemStack segmentStack = stack("tentacle/segment");
	private final ItemStack tipStack = stack("tentacle/tip");
	private final ItemRenderState segment = new ItemRenderState();
	private final ItemRenderState tip = new ItemRenderState();

	public TentacleFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context, ItemModelManager itemModelManager) {
		super(context);
		this.itemModelManager = itemModelManager;
		TentacleTravel.models = itemModelManager;
	}

	/** Les tentacules de ce joueur (id d'entité) sont-elles en mode déplacement ? */
	public static boolean isMoving(int entityId) {
		return MOVING.getOrDefault(entityId, false);
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer<?> player) {
				helper.register(new TentacleFeature(player, context.getItemModelManager()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TentacleStatePayload.ID,
				(payload, context) -> MOVING.put(payload.entityId(), payload.moving()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			MOVING.clear();
			REACH.clear();
		});
	}

	private static ItemStack stack(String model) {
		ItemStack stack = new ItemStack(Items.INK_SAC);
		stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id(model));
		return stack;
	}

	/** Mode déplacement lissé image par image (0 = au repos, 1 = tendues vers l'avant). */
	private static float reach(int id) {
		float target = MOVING.getOrDefault(id, false) ? 1.0F : 0.0F;
		Reach state = REACH.computeIfAbsent(id, k -> new Reach());
		long now = System.nanoTime();
		float dt = Math.min(0.1F, (now - state.last) / 1.0e9F);
		state.last = now;
		state.value += (target - state.value) * (1.0F - (float) Math.exp(-dt * 5.0F));
		return state.value;
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
			float limbAngle, float limbDistance) {
		if (state.invisible || state.spectator || ArmorSet.setLevel(state.equippedHeadStack, state.equippedChestStack,
				state.equippedLegsStack, state.equippedFeetStack) < ArmorSet.TENTACLE_LEVEL) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (TentacleTravel.drawsLocalPlayer() && client.player != null && client.player.getId() == state.id) {
			return;   // en mode déplacement, ses tentacules sont dessinées dans le monde (TentacleTravel)
		}
		itemModelManager.clearAndUpdate(segment, segmentStack, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, state.id);
		itemModelManager.clearAndUpdate(tip, tipStack, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, state.id);
		float move = reach(state.id);
		float t = state.age;

		matrices.push();
		getContextModel().body.applyTransform(matrices);
		// repère du buste (+y en bas, +z dans le dos) -> repère des tentacules (+y en haut, +z devant, +x à gauche)
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
		for (int k = 0; k < 4; k++) {
			int side = k % 2 == 0 ? 1 : -1;
			boolean upper = k < 2;
			Vector3f root = new Vector3f(side * 0.12F, upper ? -0.18F : -0.5F, -0.16F);
			// au repos : vers l'arrière et l'extérieur ; en mouvement : vers l'avant, comme des pattes qui tirent
			Vector3f rest = new Vector3f(side * 0.55F, upper ? 0.3F : -0.45F, -1.0F).normalize();
			float pull = 0.5F + 0.5F * (float) Math.sin(t * 0.45F + k * Math.PI / 2);
			Vector3f forward = new Vector3f(side * 0.85F, upper ? 0.15F - 0.3F * pull : -0.55F + 0.3F * pull, 0.75F).normalize();
			Vector3f direction = rest.lerp(forward, move, new Vector3f()).normalize();
			Vector3f bendAxis = new Vector3f(direction).cross(0, 1, 0);
			if (bendAxis.lengthSquared() < 1.0E-4F) {
				bendAxis.set(1, 0, 0);
			}
			bendAxis.normalize();
			float length = SEGMENT_LENGTH * (1.0F + 0.35F * move * (pull - 0.3F));

			Vector3f point = new Vector3f(root);
			for (int i = 0; i < SEGMENTS; i++) {
				float f = (float) i / SEGMENTS;
				// ondulation qui court le long de la tentacule, plus ample vers le bout ; elle s'enroule un peu
				float wave = (0.35F - 0.2F * move) * f * (float) Math.sin(t * 0.18F - i * 0.7F + k * 1.7F);
				float curl = (upper ? -0.9F : 0.7F) * f * f * (1.0F - 0.6F * move);
				Vector3f dir = new Vector3f(direction).rotateAxis(wave + curl, bendAxis.x, bendAxis.y, bendAxis.z);
				dir.rotateAxis(0.25F * f * (float) Math.cos(t * 0.13F + k), direction.x, direction.y, direction.z);
				float width = (0.2F - 0.13F * f) / (2 * MODEL_RADIUS);
				matrices.push();
				matrices.translate(point.x, point.y, point.z);
				matrices.multiply(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z));
				matrices.scale(width, length + 0.01F, width);
				matrices.translate(0.0F, 0.5F, 0.0F);
				(i == SEGMENTS - 1 ? tip : segment).render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
				matrices.pop();
				point.add(dir.mul(length));
			}
		}
		matrices.pop();
	}
}
