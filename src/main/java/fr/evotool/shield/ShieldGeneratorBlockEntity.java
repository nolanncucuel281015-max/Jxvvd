package fr.evotool.shield;

import fr.evotool.EvoTool;
import fr.evotool.block.ModBlocks;
import fr.evotool.component.ModComponents;
import fr.evotool.evo.EvoProgression;
import fr.evotool.item.ModItems;
import fr.evotool.upgrade.GiantItem;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Le générateur de bouclier : il brûle 1 évolium toutes les 10 minutes pour tenir un dôme d'énergie autour de lui
 * (rayon réglable de 3 à 64 blocs). Les monstres qui sont dedans en sont expulsés, ceux qui s'en approchent sont
 * repoussés (les creepers n'y explosent pas) et les projectiles qui ne viennent pas d'un joueur s'y désintègrent.
 * Sa case avale l'évolium (et les blocs d'évolium, 9 chacun) jusqu'à 1 milliard.
 */
public class ShieldGeneratorBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final long CAPACITY = 1_000_000_000L;
	/** 1 évolium toutes les 10 minutes. */
	public static final int TICKS_PER_EVOLIUM = 12_000;
	public static final int MIN_RADIUS = 3;
	public static final int MAX_RADIUS = 64;
	public static final int DEFAULT_RADIUS = 12;
	/** Rayon de la sphère dans le modèle du dôme (15 pixels). */
	private static final float MODEL_RADIUS = 15.0F / 16.0F;

	private long fuel;
	private int burn;
	private int radius = DEFAULT_RADIUS;
	private boolean enabled = true;
	private int age;
	private boolean wasActive;
	private float spin;
	private DisplayEntity.ItemDisplayEntity dome;
	private int domeRadius = -1;

	private final SimpleInventory input = new SimpleInventory(1) {
		@Override
		public void markDirty() {
			super.markDirty();
			ShieldGeneratorBlockEntity.this.markDirty();
		}
	};

	/**
	 * Valeurs envoyées au menu. Minecraft les transmet sur 16 bits : l'évolium (jusqu'à 1 milliard) est coupé en deux.
	 * 0 = évolium (bits bas), 1 = évolium (bits hauts), 2 = ticks restants de l'évolium en cours, 3 = rayon, 4 = allumé.
	 */
	private final PropertyDelegate properties = new PropertyDelegate() {
		@Override
		public int get(int index) {
			return switch (index) {
				case 0 -> (int) (fuel & 0xFFFF);
				case 1 -> (int) (fuel >>> 16);
				case 2 -> burn;
				case 3 -> radius;
				case 4 -> enabled ? 1 : 0;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
		}

		@Override
		public int size() {
			return 5;
		}
	};

	public ShieldGeneratorBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SHIELD_GENERATOR_ENTITY, pos, state);
	}

	/** Évolium que vaut cet objet dans la case (0 s'il n'y va pas). */
	public static int fuelValue(ItemStack stack) {
		if (stack.isOf(ModItems.EVOLIUM)) {
			return 1;
		}
		return stack.isOf(ModBlocks.EVOLIUM_BLOCK.asItem()) ? 9 : 0;
	}

	public long fuel() {
		return fuel;
	}

	public int radius() {
		return radius;
	}

	/** Allumé et alimenté : le dôme est là. */
	public boolean isActive() {
		return enabled && (burn > 0 || fuel > 0);
	}

	public SimpleInventory input() {
		return input;
	}

	/** Reprend l'évolium et le rayon d'un générateur ramassé. */
	public void load(long storedFuel, int storedRadius) {
		fuel = MathHelper.clamp(storedFuel, 0L, CAPACITY);
		radius = MathHelper.clamp(storedRadius, MIN_RADIUS, MAX_RADIUS);
		markDirty();
	}

	/** Ce que lâche le générateur cassé : lui-même avec son évolium et son rayon, et ce qui restait dans la case. */
	public List<ItemStack> drops() {
		List<ItemStack> drops = new ArrayList<>();
		ItemStack self = new ItemStack(ModBlocks.SHIELD_GENERATOR);
		// l'évolium entamé n'est pas perdu : il compte comme un évolium entier
		long stored = Math.min(CAPACITY, fuel + (burn > 0 ? 1 : 0));
		if (stored > 0) {
			self.set(ModComponents.SHIELD_FUEL, stored);
		}
		self.set(ModComponents.SHIELD_RADIUS, radius);
		drops.add(self);
		if (!input.getStack(0).isEmpty()) {
			drops.add(input.getStack(0).copy());
			input.setStack(0, ItemStack.EMPTY);
		}
		removeDome();
		return drops;
	}

	/** Boutons du menu (voir ShieldGeneratorScreenHandler). */
	public void press(int button) {
		switch (button) {
			case ShieldGeneratorScreenHandler.BUTTON_MINUS_TEN -> setRadius(radius - 10);
			case ShieldGeneratorScreenHandler.BUTTON_MINUS_ONE -> setRadius(radius - 1);
			case ShieldGeneratorScreenHandler.BUTTON_PLUS_ONE -> setRadius(radius + 1);
			case ShieldGeneratorScreenHandler.BUTTON_PLUS_TEN -> setRadius(radius + 10);
			case ShieldGeneratorScreenHandler.BUTTON_TOGGLE -> {
				enabled = !enabled;
				markDirty();
			}
			default -> {
			}
		}
	}

	private void setRadius(int value) {
		radius = MathHelper.clamp(value, MIN_RADIUS, MAX_RADIUS);
		markDirty();
	}

	public static void tick(World world, BlockPos pos, BlockState state, ShieldGeneratorBlockEntity generator) {
		if (world instanceof ServerWorld serverWorld) {
			generator.serverTick(serverWorld);
		}
	}

	/** Un tick du générateur (public pour l'auto-test). */
	public void serverTick(ServerWorld world) {
		age++;
		absorb();
		if (enabled) {
			if (burn <= 0 && fuel > 0) {
				fuel--;
				burn = TICKS_PER_EVOLIUM;
				markDirty();
			}
			if (burn > 0) {
				burn--;
				if (age % 200 == 0) {
					markDirty();
				}
			}
		}
		boolean active = isActive();
		if (active != wasActive) {
			world.playSound(null, pos, active ? SoundEvents.BLOCK_BEACON_ACTIVATE : SoundEvents.BLOCK_BEACON_DEACTIVATE,
					SoundCategory.BLOCKS, 1.0F, 1.2F);
			wasActive = active;
		}
		if (active) {
			updateDome(world);
			if (age % 4 == 0) {
				protect(world);
			}
		} else {
			removeDome();
		}
	}

	/** La case avale l'évolium posé dedans, jusqu'à 1 milliard. */
	private void absorb() {
		ItemStack stack = input.getStack(0);
		int value = fuelValue(stack);
		if (value <= 0) {
			return;
		}
		long room = (CAPACITY - fuel) / value;
		int taken = (int) Math.min(stack.getCount(), room);
		if (taken > 0) {
			fuel += (long) taken * value;
			stack.decrement(taken);
			input.setStack(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
			markDirty();
		}
	}

	// ------------------------------------------------------------------ dôme d'énergie

	private void updateDome(ServerWorld world) {
		if (dome == null || dome.isRemoved()) {
			// un ancien dôme de ce générateur (tronçon rechargé…) est retiré avant d'en faire un neuf
			String tag = domeTag();
			for (DisplayEntity.ItemDisplayEntity old : world.getEntitiesByType(EntityType.ITEM_DISPLAY,
					new Box(pos).expand(MAX_RADIUS + 2), e -> e.getCommandTags().contains(tag))) {
				GiantItem.discard(old);
			}
			ItemStack stack = new ItemStack(Items.AMETHYST_SHARD);
			stack.set(DataComponentTypes.ITEM_MODEL, EvoTool.id("shield/dome"));
			dome = GiantItem.create(world, stack, ItemDisplayContext.NONE, Vec3d.ofCenter(pos), 0.0F, new Quaternionf(), 0.1F, 1);
			dome.addCommandTag(tag);
			dome.setViewRange(4.0F);
			domeRadius = -1;
			world.spawnParticles(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.3, 0.6, 0.3, 0.3);
		}
		boolean resized = domeRadius != radius;
		if (resized || age % 20 == 0) {
			if (resized) {
				// l'entité est posée en bas de la sphère, avec une boîte de la taille du dôme (pour être vue de loin)
				Vec3d c = Vec3d.ofCenter(pos);
				dome.setPosition(c.x, c.y - radius, c.z);
				dome.setDisplayWidth(radius * 2.0F + 1.0F);
				dome.setDisplayHeight(radius * 2.0F + 1.0F);
				domeRadius = radius;
			}
			spin += 6.0F;
			float scale = radius / MODEL_RADIUS;
			dome.setTransformation(new AffineTransformation(new Vector3f(0.0F, radius, 0.0F),
					new Quaternionf().rotationY((float) Math.toRadians(spin)), new Vector3f(scale), new Quaternionf()));
			dome.setInterpolationDuration(resized ? 10 : 20);
			dome.setStartInterpolation(0);
		}
	}

	private String domeTag() {
		return "evotool_shield_dome_" + pos.asLong();
	}

	private void removeDome() {
		if (dome != null) {
			GiantItem.discard(dome);
			dome = null;
			domeRadius = -1;
		}
	}

	@Override
	public void markRemoved() {
		super.markRemoved();
		removeDome();
	}

	// ------------------------------------------------------------------ protection

	private void protect(ServerWorld world) {
		Vec3d c = Vec3d.ofCenter(pos);
		double r = radius;
		Box area = new Box(pos).expand(r + 3);
		for (LivingEntity mob : world.getEntitiesByClass(LivingEntity.class, area, e -> e instanceof Monster && e.isAlive())) {
			Vec3d at = mob.getEntityPos().add(0, mob.getHeight() / 2, 0);
			double distance = at.distanceTo(c);
			if (distance > r + 2) {
				continue;
			}
			if (mob instanceof CreeperEntity creeper) {
				creeper.setFuseSpeed(-1);   // pas d'explosion contre le bouclier
			}
			Vec3d out = new Vec3d(at.x - c.x, 0, at.z - c.z);
			if (out.lengthSquared() < 1.0E-4) {
				out = new Vec3d(1, 0, 0);
			}
			out = out.normalize();
			if (distance < r - 2 && !EvoProgression.isBoss(mob)) {
				// dedans : le monstre est rejeté hors du dôme, sur le sol
				Vec3d target = c.add(out.multiply(r + 2.5));
				int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, MathHelper.floor(target.x), MathHelper.floor(target.z));
				world.spawnParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 20, 0.3, 0.5, 0.3, 0.05);
				mob.requestTeleport(target.x, top, target.z);
				world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 0.5F, 1.4F);
			} else {
				// au bord : le bouclier le repousse
				mob.takeKnockback(1.2, c.x - at.x, c.z - at.z);
				Vec3d spark = c.add(at.subtract(c).normalize().multiply(r));
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, spark.x, spark.y, spark.z, 8, 0.2, 0.3, 0.2, 0.1);
			}
		}
		for (ProjectileEntity projectile : world.getEntitiesByClass(ProjectileEntity.class, area,
				p -> !(p.getOwner() instanceof PlayerEntity))) {
			Vec3d at = projectile.getEntityPos();
			if (at.distanceTo(c) <= r + 0.5) {
				world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.1, 0.1, 0.1, 0.2);
				world.playSound(null, projectile.getBlockPos(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.BLOCKS, 1.0F, 1.5F);
				projectile.discard();
			}
		}
	}

	// ------------------------------------------------------------------ menu et sauvegarde

	@Override
	public Text getDisplayName() {
		return Text.translatable("block.evotool.shield_generator");
	}

	@Override
	public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
		return new ShieldGeneratorScreenHandler(syncId, playerInventory, input, properties, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayerEntity player) {
		return pos;
	}

	@Override
	protected void writeData(WriteView view) {
		super.writeData(view);
		view.putLong("Fuel", fuel);
		view.putInt("Burn", burn);
		view.putInt("Radius", radius);
		view.putBoolean("Enabled", enabled);
		view.put("Input", ItemStack.OPTIONAL_CODEC, input.getStack(0));
	}

	@Override
	protected void readData(ReadView view) {
		super.readData(view);
		fuel = MathHelper.clamp(view.getLong("Fuel", 0L), 0L, CAPACITY);
		burn = view.getInt("Burn", 0);
		radius = MathHelper.clamp(view.getInt("Radius", DEFAULT_RADIUS), MIN_RADIUS, MAX_RADIUS);
		enabled = view.getBoolean("Enabled", true);
		input.setStack(0, view.read("Input", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
	}
}
