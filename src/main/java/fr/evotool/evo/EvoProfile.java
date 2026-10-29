package fr.evotool.evo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.registry.RegistryKey;

/** Décrit comment un outil évolue : d'où vient l'XP, ce qu'il débloque et à quel niveau. */
public final class EvoProfile {
	public record ModeUnlock(EvoMode mode, int level) {
	}

	public record EnchantStep(RegistryKey<Enchantment> enchantment, String translationKey, int[] levels) {
		public int levelAt(int toolLevel) {
			int count = 0;
			for (int threshold : levels) {
				if (toolLevel >= threshold) {
					count++;
				}
			}
			return count;
		}
	}

	/** Ce que compte le compteur affiché dans l'info-bulle. */
	public enum Counter {
		BLOCKS, KILLS, FARMING, DAMAGE;

		public String translationKey() {
			return "tooltip.evotool.count." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private XpCurve curve = XpCurve.MINING;
	private Counter counter = Counter.BLOCKS;
	private boolean blockXp;
	private boolean killXp;
	private boolean areaMining;
	private boolean speedBonus;
	private boolean damageBonus;
	private EquipmentSlot armorSlot;
	private final List<ModeUnlock> modes = new ArrayList<>(List.of(new ModeUnlock(EvoMode.SINGLE, 0)));
	private final Map<Power, Integer> powers = new LinkedHashMap<>();
	private final List<EnchantStep> enchants = new ArrayList<>();

	EvoProfile curve(XpCurve value) {
		curve = value;
		return this;
	}

	EvoProfile counter(Counter value) {
		counter = value;
		return this;
	}

	EvoProfile blockXp() {
		blockXp = true;
		speedBonus = true;
		return this;
	}

	EvoProfile killXp() {
		killXp = true;
		damageBonus = true;
		return this;
	}

	EvoProfile armor(EquipmentSlot slot) {
		armorSlot = slot;
		curve = XpCurve.ARMOR;
		counter = Counter.DAMAGE;
		return this;
	}

	EvoProfile withAreaMining() {
		areaMining = true;
		return this;
	}

	EvoProfile mode(EvoMode mode, int level) {
		modes.add(new ModeUnlock(mode, level));
		return this;
	}

	EvoProfile power(Power power, int level) {
		powers.put(power, level);
		return this;
	}

	/** @param name nom vanilla (« fortune ») ou clé de traduction complète (« enchantment.evotool.xxx »). */
	EvoProfile enchant(RegistryKey<Enchantment> enchantment, String name, int... levels) {
		String key = name.contains(".") ? name : "enchantment.minecraft." + name;
		enchants.add(new EnchantStep(enchantment, key, levels));
		return this;
	}

	public XpCurve curve() {
		return curve;
	}

	public Counter counter() {
		return counter;
	}

	/** L'outil gagne de l'XP en cassant des blocs. */
	public boolean gainsBlockXp() {
		return blockXp;
	}

	/** L'outil gagne de l'XP en tuant des monstres. */
	public boolean gainsKillXp() {
		return killXp;
	}

	/** Les modes de zone s'appliquent au minage (pioche, pelle) et pas seulement au labour (houe). */
	public boolean areaMining() {
		return areaMining;
	}

	public boolean speedBonus() {
		return speedBonus;
	}

	public boolean damageBonus() {
		return damageBonus;
	}

	/** Emplacement porté pour une pièce d'armure, sinon null. */
	public EquipmentSlot armorSlot() {
		return armorSlot;
	}

	public boolean isArmor() {
		return armorSlot != null;
	}

	public List<ModeUnlock> modes() {
		return modes;
	}

	public Map<Power, Integer> powers() {
		return powers;
	}

	public List<EnchantStep> enchants() {
		return enchants;
	}

	public boolean hasPower(Power power, int level) {
		Integer required = powers.get(power);
		return required != null && level >= required;
	}

	public int modeLevel(EvoMode mode) {
		for (ModeUnlock unlock : modes) {
			if (unlock.mode() == mode) {
				return unlock.level();
			}
		}
		return -1;
	}

	public boolean modeUnlocked(EvoMode mode, int level) {
		int required = modeLevel(mode);
		return required >= 0 && level >= required;
	}
}
