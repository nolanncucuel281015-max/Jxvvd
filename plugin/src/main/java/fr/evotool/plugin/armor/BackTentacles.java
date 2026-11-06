package fr.evotool.plugin.armor;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.upgrade.Burrow;
import fr.evotool.plugin.util.Keys;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Les 4 tentacules noires de l'armure abyssale (ensemble niveau 150), portées sur le dos du joueur comme les ailes :
 * au repos elles ondulent dans le dos ; en mode déplacement elles se tendent vers l'avant et le sol, comme des pattes
 * qui tirent le joueur. Les gestes sont ceux du mod (TentacleFeature).
 */
public final class BackTentacles {
	private static final int SEGMENTS = 9;
	private static final float SEGMENT_LENGTH = 0.2F;
	private static final float MODEL_RADIUS = 3.0F / 16.0F;
	private static final float NECK_BELOW_TOP = 0.4F;
	private static final float MODEL_SCALE = 0.9375F;

	private static final class Set {
		final List<ItemDisplay> parts = new ArrayList<>();
		float reach;

		boolean valid(Player player) {
			for (ItemDisplay d : parts) {
				if (!d.isValid() || !player.getPassengers().contains(d)) {
					return false;
				}
			}
			return true;
		}

		void remove() {
			parts.forEach(Entity::remove);
			parts.clear();
		}
	}

	private static final Map<UUID, Set> SETS = new HashMap<>();

	private BackTentacles() {
	}

	public static void enable() {
		EvoToolPlugin.ticker(BackTentacles::tick);
	}

	public static void clear() {
		SETS.values().forEach(Set::remove);
		SETS.clear();
	}

	private static boolean shown(Player player) {
		return ArmorSet.hasTentacles(player) && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
				&& !player.isInvisible() && !player.isInsideVehicle() && !Burrow.isBurrowing(player);
	}

	private static void tick(int tick) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			Set set = SETS.get(player.getUniqueId());
			boolean show = shown(player);
			if (set != null && (!show || !set.valid(player))) {
				set.remove();
				SETS.remove(player.getUniqueId());
				set = null;
			}
			if (!show) {
				continue;
			}
			if (set == null) {
				set = new Set();
				for (int k = 0; k < 4; k++) {
					for (int i = 0; i < SEGMENTS; i++) {
						set.parts.add(part(player, i == SEGMENTS - 1 ? "tentacle/tip" : "tentacle/segment"));
					}
				}
				SETS.put(player.getUniqueId(), set);
			}
			set.reach += ((Tentacles.isMoving(player) ? 1.0F : 0.0F) - set.reach) * 0.22F;
			if (tick % 2 == 0) {
				pose(player, set, tick);
			}
		}
		for (Iterator<Map.Entry<UUID, Set>> it = SETS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Set> e = it.next();
			if (Bukkit.getPlayer(e.getKey()) == null) {
				e.getValue().remove();
				it.remove();
			}
		}
	}

	private static ItemDisplay part(Player player, String model) {
		ItemDisplay display = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
			d.setItemStack(Items.display(model));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setPersistent(false);
			d.getPersistentDataContainer().set(Keys.TEMPORARY, PersistentDataType.BYTE, (byte) 1);
			d.setBillboard(Display.Billboard.FIXED);
			d.setViewRange(0.6F);
			d.setShadowRadius(0);
			d.setRotation(0, 0);
		});
		player.addPassenger(display);
		return display;
	}

	private static void pose(Player player, Set set, int tick) {
		float move = set.reach;
		float t = tick;
		Matrix4f body = new Matrix4f().rotateY((float) Math.toRadians(-player.getBodyYaw()))
				.translate(0, -NECK_BELOW_TOP - (player.isSneaking() ? 0.25F : 0.0F), 0).scale(MODEL_SCALE);
		if (player.isSneaking()) {
			body.rotateX((float) Math.toRadians(28.6));
		}
		int index = 0;
		for (int k = 0; k < 4; k++) {
			int side = k % 2 == 0 ? 1 : -1;
			boolean upper = k < 2;
			Vector3f root = new Vector3f(side * 0.12F, upper ? -0.18F : -0.5F, -0.16F);
			Vector3f rest = new Vector3f(side * 0.55F, upper ? 0.3F : -0.45F, -1.0F).normalize();
			float pull = 0.5F + 0.5F * (float) Math.sin(t * 0.45F + k * Math.PI / 2);
			Vector3f forward = new Vector3f(side * 0.6F, upper ? -0.75F - 0.15F * pull : -0.95F + 0.15F * pull, upper ? 0.45F : -0.1F).normalize();
			Vector3f direction = rest.lerp(forward, move, new Vector3f()).normalize();
			Vector3f bendAxis = new Vector3f(direction).cross(0, 1, 0);
			if (bendAxis.lengthSquared() < 1.0E-4F) {
				bendAxis.set(1, 0, 0);
			}
			bendAxis.normalize();
			float length = SEGMENT_LENGTH * (1.0F + move * (1.6F + 0.3F * (pull - 0.5F)));
			Vector3f point = new Vector3f(root);
			for (int i = 0; i < SEGMENTS; i++) {
				float f = (float) i / SEGMENTS;
				float wave = (0.35F - 0.27F * move) * f * (float) Math.sin(t * 0.18F - i * 0.7F + k * 1.7F);
				float curl = (upper ? -0.9F : 0.7F) * f * f * (1.0F - 0.9F * move);
				Vector3f dir = new Vector3f(direction).rotateAxis(wave + curl, bendAxis.x, bendAxis.y, bendAxis.z);
				dir.rotateAxis(0.25F * f * (float) Math.cos(t * 0.13F + k), direction.x, direction.y, direction.z);
				float width = (0.28F - 0.17F * f) / (2 * MODEL_RADIUS);
				Matrix4f m = new Matrix4f(body).translate(point)
						.rotate(new Quaternionf().rotationTo(0, 1, 0, dir.x, dir.y, dir.z))
						.scale(width, length + 0.01F, width)
						.translate(0.0F, 0.5F, 0.0F)
						.rotateY((float) Math.PI);
				ItemDisplay display = set.parts.get(index++);
				display.setInterpolationDelay(0);
				display.setInterpolationDuration(2);
				display.setTransformationMatrix(m);
				point.add(dir.mul(length));
			}
		}
	}
}
