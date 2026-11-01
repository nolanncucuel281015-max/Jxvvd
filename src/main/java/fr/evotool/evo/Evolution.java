package fr.evotool.evo;

import fr.evotool.armor.ArmorSet;
import fr.evotool.network.EvolvedPayload;
import fr.evotool.upgrade.GiantItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;

/**
 * Évolution à la touche R : quand un objet a toute l'XP de sa prochaine forme, il attend. La touche R le fait
 * apparaître en géant devant le joueur : il monte en tournant de plus en plus vite dans un tourbillon d'étoiles et
 * de runes, au son de carillons de plus en plus aigus, puis éclate dans une gerbe de lumière sous sa nouvelle forme
 * (et la nouvelle forme jaillit à l'écran comme un totem), ralentit et revient dans la main dans un « pfff ».
 */
public final class Evolution {
	/** Durée de la montée en puissance, puis de toute l'animation (ticks). */
	private static final int CLIMAX = 40;
	private static final int END = 62;
	private static final float SIZE = 1.3F;

	private static final class Running {
		final ServerPlayerEntity player;
		final List<ItemStack> stacks;
		final DisplayEntity.ItemDisplayEntity display;
		final Vec3d center;
		final int start;
		float angle;

		Running(ServerPlayerEntity player, List<ItemStack> stacks, DisplayEntity.ItemDisplayEntity display, Vec3d center, int start) {
			this.player = player;
			this.stacks = stacks;
			this.display = display;
			this.center = center;
			this.start = start;
		}
	}

	private static final Map<UUID, Running> RUNNING = new HashMap<>();

