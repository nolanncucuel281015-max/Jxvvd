package fr.evotool.boss;

import fr.evotool.item.ModItems;
import fr.evotool.upgrade.GiantItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Apparence du Slime Cloche : des objets d'affichage (modèles 3D de bell_boss_part) qui suivent le boss
 * et sont animés à chaque tick (rebonds de gelée, balancement de cloche, marche, coups de poing, ailes).
 */
final class BossVisual {
	// index des modèles (custom_model_data de evotool:bell_boss_part, voir tools/generate_assets.py BOSS_PARTS)
	private static final int SLIME_BODY = 0;
	private static final int SLIME_BELL = 1;
	private static final int WAR_BELL = 2;
	private static final int COLOSSUS_BELL = 3;
	private static final int COLOSSUS_ARM = 4;
	private static final int COLOSSUS_LEG = 5;
	private static final int TITAN_BELL = 6;
	private static final int TITAN_CROWN = 7;
	private static final int DIVINE_BELL = 8;
	private static final int DIVINE_HALO = 9;
	private static final int DIVINE_WINGS = 10;
	private static final int TITAN_ARM = 11;
	private static final int TITAN_LEG = 12;
	private static final int DIVINE_ARM = 13;
	private static final int DIVINE_LEG = 14;
	/** Chaque phase de combat a son jeu de 15 modèles (couleurs) : index = morceau + 15 x (phase - 1). */
	private static final int PARTS_PER_PHASE = 15;

	private enum Role { BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, CROWN, HALO, WINGS }

	/** offset : position du point d'ancrage (pivot) par rapport aux pieds du boss, face avant vers +z. */
	private record Part(DisplayEntity.ItemDisplayEntity entity, int model, Role role, Vector3f offset, float scale, Vector3f pivot) {
	}

	private static final Vector3f BOTTOM = new Vector3f(0, -0.5F, 0);
	private static final Vector3f TOP = new Vector3f(0, 0.5F, 0);
	private static final Vector3f CENTER = new Vector3f();

	private final BellBossEntity boss;
	private final List<Part> parts = new ArrayList<>();
	private int builtForm = -1;
	private int builtPhase = -1;

	BossVisual(BellBossEntity boss) {
		this.boss = boss;
	}

	void tick(ServerWorld world) {
		int form = boss.getForm();
		boolean broken = parts.stream().anyMatch(p -> p.entity().isRemoved());
		if (form != builtForm || broken) {
			build(world, form);
		} else if (boss.getPhase() != builtPhase) {
			// nouvelle phase : mêmes morceaux, nouvelles couleurs
			builtPhase = boss.getPhase();
			for (Part part : parts) {
				part.entity().setItemStack(stack(part.model()));
			}
		}
		for (Part part : parts) {
			part.entity().setPosition(boss.getX(), boss.getY(), boss.getZ());
			part.entity().setGlowing(boss.hurtTicks > 0 || boss.isTransforming());
		}
		if (boss.age % 2 == 0) {
			pose(form);
		}
	}

	private void build(ServerWorld world, int form) {
		discard(world, false);
		builtForm = form;
		builtPhase = boss.getPhase();
		switch (form) {
			case 0 -> add(world, SLIME_BODY, Role.BODY, new Vector3f(0, 0, 0), 2.0F, BOTTOM);
			case 1 -> add(world, SLIME_BELL, Role.BODY, new Vector3f(0, 0.25F, 0), 2.4F, BOTTOM);
			case 2 -> add(world, WAR_BELL, Role.BODY, new Vector3f(0, 0.25F, 0), 2.8F, BOTTOM);
			default -> {
				float k = form == 3 ? 1.0F : form == 4 ? 1.2F : 1.35F;
				int bell = form == 3 ? COLOSSUS_BELL : form == 4 ? TITAN_BELL : DIVINE_BELL;
				int arm = form == 3 ? COLOSSUS_ARM : form == 4 ? TITAN_ARM : DIVINE_ARM;
				int leg = form == 3 ? COLOSSUS_LEG : form == 4 ? TITAN_LEG : DIVINE_LEG;
				add(world, leg, Role.LEFT_LEG, new Vector3f(0.55F * k, 1.5F * k, 0), 1.5F * k, TOP);
				add(world, leg, Role.RIGHT_LEG, new Vector3f(-0.55F * k, 1.5F * k, 0), 1.5F * k, TOP);
				add(world, bell, Role.BODY, new Vector3f(0, 1.35F * k, 0), 2.5F * k, BOTTOM);
				add(world, arm, Role.LEFT_ARM, new Vector3f(1.35F * k, 3.0F * k, 0), 1.6F * k, TOP);
				add(world, arm, Role.RIGHT_ARM, new Vector3f(-1.35F * k, 3.0F * k, 0), 1.6F * k, TOP);
				if (form >= 4) {
					add(world, TITAN_CROWN, Role.CROWN, new Vector3f(0, 3.6F * k, 0), 1.4F * k, BOTTOM);
				}
				if (form >= 5) {
					add(world, DIVINE_HALO, Role.HALO, new Vector3f(0, 4.7F * k, 0), 2.2F * k, CENTER);
					add(world, DIVINE_WINGS, Role.WINGS, new Vector3f(0, 3.0F * k, -0.9F * k), 3.2F * k, CENTER);
				}
			}
		}
		pose(form);
	}

