package fr.evotool.plugin.boss;

import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Giant;
import fr.evotool.plugin.util.Fx;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Ravager;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Apparence du Slime Cloche : les modèles 3D du mod (bell_boss_part) en objets d'affichage qui suivent le boss et sont
 * animés à chaque tick : gelée qui rebondit, cloche qui se balance et sonne, marche, coups de poing ; la Cloche Divine
 * flotte, ses ailes battent, ses mains volent, son halo tourne et des petites cloches gravitent autour.
 */
final class BossVisual {
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
	private static final int PARTS_PER_PHASE = 21;
	private static final Color GOLD_GLOW = Color.fromRGB(0xFFD23A);
	private static final Color RED_GLOW = Color.fromRGB(0xFF3A2A);

	private enum Role { BODY, ROBE, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, CROWN, LEFT_HAND, RIGHT_HAND,
		LEFT_WING, RIGHT_WING, HALO, AUREOLE, SATELLITE }

	private record Part(ItemDisplay entity, int model, Role role, Vector3f offset, float scale, Vector3f pivot, int slot, int slots) {
	}

	private static final Quaternionf FLIP = new Quaternionf().rotationY((float) Math.PI);
	private static final Vector3f BOTTOM = new Vector3f(0, -0.5F, 0);
	private static final Vector3f TOP = new Vector3f(0, 0.5F, 0);
	private static final Vector3f CENTER = new Vector3f();

	private final BellBoss.Boss boss;
	private final List<Part> parts = new ArrayList<>();
	private int builtForm = -1;
	private int builtPhase = -1;

	BossVisual(BellBoss.Boss boss) {
		this.boss = boss;
	}

	void tick() {
		int form = boss.form();
		boolean broken = parts.stream().anyMatch(p -> !p.entity().isValid());
		if (form != builtForm || broken) {
			build(form);
		} else if (boss.phase() != builtPhase) {
			builtPhase = boss.phase();
			for (Part part : parts) {
				part.entity().setItemStack(stack(part.model()));
			}
		}
		Ravager e = boss.entity;
		boolean glow = boss.hurtTicks > 0 || boss.transforming();
		Color glowColor = boss.phase() == 2 ? RED_GLOW : GOLD_GLOW;
		Location at = new Location(e.getWorld(), e.getX(), e.getY(), e.getZ());
		for (Part part : parts) {
			part.entity().teleport(at);
			part.entity().setGlowing(glow);
			part.entity().setGlowColorOverride(glowColor);
		}
		if (boss.age % 2 == 0) {
			pose(form);
		}
	}

	private void build(int form) {
		discard(false);
		builtForm = form;
		builtPhase = boss.phase();
		switch (form) {
			case 0 -> add(SLIME_BODY, Role.BODY, new Vector3f(0, 0, 0), 2.2F, BOTTOM);
			case 1 -> add(SLIME_BELL, Role.BODY, new Vector3f(0, 0.35F, 0), 2.3F, BOTTOM);
			case 2 -> {
				add(WAR_BELL, Role.BODY, new Vector3f(0, 0.4F, 0), 2.7F, BOTTOM);
				satellites(WAR_SATELLITE, 2, 0.9F);
			}
			case 3, 4 -> {
				float k = form == 3 ? 1.0F : 1.2F;
				boolean titan = form == 4;
				int leg = titan ? TITAN_LEG : COLOSSUS_LEG;
				add(leg, Role.LEFT_LEG, new Vector3f(0.6F * k, 1.55F * k, 0), 1.55F * k, TOP);
				add(leg, Role.RIGHT_LEG, new Vector3f(-0.6F * k, 1.55F * k, 0), 1.55F * k, TOP);
				add(titan ? TITAN_BELL : COLOSSUS_BELL, Role.BODY, new Vector3f(0, 1.35F * k, 0), 2.4F * k, BOTTOM);
				add(titan ? TITAN_ARM : COLOSSUS_ARM, Role.LEFT_ARM, new Vector3f(1.45F * k, 3.0F * k, 0), 1.55F * k, TOP);
				add(titan ? TITAN_ARM_WEAPON : COLOSSUS_ARM, Role.RIGHT_ARM, new Vector3f(-1.45F * k, 3.0F * k, 0), 1.55F * k, TOP);
				if (titan) {
					add(TITAN_CROWN, Role.CROWN, new Vector3f(0, 3.33F * k, 0), 1.3F * k, BOTTOM);
					satellites(WAR_SATELLITE, 2, 1.0F);
				}
			}
			default -> {
				float k = 1.35F;
				add(DIVINE_ROBE, Role.ROBE, new Vector3f(0, 2.3F * k, 0), 2.4F * k, TOP);
				add(DIVINE_BELL, Role.BODY, new Vector3f(0, 2.2F * k, 0), 3.1F * k, BOTTOM);
				add(DIVINE_WING, Role.LEFT_WING, new Vector3f(0.5F * k, 4.3F * k, -1.05F * k), 2.9F * k, CENTER);
				add(DIVINE_WING_LEFT, Role.RIGHT_WING, new Vector3f(-0.5F * k, 4.3F * k, -1.05F * k), 2.9F * k, CENTER);
				add(DIVINE_HAND, Role.LEFT_HAND, new Vector3f(2.6F * k, 3.2F * k, 0.6F * k), 1.7F * k, CENTER);
				add(DIVINE_HAND_LEFT, Role.RIGHT_HAND, new Vector3f(-2.6F * k, 3.2F * k, 0.6F * k), 1.7F * k, CENTER);
				add(DIVINE_HALO, Role.HALO, new Vector3f(0, 6.45F * k, -0.05F * k), 2.0F * k, CENTER);
				add(DIVINE_AUREOLE, Role.AUREOLE, new Vector3f(0, 5.0F * k, -1.35F * k), 3.9F * k, CENTER);
				satellites(SATELLITE_BELL, 4, 1.0F * k);
			}
		}
		pose(form);
	}

