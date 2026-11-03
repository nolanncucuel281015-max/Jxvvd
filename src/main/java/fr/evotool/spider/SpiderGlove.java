package fr.evotool.spider;

import fr.evotool.component.ModComponents;
import fr.evotool.item.ModItems;
import fr.evotool.network.WebPayload;
import fr.evotool.network.WebStatePayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/**
 * Gant de Spider-Man, côté serveur : l'éveil du rituel (la première fois qu'on le tient), le tir de toile, les
 * toiles qui disparaissent toutes seules, plus de dégâts de chute, et la toile montrée aux autres joueurs. Le
 * balancement lui-même est calculé par le client du joueur (fluide).
 */
public final class SpiderGlove {
	/** Portée du tir de toile. */
	public static final double SHOT_RANGE = 24.0;
	/** Les toiles du tir disparaissent au bout de 10 s. */
	private static final int WEB_LIFE = 200;
	private static final int SHOT_COOLDOWN = 20;

	private record TempWeb(ServerWorld world, BlockPos pos, int expires) {
	}

	private static final List<TempWeb> WEBS = new ArrayList<>();
	private static final Map<UUID, Integer> NEXT_SHOT = new HashMap<>();
	private static final Map<UUID, Vec3d> ANCHORS = new HashMap<>();

	private SpiderGlove() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int tick = server.getTicks();
			if (tick % 10 == 0) {
				for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
					awakenInventory(player);
				}
			}
			for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
				TempWeb web = it.next();
				if (tick >= web.expires()) {
					if (web.world().getBlockState(web.pos()).isOf(Blocks.COBWEB)) {
						web.world().removeBlock(web.pos(), false);
					}
					it.remove();
				}
			}
		});
		// Spider-Man ne se fait pas mal en tombant
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(entity instanceof ServerPlayerEntity player && source.isIn(DamageTypeTags.IS_FALL) && holds(player)));
		ServerPlayNetworking.registerGlobalReceiver(WebPayload.ID, (payload, context) -> {
			ServerPlayerEntity player = context.player();
			Vec3d anchor = new Vec3d(payload.x(), payload.y(), payload.z());
			if (payload.attached() && (!holds(player) || anchor.squaredDistanceTo(player.getEyePos()) > 64 * 64)) {
				return;
			}
			setWeb(player, payload.attached() ? anchor : null);
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ANCHORS.remove(handler.getPlayer().getUuid());
			NEXT_SHOT.remove(handler.getPlayer().getUuid());
		});
	}

	/** Le joueur tient-il le gant (dans une main ou l'autre) ? */
	public static boolean holds(PlayerEntity player) {
		return player.getMainHandStack().isOf(ModItems.SPIDER_GLOVE) || player.getOffHandStack().isOf(ModItems.SPIDER_GLOVE);
	}

	/** La toile du joueur s'accroche à ce point (null : lâchée) ; ceux qui le voient la voient aussi. */
	public static void setWeb(ServerPlayerEntity player, Vec3d anchor) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (anchor == null) {
			ANCHORS.remove(player.getUuid());
		} else {
			ANCHORS.put(player.getUuid(), anchor);
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)),
					anchor.x, anchor.y, anchor.z, 12, 0.2, 0.2, 0.2, 0.05);
			world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.8F, 1.6F);
		}
		WebStatePayload state = anchor == null
				? new WebStatePayload(player.getId(), false, 0, 0, 0)
				: new WebStatePayload(player.getId(), true, anchor.x, anchor.y, anchor.z);
		for (ServerPlayerEntity viewer : PlayerLookup.tracking(player)) {
			if (ServerPlayNetworking.canSend(viewer, WebStatePayload.ID)) {
				ServerPlayNetworking.send(viewer, state);
			}
		}
	}

	/** La toile du joueur (null s'il n'en a pas) — auto-test. */
	public static Vec3d anchor(ServerPlayerEntity player) {
		return ANCHORS.get(player.getUuid());
	}

	// ------------------------------------------------------------------ le rituel

	/** Le gant fraîchement sorti du rituel s'éveille dans l'inventaire : la foudre tombe sur son porteur. */
	private static void awakenInventory(ServerPlayerEntity player) {
		for (int i = 0; i < player.getInventory().size(); i++) {
			ItemStack stack = player.getInventory().getStack(i);
			if (stack.isOf(ModItems.SPIDER_GLOVE) && !stack.contains(ModComponents.AWAKENED)) {
				awaken(player, stack);
			}
		}
	}

	/** L'éveil du gant (public pour l'auto-test). */
	public static void awaken(ServerPlayerEntity player, ItemStack glove) {
		glove.set(ModComponents.AWAKENED, Unit.INSTANCE);
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world, SpawnReason.TRIGGERED);
		if (lightning != null) {
			lightning.refreshPositionAfterTeleport(player.getEntityPos());
			lightning.setCosmetic(true);
			world.spawnEntity(lightning);
		}
		double x = player.getX();
		double y = player.getY() + 1.0;
		double z = player.getZ();
		world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB)), x, y, z, 60, 1.0, 1.0, 1.0, 0.1);
		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y, z, 60, 0.6, 0.8, 0.6, 0.5);
		world.spawnParticles(ParticleTypes.END_ROD, x, y, z, 40, 0.8, 1.2, 0.8, 0.1);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.6F, 1.4F);
		player.sendMessage(Text.translatable("message.evotool.spider_glove_awakened").formatted(Formatting.RED, Formatting.BOLD), false);
	}

	// ------------------------------------------------------------------ le tir de toile

	/** Accroupi + clic droit : une toile file là où l'on vise ; une créature touchée est engluée (public pour l'auto-test). */
	public static boolean webShot(ServerPlayerEntity player) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		int now = world.getServer().getTicks();
		if (now < NEXT_SHOT.getOrDefault(player.getUuid(), 0)) {
			return false;
		}
		NEXT_SHOT.put(player.getUuid(), now + SHOT_COOLDOWN);
		Vec3d eye = player.getEyePos();
		Vec3d far = eye.add(player.getRotationVector().multiply(SHOT_RANGE));
		BlockHitResult block = world.raycast(new RaycastContext(eye, far, RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		Vec3d end = block.getType() == HitResult.Type.MISS ? far : block.getPos();
		EntityHitResult aimed = ProjectileUtil.raycast(player, eye, end, new Box(eye, end).expand(1.0),
				e -> e instanceof LivingEntity && e.isAlive() && e != player, eye.squaredDistanceTo(end));
		Vec3d hit = aimed != null ? aimed.getEntity().getBoundingBox().getCenter() : end;
		// le fil de toile
		Vec3d step = hit.subtract(eye);
		int count = (int) (step.length() * 2);
		ItemStackParticleEffect silk = new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.COBWEB));
		for (int i = 1; i <= count; i++) {
			Vec3d p = eye.add(step.multiply(i / (double) count)).add(0, -0.2, 0);
			world.spawnParticles(silk, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.8F);
		if (aimed != null && aimed.getEntity() instanceof LivingEntity target) {
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4), player);
			target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1), player);
			target.damage(world, player.getDamageSources().playerAttack(player), 2.0F);
			placeWeb(world, target.getBlockPos(), now);
			world.playSound(null, target.getBlockPos(), SoundEvents.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 1.2F, 0.8F);
			return true;
		}
		if (block.getType() == HitResult.Type.BLOCK) {
			placeWeb(world, block.getBlockPos().offset(block.getSide()), now);
		}
		return true;
	}

	/** Une toile temporaire (seulement dans l'air ou ce qui se remplace, comme l'herbe). */
	private static void placeWeb(ServerWorld world, BlockPos pos, int now) {
		if (world.getBlockState(pos).isReplaceable() && !world.getBlockState(pos).isOf(Blocks.WATER)
				&& !world.getBlockState(pos).isOf(Blocks.LAVA)) {
			world.setBlockState(pos, Blocks.COBWEB.getDefaultState());
			WEBS.add(new TempWeb(world, pos, now + WEB_LIFE));
		}
	}
}
