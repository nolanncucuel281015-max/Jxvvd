package fr.evotool.client;

import fr.evotool.EvoTool;
import fr.evotool.upgrade.Burrow;
import fr.evotool.upgrade.InfinityWings;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.item.ItemModelManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
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

/**
 * Ailes de l'infini du plastron amélioré, dessinées sur le buste du modèle du joueur, comme une cape : elles sont
 * collées à l'armure et suivent chacun de ses mouvements (rotation du corps, accroupi, nage…) à chaque image.
 * Repliées le long du dos au sol, elles se déploient et battent en vol ; le symbole de l'infini brille entre les deux.
 * Les modèles 3D viennent de tools/generate_assets.py (gen_wings).
 */
public class InfinityWingsFeature extends FeatureRenderer<PlayerEntityRenderState, PlayerEntityModel> {
	private static final float WING_SCALE = 1.0F;
	private static final float EMBLEM_SCALE = 0.44F;
	/** Racine de l'aile (épaule) dans son modèle, en blocs depuis le centre du modèle. */
	private static final float WING_ROOT_X = 0.6F / 16;
	/** Charnière des ailes sur le dos, depuis le haut du buste (repère des ailes : +y en haut, +z devant, +x à gauche). */
	private static final float HINGE_X = 0.1F;
	private static final float HINGE_Y = -0.16F;
	private static final float HINGE_Z = -0.2F;

	/** Le joueur vole-t-il (mis à jour à chaque tick du client) : clé = id de l'entité. */
	private static final Map<Integer, Boolean> FLYING = new HashMap<>();
	/** Ouverture des ailes, lissée image par image : 0 = repliées, 1 = déployées. */
	private static final Map<Integer, Openness> OPEN = new HashMap<>();

	private static final class Openness {
		float value;
		long last = System.nanoTime();
	}

	private final ItemModelManager itemModelManager;
	private final ItemStack leftStack = wingStack("infinity_wing");
	private final ItemStack rightStack = wingStack("infinity_wing_right");
	private final ItemStack emblemStack = wingStack("infinity_emblem");
	private final ItemRenderState left = new ItemRenderState();
	private final ItemRenderState right = new ItemRenderState();
	private final ItemRenderState emblem = new ItemRenderState();

	public InfinityWingsFeature(FeatureRendererContext<PlayerEntityRenderState, PlayerEntityModel> context,
			ItemModelManager itemModelManager) {
		super(context);
		this.itemModelManager = itemModelManager;
	}

	public static void register() {
		LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof PlayerEntityRenderer<?> player) {
				helper.register(new InfinityWingsFeature(player, context.getItemModelManager()));
			}
		});
		ClientTickEvents.END_WORLD_TICK.register(world -> {
			MinecraftClient client = MinecraftClient.getInstance();
			FLYING.clear();
			for (AbstractClientPlayerEntity player : world.getPlayers()) {
				// notre joueur : son vol ; les autres (leur vol n'est pas envoyé) : quand ils sont en l'air
				boolean flying = player == client.player
						? player.getAbilities().flying || player.isGliding()
						: !player.isOnGround() && !player.isTouchingWater() && !player.hasVehicle();
				FLYING.put(player.getId(), flying);
			}
			OPEN.keySet().retainAll(FLYING.keySet());
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			FLYING.clear();
			OPEN.clear();
		});
	}

	private static ItemStack wingStack(String model) {
		ItemStack stack = new ItemStack(Items.FEATHER);
		stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id("wings/" + model));
		return stack;
	}

	private static float open(int id) {
		boolean flying = FLYING.getOrDefault(id, false);
		Openness open = OPEN.computeIfAbsent(id, k -> new Openness());
		long now = System.nanoTime();
		float dt = Math.min(0.1F, (now - open.last) / 1.0e9F);
		open.last = now;
		open.value += ((flying ? 1.0F : 0.0F) - open.value) * (1.0F - (float) Math.exp(-dt * 6.0F));
		return open.value;
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static float lerp(float t, float a, float b) {
		return a + (b - a) * t;
	}

	@Override
	public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, PlayerEntityRenderState state,
			float limbAngle, float limbDistance) {
		if (state.invisible || state.spectator || !InfinityWings.isWingChestplate(state.equippedChestStack)) {
			return;
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (Burrow.clientActive && client.player != null && client.player.getId() == state.id) {
			return;   // sous terre, les ailes sont rangées
		}
		itemModelManager.clearAndUpdate(left, leftStack, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, state.id);
		itemModelManager.clearAndUpdate(right, rightStack, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, state.id);
		itemModelManager.clearAndUpdate(emblem, emblemStack, ItemDisplayContext.NONE, client.world, (HeldItemContext) null, state.id);

		float open = open(state.id);
		float t = state.age;
		float flap = open * 24.0F * (float) Math.sin(t * 0.33);
		float breathe = (1.0F - open) * 2.5F * (float) Math.sin(t * 0.08);
		float spread = lerp(open, -35.0F, 8.0F) + flap + breathe;    // repliées : l'aile descend en arrière
		float sweep = lerp(open, 78.0F, 20.0F) - flap * 0.25F;        // repliées : couchée le long du dos
		float tilt = lerp(open, 22.0F, 0.0F);                         // repliées : le bas s'écarte des jambes

		matrices.push();
		// collées au buste : elles suivent sa rotation, son inclinaison quand on s'accroupit, la nage…
		getContextModel().body.applyTransform(matrices);
		// repère du modèle du joueur (+y en bas, +z dans le dos) -> repère des ailes (+y en haut, +z devant)
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(180.0F));
		for (int side = -1; side <= 1; side += 2) {
			// côté +1 = gauche du joueur
			matrices.push();
			matrices.translate(side * HINGE_X, HINGE_Y, HINGE_Z);
			matrices.multiply(new Quaternionf().rotationX(rad(tilt)).rotateY(rad(side * sweep)).rotateZ(rad(side * spread)));
			matrices.scale(WING_SCALE, WING_SCALE, WING_SCALE);
			matrices.translate(-side * WING_ROOT_X, 0.0F, 0.0F);
			(side > 0 ? left : right).render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
			matrices.pop();
		}
		// le symbole de l'infini, entre les ailes : il se balance doucement et pulse
		float pulse = EMBLEM_SCALE * (1.0F + 0.06F * (float) Math.sin(t * 0.2));
		matrices.translate(0.0F, HINGE_Y + 0.02F, HINGE_Z - 0.06F);
		matrices.multiply(new Quaternionf().rotationX(rad(tilt)).rotateZ(rad(6 * Math.sin(t * 0.05))));
		matrices.scale(pulse, pulse, pulse);
		emblem.render(matrices, queue, light, OverlayTexture.DEFAULT_UV, state.outlineColor);
		matrices.pop();
	}
}
