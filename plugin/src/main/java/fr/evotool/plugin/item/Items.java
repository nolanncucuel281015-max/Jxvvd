package fr.evotool.plugin.item;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import fr.evotool.plugin.evo.EvoKind;
import fr.evotool.plugin.evo.Progression;
import fr.evotool.plugin.util.Keys;
import fr.evotool.plugin.util.Lang;
import fr.evotool.plugin.weapon.MagicKind;
import fr.evotool.plugin.weapon.WeaponKind;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/** Fabrique et reconnaît les objets du mod. */
public final class Items {
	private Items() {
	}

	/** Un objet du mod, prêt à l'emploi (niveau 0 pour les objets évolutifs). */
	public static ItemStack create(EvoItem item) {
		return create(item, 1);
	}

	public static ItemStack create(EvoItem item, int count) {
		ItemStack stack = new ItemStack(item.base(), Math.max(1, count));
		stack.editMeta(meta -> {
			meta.getPersistentDataContainer().set(Keys.ITEM, PersistentDataType.STRING, item.id());
			meta.setItemModel(Keys.key(item.id()));
			meta.itemName(Lang.tr(item.translationKey()));
			meta.setRarity(item.rarity());
			if (!item.stackable()) {
				meta.setMaxStackSize(1);
			} else if (item.base().getMaxStackSize() < 64) {
				meta.setMaxStackSize(64);
			}
			if (item == EvoItem.BOSS_HEART) {
				meta.setEnchantmentGlintOverride(true);
			}
			if (item == EvoItem.EVOLIUM_LIGHTER && meta instanceof Damageable damageable) {
				damageable.setMaxDamage(128);
			}
			if (item.kind() != null) {
				meta.setUnbreakable(true);
			}
			if (item.kind() != null || item == EvoItem.SPIDER_GLOVE) {
				meta.setAttributeModifiers(baseAttributes(item));
			}
		});
		if (item.kind() != null) {
			MagicKind magic = magicKind(item.kind());
			if (magic != null) {
				stack.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.CHARGES, PersistentDataType.INTEGER, magic.maxCharges()));
			}
			Progression.refresh(stack, true);
		} else {
			Lore.update(stack);
		}
		return stack;
	}

	/**
	 * Attributs de départ : ceux de l'objet de base de Minecraft (outils, armure), ou ceux de l'arme du mod.
	 * L'évolution y ajoute ses bonus (voir {@link Progression#refresh}).
	 */
	public static Multimap<Attribute, AttributeModifier> baseAttributes(EvoItem item) {
		Multimap<Attribute, AttributeModifier> map = ArrayListMultimap.create();
		WeaponKind weapon = item.kind() != null ? weaponKind(item.kind()) : null;
		MagicKind magic = item.kind() != null ? magicKind(item.kind()) : null;
		if (weapon != null) {
			map.put(Attribute.ATTACK_DAMAGE, modifier("base_attack_damage", weapon.damage() - 1.0, EquipmentSlotGroup.MAINHAND));
			map.put(Attribute.ATTACK_SPEED, modifier("base_attack_speed", weapon.speed() - 4.0, EquipmentSlotGroup.MAINHAND));
			if (weapon.reach() > 0) {
				map.put(Attribute.ENTITY_INTERACTION_RANGE, modifier("weapon_reach", weapon.reach(), EquipmentSlotGroup.MAINHAND));
			}
		} else if (magic != null) {
			map.put(Attribute.ATTACK_DAMAGE, modifier("base_attack_damage", magic.damage() - 1.0, EquipmentSlotGroup.MAINHAND));
			map.put(Attribute.ATTACK_SPEED, modifier("base_attack_speed", magic.speed() - 4.0, EquipmentSlotGroup.MAINHAND));
		} else if (item == EvoItem.SPIDER_GLOVE) {
			// force d'araignée : un coup de poing à 7 dégâts qui repousse loin (2 coups par seconde)
			map.put(Attribute.ATTACK_DAMAGE, modifier("base_attack_damage", 6.0, EquipmentSlotGroup.MAINHAND));
			map.put(Attribute.ATTACK_SPEED, modifier("base_attack_speed", -2.0, EquipmentSlotGroup.MAINHAND));
			map.put(Attribute.ATTACK_KNOCKBACK, modifier("spider_strength", 1.5, EquipmentSlotGroup.MAINHAND));
		} else {
			map.putAll(item.base().getDefaultAttributeModifiers());
		}
		return map;
	}

	public static AttributeModifier modifier(String key, double amount, EquipmentSlotGroup slot) {
		return new AttributeModifier(Keys.key(key), amount, AttributeModifier.Operation.ADD_NUMBER, slot);
	}

	/** Quel objet du mod c'est (null : un objet de Minecraft). */
	public static EvoItem of(ItemStack stack) {
		if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
			return null;
		}
		String id = stack.getItemMeta().getPersistentDataContainer().get(Keys.ITEM, PersistentDataType.STRING);
		return id == null ? null : EvoItem.byId(id);
	}

	public static boolean is(ItemStack stack, EvoItem item) {
		return of(stack) == item;
	}

	/** Profil d'évolution de l'objet (null : il n'évolue pas). */
	public static EvoKind kindOf(ItemStack stack) {
		EvoItem item = of(stack);
		return item == null ? null : item.kind();
	}

	public static WeaponKind weaponKind(EvoKind kind) {
		for (WeaponKind w : WeaponKind.values()) {
			if (w.name().equals(kind.name())) {
				return w;
			}
		}
		return null;
	}

	public static MagicKind magicKind(EvoKind kind) {
		for (MagicKind m : MagicKind.values()) {
			if (m.name().equals(kind.name())) {
				return m;
			}
		}
		return null;
	}

	/** Un objet qui ne sert qu'à montrer un modèle du pack (objets d'affichage : tentacules, dôme, ailes…). */
	public static ItemStack display(String model, float... floats) {
		ItemStack stack = new ItemStack(Material.PAPER);
		stack.editMeta(meta -> {
			meta.setItemModel(NamespacedKey.fromString(model.contains(":") ? model : Keys.NAMESPACE + ":" + model));
			if (floats.length > 0) {
				setFloats(meta, floats);
			}
		});
		return stack;
	}

	public static void setFloats(ItemMeta meta, float... floats) {
		CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
		java.util.ArrayList<Float> list = new java.util.ArrayList<>();
		for (float f : floats) {
			list.add(f);
		}
		cmd.setFloats(list);
		meta.setCustomModelDataComponent(cmd);
	}

	/** Les objets dans l'ordre du catalogue (l'onglet créatif du mod). */
	public static List<EvoItem> catalogue() {
		return List.of(EvoItem.values());
	}

	/** Emplacement d'une pièce d'armure du mod (null : pas une armure). */
	public static EquipmentSlot armorSlot(ItemStack stack) {
		EvoKind kind = kindOf(stack);
		return kind != null && kind.profile().isArmor() ? kind.profile().armorSlot() : null;
	}
}