	private ItemStack stack(int model) {
		ItemStack stack = new ItemStack(ModItems.BELL_BOSS_PART);
		float index = model + PARTS_PER_PHASE * (builtPhase - 1);
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(index), List.of(), List.of(), List.of()));
		return stack;
	}

	private void add(ServerWorld world, int model, Role role, Vector3f offset, float scale, Vector3f pivot) {
		DisplayEntity.ItemDisplayEntity entity = GiantItem.spawn(world, stack(model), ItemDisplayContext.NONE,
				new Vec3d(boss.getX(), boss.getY(), boss.getZ()), 0.0F, new Quaternionf(), 0.2F, 1);
		entity.setBrightness(null);
		entity.setTeleportDuration(2);
		parts.add(new Part(entity, model, role, offset, scale, pivot));
	}

	private void pose(int form) {
		float t = boss.age;
		Quaternionf yaw = new Quaternionf().rotationY((float) Math.toRadians(-boss.bodyYaw));
		double speed = boss.getVelocity().horizontalLength();
		float walk = (float) Math.min(1.0, speed * 12);
		boolean airborne = !boss.isOnGround();
		boolean transforming = boss.isTransforming();
		for (Part part : parts) {
			Quaternionf local = new Quaternionf();
			Vector3f scale = new Vector3f(part.scale());
			switch (part.role()) {
				case BODY -> {
					if (form <= 1) {
						// gelée : s'écrase au sol, s'étire en l'air
						float squash = airborne ? 1.15F : 1.0F + 0.07F * (float) Math.sin(t * 0.3);
						float side = 1.0F / (float) Math.sqrt(squash);
						scale.mul(side, squash, side);
					}
					float sway = 4.0F * (float) Math.sin(t * 0.1);
					if (boss.ringTicks > 0) {
						sway += 18.0F * (float) Math.sin(boss.ringTicks * 0.9) * boss.ringTicks / 20.0F;
					}
					if (transforming) {
						// la cloche tremble et sonne pendant la transformation
						sway = 12.0F * (float) Math.sin(t * 1.3);
						scale.mul(1.0F + 0.04F * (float) Math.sin(t * 0.9));
					}
					if (form >= 1) {
						local.rotationZ((float) Math.toRadians(sway));
					}
				}
				case LEFT_LEG, RIGHT_LEG -> {
					float phase = part.role() == Role.LEFT_LEG ? 0 : (float) Math.PI;
					local.rotationX((float) Math.toRadians(32.0 * walk * Math.sin(t * 0.35 + phase)));
				}
				case LEFT_ARM, RIGHT_ARM -> {
					boolean right = part.role() == Role.RIGHT_ARM;
					float phase = right ? 0 : (float) Math.PI;
					float swing = (float) (28.0 * walk * Math.sin(t * 0.35 + phase));
					if (airborne || transforming) {
						swing = -150.0F;                          // bras levés pendant le bond et la transformation
					} else if (right && boss.punchTicks > 0) {
						swing = -110.0F * boss.punchTicks / 8.0F;  // coup de poing
					}
					local.rotationX((float) Math.toRadians(swing))
							.rotateZ((float) Math.toRadians(right ? -8 : 8));
				}
				case HALO -> local.rotationY(t * 0.06F);
				case WINGS -> local.rotationX((float) Math.toRadians(-10 + 8 * Math.sin(t * 0.2)));
				case CROWN -> {
				}
			}
			apply(part, yaw, local, scale);
		}
	}

	private static void apply(Part part, Quaternionf yaw, Quaternionf local, Vector3f scale) {
		Quaternionf rotation = new Quaternionf(yaw).mul(local);
		Vector3f pivot = new Vector3f(part.pivot()).mul(scale).rotate(rotation);
		Vector3f translation = new Vector3f(part.offset()).rotate(yaw).sub(pivot);
		DisplayEntity.ItemDisplayEntity entity = part.entity();
		entity.setTransformation(new AffineTransformation(translation, rotation, scale, new Quaternionf()));
		entity.setInterpolationDuration(2);
		entity.setStartInterpolation(0);
	}

	void discard(ServerWorld world, boolean poof) {
		for (Part part : parts) {
			if (!part.entity().isRemoved()) {
				if (poof) {
					GiantItem.remove(world, part.entity(), part.scale());
				} else {
					GiantItem.discard(part.entity());
				}
			}
		}
		parts.clear();
		builtForm = -1;
		builtPhase = -1;
	}
}
