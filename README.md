# Evo Tool — la Pioche Évolutive (Fabric 1.21.11)

Une pioche qui **évolue** : plus tu casses de blocs, plus elle gagne de l'XP et monte de niveau (jusqu'au niveau 100).

## Déblocages

| Niveau | Déblocage |
|-------:|-----------|
| 10  | Auto-smelt (activable / désactivable dans le menu) |
| 15  | Fortune I |
| 20  | Fortune II + minage **3x3** |
| 25  | Fortune III |
| 30  | Fortune IV + minage **3x3x3** |
| 35  | Fortune V |
| 40  | Fortune VI + minage **5x5** |
| 50  | minage **5x5x5** |
| 100 | **Minage instantané** de tout (ardoise, minerais, obsidienne…) |

En plus, **tous les 5 niveaux la pioche prend une nouvelle forme** (voir plus bas).

La vitesse de minage augmente aussi un peu à chaque niveau.

- **Touche Y** (configurable) : ouvre le menu pour choisir le mode (1 bloc, 3x3, 3x3x3, 5x5, 5x5x5) et l'auto-smelt.
- **Accroupi** : mine toujours un seul bloc.
- La pioche est incassable et résiste à la lave.

### Courbe d'XP

1 XP par bloc minable à la pioche, 6 par minerai, 15 par rubis/saphir, 60 par évolium.

| Niveau | XP totale | Ordre de grandeur |
|-------:|----------:|-------------------|
| 20  | ~1 500     | ~10 min de minage |
| 30  | ~8 600     | |
| 50  | ~112 000   | |
| 100 | ~4 400 000 | très, très long |

## Nouveaux minerais

| Minerai | Où | Outil requis | Drop |
|---------|----|--------------|------|
| **Évolium** | Ardoise des abîmes uniquement (Y -64 à -20), plus rare que le diamant | Pioche en **diamant** | Évolium (directement, comme le diamant — Fortune marche) |
| **Rubis** | Pierre et ardoise (Y -48 à 48) | Pioche en fer | Rubis |
| **Saphir** | Pierre et ardoise (Y -32 à 64) | Pioche en fer | Saphir |

Chaque gemme a son bloc de stockage (9 gemmes).

## Recette de la pioche

```
[Bloc d'évolium]
[    Bâton     ]
```

## Modèle 3D — 21 formes

La pioche a un vrai modèle 3D (pas une simple texture). **Tous les 5 niveaux elle change de forme** :
nouvelles couleurs et nouvelles pièces qui s'ajoutent aux précédentes.

| Niv. | Forme | Ajout | Niv. | Forme | Ajout |
|----:|-------|-------|----:|-------|-------|
| 0  | Fer brut   | modèle de base            | 55  | Givre     | barbes sous les bras |
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
