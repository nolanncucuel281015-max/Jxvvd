# Evo Tool — la Pioche Évolutive (Fabric 1.21.11)

Une pioche qui **évolue** : plus tu casses de blocs, plus elle gagne de l'XP et monte de niveau (jusqu'au niveau 100).

## Déblocages

| Niveau | Déblocage |
|-------:|-----------|
| 10  | Auto-smelt (activable / désactivable dans le menu) |
| 15  | Fortune I |
| 20  | Fortune II + minage **3x3** + nouvelle apparence |
| 25  | Fortune III |
| 30  | Fortune IV + minage **3x3x3** |
| 35  | Fortune V |
| 40  | Fortune VI + minage **5x5** |
| 50  | minage **5x5x5** + nouvelle apparence |
| 100 | **Minage instantané** de tout (ardoise, minerais, obsidienne…) + forme finale dorée |

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

## Modèle 3D

La pioche a un vrai modèle 3D (pas une simple texture) avec 4 apparences qui changent selon le niveau :
acier (0–19), cyan (20–49), sombre avec pointes de cristal (50–99), et forme finale dorée avec un halo de cristaux flottants (100).

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