	private void satellites(int model, int count, float scale) {
		for (int i = 0; i < count; i++) {
			addPart(model, Role.SATELLITE, new Vector3f(), scale, CENTER, i, count);
		}
	}

	private org.bukkit.inventory.ItemStack stack(int model) {
		return Items.display("bell_boss_part", model + PARTS_PER_PHASE * (Math.max(1, builtPhase) - 1));
	}

	private void add(int model, Role role, Vector3f offset, float scale, Vector3f pivot) {
		addPart(model, role, offset, scale, pivot, 0, 1);
	}

	private void addPart(int model, Role role, Vector3f offset, float scale, Vector3f pivot, int slot, int slots) {
		Ravager e = boss.entity;
		World world = e.getWorld();
		ItemDisplay entity = Giant.create(world, Fx.feet(e), stack(model), ItemDisplay.ItemDisplayTransform.NONE, 0.0F,
				Giant.transformation(new Quaternionf(), 0.2F));
		entity.setBrightness(null);
		entity.setTeleportDuration(2);
		entity.setViewRange(2.0F);
		parts.add(new Part(entity, model, role, offset, scale, pivot, slot, slots));
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private void pose(int form) {
		Ravager e = boss.entity;
		float t = boss.age;
		Quaternionf yaw = new Quaternionf().rotationY(rad(-e.getBodyYaw()));
		double speed = Math.hypot(e.getVelocity().getX(), e.getVelocity().getZ());
		float walk = (float) Math.min(1.0, speed * 12);
		boolean airborne = !e.isOnGround();
		boolean transforming = boss.transforming();
		boolean divine = form >= 5;
		float hover = divine ? 0.18F * (float) Math.sin(t * 0.08) + (transforming ? 0.6F : 0.0F) : 0.0F;
		float sway = 4.0F * (float) Math.sin(t * 0.1);
		if (boss.ringTicks > 0) {
			sway += 18.0F * (float) Math.sin(boss.ringTicks * 0.9) * boss.ringTicks / 20.0F;
		}
		if (transforming) {
			sway = 12.0F * (float) Math.sin(t * 1.3);
		}
		for (Part part : parts) {
			Quaternionf local = new Quaternionf();
			Vector3f scale = new Vector3f(part.scale());
			Vector3f offset = new Vector3f(part.offset());
			switch (part.role()) {
				case BODY -> {
					if (form == 0) {
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
						swing = -150.0F;
					} else if (right && boss.punchTicks > 0) {
						swing = -110.0F * boss.punchTicks / 8.0F;
					}
					local.rotationX(rad(swing)).rotateZ(rad(right ? -8 : 8));
				}
				case LEFT_HAND, RIGHT_HAND -> {
					boolean right = part.role() == Role.RIGHT_HAND;
					float phase = right ? 0 : (float) Math.PI;
					offset.y += hover + 0.25F * (float) Math.sin(t * 0.11 + phase);
					offset.x += (right ? -1 : 1) * 0.15F * (float) Math.sin(t * 0.07 + phase);
					if (right && boss.punchTicks > 0) {
						offset.z += 1.6F * boss.punchTicks / 8.0F;
					}
					if (transforming || boss.ringTicks > 0) {
						offset.y += 1.2F;
					}
					local.rotationZ(rad((right ? 1 : -1) * (10 + 6 * Math.sin(t * 0.09 + phase)))).rotateX(rad(-10 + 8 * Math.sin(t * 0.1)));
				}
				case LEFT_WING, RIGHT_WING -> {
					boolean right = part.role() == Role.RIGHT_WING;
					float flap = (float) (14 * Math.sin(t * (transforming ? 0.5 : 0.16)));
					float spread = transforming ? 28 : 14;
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
					offset.set((float) Math.cos(a) * radius, height + 0.25F * (float) Math.sin(t * 0.2 + part.slot()), (float) Math.sin(a) * radius);
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
		ItemDisplay entity = part.entity();
		entity.setInterpolationDelay(0);
		entity.setInterpolationDuration(2);
		entity.setTransformation(new Transformation(translation, rotation, scale, new Quaternionf(FLIP)));
	}

	void discard(boolean poof) {
		for (Part part : parts) {
			if (part.entity().isValid()) {
				if (poof) {
					Giant.remove(part.entity(), part.scale());
				} else {
					part.entity().remove();
				}
			}
		}
		parts.clear();
		builtForm = -1;
		builtPhase = -1;
	}
}
