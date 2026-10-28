# Evo Tool — outils évolutifs (Fabric 1.21.11)

Six outils en évolium qui **évoluent** quand tu t'en sers, jusqu'au niveau 100 :
pioche, pelle, hache, houe, épée et masse (mace). Tous :

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

## Houe — en labourant et en récoltant

| Niveau | Déblocage |
|-------:|-----------|
| 10  | **Replantation** automatique des cultures récoltées |
| 15 / 20 / 25 / 30 / 35 / 40 | Fortune I à VI (plus de récoltes) |
| 20 / 40 / 60 / 80 | Labour **et** récolte en zone 3x3, 5x5, 7x7, 9x9 |
| 100 | **Pousse instantanée** : les cultures replantées sont déjà mûres |

## Épée et masse — en tuant des monstres

XP par monstre tué = la moitié de ses PV max (zombie = 10 XP, enderman = 20 XP, boss = x5).
Les animaux ne rapportent rien. Chaque niveau donne +0,1 dégât (+10 au niveau 100).

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

## Courbes d'XP

| Niveau | Outils (blocs) | Épée / masse (monstres) |
|-------:|---------------:|------------------------:|
| 20  | ~1 500 XP (~10 min) | ~400 XP |
| 50  | ~112 000 XP | ~28 000 XP |
| 100 | ~4 400 000 XP | ~1 100 000 XP |

Blocs : 1 XP par bloc minable avec l'outil, 6 par minerai, 15 par rubis/saphir, 60 par évolium, 3 par culture mûre, 1 par bloc labouré.

## Nouveaux minerais

| Minerai | Où | Outil requis | Drop |
|---------|----|--------------|------|
| **Évolium** | Ardoise des abîmes uniquement (Y -64 à -20), plus rare que le diamant | Pioche en **diamant** | Évolium (directement, comme le diamant — Fortune marche) |
| **Rubis** | Pierre et ardoise (Y -48 à 48) | Pioche en fer | Rubis |
| **Saphir** | Pierre et ardoise (Y -32 à 64) | Pioche en fer | Saphir |

Chaque gemme a son bloc de stockage (9 gemmes).

## Recettes

Comme les outils en diamant, avec de l'**évolium** à la place du diamant :
pioche (3 + 2 bâtons), pelle (1 + 2), hache (3 + 2), houe (2 + 2), épée (2 + 1).

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

Le `.jar` se trouve dans `build/libs/`. À mettre dans le dossier `mods/` avec Fabric Loader et Fabric API pour 1.21.11.
Si une version de dépendance n'est plus disponible, mets à jour `gradle.properties` avec les versions de https://fabricmc.net/develop.
