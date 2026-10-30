package fr.evotool.weapon;

/**
 * Les armes magiques : elles ont des charges (une par sort) qui se rechargent toutes seules avec le temps,
 * voir {@link ArcaneWeapons}. Accroupi + clic droit lance la variante puissante du sort.
 */
public enum MagicKind {
	/** Missile arcanique à tête chercheuse ; accroupi : salve qui vide toutes les charges. */
	ARCANE_WAND("arcane_wand", 6, 40, 3.0, 1.6, 0xB060FF),
	/** Éclair en chaîne qui saute d'ennemi en ennemi ; accroupi : onde de tonnerre autour de soi. */
	THUNDER_GAUNTLET("thunder_gauntlet", 3, 80, 8.0, 1.4, 0x4FD8FF),
	/** Éclats d'étoile qui tournent autour du joueur ; accroupi : ils partent tous en même temps. */
	CELESTIAL_ORB("celestial_orb", 2, 240, 3.0, 1.6, 0xFFE070),
	/** Trou noir qui aspire les ennemis puis implose ; accroupi : il apparaît sur soi. */
	VOID_GRIMOIRE("void_grimoire", 2, 240, 2.0, 1.6, 0x8A2AFF),
	/** Disque solaire qui part et revient comme un boomerang ; accroupi : trois disques en éventail. */
	SOLAR_CHAKRAM("solar_chakram", 3, 60, 6.0, 2.0, 0xFF8A1A),
	/** Feux follets qui drainent la vie des ennemis ; accroupi : soin des âmes. */
	SPIRIT_LANTERN("spirit_lantern", 4, 100, 3.0, 1.6, 0x4AFFE0),
	/** Rayon perforant d'évolium ; accroupi : surcharge avec toutes les charges. */
	EVOLIUM_BLASTER("evolium_blaster", 8, 20, 3.0, 1.6, 0xD070FF);

	private final String id;
	private final int maxCharges;
	private final int rechargeTicks;
	private final double damage;
	private final double speed;
	private final int color;

	MagicKind(String id, int maxCharges, int rechargeTicks, double damage, double speed, int color) {
		this.id = id;
		this.maxCharges = maxCharges;
		this.rechargeTicks = rechargeTicks;
		this.damage = damage;
		this.speed = speed;
		this.color = color;
	}

	public String id() {
		return id;
	}

	public int maxCharges() {
		return maxCharges;
	}

	/** Ticks pour regagner une charge. */
	public int rechargeTicks() {
		return rechargeTicks;
	}

	/** Dégâts au corps à corps. */
	public double damage() {
		return damage;
	}

	public double speed() {
		return speed;
	}

	/** Couleur de la barre de charges. */
	public int color() {
		return color;
	}

	public String abilityKey() {
		return "weapon.evotool." + id + ".ability";
	}

	public String sneakKey() {
		return "weapon.evotool." + id + ".sneak";
	}
}
