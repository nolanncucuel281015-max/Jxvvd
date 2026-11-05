package fr.evotool.plugin.spider;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.input.InputListener;
import fr.evotool.plugin.item.EvoItem;
import fr.evotool.plugin.item.Items;
import fr.evotool.plugin.util.Fx;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Gant de Spider-Man : la toile pour se balancer (clic droit ; accroupi : remonter, saut : lâcher), l'escalade des
 * murs, les pouvoirs de toile (accroupi + clic droit ; F : changer), le sens d'araignée, l'agilité, l'éveil du rituel
 * et les toiles qui disparaissent toutes seules. Le serveur connaît les touches du joueur : le pendule est calculé
 * ici et donné au joueur comme une vitesse, à chaque tick.
 */
public final class SpiderGlove implements Listener {
	public static final double SHOT_RANGE = 24.0;
	public static final double PULL_RANGE = 32.0;
	private static final double RANGE = 48.0;
	private static final double MAX_SPEED = 2.4;
	private static final double ZIP_SPEED = 1.8;
	private static final int ZIP_TICKS = 40;
	private static final int WEB_LIFE = 200;
	private static final double SENSE_RANGE = 32.0;
	private static final int DODGE_COOLDOWN = 40;
	private static final double BOMB_RADIUS = 4.5;

	private record TempWeb(Block block, int expires) {
	}

	/** La toile d'un joueur : point d'accroche, longueur, fil visible. */
	private static final class Web {
		final Vector anchor;
		final ItemDisplay strand;
		final boolean zip;
		double rope;
		int groundTicks;
		int zipTicks;
		boolean wasJumping = true;

		Web(Vector anchor, ItemDisplay strand, boolean zip, double rope) {
			this.anchor = anchor;
			this.strand = strand;
			this.zip = zip;
			this.rope = rope;
		}
	}

	private static final List<TempWeb> WEBS = new ArrayList<>();
	private static final Map<UUID, int[]> NEXT_USE = new HashMap<>();
	private static final Map<UUID, Integer> NEXT_DODGE = new HashMap<>();
	private static final Map<UUID, Set<UUID>> THREATS = new HashMap<>();
	private static final Map<UUID, Web> ACTIVE = new HashMap<>();

	public record Aim(Vector eye, Vector end, RayTraceResult block, LivingEntity target) {
		public boolean onBlock() {
			return target == null && block != null && block.getHitBlock() != null;
		}
	}

	public static void enable() {
		EvoToolPlugin.listen(new SpiderGlove());
		EvoToolPlugin.ticker(SpiderGlove::tick);
		InputListener.onUse((player, stack, clicked, hand) -> {
			if (!Items.is(stack, EvoItem.SPIDER_GLOVE)) {
				return false;
			}
			if (player.isSneaking()) {
				usePower(player, WebPower.of(stack));
			} else {
				toggle(player);
			}
			return true;
		});
		InputListener.onSwap((player, main) -> {
			if (!Items.is(main, EvoItem.SPIDER_GLOVE)) {
				return false;
			}
			cyclePower(player);
			return true;
		});
	}

	public static boolean holds(Player player) {
		return Items.is(player.getInventory().getItemInMainHand(), EvoItem.SPIDER_GLOVE)
				|| Items.is(player.getInventory().getItemInOffHand(), EvoItem.SPIDER_GLOVE);
	}

	public static ItemStack glove(Player player) {
		ItemStack main = player.getInventory().getItemInMainHand();
		if (Items.is(main, EvoItem.SPIDER_GLOVE)) {
			return main;
		}
		ItemStack off = player.getInventory().getItemInOffHand();
		return Items.is(off, EvoItem.SPIDER_GLOVE) ? off : null;
	}

