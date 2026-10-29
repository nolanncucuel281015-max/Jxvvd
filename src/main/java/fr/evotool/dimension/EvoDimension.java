package fr.evotool.dimension;

import com.mojang.serialization.Codec;
import fr.evotool.EvoTool;
import fr.evotool.boss.BellBossEntity;
import fr.evotool.boss.ModEntities;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Dimension évolutive : plus un joueur y reste longtemps, plus tout évolue autour de lui.
 * Toutes les 10 minutes passées dans la dimension (sans en sortir), la dimension passe une phase (0 à 5) :
 * les blocs évolutifs proches rattrapent cette phase et le boss, le Slime Cloche, prend une nouvelle forme.
 */
public final class EvoDimension {
	public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, EvoTool.id("evolutive"));
	public static final int MAX_PHASE = 5;
	public static final int PHASE_TICKS = 20 * 60 * 10;
	/** Le boss apparaît 30 secondes après l'arrivée, puis 3 minutes après avoir été vaincu. */
	private static final int FIRST_BOSS_DELAY = 20 * 30;
	private static final int BOSS_RESPAWN_DELAY = 20 * 60 * 3;
	private static final int BOSS_SEARCH_RANGE = 96;
	/** Rayon de l'onde d'évolution lancée à chaque nouvelle phase. */
	private static final int WAVE_RADIUS = 40;
	private static final int WAVE_HALF_HEIGHT = 20;

	/** Temps (en ticks) passé dans la dimension pendant la visite en cours, sauvegardé avec le joueur. */
	public static final AttachmentType<Integer> TIME = AttachmentRegistry.<Integer>create(EvoTool.id("evolutive_time"),
			builder -> builder.persistent(Codec.INT).initializer(() -> 0));

	private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_BOSS = new HashMap<>();
	private static final List<Wave> WAVES = new ArrayList<>();

	private record Wave(ServerWorld world, BlockPos center, int phase, int[] radius) {
	}

	private EvoDimension() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EvoDimension::tick);
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (origin.getRegistryKey() == WORLD && destination.getRegistryKey() != WORLD) {
				player.setAttached(TIME, 0);   // on repart de zéro à la prochaine visite
				removeBar(player.getUuid());
			} else if (destination.getRegistryKey() == WORLD) {
				player.sendMessage(Text.translatable("message.evotool.enter_dimension").formatted(Formatting.LIGHT_PURPLE), false);
				destination.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.AMBIENT, 1.0F, 0.6F);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> removeBar(handler.getPlayer().getUuid()));
	}

	public static int time(PlayerEntity player) {
		Integer t = player.getAttached(TIME);
		return t == null ? 0 : t;
	}

	public static int phaseOf(int ticks) {
		return Math.min(MAX_PHASE, ticks / PHASE_TICKS);
	}

	public static int phase(PlayerEntity player) {
		return phaseOf(time(player));
	}

	private static void tick(MinecraftServer server) {
		ServerWorld world = server.getWorld(WORLD);
		if (world != null) {
			for (ServerPlayerEntity player : world.getPlayers()) {
				if (player.isSpectator()) {
					continue;
				}
				int before = time(player);
				int after = before + 1;
				player.setAttached(TIME, after);
				if (phaseOf(after) > phaseOf(before)) {
					phaseUp(world, player, phaseOf(after));
				}
				updateBar(player, after);
				if (after % 100 == 0) {
					maybeSpawnBoss(world, player, after);
				}
			}
		}
		tickWaves();
		if (server.getTicks() % 20 == 0) {
			BARS.entrySet().removeIf(entry -> {
				ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
				if (player == null || player.getEntityWorld().getRegistryKey() != WORLD) {
					entry.getValue().clearPlayers();
					return true;
				}
				return false;
			});
		}
	}

	private static void phaseUp(ServerWorld world, ServerPlayerEntity player, int phase) {
		player.sendMessage(Text.translatable("message.evotool.phase_up", phase, MAX_PHASE)
				.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.AMBIENT, 2.0F, 0.5F + phase * 0.1F);
		world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, 2.0F, 0.8F);
		world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 120, 3, 2, 3, 0.15);
		startWave(world, player.getBlockPos(), phase);
	}

	/** Onde d'évolution : un anneau qui s'élargit d'un bloc par tick et fait évoluer les blocs qu'il traverse. */
	public static void startWave(ServerWorld world, BlockPos center, int phase) {
		WAVES.add(new Wave(world, center, phase, new int[] {0}));
	}

	private static void tickWaves() {
		Iterator<Wave> it = WAVES.iterator();
		while (it.hasNext()) {
			Wave wave = it.next();
			int r = ++wave.radius()[0];
			if (r > WAVE_RADIUS) {
				it.remove();
				continue;
			}
			ServerWorld world = wave.world();
			BlockPos.Mutable p = new BlockPos.Mutable();
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					double d = Math.sqrt(dx * dx + dz * dz);
					if (d > r || d <= r - 1) {
						continue;
					}
					int x = wave.center().getX() + dx;
					int z = wave.center().getZ() + dz;
					if (!world.isChunkLoaded(x >> 4, z >> 4)) {
						continue;
					}
					boolean shown = false;
					for (int dy = -WAVE_HALF_HEIGHT; dy <= WAVE_HALF_HEIGHT; dy++) {
						p.set(x, wave.center().getY() + dy, z);
						BlockState state = world.getBlockState(p);
						if (state.contains(EvolvingBlock.EVOLUTION) && state.get(EvolvingBlock.EVOLUTION) < wave.phase()) {
							world.setBlockState(p, state.with(EvolvingBlock.EVOLUTION, wave.phase()), Block.NOTIFY_LISTENERS);
							if (!shown && (dx + dz) % 3 == 0 && world.getBlockState(p.up()).isAir()) {
								world.spawnParticles(ParticleTypes.END_ROD, x + 0.5, p.getY() + 1.1, z + 0.5, 1, 0.2, 0.1, 0.2, 0.02);
								shown = true;
							}
						}
					}
				}
			}
		}
	}

	/** Tick aléatoire d'un bloc évolutif : il gagne une évolution s'il est en retard sur le joueur le plus proche. */
	public static void evolveNearPlayers(ServerWorld world, BlockPos pos, BlockState state) {
		if (world.getRegistryKey() != WORLD) {
			return;
		}
		PlayerEntity nearest = null;
		double best = 48 * 48;
		for (ServerPlayerEntity player : world.getPlayers()) {
			double d = player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
			if (d < best) {
				best = d;
				nearest = player;
			}
		}
		if (nearest != null) {
			evolveToward(world, pos, state, phase(nearest));
		}
	}

	/** Fait gagner une évolution au bloc s'il est en dessous de {@code phase}. */
	public static boolean evolveToward(ServerWorld world, BlockPos pos, BlockState state, int phase) {
		if (!state.contains(EvolvingBlock.EVOLUTION)) {
			return false;
		}
		int evolution = state.get(EvolvingBlock.EVOLUTION);
		if (evolution >= phase) {
			return false;
		}
		world.setBlockState(pos, state.with(EvolvingBlock.EVOLUTION, evolution + 1), Block.NOTIFY_LISTENERS);
		world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.3, 0.1, 0.3, 0.02);
		return true;
	}

	private static void updateBar(ServerPlayerEntity player, int ticks) {
		ServerBossBar bar = BARS.computeIfAbsent(player.getUuid(), id -> {
			ServerBossBar b = new ServerBossBar(Text.empty(), BossBar.Color.PURPLE, BossBar.Style.NOTCHED_10);
			b.addPlayer(player);
			return b;
		});
		if (!bar.getPlayers().contains(player)) {
			bar.clearPlayers();
			bar.addPlayer(player);
		}
		if (ticks % 20 != 1) {
			return;
		}
		int phase = phaseOf(ticks);
		if (phase >= MAX_PHASE) {
			bar.setName(Text.translatable("bar.evotool.dimension_max", MAX_PHASE).formatted(Formatting.GOLD));
			bar.setPercent(1.0F);
			bar.setColor(BossBar.Color.YELLOW);
			return;
		}
		int left = (PHASE_TICKS - ticks % PHASE_TICKS) / 20;
		bar.setName(Text.translatable("bar.evotool.dimension", phase, MAX_PHASE,
				String.format("%d:%02d", left / 60, left % 60)).formatted(Formatting.LIGHT_PURPLE));
		bar.setPercent((ticks % PHASE_TICKS) / (float) PHASE_TICKS);
	}

	private static void removeBar(UUID player) {
		ServerBossBar bar = BARS.remove(player);
		if (bar != null) {
			bar.clearPlayers();
		}
	}

	private static void maybeSpawnBoss(ServerWorld world, ServerPlayerEntity player, int ticks) {
		if (ticks < FIRST_BOSS_DELAY) {
			return;
		}
		Integer next = NEXT_BOSS.get(player.getUuid());
		if (next != null && world.getServer().getTicks() < next) {
			return;
		}
		if (!world.getEntitiesByType(ModEntities.BELL_BOSS, player.getBoundingBox().expand(BOSS_SEARCH_RANGE), e -> e.isAlive()).isEmpty()) {
			return;
		}
		double angle = world.getRandom().nextDouble() * Math.PI * 2;
		int x = (int) Math.floor(player.getX() + Math.cos(angle) * 18);
		int z = (int) Math.floor(player.getZ() + Math.sin(angle) * 18);
		if (!world.isChunkLoaded(x >> 4, z >> 4)) {
			return;
		}
		int y = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y <= world.getBottomY() + 2) {
			return;
		}
		spawnBoss(world, new BlockPos(x, y, z), phase(player));
		player.sendMessage(Text.translatable("message.evotool.boss_awakens",
				Text.translatable(BellBossEntity.formKey(phase(player)))).formatted(Formatting.RED, Formatting.BOLD), false);
	}

	public static BellBossEntity spawnBoss(ServerWorld world, BlockPos pos, int form) {
		BellBossEntity boss = ModEntities.BELL_BOSS.create(world, SpawnReason.EVENT);
		if (boss == null) {
			return null;
		}
		boss.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.getRandom().nextFloat() * 360, 0);
		boss.setPersistent();
		boss.applyForm(form, true);
		world.spawnEntity(boss);
		world.playSound(null, pos, SoundEvents.BLOCK_BELL_USE, SoundCategory.HOSTILE, 4.0F, 0.5F);
		world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
		return boss;
	}

	/** Le boss est vaincu : les joueurs proches auront un nouveau boss dans 3 minutes. */
	public static void onBossDefeated(ServerWorld world, BlockPos pos) {
		int next = world.getServer().getTicks() + BOSS_RESPAWN_DELAY;
		for (ServerPlayerEntity player : world.getPlayers()) {
			if (player.getBlockPos().isWithinDistance(pos, BOSS_SEARCH_RANGE)) {
				NEXT_BOSS.put(player.getUuid(), next);
			}
		}
	}
}
