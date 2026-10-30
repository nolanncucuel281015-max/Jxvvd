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
 * et sont animés à chaque tick : gelée qui rebondit, cloche qui se balance et sonne, marche, coups de poing,
 * et pour la Cloche Divine : elle flotte, ses ailes battent, ses mains volent autour d'elle, son halo et
 * son auréole tournent et des petites cloches gravitent autour.
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
	private static final int TITAN_ARM = 8;
	private static final int TITAN_ARM_WEAPON = 9;
	private static final int TITAN_LEG = 10;
	private static final int DIVINE_BELL = 11;
	private static final int DIVINE_ROBE = 12;
	private static final int DIVINE_HAND = 13;
	private static final int DIVINE_HAND_LEFT = 14;
	private static final int DIVINE_WING = 15;
	private static final int DIVINE_WING_LEFT = 16;
	private static final int DIVINE_HALO = 17;
	private static final int DIVINE_AUREOLE = 18;
	private static final int SATELLITE_BELL = 19;
	private static final int WAR_SATELLITE = 20;
	/** Chaque phase de combat a son jeu de modèles (couleurs) : index = morceau + PARTS_PER_PHASE x (phase - 1). */
	private static final int PARTS_PER_PHASE = 21;

	private static final int GOLD_GLOW = 0xFFD23A;
	private static final int RED_GLOW = 0xFF3A2A;

	private enum Role { BODY, ROBE, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, CROWN, LEFT_HAND, RIGHT_HAND,
		LEFT_WING, RIGHT_WING, HALO, AUREOLE, SATELLITE }

	/** offset : position du point d'ancrage (pivot) par rapport aux pieds du boss, face avant vers +z. */
	private record Part(DisplayEntity.ItemDisplayEntity entity, int model, Role role, Vector3f offset, float scale,
			Vector3f pivot, int slot, int slots) {
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
		boolean glow = boss.hurtTicks > 0 || boss.isTransforming();
		int glowColor = boss.getPhase() == 2 ? RED_GLOW : GOLD_GLOW;
		for (Part part : parts) {
			part.entity().setPosition(boss.getX(), boss.getY(), boss.getZ());
			part.entity().setGlowing(glow);
			part.entity().setGlowColorOverride(glowColor);
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
			case 0 -> add(world, SLIME_BODY, Role.BODY, new Vector3f(0, 0, 0), 2.2F, BOTTOM);
			case 1 -> add(world, SLIME_BELL, Role.BODY, new Vector3f(0, 0.35F, 0), 2.3F, BOTTOM);
			case 2 -> {
				add(world, WAR_BELL, Role.BODY, new Vector3f(0, 0.4F, 0), 2.7F, BOTTOM);
				satellites(world, WAR_SATELLITE, 2, 0.9F);
			}
			case 3, 4 -> {
				float k = form == 3 ? 1.0F : 1.2F;
				boolean titan = form == 4;
				int leg = titan ? TITAN_LEG : COLOSSUS_LEG;
				add(world, leg, Role.LEFT_LEG, new Vector3f(0.6F * k, 1.55F * k, 0), 1.55F * k, TOP);
				add(world, leg, Role.RIGHT_LEG, new Vector3f(-0.6F * k, 1.55F * k, 0), 1.55F * k, TOP);
				add(world, titan ? TITAN_BELL : COLOSSUS_BELL, Role.BODY, new Vector3f(0, 1.35F * k, 0), 2.4F * k, BOTTOM);
				add(world, titan ? TITAN_ARM : COLOSSUS_ARM, Role.LEFT_ARM, new Vector3f(1.45F * k, 3.0F * k, 0), 1.55F * k, TOP);
				add(world, titan ? TITAN_ARM_WEAPON : COLOSSUS_ARM, Role.RIGHT_ARM, new Vector3f(-1.45F * k, 3.0F * k, 0), 1.55F * k, TOP);
				if (titan) {
					add(world, TITAN_CROWN, Role.CROWN, new Vector3f(0, 3.33F * k, 0), 1.3F * k, BOTTOM);
					satellites(world, WAR_SATELLITE, 2, 1.0F);
				}
			}
			default -> {
				float k = 1.35F;
				add(world, DIVINE_ROBE, Role.ROBE, new Vector3f(0, 2.3F * k, 0), 2.4F * k, TOP);
				add(world, DIVINE_BELL, Role.BODY, new Vector3f(0, 2.2F * k, 0), 3.1F * k, BOTTOM);
				add(world, DIVINE_WING, Role.LEFT_WING, new Vector3f(0.5F * k, 4.3F * k, -1.05F * k), 2.9F * k, CENTER);
				add(world, DIVINE_WING_LEFT, Role.RIGHT_WING, new Vector3f(-0.5F * k, 4.3F * k, -1.05F * k), 2.9F * k, CENTER);
				add(world, DIVINE_HAND, Role.LEFT_HAND, new Vector3f(2.6F * k, 3.2F * k, 0.6F * k), 1.7F * k, CENTER);
				add(world, DIVINE_HAND_LEFT, Role.RIGHT_HAND, new Vector3f(-2.6F * k, 3.2F * k, 0.6F * k), 1.7F * k, CENTER);
				add(world, DIVINE_HALO, Role.HALO, new Vector3f(0, 6.45F * k, -0.05F * k), 2.0F * k, CENTER);
				add(world, DIVINE_AUREOLE, Role.AUREOLE, new Vector3f(0, 5.0F * k, -1.35F * k), 3.9F * k, CENTER);
				satellites(world, SATELLITE_BELL, 4, 1.0F * k);
			}
		}
		pose(form);
	}

	private void satellites(ServerWorld world, int model, int count, float scale) {
		for (int i = 0; i < count; i++) {
			addPart(world, model, Role.SATELLITE, new Vector3f(), scale, CENTER, i, count);
		}
	}

	private ItemStack stack(int model) {
		ItemStack stack = new ItemStack(ModItems.BELL_BOSS_PART);
		float index = model + PARTS_PER_PHASE * (builtPhase - 1);
		stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, new CustomModelDataComponent(List.of(index), List.of(), List.of(), List.of()));
		return stack;
	}

	private void add(ServerWorld world, int model, Role role, Vector3f offset, float scale, Vector3f pivot) {
		addPart(world, model, role, offset, scale, pivot, 0, 1);
	}

	private void addPart(ServerWorld world, int model, Role role, Vector3f offset, float scale, Vector3f pivot, int slot, int slots) {
		DisplayEntity.ItemDisplayEntity entity = GiantItem.spawn(world, stack(model), ItemDisplayContext.NONE,
				new Vec3d(boss.getX(), boss.getY(), boss.getZ()), 0.0F, new Quaternionf(), 0.2F, 1);
		entity.setBrightness(null);
		entity.setTeleportDuration(2);
		entity.setViewRange(2.0F);
		parts.add(new Part(entity, model, role, offset, scale, pivot, slot, slots));
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private void pose(int form) {
		float t = boss.age;
		Quaternionf yaw = new Quaternionf().rotationY(rad(-boss.bodyYaw));
		double speed = boss.getVelocity().horizontalLength();
		float walk = (float) Math.min(1.0, speed * 12);
		boolean airborne = !boss.isOnGround();
		boolean transforming = boss.isTransforming();
		boolean divine = form >= 5;
		// la Cloche Divine flotte : tout le haut du corps monte et descend doucement
		float hover = divine ? 0.18F * (float) Math.sin(t * 0.08) + (transforming ? 0.6F : 0.0F) : 0.0F;
		float sway = 4.0F * (float) Math.sin(t * 0.1);
		if (boss.ringTicks > 0) {
			sway += 18.0F * (float) Math.sin(boss.ringTicks * 0.9) * boss.ringTicks / 20.0F;
		}
		if (transforming) {
			sway = 12.0F * (float) Math.sin(t * 1.3);   // la cloche tremble et sonne pendant la transformation
		}
		for (Part part : parts) {
			Quaternionf local = new Quaternionf();
			Vector3f scale = new Vector3f(part.scale());
			Vector3f offset = new Vector3f(part.offset());
			switch (part.role()) {
				case BODY -> {
					if (form == 0) {
						// gelée : s'écrase au sol, s'étire en l'air
						float squash = airborne ? 1.15F : 1.0F + 0.07F * (float) Math.sin(t * 0.3);
						float side = 1.0F / (float) Math.sqrt(squash);
						scale.mul(side, squash, side);
					} else {
						local.rotationZ(rad(sway));
					}
					if (transforming) {
						scale.mul(1.0F + 0.04F * (float) Math.sin(t * 0.9));
					}
					offset.y += hover;
				}
				case ROBE -> {
					local.rotationZ(rad(sway * 0.6F)).rotateX(rad(4 * Math.sin(t * 0.13)));
					offset.y += hover;
				}
				case LEFT_LEG, RIGHT_LEG -> {
					float phase = part.role() == Role.LEFT_LEG ? 0 : (float) Math.PI;
					local.rotationX(rad(32.0 * walk * Math.sin(t * 0.35 + phase)));
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
					local.rotationX(rad(swing)).rotateZ(rad(right ? -8 : 8));
				}
				case LEFT_HAND, RIGHT_HAND -> {
					boolean right = part.role() == Role.RIGHT_HAND;
					float phase = right ? 0 : (float) Math.PI;
					offset.y += hover + 0.25F * (float) Math.sin(t * 0.11 + phase);
					offset.x += (right ? -1 : 1) * 0.15F * (float) Math.sin(t * 0.07 + phase);
					if (right && boss.punchTicks > 0) {
						offset.z += 1.6F * boss.punchTicks / 8.0F;   // la main frappe en avant
					}
					if (transforming || boss.ringTicks > 0) {
						offset.y += 1.2F;                             // mains levées vers le ciel
					}
					local.rotationZ(rad((right ? 1 : -1) * (10 + 6 * Math.sin(t * 0.09 + phase))))
							.rotateX(rad(-10 + 8 * Math.sin(t * 0.1)));
				}
				case LEFT_WING, RIGHT_WING -> {
					boolean right = part.role() == Role.RIGHT_WING;
					float flap = (float) (14 * Math.sin(t * (transforming ? 0.5 : 0.16)));
					float spread = transforming ? 28 : 14;
					// l'aile gauche (vers +x) monte avec un angle z positif, la droite avec un angle négatif
					local.rotationY(rad(right ? -25 : 25)).rotateZ(rad((right ? -1 : 1) * (spread + flap)));
					offset.y += hover;
				}
				case HALO -> {
					local.rotationX(rad(-14)).rotateY(t * 0.05F);
					offset.y += hover;
				}
				case AUREOLE -> {
					local.rotationZ(t * 0.015F);
					offset.y += hover;
				}
				case SATELLITE -> {
					double a = t * (divine ? 0.045 : 0.06) + Math.PI * 2 * part.slot() / part.slots();
					float radius = divine ? 4.2F : 2.5F + 0.5F * form / 4.0F;
					float height = divine ? 3.4F + 1.0F * (part.slot() % 2) : 2.0F + 0.3F * form;
					offset.set((float) Math.cos(a) * radius, height + 0.25F * (float) Math.sin(t * 0.2 + part.slot()),
							(float) Math.sin(a) * radius);
					local.rotationY(rad(-Math.toDegrees(a))).rotateZ(rad(12 * Math.sin(t * 0.3 + part.slot())));
				}
				case CROWN -> {
				}
			}
			apply(part, yaw, local, scale, offset);
		}
	}

	private static void apply(Part part, Quaternionf yaw, Quaternionf local, Vector3f scale, Vector3f offset) {
		Quaternionf rotation = new Quaternionf(yaw).mul(local);
		Vector3f pivot = new Vector3f(part.pivot()).mul(scale).rotate(rotation);
		Vector3f translation = new Vector3f(offset).rotate(yaw).sub(pivot);
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
