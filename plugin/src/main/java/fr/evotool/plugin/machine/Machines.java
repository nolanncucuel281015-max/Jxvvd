package fr.evotool.plugin.machine;

import fr.evotool.plugin.EvoToolPlugin;
import fr.evotool.plugin.block.Blocks;
import fr.evotool.plugin.block.CustomBlock;
import fr.evotool.plugin.util.Keys;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Les machines du mod (générateur de bouclier, combineur élémentaire) : leur état est rangé dans les données du
 * tronçon où elles sont posées (les blocs musicaux qui les portent n'ont pas d'entité de bloc). Les machines des
 * tronçons chargés tournent à chaque tick.
 */
public final class Machines implements Listener {
	/** Une machine posée. */
	public interface Machine {
		/** Un tick (seulement quand son tronçon est chargé). */
		void tick(int tick);

		/** Range son état dans le tronçon. */
		void save(PersistentDataContainer data);

		/** Son tronçon se décharge (ou le serveur s'arrête) : retirer ce qui s'affiche. */
		default void unload() {
		}
	}

	private static final Map<CustomBlock, Function<Block, Machine>> TYPES = new EnumMap<>(CustomBlock.class);
	private static final Map<Block, Machine> LOADED = new HashMap<>();
	private static final String PREFIX = "machine_";

	public static void register(CustomBlock type, Function<Block, Machine> factory) {
		TYPES.put(type, factory);
	}

	public static void enable() {
		EvoToolPlugin.listen(new Machines());
		EvoToolPlugin.ticker(Machines::tick);
		for (World world : Bukkit.getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				load(chunk);
			}
		}
		// sauvegarde régulière (un arrêt brutal ne perd que quelques secondes)
		EvoToolPlugin.ticker(tick -> {
			if (tick % 200 == 0) {
				saveAll();
			}
		});
	}

	public static void disable() {
		saveAll();
		LOADED.values().forEach(Machine::unload);
		LOADED.clear();
	}

	private static NamespacedKey key(Block block) {
		return Keys.key(PREFIX + block.getX() + "_" + block.getY() + "_" + block.getZ());
	}

	/** L'état rangé de la machine (un conteneur vide s'il n'y en a pas). */
	public static PersistentDataContainer stored(Block block) {
		PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
		PersistentDataContainer data = chunk.get(key(block), PersistentDataType.TAG_CONTAINER);
		return data != null ? data : chunk.getAdapterContext().newPersistentDataContainer();
	}

	public static void save(Block block, Machine machine) {
		PersistentDataContainer chunk = block.getChunk().getPersistentDataContainer();
		PersistentDataContainer data = chunk.getAdapterContext().newPersistentDataContainer();
		machine.save(data);
		chunk.set(key(block), PersistentDataType.TAG_CONTAINER, data);
	}

	private static void saveAll() {
		for (Map.Entry<Block, Machine> e : LOADED.entrySet()) {
			if (e.getKey().getWorld().isChunkLoaded(e.getKey().getX() >> 4, e.getKey().getZ() >> 4)) {
				save(e.getKey(), e.getValue());
			}
		}
	}

	/** La machine posée là (créée si besoin), ou null si ce n'est pas une machine. */
	public static Machine at(Block block) {
		Machine machine = LOADED.get(block);
		if (machine != null) {
			return machine;
		}
		CustomBlock type = Blocks.at(block);
		Function<Block, Machine> factory = type == null ? null : TYPES.get(type);
		if (factory == null) {
			return null;
		}
		machine = factory.apply(block);
		LOADED.put(block, machine);
		return machine;
	}

	/** La machine est cassée : on l'oublie (son contenu a déjà été rendu). */
	public static void removed(Block block) {
		Machine machine = LOADED.remove(block);
		if (machine != null) {
			machine.unload();
		}
		block.getChunk().getPersistentDataContainer().remove(key(block));
	}

	private static void tick(int tick) {
		for (Iterator<Map.Entry<Block, Machine>> it = new ArrayList<>(LOADED.entrySet()).iterator(); it.hasNext(); ) {
			Map.Entry<Block, Machine> e = it.next();
			Block block = e.getKey();
			if (!TYPES.containsKey(Blocks.at(block))) {
				// le bloc a disparu autrement (commande, explosion…)
				removed(block);
				continue;
			}
			e.getValue().tick(tick);
		}
	}

	private static void load(Chunk chunk) {
		PersistentDataContainer pdc = chunk.getPersistentDataContainer();
		List<NamespacedKey> stale = new ArrayList<>();
		for (NamespacedKey key : pdc.getKeys()) {
			if (!key.getNamespace().equals(Keys.NAMESPACE) || !key.getKey().startsWith(PREFIX)) {
				continue;
			}
			String[] xyz = key.getKey().substring(PREFIX.length()).split("_");
			try {
				Block block = chunk.getWorld().getBlockAt(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
				if (at(block) == null) {
					stale.add(key);
				}
			} catch (RuntimeException e) {
				stale.add(key);
			}
		}
		stale.forEach(pdc::remove);
	}

	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		load(event.getChunk());
	}

	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent event) {
		Chunk chunk = event.getChunk();
		for (Iterator<Map.Entry<Block, Machine>> it = LOADED.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Block, Machine> e = it.next();
			Block block = e.getKey();
			if (block.getWorld().equals(chunk.getWorld()) && block.getX() >> 4 == chunk.getX() && block.getZ() >> 4 == chunk.getZ()) {
				save(block, e.getValue());
				e.getValue().unload();
				it.remove();
			}
		}
	}

	// ------------------------------------------------------------------ objets rangés

	public static void putItem(PersistentDataContainer data, String name, ItemStack stack) {
		NamespacedKey key = Keys.key(name);
		if (stack == null || stack.isEmpty()) {
			data.remove(key);
		} else {
			data.set(key, PersistentDataType.BYTE_ARRAY, stack.serializeAsBytes());
		}
	}

	public static ItemStack getItem(PersistentDataContainer data, String name) {
		byte[] bytes = data.get(Keys.key(name), PersistentDataType.BYTE_ARRAY);
		if (bytes == null) {
			return null;
		}
		try {
			return ItemStack.deserializeBytes(bytes);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
