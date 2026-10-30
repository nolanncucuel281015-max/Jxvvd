package fr.evotool.weapon;

/**
 * Les armes moddées (elles n'évoluent pas) : dégâts et vitesse affichés dans l'infobulle, allonge en plus,
 * et temps de recharge de l'attaque spéciale (clic droit), voir {@link WeaponPowers}.
 */
public enum WeaponKind {
	/** Moisson : tourbillon autour du joueur qui vole la vie. Passif : vol de vie. */
	SCYTHE("evolium_scythe", 9.0, 1.0, 1.0, 120),
	/** Iaijutsu : ruée de 8 blocs qui tranche tout sur le passage. Passif : saignement. */
	KATANA("ruby_katana", 7.0, 2.0, 0.0, 100),
	/** Séisme : frappe le sol, projette et ralentit tout autour. Passif : soulève et ralentit. */
	WARHAMMER("sapphire_warhammer", 12.0, 0.7, 0.0, 160),
	/** Lame céleste : vague d'énergie qui file sur 16 blocs. Passif : fendoir (touche les voisins de la cible). */
	GREATSWORD("evolite_greatsword", 14.0, 0.8, 1.0, 140),
	/** Pas de l'ombre : téléportation dans le dos de la cible visée (32 blocs), invisible 20 s. Passif : coup dans le dos x2. */
	DAGGER("shadow_dagger", 5.0, 2.8, 0.0, 80),
	/** Fauchage royal : balayage en demi-cercle de 5,5 blocs. Passif : transperce jusqu'à l'ennemi derrière. */
	HALBERD("royal_halberd", 10.0, 0.9, 2.0, 100),
	/** Éclat de cristal : projectile magique à 32 blocs. Passif : cristallise (ralentit) la cible. */
	STAFF("crystal_staff", 4.0, 1.6, 0.0, 16),
	/** Chaque coup d'épée (même dans le vide) fait un slash qui explose, sans casser le sol. Clic droit : détonations en ligne. */
	BLAST_BLADE("blast_blade", 9.0, 1.2, 0.0, 140);

	private final String id;
	private final double damage;
	private final double speed;
	private final double reach;
	private final int cooldown;

	WeaponKind(String id, double damage, double speed, double reach, int cooldown) {
		this.id = id;
		this.damage = damage;
		this.speed = speed;
		this.reach = reach;
		this.cooldown = cooldown;
	}

	public String id() {
		return id;
	}

	/** Dégâts d'un coup (avec le point de dégâts de base du joueur). */
	public double damage() {
		return damage;
	}

	/** Coups par seconde. */
	public double speed() {
		return speed;
	}

	/** Allonge en plus des 3 blocs normaux. */
	public double reach() {
		return reach;
	}

	public int cooldown() {
		return cooldown;
	}

	public String passiveKey() {
		return "weapon.evotool." + id + ".passive";
	}

	public String abilityKey() {
		return "weapon.evotool." + id + ".ability";
	}
}
