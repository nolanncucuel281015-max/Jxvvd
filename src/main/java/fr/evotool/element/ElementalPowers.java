package fr.evotool.element;

import fr.evotool.armor.Tentacles;
import fr.evotool.upgrade.Burrow;
import fr.evotool.upgrade.InfinityWings;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Pouvoirs des objets élémentaires (côté serveur ; le glissement de glace et le double saut du vent sont côté
 * client, la marche sur l'eau dans LivingEntityMixin).
 * <ul>
 *   <li>Arme / outil, à chaque coup : glace = gèle, feu = enflamme, électricité = arcs sur 3 ennemis proches,
 *   eau = vague qui repousse et soigne, vent = projette en l'air, terre = enracine et secoue les ennemis autour,
 *   ombre = aveugle, flétrit et vole de la vie, lumière = fait briller (et brûler les morts-vivants),
 *   poison = empoisonne.</li>
 *   <li>Armure : glace = attaquants gelés, pas de gel ; bottes = glissement de glace, l'eau gèle sous les pas.
 *   Feu = résistance au feu, attaquants enflammés ; plastron = vol de feu. Électricité = décharge sur les
 *   attaquants ; jambières = sprint électrique. Eau = respiration aquatique ; plastron = nage rapide ;
 *   bottes = marche sur l'eau. Vent = pas de dégâts de chute ; bottes = double saut. Terre = Résistance ;
 *   jambières = force tellurique (Célérité II) ; bottes = atterrissage sismique. Ombre = attaquants flétris ;
 *   casque = vision nocturne ; plastron = invisible accroupi. Lumière = attaquants révélés (morts-vivants
 *   brûlés) ; casque = révèle les monstres autour ; plastron = Régénération. Poison = attaquants empoisonnés,
 *   immunité au poison ; plastron = aura toxique.</li>
 * </ul>
 */
public final class ElementalPowers {
	/** Tag posé tant que le plastron de feu donne le vol. */
	public static final String FIRE_FLIGHT_TAG = "evotool_fire_flight";
	/** Vitesse du vol de feu (créatif : 0,05). */
	public static final float FIRE_FLY_SPEED = 0.07F;
	private static final int EFFECT_TICKS = 60;
	/** Un arc électrique (ou une décharge) ne déclenche pas à son tour les pouvoirs. */
	private static boolean applying;
	/** Hauteur de chute en cours des joueurs aux bottes de terre (pour l'atterrissage sismique). */
	private static final java.util.Map<java.util.UUID, Double> FALLING = new java.util.HashMap<>();

	private ElementalPowers() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (applying || blocked || damageTaken <= 0 || !(entity.getEntityWorld() instanceof ServerWorld world)) {
				return;
			}
			// l'arme (ou l'arc) du joueur qui frappe
			if (source.getAttacker() instanceof ServerPlayerEntity player && entity != player) {
				Element element = Elements.get(player.getMainHandStack());
				if (element != null && Elements.armorSlot(player.getMainHandStack()) == null) {
					applying = true;
					try {
						onHit(world, player, entity, element);
					} finally {
						applying = false;
					}
				}
			}
			// l'armure du joueur frappé
			if (entity instanceof ServerPlayerEntity player && source.getAttacker() instanceof LivingEntity attacker && attacker != player) {
				applying = true;
				try {
					for (Element element : Elements.armorElements(player)) {
						onHurt(world, player, attacker, element);
					}
				} finally {
					applying = false;
				}
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayerEntity player)) {
				return true;
			}
			if (source.isIn(DamageTypeTags.IS_FALL) && (hasPiece(player, Element.WIND)
					|| Elements.wears(player, EquipmentSlot.FEET, Element.EARTH))) {
				return false;   // le vent amortit les chutes ; la terre les encaisse (atterrissage sismique)
			}
			return !(source.isOf(DamageTypes.FREEZE) && hasPiece(player, Element.ICE));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				tick(player, tick);
			}
		});
	}

	private static boolean hasPiece(ServerPlayerEntity player, Element element) {
		return Elements.armorElements(player).contains(element);
	}

	/** Le plastron de feu fait voler (sauf en créatif, sous terre ou en mode déplacement des tentacules). */
	public static boolean fireFlight(ServerPlayerEntity player) {
		return Elements.wears(player, EquipmentSlot.CHEST, Element.FIRE) && !player.isCreative() && !player.isSpectator()
				&& !Burrow.isBurrowing(player) && !player.getCommandTags().contains(Tentacles.MOVE_TAG);
	}

	// ------------------------------------------------------------------ coups

	private static void onHit(ServerWorld world, ServerPlayerEntity player, LivingEntity target, Element element) {
		Vec3d c = target.getBoundingBox().getCenter();
		switch (element) {
			case ICE -> {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 3), player);
				target.setFrozenTicks(Math.max(target.getFrozenTicks(), target.getMinFreezeDamageTicks() + 60));
				world.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 25, 0.4, 0.5, 0.4, 0.05);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.PLAYERS, 1.0F, 1.2F);
			}
			case FIRE -> {
				target.setOnFireFor(5.0F);
				world.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 25, 0.4, 0.5, 0.4, 0.05);
				world.playSound(null, target.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 0.8F, 1.2F);
			}
			case LIGHTNING -> {
				List<LivingEntity> near = world.getEntitiesByClass(LivingEntity.class, target.getBoundingBox().expand(5.0),
						e -> e != target && e != player && e.isAlive() && e instanceof Monster);
				near.sort((a, b) -> Double.compare(a.squaredDistanceTo(target), b.squaredDistanceTo(target)));
				for (LivingEntity other : near.subList(0, Math.min(3, near.size()))) {
					arc(world, c, other.getBoundingBox().getCenter());
					other.damage(world, player.getDamageSources().playerAttack(player), 4.0F);
				}
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.4, 0.5, 0.4, 0.3);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.5F, 1.6F);
			}
			case WATER -> {
				target.takeKnockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ());
				player.heal(2.0F);
				world.spawnParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 40, 0.5, 0.5, 0.5, 0.2);
				world.spawnParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 15, 0.4, 0.4, 0.4, 0.05);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
			case WIND -> {
				target.addVelocity(0.0, 0.9, 0.0);
				if (target instanceof ServerPlayerEntity other) {
					other.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(other));
				}
				world.spawnParticles(ParticleTypes.CLOUD, c.x, target.getY(), c.z, 20, 0.5, 0.1, 0.5, 0.1);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2F, 0.7F);
			}
			case EARTH -> {
				// la cible est enracinée, le sol tremble autour d'elle
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 4), player);
				quake(world, player, target.getEntityPos(), 3.0, 3.0F, target);
			}
			case SHADOW -> {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0), player);
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), player);
				player.heal(1.0F);
				world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.05);
				world.spawnParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 6, 0.3, 0.4, 0.3, 0.03);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.0F, 1.4F);
			}
			case LIGHT -> {
				// la cible brille (on la voit à travers les murs) ; les morts-vivants brûlent comme au soleil
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0), player);
				if (target.getType().isIn(EntityTypeTags.UNDEAD)) {
					target.setOnFireFor(5.0F);
					target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 0), player);
				}
				world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.08);
				world.playSound(null, target.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, 1.8F);
			}
			case POISON -> {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 1), player);
				world.spawnParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0.05);
				world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 1.0F, 1.4F);
			}
		}
	}

	/**
	 * Le sol tremble : les monstres autour de {@code at} (sauf {@code spared}) prennent des dégâts et décollent,
	 * avec des éclats de terre.
	 */
	private static void quake(ServerWorld world, ServerPlayerEntity player, Vec3d at, double radius, float damage, LivingEntity spared) {
		BlockState ground = world.getBlockState(BlockPos.ofFloored(at.x, at.y - 0.5, at.z));
		if (ground.isAir()) {
			ground = Blocks.DIRT.getDefaultState();
		}
		world.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, ground),
				at.x, at.y + 0.1, at.z, (int) (radius * 25), radius * 0.5, 0.1, radius * 0.5, 0.2);
		world.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.5F, 0.6F);
		for (LivingEntity other : world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(at, at).expand(radius, 2.0, radius),
				e -> e != spared && e != player && e.isAlive() && e instanceof Monster)) {
			other.damage(world, player.getDamageSources().playerAttack(player), damage);
			other.addVelocity(0.0, 0.45, 0.0);
		}
	}

	/** Un arc électrique entre deux points. */
	private static void arc(ServerWorld world, Vec3d from, Vec3d to) {
		Vec3d step = to.subtract(from);
		int count = Math.max(3, (int) (step.length() * 3));
		for (int i = 0; i <= count; i++) {
			Vec3d p = from.add(step.multiply(i / (double) count));
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.08, 0.08, 0.08, 0.02);
		}
	}

	// ------------------------------------------------------------------ armure frappée

	private static void onHurt(ServerWorld world, ServerPlayerEntity player, LivingEntity attacker, Element element) {
		Vec3d c = attacker.getBoundingBox().getCenter();
		switch (element) {
			case ICE -> {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), player);
				attacker.setFrozenTicks(Math.max(attacker.getFrozenTicks(), attacker.getMinFreezeDamageTicks()));
				world.spawnParticles(ParticleTypes.SNOWFLAKE, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.03);
			}
			case FIRE -> {
				attacker.setOnFireFor(4.0F);
				world.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.03);
			}
			case LIGHT -> {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0), player);
				if (attacker.getType().isIn(EntityTypeTags.UNDEAD)) {
					attacker.setOnFireFor(4.0F);
				}
				world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.05);
			}
			case POISON -> {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), player);
				world.spawnParticles(ParticleTypes.ITEM_SLIME, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.03);
			}
			case SHADOW -> {
				attacker.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0), player);
				world.spawnParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.03);
			}
			case LIGHTNING -> {
				arc(world, player.getBoundingBox().getCenter(), c);
				attacker.damage(world, player.getDamageSources().thorns(player), 3.0F);
				world.playSound(null, attacker.getBlockPos(), SoundEvents.BLOCK_BEEHIVE_SHEAR, SoundCategory.PLAYERS, 0.4F, 2.0F);
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ chaque tick

	/** Pouvoirs permanents de l'armure (public pour l'auto-test). */
	public static void tick(ServerPlayerEntity player, int tick) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (tick % 20 == 0) {
			if (hasPiece(player, Element.FIRE)) {
				effect(player, StatusEffects.FIRE_RESISTANCE, 0);
			}
			if (hasPiece(player, Element.WATER)) {
				effect(player, StatusEffects.WATER_BREATHING, 0);
				if (Elements.wears(player, EquipmentSlot.CHEST, Element.WATER) && player.isTouchingWater()) {
					effect(player, StatusEffects.DOLPHINS_GRACE, 0);
					effect(player, StatusEffects.CONDUIT_POWER, 0);
				}
			}
			if (Elements.wears(player, EquipmentSlot.LEGS, Element.LIGHTNING)) {
				effect(player, StatusEffects.SPEED, 1);
			}
			if (hasPiece(player, Element.EARTH)) {
				effect(player, StatusEffects.RESISTANCE, 0);
			}
			if (Elements.wears(player, EquipmentSlot.LEGS, Element.EARTH)) {
				effect(player, StatusEffects.HASTE, 1);
			}
			// casque de lumière : les monstres autour brillent, on les voit à travers les murs
			if (Elements.wears(player, EquipmentSlot.HEAD, Element.LIGHT)) {
				for (LivingEntity monster : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(24.0),
						e -> e instanceof Monster && e.isAlive())) {
					monster.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 40, 0, true, false), player);
				}
			}
			// plastron de lumière : aura sacrée (rajoutée seulement quand elle s'épuise : soin au rythme normal)
			if (Elements.wears(player, EquipmentSlot.CHEST, Element.LIGHT)) {
				StatusEffectInstance regen = player.getStatusEffect(StatusEffects.REGENERATION);
				if (regen == null || regen.getDuration() < 40) {
					player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0, true, false, true));
				}
			}
			// plastron de poison : aura toxique qui empoisonne les monstres proches
			if (Elements.wears(player, EquipmentSlot.CHEST, Element.POISON) && tick % 40 == 0) {
				for (LivingEntity monster : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(4.0),
						e -> e instanceof Monster && e.isAlive())) {
					monster.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0), player);
				}
				world.spawnParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 1.0, player.getZ(), 25, 2.0, 0.6, 2.0, 0.02);
			}
			if (Elements.wears(player, EquipmentSlot.HEAD, Element.SHADOW)) {
				// assez longue pour ne pas clignoter (la vision nocturne clignote ses 10 dernières secondes)
				player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false, true));
			}
		}
		// poison : on y est immunisé
		if (hasPiece(player, Element.POISON) && player.hasStatusEffect(StatusEffects.POISON)) {
			player.removeStatusEffect(StatusEffects.POISON);
		}
		// plastron d'ombre : accroupi, on se fond dans l'ombre
		if (Elements.wears(player, EquipmentSlot.CHEST, Element.SHADOW) && player.isSneaking()) {
			player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 10, 0, true, false, false));
			if (tick % 4 == 0) {
				world.spawnParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 0.8, player.getZ(), 1, 0.3, 0.4, 0.3, 0.0);
			}
		}
		// bottes de terre : en tombant de haut, on retombe dans un séisme (sans dégâts)
		if (Elements.wears(player, EquipmentSlot.FEET, Element.EARTH)) {
			double fall = player.fallDistance;
			if (!player.isOnGround()) {
				if (fall > 0) {
					FALLING.merge(player.getUuid(), fall, Math::max);
				}
			} else {
				Double fell = FALLING.remove(player.getUuid());
				if (fell != null && fell >= 4.0) {
					quake(world, player, player.getEntityPos(), Math.min(6.0, 2.0 + fell / 3.0), (float) Math.min(12.0, fell), null);
				}
			}
		} else {
			FALLING.remove(player.getUuid());
		}

		// vol de feu : le plastron donne le vol (double saut), avec une traînée de flammes
		boolean fire = fireFlight(player);
		boolean tagged = player.getCommandTags().contains(FIRE_FLIGHT_TAG);
		PlayerAbilities abilities = player.getAbilities();
		if (fire && (!tagged || !abilities.allowFlying)) {
			player.addCommandTag(FIRE_FLIGHT_TAG);
			InfinityWings.restoreAbilities(player);
		} else if (!fire && tagged) {
			player.removeCommandTag(FIRE_FLIGHT_TAG);
			if (!Burrow.isBurrowing(player) && !player.getCommandTags().contains(Tentacles.MOVE_TAG)) {
				InfinityWings.restoreAbilities(player);
			}
		}
		if (fire && abilities.flying && tick % 2 == 0) {
			world.spawnParticles(ParticleTypes.FLAME, player.getX(), player.getY() - 0.1, player.getZ(), 4, 0.2, 0.05, 0.2, 0.02);
			world.spawnParticles(ParticleTypes.SMOKE, player.getX(), player.getY() - 0.2, player.getZ(), 1, 0.15, 0.05, 0.15, 0.01);
		}

		// bottes de glace : l'eau gèle sous les pas, flocons quand on glisse
		if (Elements.wears(player, EquipmentSlot.FEET, Element.ICE)) {
			if (player.isOnGround() && !player.isSneaking()) {
				freezeWater(world, player);
			}
			if (player.isSprinting() && tick % 2 == 0) {
				world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 0.1, player.getZ(), 3, 0.3, 0.05, 0.3, 0.01);
			}
		}
		// jambières électriques : étincelles quand on sprinte
		if (Elements.wears(player, EquipmentSlot.LEGS, Element.LIGHTNING) && player.isSprinting() && tick % 2 == 0) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 0.6, player.getZ(), 3, 0.3, 0.3, 0.3, 0.05);
		}
	}

	private static void effect(ServerPlayerEntity player, RegistryEntry<StatusEffect> effect, int amplifier) {
		player.addStatusEffect(new StatusEffectInstance(effect, EFFECT_TICKS, amplifier, true, false, true));
	}

	/** Comme Semelles givrantes : l'eau calme autour des pieds devient de la glace qui fond toute seule. */
	private static void freezeWater(ServerWorld world, ServerPlayerEntity player) {
		BlockPos feet = player.getBlockPos();
		BlockState frosted = Blocks.FROSTED_ICE.getDefaultState();
		for (BlockPos pos : BlockPos.iterate(feet.add(-2, -1, -2), feet.add(2, -1, 2))) {
			if (pos.getSquaredDistance(feet.getX(), pos.getY(), feet.getZ()) > 6.5 || !world.getBlockState(pos.up()).isAir()) {
				continue;
			}
			BlockState state = world.getBlockState(pos);
			if (state.isOf(Blocks.WATER) && state.get(FluidBlock.LEVEL) == 0 && frosted.canPlaceAt(world, pos)) {
				world.setBlockState(pos, frosted);
				world.scheduleBlockTick(pos, Blocks.FROSTED_ICE, MathHelper.nextInt(player.getRandom(), 60, 120));
			}
		}
	}

	/** Double saut des bottes de vent (le client l'a fait) : le souffle, et la chute est oubliée. */
	public static void windJump(ServerPlayerEntity player) {
		if (!Elements.wears(player, EquipmentSlot.FEET, Element.WIND)) {
			return;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		player.onLanding();
		world.spawnParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 15, 0.4, 0.05, 0.4, 0.05);
		world.spawnParticles(ParticleTypes.POOF, player.getX(), player.getY(), player.getZ(), 6, 0.3, 0.05, 0.3, 0.02);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.0F, 1.4F);
	}
}