	private Evolution() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick(server.getTicks()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> cancel(handler.getPlayer().getUuid()));
		// Les objets d'une ancienne version sont remis aux règles actuelles (enchantements, bonus) à la connexion.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> rebalance(handler.getPlayer()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Running running : RUNNING.values()) {
				GiantItem.discard(running.display);
			}
			RUNNING.clear();
		});
	}

	/** Objets prêts à évoluer : celui en main, sinon dans l'autre main, sinon les pièces d'armure portées. */
	public static List<ItemStack> candidates(ServerPlayerEntity player) {
		for (Hand hand : Hand.values()) {
			ItemStack stack = player.getStackInHand(hand);
			if (EvoProgression.isReadyToEvolve(stack)) {
				return List.of(stack);
			}
		}
		List<ItemStack> armor = new ArrayList<>();
		for (EquipmentSlot slot : ArmorSet.SLOTS) {
			ItemStack stack = player.getEquippedStack(slot);
			if (ArmorSet.isEvolvingArmor(stack, slot) && EvoProgression.isReadyToEvolve(stack)) {
				armor.add(stack);
			}
		}
		return armor;
	}

	/** Touche R : lance l'animation d'évolution (l'objet n'évolue vraiment qu'à son apogée). */
	public static boolean start(ServerPlayerEntity player) {
		if (RUNNING.containsKey(player.getUuid()) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		List<ItemStack> stacks = candidates(player);
		if (stacks.isEmpty()) {
			player.sendMessage(Text.translatable("message.evotool.nothing_to_evolve").formatted(Formatting.GRAY), true);
			return false;
		}
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		Vec3d forward = Vec3d.fromPolar(0.0F, player.getYaw());
		Vec3d center = player.getEyePos().add(forward.multiply(2.2)).add(0.0, -0.3, 0.0);
		DisplayEntity.ItemDisplayEntity display = GiantItem.spawn(world, stacks.get(0).copy(), ItemDisplayContext.FIXED,
				center, player.getYaw(), new Quaternionf(), SIZE, 10);
		RUNNING.put(player.getUuid(), new Running(player, stacks, display, center, world.getServer().getTicks()));

		BlockPos pos = BlockPos.ofFloored(center);
		world.playSound(null, pos, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.3F);
		world.playSound(null, pos, SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 1.0F, 0.8F);
		player.sendMessage(Text.translatable("message.evotool.evolving", stacks.get(0).getName())
				.formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), true);
		return true;
	}

	/** Termine tout de suite l'animation de ce joueur (auto-test) : vrai si l'objet a évolué. */
	public static boolean completeNow(ServerPlayerEntity player) {
		Running running = RUNNING.remove(player.getUuid());
		if (running == null) {
			return false;
		}
		ServerWorld world = (ServerWorld) running.display.getEntityWorld();
		boolean evolved = climax(running, world);
		GiantItem.discard(running.display);
		return evolved;
	}

	public static boolean isRunning(ServerPlayerEntity player) {
		return RUNNING.containsKey(player.getUuid());
	}

	private static void cancel(UUID id) {
		Running running = RUNNING.remove(id);
		if (running != null) {
			GiantItem.discard(running.display);
		}
	}

	private static float rad(double degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static void tick(int now) {
		for (Iterator<Running> it = RUNNING.values().iterator(); it.hasNext(); ) {
			Running running = it.next();
			int age = now - running.start;
			if (running.display.isRemoved() || running.player.isRemoved()) {
				GiantItem.discard(running.display);
				it.remove();
				continue;
			}
			ServerWorld world = (ServerWorld) running.display.getEntityWorld();
			if (age < CLIMAX) {
				charge(running, world, age);
			} else if (age == CLIMAX) {
				if (!climax(running, world)) {
					GiantItem.remove(world, running.display, SIZE);
					it.remove();
				}
			} else if (age < END) {
				settle(running, world, age - CLIMAX);
			} else {
				GiantItem.remove(world, running.display, SIZE);
				it.remove();
			}
		}
	}

	/** Montée en puissance : l'objet s'élève en tournant de plus en plus vite, les étoiles tourbillonnent. */
	private static void charge(Running running, ServerWorld world, int age) {
		float progress = age / (float) CLIMAX;
		running.angle += Math.min(45.0F, 3.0F + age * 1.1F);
		Quaternionf rotation = new Quaternionf().rotationY(rad(running.angle)).rotateZ(rad(8.0 * Math.sin(age * 0.35)));
		GiantItem.setPose(running.display, rotation, SIZE * (1.0F + 0.15F * progress), 1);
		Vec3d c = running.center.add(0.0, 0.4 * progress, 0.0);
		running.display.setPosition(c.x, c.y, c.z);

		double radius = 1.4 - 0.7 * progress;
		for (int k = 0; k < 3; k++) {
			double a = age * 0.45 + k * Math.PI * 2 / 3;
			world.spawnParticles(ParticleTypes.END_ROD, c.x + Math.cos(a) * radius, c.y - 0.6 + 1.2 * ((age + k * 7) % 14) / 14.0,
					c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
		}
		world.spawnParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.3, c.z, 8, 1.4, 1.0, 1.4, 1.0);
		if (age > 24) {
			world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 4, 0.5, 0.5, 0.5, 0.15);
		}
		BlockPos pos = BlockPos.ofFloored(c);
		if (age % 8 == 0) {
			world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.4F, 0.6F + 1.4F * progress);
		}
		if (age == 20) {
			world.playSound(null, pos, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.0F, 1.2F);
		}
		if (age == 34) {
			world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.2F, 1.5F);
		}
	}

	/** Apogée : l'objet évolue vraiment, éclate en lumière et montre sa nouvelle forme. Faux s'il n'est plus là. */
	private static boolean climax(Running running, ServerWorld world) {
		ServerPlayerEntity player = running.player;
		ItemStack shown = ItemStack.EMPTY;
		for (ItemStack stack : running.stacks) {
			if (owns(player, stack) && EvoProgression.evolve(stack, player, world)) {
				if (shown.isEmpty()) {
					shown = stack;
				}
				player.sendMessage(Text.translatable("message.evotool.evolved", stack.getName(), EvoProgression.get(stack).level())
						.formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), false);
			}
		}
		Vec3d c = new Vec3d(running.display.getX(), running.display.getY(), running.display.getZ());
		if (shown.isEmpty()) {
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.4, 0.4, 0.4, 0.02);
			player.sendMessage(Text.translatable("message.evotool.evolution_cancelled").formatted(Formatting.RED), true);
			return false;
		}
		running.display.setItemStack(shown.copy());
		GiantItem.setPose(running.display, new Quaternionf().rotationY(rad(running.angle)), SIZE * 1.4F, 2);

		world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 90, 0.5, 0.6, 0.5, 0.6);
		world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 50, 0.2, 0.2, 0.2, 0.35);
		world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		BlockPos pos = BlockPos.ofFloored(c);
		world.playSound(null, pos, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.7F, 1.3F);
		world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
		world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 1.0F, 1.2F);
		if (ServerPlayNetworking.canSend(player, EvolvedPayload.ID)) {
			ServerPlayNetworking.send(player, new EvolvedPayload(shown.copy()));
		}
		return true;
	}

	/** Après l'éclat : la nouvelle forme ralentit doucement en flottant, puis disparaît. */
	private static void settle(Running running, ServerWorld world, int age) {
		float progress = age / (float) (END - CLIMAX);
		running.angle += Math.max(4.0F, 30.0F - age * 1.4F);
		GiantItem.setPose(running.display, new Quaternionf().rotationY(rad(running.angle)),
				SIZE * (1.4F - 0.3F * progress), 2);
		if (age % 3 == 0) {
			Vec3d c = new Vec3d(running.display.getX(), running.display.getY(), running.display.getZ());
			world.spawnParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 3, 0.6, 0.6, 0.6, 0.02);
		}
	}

	/** L'objet est-il toujours sur le joueur (il a pu le jeter ou le ranger pendant l'animation) ? */
	private static boolean owns(ServerPlayerEntity player, ItemStack stack) {
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			if (inventory.getStack(i) == stack) {
				return true;
			}
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (player.getEquippedStack(slot) == stack) {
				return true;
			}
		}
		return false;
	}

	/** Remet les objets évolutifs du joueur aux règles actuelles (enchantements plafonnés, bonus). */
	private static void rebalance(ServerPlayerEntity player) {
		List<ItemStack> stacks = new ArrayList<>();
		PlayerInventory inventory = player.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			stacks.add(inventory.getStack(i));
		}
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			stacks.add(player.getEquippedStack(slot));
		}
		for (ItemStack stack : stacks) {
			if (EvoProgression.kindOf(stack) != null) {
				EvoProgression.refresh(stack, player.getEntityWorld().getRegistryManager());
			}
		}
	}
}
