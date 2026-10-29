# Evo Tool — outils et armure évolutifs (Fabric 1.21.11)

Six outils et une armure complète en évolium qui **évoluent** quand tu t'en sers, jusqu'au niveau 100 :
pioche, pelle, hache, houe, épée, masse (mace), casque, plastron, jambières et bottes. Tous :

- changent de **forme tous les 5 niveaux** (21 formes, modèles 3D, voir plus bas) ;
- sont incassables et résistent à la lave ;
- ont un menu avec la **touche Y** (configurable) : niveau, XP, mode de zone, pouvoirs à activer / couper, bonus actifs ;
- **accroupi** = un seul bloc (pas de zone, pas d'abattage).

## Pioche et pelle — en cassant des blocs

| Niveau | Déblocage |
|-------:|-----------|
| 10  | Auto-smelt (minerais → lingots, sable → verre…) |
| 15 / 20 / 25 / 30 / 35 / 40 | Fortune I à VI |
| 20 / 30 / 40 / 50 | Zone **3x3**, **3x3x3**, **5x5**, **5x5x5** |
| 100 | **Minage instantané** de tout (ardoise, minerais, obsidienne…) |

La vitesse de minage augmente un peu à chaque niveau. En zone, la pioche ne casse que ce qui se mine à la pioche, la pelle que ce qui se mine à la pelle.

## Hache — en coupant du bois et en tuant des monstres

| Niveau | Déblocage |
|-------:|-----------|
| 10  | **Abattage** : tout l'arbre tombe d'un coup |
| 15 / 25 / 35 / 45 / 55 | Tranchant I à V |
| 20  | L'abattage emporte aussi les feuilles |
| 30  | **Replantation** automatique d'une pousse |
| 40  | Vol de vie |
| 60  | Onde de choc |
| 100 | Minage instantané + exécution |

**Laser bûcheron** : clic droit avec la Hache Évolutive (dans le vide ou sur un bloc qui ne s'écorce pas ;
viser un tronc l'écorce toujours, comme une hache normale). Un rayon laser fait le tour de toi en 2 secondes et
coupe **tous les arbres à 50 blocs** (troncs et feuilles). Tout le bois revient à tes pieds, ou **directement dans
ton inventaire** si la hache est améliorée à l'évolite (télékinésie). Les maisons en bûches ne sont pas touchées
(seuls les troncs avec des feuilles naturelles autour comptent). Avec la replantation (niveau 30), chaque arbre
coupé est replanté. Recharge : 10 secondes.

## Houe — en labourant et en récoltant

| Niveau | Déblocage |
|-------:|-----------|
| 10  | **Replantation** automatique des cultures récoltées |
| 15 / 20 / 25 / 30 / 35 / 40 | Fortune I à VI (plus de récoltes) |
| 20 / 40 / 60 / 80 | Labour **et** récolte en zone 3x3, 5x5, 7x7, 9x9 |
| 100 | **Pousse instantanée** : les cultures replantées sont déjà mûres |

## Arc — en tuant des monstres avec ses flèches

Craft comme l'arc normal : 3 évolium + 3 ficelles. Un monstre tué par une flèche de l'Arc Évolutif lui donne de l'XP
(même courbe que l'épée). Il change de forme **tous les 5 niveaux** (21 formes 3D, avec l'animation de tension) :
poignée blindée (20), plaques d'armure sur les branches (30), ailerons tranchants (40), ailes de dragon aux
extrémités (50), corde d'énergie (60), cristaux flottants (70), halo magique derrière l'arc (80),
anneau runique (90) et couronne de pointes de lumière (100).

| Niveau | Bonus |
|-------:|-------|
| 10 | Vol de vie |
| 15 / 25 / 35 / 45 / 55 | Puissance I à V |
| 20 / 40 | Frappe I et II |
| 30 | Flamme |
| 50 | Infinité + foudre (20 % de chance par flèche) |
| 100 | Exécution |

**Arc céleste** (niveau 100 + amélioré à l'évolite) : clic droit, **ton arc apparaît en géant** devant toi
(« pfff », 6 blocs de haut, bandé) et suit ton regard. Il tire **une flèche toutes les demi-secondes** (20 flèches),
droit sur les monstres à 48 blocs, puis disparaît. Recharge : 12 secondes en tout.

## Épée, lance et masse — en tuant des monstres

XP par monstre tué = la moitié de ses PV max (zombie = 10 XP, enderman = 20 XP, boss = x5).
Les animaux ne rapportent rien. Chaque niveau donne +0,1 dégât (+10 au niveau 100).

**Épée — explosion céleste** : accroupi + clic droit, une énorme explosion part de toi et s'étend sur
**20 blocs** (onde de choc animée). Elle blesse et repousse les monstres (plus fort près de toi, et plus fort
avec le niveau), sans casser de blocs ni toucher les joueurs et les animaux. Recharge : 10 secondes.

**Lance** (nouvelle arme de la 1.21.11) : mêmes pouvoirs que l'épée (vol de vie, onde de choc, foudre, exécution),
avec Tranchant I à V (15/25/35/45/55), **Élan évolutif** I à III (20/40/60) et Butin I à III (30/50/70).
Elle garde la charge de la lance en netherite, mais ses estocs n'ont **pas besoin d'être chargés** : on peut
enchaîner les Élans pour s'élancer encore et encore. **Tant que tu tiens la Lance Évolutive, tu ne perds
pas de faim** (coups, Élan, charge…). L'Élan vanilla (qui coûte 4 points de faim par niveau à chaque estoc) est
automatiquement remplacé par l'Élan évolutif sur la lance.

| Niveau | Épée | Masse |
|-------:|------|-------|
| 10  | Vol de vie (10 % des dégâts) | Vol de vie |
| 15 → 55 | Tranchant I à V (15/25/35/45/55) | Densité I à V (15/25/35/45/55) |
| 20 → 60 | Butin I à V (20/30/40/50/60) | Brèche I à IV (20/30/40/50) |
| 30  | **Onde de choc** (monstres à 3 blocs : moitié des dégâts) | Onde de choc |
| 35 / 55 / 70 | Affilage I à III | — |
| 50  | **Foudre** (20 % de chance) | Foudre |
| 60 / 70 / 80 | — | Charge de vent I à III |
| 65 / 85 | Aura de feu I et II | — |
| 75  | Onde de choc élargie (5 blocs) | Onde de choc élargie |
| 100 | **Exécution** : tue en un coup tout monstre qui n'est pas un boss | Exécution |

La masse garde l'attaque écrasante de la mace vanilla (plus tu tombes de haut, plus ça fait mal).

## Armure — en encaissant des coups

Chaque pièce portée gagne 1 XP par point de dégât encaissé (20 max par coup, pour qu'une grosse chute
ne fasse pas tout monter d'un coup). C'est **long** : environ 4 min pour le niveau 50, 45 min pour le
niveau 80 et **2 h 30 de coups en continu** pour le niveau 100.

Les bonus dépendent du **niveau de l'ensemble** = moyenne des 4 pièces (une pièce manquante compte 0),
donc il faut porter l'armure complète. Les effets sont **permanents** tant qu'on la porte :

| Niveau d'ensemble | Bonus |
|------------------:|-------|
| 10 / 30 / 50 / 75 / 100 | Résistance I à **V** |
| 15 / 45 / 70 | Vitesse I à **III** |
| 20 / 55 / 90 | Régénération I à **III** |
| 80  | **+4 cœurs** |
| 100 | **+10 cœurs** au total |

Résistance V = plus aucun dégât (sauf /kill et le vide).

**Look de chevalier qui change tous les 5 niveaux** (21 formes) : nouvelles couleurs **et** un nouveau détail
à chaque forme, qui s'ajoute aux précédents.

| Niv. | Casque (3D) | Plastron / jambières / bottes |
|----:|-------------|-------------------------------|
| 0  | heaume à cornes, visière | armure de plates, cotte de mailles |
| 5  | bandeau | rivets |
| 10 | protège-joues | jointures lumineuses |
| 15 | gemme frontale | grosse gemme sur le plastron |
| 20 | cornes plus longues | bordure colorée des épaulières |
| 25 | rivets sertis | gemmes sur les genouillères |
| 30 | crête | épaulières à pointes |
| 35 | protège-nuque | blason en croix |
| 40 | pointes sur la crête | manchettes des gantelets |
| 45 | mentonnière | bordures couleur gemme |
| 50 | bouts de cornes en gemme | éperons, bouts de bottes sertis |
| 55 | petites cornes frontales | clous sur la ceinture |
| 60 | visière lumineuse | runes |
| 65 | pointes à l'arrière | **cape** dans le dos |
| 70 | 2 cristaux flottants | runes sur les bras |
| 75 | ailes | ailes brodées sur la cape |
| 80 | cristal au zénith | ailettes sur les bottes |
| 85 | cristaux latéraux | bandes lumineuses sur les cuissardes |
| 90 | couronne de gemmes | bordures en gemme |
| 95 | diadème | filigrane doré |
| 100 | halo complet | toutes les jointures brillent |

## Évolite et améliorations

**L'évolite** se trouve dans la **pierre de l'End**, un peu plus souvent que les débris antiques de la netherite
(pioche en diamant requise, très résistant aux explosions). Fais fondre le minerai pour obtenir un **lingot d'évolite**.

Dans une **enclume** : objet évolutif + lingot d'évolite (10 niveaux d'XP) = **version améliorée** (qui brille),
avec un pouvoir magique :

| Objet | Pouvoir |
|-------|---------|
| Pioche, pelle, hache, houe | **Télékinésie** : les objets cassés vont directement dans l'inventaire |
| Épée | **Slash magique** : clic droit, une vague tranchante frappe tout sur 8 blocs devant toi |
| Lance | **Charge fulgurante** : pendant la charge, Vitesse III et chaque coup foudroie la cible |
| Arc | **Arc céleste** (au niveau 100) : clic droit, ton arc apparaît en géant et tire une flèche toutes les demi-secondes sur les monstres |
| Masse | **Envol** : clic droit, bond dans les airs sans dégâts de chute (parfait pour l'attaque écrasante) |
| Casque | **Vue du chevalier** : les monstres à 24 blocs brillent à travers les murs |
| Plastron | **Bouclier magique** : 4 cœurs dorés permanents qui se rechargent hors combat |
| Jambières | **Jambes célestes** : monte les blocs sans sauter, plus de dégâts de chute |
| Bottes | **Nuage magique** : **double saut** pour s'envoler sur un nuage, comme en créatif |

## Courbes d'XP

| Niveau | Outils (blocs) | Épée / masse (monstres) |
|-------:|---------------:|------------------------:|
| 20  | ~1 500 XP (~10 min) | ~400 XP |
| 50  | ~112 000 XP | ~28 000 XP |
| 100 | ~4 400 000 XP | ~1 100 000 XP |

Armure : ~2 000 XP pour le niveau 50, ~22 000 pour le niveau 80, ~70 000 pour le niveau 100.

Blocs : 1 XP par bloc minable avec l'outil, 6 par minerai, 15 par rubis/saphir, 60 par évolium, 3 par culture mûre, 1 par bloc labouré.

## Commande /upgrade

```
/upgrade <outil> <niveau>
```

- `<outil>` : `pickaxe`, `shovel`, `axe`, `hoe`, `sword`, `spear`, `bow`, `mace`, `helmet`, `chestplate`, `leggings`, `boots`
  (ou en français : `pioche`, `pelle`, `hache`, `houe`, `epee`, `lance`, `arc`, `masse`, `casque`, `plastron`, `jambieres`, `bottes`)
- `armor` / `armure` : les 4 pièces d'armure d'un coup (ex. `/upgrade armor 100`)
- `<niveau>` : de 0 à 100

Exemple : `/upgrade pickaxe 50`. La commande règle l'outil tenu en main (ou le premier trouvé dans l'inventaire) ;
si tu n'en as pas, elle t'en donne un neuf au niveau demandé. Elle peut aussi faire redescendre de niveau.
Il faut être opérateur (comme pour `/give`).

## Nouveaux minerais

| Minerai | Où | Outil requis | Drop |
|---------|----|--------------|------|
| **Évolium** | Ardoise des abîmes uniquement (Y -64 à -20), plus rare que le diamant | Pioche en **diamant** | Évolium (directement, comme le diamant — Fortune marche) |
| **Rubis** | Pierre et ardoise (Y -48 à 48) | Pioche en fer | Rubis |
| **Saphir** | Pierre et ardoise (Y -32 à 64) | Pioche en fer | Saphir |

Chaque gemme a son bloc de stockage (9 gemmes).

## Recettes

Comme les outils en diamant, avec de l'**évolium** à la place du diamant :
pioche (3 + 2 bâtons), pelle (1 + 2), hache (3 + 2), houe (2 + 2), épée (2 + 1),
lance (1 évolium en haut à droite + 2 bâtons en diagonale), arc (3 évolium + 3 ficelles),
casque (5), plastron (8), jambières (7), bottes (4).

La **masse** : un bloc d'évolium au-dessus d'un bâton.

```
[Bloc d'évolium]
[    Bâton     ]
```

## Modèle 3D — 21 formes

Chaque outil a un vrai modèle 3D (pas une simple texture). **Tous les 5 niveaux il change de forme** :
nouvelles couleurs et nouvelles pièces qui s'ajoutent aux précédentes (pointes, gemmes, ailes, cristaux flottants…).
Le tableau détaille la pioche ; les autres outils suivent les mêmes thèmes avec leurs propres pièces.

| Niv. | Forme | Ajout | Niv. | Forme | Ajout |
|----:|-------|-------|----:|-------|-------|
| 0  | Fer brut   | modèle de base (pioche)   | 55  | Givre     | barbes sous les bras |
| 5  | Fer forgé  | arêtes de renfort         | 60  | Sculk     | runes lumineuses sur le manche |
| 10 | Braises    | anneau de braise          | 65  | Magma     | épines sur les bras |
| 15 | Doré       | gemmes incrustées         | 70  | End       | 2 cristaux flottants |
| 20 | Diamant    | couronne                  | 75  | Céleste   | ailes |
| 25 | Émeraude   | clous de gemme            | 80  | Néant     | cristal au zénith |
| 30 | Améthyste  | ailerons                  | 85  | Prisme    | cristaux latéraux (gemme arc-en-ciel) |
| 35 | Prismarine | pommeau en gemme          | 90  | Stellaire | cristal sous le pommeau |
| 40 | Netherite  | lames plus longues        | 95  | Divin     | diadème |
| 45 | Blaze      | garde sur le manche       | 100 | Ultime    | halo complet, cœur surchargé |
| 50 | Évolium    | pointe de cristal         |     |           | |

Les textures et modèles sont générés par `tools/generate_assets.py` (Python 3 + Pillow) :

```
python3 tools/generate_assets.py
```

## Compiler

Java 21 requis.

```
./gradlew build
```

Le `.jar` se trouve dans `build/libs/`.

Sans rien installer : à chaque push, GitHub Actions compile le mod. Ouvre l'onglet **Actions** du dépôt,
clique sur la dernière exécution « Build » réussie, puis télécharge **evotool-mod** dans la section *Artifacts*
(un .zip qui contient le .jar). À mettre dans le dossier `mods/` avec Fabric Loader et Fabric API pour 1.21.11.
Si une version de dépendance n'est plus disponible, mets à jour `gradle.properties` avec les versions de https://fabricmc.net/develop.
