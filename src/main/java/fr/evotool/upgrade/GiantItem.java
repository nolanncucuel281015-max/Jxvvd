package fr.evotool.upgrade;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.Brightness;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Objets géants affichés dans le monde (arc céleste, pioche géante) : une entité d'affichage d'objet,
 * qui apparaît et disparaît dans un « pfff ».
 */
public final class GiantItem {
	private static final String TAG = "evotool_giant";
	private static final String OLD_TAG = "evotool_celestial_bow";
	/** Géants en cours : ceux qu'on trouve au chargement sans être dans cette liste viennent d'une sauvegarde. */
	private static final Set<UUID> ACTIVE = new HashSet<>();

	private GiantItem() {
	}

	public static void init() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			boolean tagged = entity.getCommandTags().contains(TAG) || entity.getCommandTags().contains(OLD_TAG);
			if (tagged && !ACTIVE.contains(entity.getUuid())) {
				entity.discard();
			}
		});
	}

	/**
	 * Fait apparaître l'objet en géant : il grandit de 0,2 à {@code scale} en {@code growTicks} ticks.
	 */
	public static DisplayEntity.ItemDisplayEntity spawn(ServerWorld world, ItemStack stack, ItemDisplayContext context,
			Vec3d pos, float yaw, Quaternionf rotation, float scale, int growTicks) {
		DisplayEntity.ItemDisplayEntity giant = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
		giant.setItemStack(stack);
		giant.setItemDisplayContext(context);
		giant.setBillboardMode(DisplayEntity.BillboardMode.FIXED);
		giant.setBrightness(new Brightness(15, 15));
		giant.setViewRange(4.0F);
		giant.setTeleportDuration(3);
		giant.setTransformation(transformation(rotation, 0.2F));
		giant.addCommandTag(TAG);
		giant.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0.0F);
		// Enregistré AVANT d'apparaître, sinon le nettoyage des sauvegardes le supprimerait aussitôt.
		ACTIVE.add(giant.getUuid());
		world.spawnEntity(giant);

		giant.setInterpolationDuration(growTicks);
		giant.setStartInterpolation(0);
		giant.setTransformation(transformation(rotation, scale));
		poof(world, pos, scale);
		return giant;
	}

	public static void setPose(DisplayEntity.ItemDisplayEntity giant, Quaternionf rotation, float scale, int ticks) {
		giant.setInterpolationDuration(ticks);
		giant.setStartInterpolation(0);
		giant.setTransformation(transformation(rotation, scale));
	}

	public static void remove(ServerWorld world, DisplayEntity.ItemDisplayEntity giant, float scale) {
		poof(world, new Vec3d(giant.getX(), giant.getY(), giant.getZ()), scale);
		ACTIVE.remove(giant.getUuid());
		giant.discard();
	}

	public static AffineTransformation transformation(Quaternionf rotation, float scale) {
		return new AffineTransformation(new Vector3f(), new Quaternionf(rotation), new Vector3f(scale, scale, scale), new Quaternionf());
	}

	public static void poof(ServerWorld world, Vec3d pos, float scale) {
		double spread = Math.max(1.0, scale / 4.0);
		world.spawnParticles(ParticleTypes.POOF, pos.x, pos.y, pos.z, 60, spread, spread, spread, 0.05);
		world.spawnParticles(ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 30, spread, spread, spread, 0.02);
		world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.0F);
	}
}