	public static Aim aim(Player player, double range) {
		Vector eye = Fx.eye(player);
		Vector dir = Fx.look(player);
		World world = player.getWorld();
		RayTraceResult block = world.rayTraceBlocks(player.getEyeLocation(), dir, range, FluidCollisionMode.NEVER, true);
		Vector end = block == null ? eye.clone().add(dir.clone().multiply(range)) : block.getHitPosition();
		double reach = end.distance(eye);
		RayTraceResult entity = world.rayTraceEntities(player.getEyeLocation(), dir, reach, 0.5,
				e -> e instanceof LivingEntity l && !l.isDead() && e != player && !(e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR));
		LivingEntity target = entity != null ? (LivingEntity) entity.getHitEntity() : null;
		return new Aim(eye, target != null ? Fx.center(target) : end, block, target);
	}

	// ------------------------------------------------------------------ pouvoirs

	public static WebPower cyclePower(Player player) {
		ItemStack glove = glove(player);
		if (glove == null) {
			return null;
		}
		WebPower next = WebPower.of(glove).next();
		glove.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.WEB_POWER, PersistentDataType.INTEGER, next.ordinal()));
		fr.evotool.plugin.item.Lore.update(glove);
		player.sendActionBar(Lang.tr("message.evotool.web_power", Lang.tr(next.translationKey()))
				.color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
		Fx.sound(player, Sound.ENTITY_SPIDER_STEP, 0.8F, 1.6F);
		return next;
	}

	public static boolean usePower(Player player, WebPower power) {
		int now = Bukkit.getCurrentTick();
		int[] next = NEXT_USE.computeIfAbsent(player.getUniqueId(), id -> new int[WebPower.values().length]);
		if (now < next[power.ordinal()]) {
			return false;
		}
		next[power.ordinal()] = now + power.cooldown();
		return switch (power) {
			case SHOT -> webShot(player);
			case PULL -> pull(player);
			case BOMB -> webBomb(player) > 0;
			case WALL -> webWall(player) > 0;
		};
	}

	private static void silk(World world, Vector from, Vector to) {
		Vector step = to.clone().subtract(from);
		int count = (int) (step.length() * 2);
		ItemStack web = new ItemStack(Material.COBWEB);
		for (int i = 1; i <= count; i++) {
			Vector p = from.clone().add(step.clone().multiply(i / (double) count)).add(new Vector(0, -0.2, 0));
			Fx.particle(world, Particle.ITEM, p.getX(), p.getY(), p.getZ(), 1, 0.02, 0.02, 0.02, 0.0, web);
		}
	}

	private static void launch(LivingEntity target, Vector velocity) {
		target.setVelocity(velocity);
		target.setFallDistance(0);
	}

	public static boolean pull(Player player) {
		Aim aim = aim(player, PULL_RANGE);
		silk(player.getWorld(), aim.eye(), aim.end());
		Fx.sound(player, Sound.ENTITY_ARROW_SHOOT, 1.0F, 1.4F);
		if (aim.target() == null) {
			if (aim.onBlock()) {
				attach(player, aim.end(), true);   // on file vers le mur
				player.setVelocity(player.getVelocity().add(new Vector(0, 0.3, 0)));
				return true;
			}
			return false;
		}
		LivingEntity target = aim.target();
		Vector d = Fx.feet(player).subtract(Fx.feet(target));
		double horizontal = Math.sqrt(d.getX() * d.getX() + d.getZ() * d.getZ());
		Vector flat = new Vector(d.getX(), 0, d.getZ()).multiply(0.08);
		if (flat.length() > 2.5) {
			flat.normalize().multiply(2.5);
		}
		launch(target, new Vector(flat.getX(), 0.4 + horizontal * 0.015 + Math.max(0.0, d.getY()) * 0.08, flat.getZ()));
		Fx.sound(target, Sound.ENTITY_SPIDER_STEP, 1.2F, 0.7F);
		return true;
	}

	public static int webBomb(Player player) {
		World world = player.getWorld();
		int now = Bukkit.getCurrentTick();
		Aim aim = aim(player, SHOT_RANGE);
		Vector at;
		if (aim.target() != null) {
			at = Fx.feet(aim.target()).add(new Vector(0, 0.5, 0));
		} else if (aim.onBlock()) {
			Block b = aim.block().getHitBlock().getRelative(aim.block().getHitBlockFace());
			at = b.getLocation().toVector().add(new Vector(0.5, 0.5, 0.5));
		} else {
			at = aim.end();
		}
		silk(world, aim.eye(), at);
		Fx.particle(world, Particle.ITEM, at.getX(), at.getY(), at.getZ(), 90, 1.6, 1.2, 1.6, 0.15, new ItemStack(Material.COBWEB));
		Fx.particle(world, Particle.CLOUD, at, 30, 1.2, 0.8, 1.2, 0.05);
		Fx.particle(world, Particle.EXPLOSION, at, 1, 0, 0, 0, 0);
		Fx.sound(world, at, Sound.ENTITY_SLIME_SQUISH, 1.5F, 0.6F);
		Fx.sound(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.5F, 1.6F);
		int count = 0;
		Block center = world.getBlockAt(at.getBlockX(), at.getBlockY(), at.getBlockZ());
		BoundingBox self = player.getBoundingBox().clone().expand(0.6);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -2; dz <= 2; dz++) {
					Block b = center.getRelative(dx, dy, dz);
					if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) <= 2 && !self.overlaps(BoundingBox.of(b)) && placeWeb(b, now)) {
						count++;
					}
				}
			}
		}
		for (LivingEntity victim : Fx.living(world, Fx.box(at, BOMB_RADIUS), player)) {
			if (Fx.center(victim).distanceSquared(at) > BOMB_RADIUS * BOMB_RADIUS) {
				continue;
			}
			victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 4));
			victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));
			Fx.hurt(player, victim, 4.0);
			placeWeb(victim.getLocation().getBlock(), now);
			count++;
		}
		return count;
	}

	public static int webWall(Player player) {
		int now = Bukkit.getCurrentTick();
		BlockFace facing = player.getFacing();
		BlockFace side = switch (facing) {
			case NORTH -> BlockFace.EAST;
			case EAST -> BlockFace.SOUTH;
			case SOUTH -> BlockFace.WEST;
			default -> BlockFace.NORTH;
		};
		Block base = player.getLocation().getBlock().getRelative(facing, 3);
		int count = 0;
		for (int w = -2; w <= 2; w++) {
			for (int h = 0; h < 3; h++) {
				Block b = base.getRelative(side, w).getRelative(BlockFace.UP, h);
				if (placeWeb(b, now)) {
					count++;
					Fx.particle(player.getWorld(), Particle.ITEM, b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02,
							new ItemStack(Material.COBWEB));
				}
			}
		}
		silk(player.getWorld(), Fx.eye(player), base.getLocation().toVector().add(new Vector(0.5, 1.5, 0.5)));
		Fx.sound(player.getWorld(), base.getLocation().toVector(), Sound.ENTITY_SPIDER_STEP, 1.5F, 0.6F);
		Fx.sound(player, Sound.ENTITY_ARROW_SHOOT, 1.0F, 1.2F);
		return count;
	}

	public static boolean webShot(Player player) {
		int now = Bukkit.getCurrentTick();
		Aim aim = aim(player, SHOT_RANGE);
		silk(player.getWorld(), aim.eye(), aim.end());
		Fx.sound(player, Sound.ENTITY_ARROW_SHOOT, 1.0F, 1.8F);
		if (aim.target() != null) {
			LivingEntity target = aim.target();
			target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
			target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 1));
			Fx.hurt(player, target, 2.0);
			placeWeb(target.getLocation().getBlock(), now);
			Fx.sound(target, Sound.ENTITY_SPIDER_STEP, 1.2F, 0.8F);
			return true;
		}
		if (aim.onBlock()) {
			placeWeb(aim.block().getHitBlock().getRelative(aim.block().getHitBlockFace()), now);
		}
		return true;
	}

	private static boolean placeWeb(Block block, int now) {
		Material type = block.getType();
		if (block.isReplaceable() && type != Material.WATER && type != Material.LAVA && type != Material.COBWEB) {
			block.setType(Material.COBWEB);
			WEBS.add(new TempWeb(block, now + WEB_LIFE));
			return true;
		}
		return false;
	}

	// ------------------------------------------------------------------ la toile pour se balancer

	private static Vector chest(Player player) {
		return Fx.feet(player).add(new Vector(0, 1.2, 0));
	}

	/** Clic droit : la toile s'accroche là où l'on vise (ou on la lâche). */
	public static void toggle(Player player) {
		if (ACTIVE.containsKey(player.getUniqueId())) {
			release(player, false);
			return;
		}
		RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), Fx.look(player), RANGE, FluidCollisionMode.NEVER, true);
		if (hit == null || hit.getHitBlock() == null) {
			return;
		}
		attach(player, hit.getHitPosition(), false);
	}

	private static void attach(Player player, Vector anchor, boolean zip) {
		release(player, false);
		ItemDisplay strand = player.getWorld().spawn(player.getLocation(), ItemDisplay.class, d -> {
			d.setItemStack(Items.display("web/strand"));
			d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
			d.setPersistent(false);
			d.getPersistentDataContainer().set(Keys.TEMPORARY, PersistentDataType.BYTE, (byte) 1);
			d.setBillboard(Display.Billboard.FIXED);
			d.setBrightness(new Display.Brightness(15, 15));
			d.setTeleportDuration(1);
			d.setShadowRadius(0);
		});
		Web web = new Web(anchor, strand, zip, Math.max(2.0, chest(player).distance(anchor) * 0.85));
		ACTIVE.put(player.getUniqueId(), web);
		Fx.particle(player.getWorld(), Particle.ITEM, anchor.getX(), anchor.getY(), anchor.getZ(), 12, 0.2, 0.2, 0.2, 0.05,
				new ItemStack(Material.COBWEB));
		Fx.sound(player, Sound.ENTITY_ARROW_SHOOT, 0.8F, 1.7F);
		drawStrand(player, web);
	}

	private static void release(Player player, boolean leap) {
		Web web = ACTIVE.remove(player.getUniqueId());
		if (web == null) {
			return;
		}
		web.strand.remove();
		if (leap) {
			Vector v = player.getVelocity();
			player.setVelocity(new Vector(v.getX() * 1.15, Math.max(v.getY(), 0.0) + 0.45, v.getZ() * 1.15));
		}
	}

	private static void webTick(Player player, Web web) {
		boolean jump = player.getCurrentInput().isJump();
		if (!holds(player) || player.isDead() || chest(player).distanceSquared(web.anchor) > 64 * 64 || !web.strand.isValid()) {
			release(player, false);
			return;
		}
		if (jump && !web.wasJumping && !((Entity) player).isOnGround()) {
			web.wasJumping = jump;
			release(player, true);
			return;
		}
		web.wasJumping = jump;
		if (web.zip) {
			Vector toAnchor = web.anchor.clone().subtract(chest(player));
			double distance = toAnchor.length();
			if (distance < 1.6 || ++web.zipTicks > ZIP_TICKS) {
				Vector v = player.getVelocity();
				release(player, false);
				player.setVelocity(new Vector(v.getX() * 0.4, 0.55, v.getZ() * 0.4));
				return;
			}
			player.setVelocity(toAnchor.multiply(Math.min(ZIP_SPEED, 0.5 + distance * 0.35) / distance));
		} else {
			if (((Entity) player).isOnGround()) {
				if (++web.groundTicks > 10) {
					release(player, false);
					return;
				}
			} else {
				web.groundTicks = 0;
			}
			if (player.isSneaking()) {
				web.rope = Math.max(2.0, web.rope - 0.6);
			}
			Vector toAnchor = web.anchor.clone().subtract(chest(player));
			double distance = toAnchor.length();
			Vector v = player.getVelocity();
			if (distance > web.rope && distance > 1.0E-3) {
				Vector dir = toAnchor.clone().multiply(1 / distance);
				double radial = v.dot(dir);
				if (radial < 0) {
					v.subtract(dir.clone().multiply(radial));
				}
				v.add(dir.clone().multiply(Math.min(0.3, (distance - web.rope) * 0.25)));
			}
			if (player.getCurrentInput().isForward()) {
				Vector look = Fx.look(player);
				v.add(new Vector(look.getX() * 0.06, 0.0, look.getZ() * 0.06));
			}
			if (v.length() > MAX_SPEED) {
				v.normalize().multiply(MAX_SPEED);
			}
			player.setVelocity(v);
			player.setFallDistance(0);
		}
		drawStrand(player, web);
	}

	/** Le fil de toile, de la main du joueur au point d'accroche (vu par tout le monde). */
	private static void drawStrand(Player player, Web web) {
		Vector look = Fx.look(player);
		Vector right = new Vector(-look.getZ(), 0, look.getX());
		right = right.lengthSquared() < 1.0E-6 ? new Vector() : right.normalize();
		Vector hand = Fx.feet(player).add(new Vector(0, 1.3, 0)).add(right.multiply(0.35)).add(player.getVelocity());
		Vector span = web.anchor.clone().subtract(hand);
		double length = span.length();
		if (length < 0.1) {
			return;
		}
		Vector unit = span.clone().multiply(1 / length);
		Location at = new Location(player.getWorld(), hand.getX(), hand.getY(), hand.getZ(), 0, 0);
		web.strand.teleport(at);
		Matrix4f m = new Matrix4f()
				.rotate(new Quaternionf().rotationTo(0, 1, 0, (float) unit.getX(), (float) unit.getY(), (float) unit.getZ()))
				.scale(1.0F, (float) length, 1.0F)
				.translate(0.0F, 0.5F, 0.0F)
				.rotateY((float) Math.PI);
		web.strand.setInterpolationDelay(0);
		web.strand.setInterpolationDuration(1);
		web.strand.setTransformationMatrix(m);
	}

	// ------------------------------------------------------------------ chaque tick

	private static void tick(int tick) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			Web web = ACTIVE.get(player.getUniqueId());
			if (web != null) {
				webTick(player, web);
			}
			boolean holding = holds(player);
			if (holding && !player.isFlying() && player.getGameMode() != GameMode.SPECTATOR) {
				climb(player);
			}
			if (tick % 10 == 0) {
				awakenInventory(player);
				if (holding && !player.isDead()) {
					agility(player);
					spiderSense(player);
				} else {
					THREATS.remove(player.getUniqueId());
				}
			}
		}
		for (Iterator<TempWeb> it = WEBS.iterator(); it.hasNext(); ) {
			TempWeb web = it.next();
			if (tick >= web.expires()) {
				if (web.block().getType() == Material.COBWEB) {
					web.block().setType(Material.AIR);
				}
				it.remove();
			}
		}
	}

	/** Grimper aux murs comme une araignée : contre un mur, avancer (ou sauter) fait monter ; accroupi, on y reste collé. */
	private static void climb(Player player) {
		if (!againstWall(player)) {
			return;
		}
		Vector v = player.getVelocity();
		var input = player.getCurrentInput();
		if (input.isForward() || input.isJump()) {
			player.setVelocity(new Vector(v.getX(), 0.22, v.getZ()));
			player.setFallDistance(0);
		} else if (player.isSneaking() && !((Entity) player).isOnGround()) {
			player.setVelocity(new Vector(v.getX(), Math.max(v.getY(), 0.0), v.getZ()));
		}
	}

	private static boolean againstWall(Player player) {
		BoundingBox box = player.getBoundingBox();
		Vector look = Fx.flatLook(player);
		BoundingBox ahead = box.clone().shift(look.getX() * 0.12, 0.1, look.getZ() * 0.12).expand(-0.02, -0.1, -0.02);
		return player.getWorld().hasCollisionsIn(ahead);
	}

	public static void agility(Player player) {
		player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 1, true, false, true));
		player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, true, false, true));
	}

	public static int spiderSense(Player player) {
		Set<UUID> threats = new HashSet<>();
		for (Entity e : player.getWorld().getNearbyEntities(player.getBoundingBox().clone().expand(SENSE_RANGE),
				e -> e instanceof Mob mob && !mob.isDead() && mob.getTarget() == player)) {
			((Mob) e).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 30, 0, true, false));
			threats.add(e.getUniqueId());
		}
		Set<UUID> before = THREATS.put(player.getUniqueId(), threats);
		boolean fresh = threats.stream().anyMatch(id -> before == null || !before.contains(id));
		if (fresh) {
			Fx.sound(player, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2F, 1.8F);
			player.sendActionBar(Lang.tr("message.evotool.spider_sense", threats.size()).color(NamedTextColor.RED));
		}
		return threats.size();
	}

	public static boolean dodge(Player player) {
		int now = Bukkit.getCurrentTick();
		if (now < NEXT_DODGE.getOrDefault(player.getUniqueId(), 0)) {
			return false;
		}
		NEXT_DODGE.put(player.getUniqueId(), now + DODGE_COOLDOWN);
		Vector at = Fx.feet(player).add(new Vector(0, 1.0, 0));
		Fx.particle(player.getWorld(), Particle.CLOUD, at, 12, 0.4, 0.6, 0.4, 0.05);
		Fx.particle(player.getWorld(), Particle.SWEEP_ATTACK, at, 1, 0, 0, 0, 0);
		Fx.sound(player, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 1.6F);
		player.sendActionBar(Lang.tr("message.evotool.spider_dodge").color(NamedTextColor.RED).decorate(TextDecoration.ITALIC));
		return true;
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent event) {
		if (!(event.getEntity() instanceof Player player) || !holds(player)) {
			return;
		}
		if (event.getCause() == EntityDamageEvent.DamageCause.FALL
				|| event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE && dodge(player)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		UUID id = event.getPlayer().getUniqueId();
		release(event.getPlayer(), false);
		NEXT_USE.remove(id);
		NEXT_DODGE.remove(id);
		THREATS.remove(id);
	}

	// ------------------------------------------------------------------ le rituel

	private static void awakenInventory(Player player) {
		for (ItemStack stack : player.getInventory().getContents()) {
			if (Items.is(stack, EvoItem.SPIDER_GLOVE) && !stack.getItemMeta().getPersistentDataContainer().has(Keys.AWAKENED)) {
				awaken(player, stack);
			}
		}
	}

	public static void awaken(Player player, ItemStack glove) {
		glove.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.AWAKENED, PersistentDataType.BYTE, (byte) 1));
		player.getWorld().strikeLightningEffect(player.getLocation());
		Vector at = Fx.feet(player).add(new Vector(0, 1.0, 0));
		Fx.particle(player.getWorld(), Particle.ITEM, at.getX(), at.getY(), at.getZ(), 60, 1.0, 1.0, 1.0, 0.1, new ItemStack(Material.COBWEB));
		Fx.particle(player.getWorld(), Particle.TOTEM_OF_UNDYING, at, 60, 0.6, 0.8, 0.6, 0.5);
		Fx.particle(player.getWorld(), Particle.END_ROD, at, 40, 0.8, 1.2, 0.8, 0.1);
		Fx.sound(player, Sound.BLOCK_END_PORTAL_SPAWN, 0.6F, 1.4F);
		player.sendMessage(Lang.tr("message.evotool.spider_glove_awakened").color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
	}

	public static void clear() {
		ACTIVE.values().forEach(w -> w.strand.remove());
		ACTIVE.clear();
		for (TempWeb web : WEBS) {
			if (web.block().getType() == Material.COBWEB) {
				web.block().setType(Material.AIR);
			}
		}
		WEBS.clear();
	}
}
