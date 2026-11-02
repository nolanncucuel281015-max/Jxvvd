# Evo Tool — outils et armure évolutifs (Fabric 1.21.11)

Six outils et une armure complète en évolium qui **évoluent** quand tu t'en sers, jusqu'au niveau 100 :
pioche, pelle, hache, houe, épée, masse (mace), casque, plastron, jambières et bottes. Tous :

- changent de **forme tous les 5 niveaux** (21 formes, modèles 3D, voir plus bas) : arrivé au bout d'une forme,
  l'objet s'arrête, **prêt à évoluer**, et il faut appuyer sur **R** (configurable) pour le faire évoluer ;
- sont incassables et résistent à la lave ;
- ont un menu avec la **touche Y** (configurable) : niveau, XP, mode de zone, pouvoirs à activer / couper, bonus actifs ;
- **accroupi** = un seul bloc (pas de zone, pas d'abattage).

## Évolution à la touche R

Les niveaux montent tout seuls avec l'XP, **sauf avant chaque nouvelle forme** (niveaux 5, 10, 15… 100) :
l'objet attend alors, avec « ✦ Prêt à évoluer ! Appuie sur R » dans le chat, la barre d'action et sa description.
Appuie sur **R** en le tenant en main (ou sans rien en main pour l'armure portée) : l'objet apparaît en géant
devant toi, monte en tournant de plus en plus vite dans un tourbillon d'étoiles et de runes, au son de carillons
de plus en plus aigus, puis **éclate en lumière sous sa nouvelle forme** (elle jaillit aussi à l'écran, comme un
totem), et les nouveaux pouvoirs se débloquent. Les pouvoirs ne se débloquent qu'aux évolutions.

## Équilibrage

Les objets sont forts sans être « cheat » : les outils vont un peu au-delà de Minecraft (Fortune IV, zone 5x5x5
au niveau 90, Tranchant et Puissance V, +8 dégâts au niveau 100), c'est **l'armure qui rend fort** (Résistance
jusqu'à IV, Force II, +6 cœurs) et les armes moddées et magiques restent plus sages (pas d'exécution en un coup,
attaques spéciales moins puissantes). Plus de pousse instantanée. Le **minage instantané** au niveau 100
(pioche, pelle, hache) ne change jamais. Les objets d'une ancienne version sont remis aux nouvelles règles à la connexion
(Fortune VI → IV…).

## Pioche et pelle — en cassant des blocs

| Niveau | Déblocage |
|-------:|-----------|
| 10  | Auto-smelt (minerais → lingots, sable → verre…) |
| 20 / 40 / 60 / 80 | Fortune I à IV |
| 20 / 40 / 60 / 90 | Zone **3x3**, **3x3x3**, **5x5**, **5x5x5** |
| 100 | **Minage instantané** de tout (pierre, ardoise, minerais, obsidienne… en un coup) |

La vitesse de minage augmente à chaque niveau (+20 au niveau 100, presque Efficacité V). En zone, la pioche ne casse que ce qui se mine à la pioche, la pelle que ce qui se mine à la pelle.

**Slash de la pioche géante** (Pioche Évolutive **niveau 100 + améliorée à l'évolite**) : clic droit. Ta pioche
apparaît en géant au-dessus de toi (« pfff »), se lève puis **s'abat vers l'avant** (POW !) : une onde tranchante
part tout droit et creuse une **tranchée de 100 blocs de long et 7 de large, de 7 blocs au-dessus de tes pieds
jusqu'à la bedrock**. Seuls les minerais
rapportent quelque chose (avec Fortune), dans l'inventaire avec la télékinésie ; le reste est pulvérisé.
Les coffres, spawners et liquides ne sont pas touchés. Recharge : 15 secondes.

## Hache — en coupant du bois et en tuant des monstres

| Niveau | Déblocage |
|-------:|-----------|
| 10  | **Abattage** : tout l'arbre tombe d'un coup |
| 20 / 35 / 50 / 65 / 80 | Tranchant I à V |
| 20  | L'abattage emporte aussi les feuilles |
| 30  | **Replantation** automatique d'une pousse |
| 40  | Vol de vie |
| 60  | Onde de choc |
| 100 | **Minage instantané** |

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
| 20 / 40 / 60 / 80 | Fortune I à IV (plus de récoltes) |
| 20 / 40 / 70 | Labour **et** récolte en zone 3x3, 5x5 puis 7x7 |

## Arc — en tuant des monstres avec ses flèches

Craft comme l'arc normal : 3 évolium + 3 ficelles. Un monstre tué par une flèche de l'Arc Évolutif lui donne de l'XP
(même courbe que l'épée). Il change de forme **tous les 5 niveaux** (21 formes 3D, avec l'animation de tension) :
poignée blindée (20), plaques d'armure sur les branches (30), ailerons tranchants (40), ailes de dragon aux
extrémités (50), corde d'énergie (60), cristaux flottants (70), halo magique derrière l'arc (80),
anneau runique (90) et couronne de pointes de lumière (100).

| Niveau | Bonus |
|-------:|-------|
| 15 | Vol de vie (8 % des dégâts) |
| 20 / 35 / 50 / 65 / 80 | Puissance I à V |
| 30 / 60 | Frappe I et II |
| 40 | Flamme |
| 50 | Foudre (15 % de chance par flèche) |
| 70 | Infinité |

**Arc céleste** (niveau 100 + amélioré à l'évolite) : clic droit, **ton arc apparaît en géant** devant toi
(« pfff », 6 blocs de haut, bandé) et suit ton regard. Il tire **une flèche toutes les demi-secondes** (20 flèches),
droit sur les monstres à 48 blocs, puis disparaît. Recharge : 12 secondes en tout.

## Épée, lance et masse — en tuant des monstres

XP par monstre tué = la moitié de ses PV max (zombie = 10 XP, enderman = 20 XP, boss = x5).
Les animaux ne rapportent rien. Chaque niveau donne +0,08 dégât (+8 au niveau 100).

**Épée — explosion céleste** : accroupi + clic droit, une explosion part de toi et s'étend sur
**15 blocs** (onde de choc animée). Elle blesse et repousse les monstres (plus fort près de toi, et plus fort
avec le niveau), sans casser de blocs ni toucher les joueurs et les animaux. Recharge : 15 secondes.

**Lance** (nouvelle arme de la 1.21.11) : mêmes pouvoirs que l'épée (vol de vie, onde de choc, foudre),
avec Tranchant I à V (20/35/50/65/80), **Élan évolutif** I à III (20/45/70) et Butin I à III (30/60/90).
Elle garde la charge de la lance en netherite, mais ses estocs n'ont **pas besoin d'être chargés** : on peut
enchaîner les Élans pour s'élancer encore et encore. **Tant que tu tiens la Lance Évolutive, tu ne perds
pas de faim** (coups, Élan, charge…). L'Élan vanilla (qui coûte 4 points de faim par niveau à chaque estoc) est
automatiquement remplacé par l'Élan évolutif sur la lance.

| Niveau | Épée | Masse |
|-------:|------|-------|
| 15  | Vol de vie (8 % des dégâts) | Vol de vie |
| 20 / 35 / 50 / 65 / 80 | Tranchant I à V | Densité I à V |
| 30 / 60 / 90 | Butin I à III | Brèche I à III |
| 30  | **Onde de choc** (monstres à 3 blocs : 40 % des dégâts) | Onde de choc |
| 35 / 60 / 85 | Affilage I à III | — |
| 50  | **Foudre** (15 % de chance) | Foudre |
| 50 / 85 | — | Charge de vent I et II |
| 55 / 90 | Aura de feu I et II | — |
| 75  | Onde de choc élargie (4,5 blocs) | Onde de choc élargie |

La masse garde l'attaque écrasante de la mace vanilla (plus tu tombes de haut, plus ça fait mal).

## Armure — en encaissant des coups

Chaque pièce portée gagne 1 XP par point de dégât encaissé (20 max par coup, pour qu'une grosse chute
ne fasse pas tout monter d'un coup). C'est **long** : environ 4 min pour le niveau 50, 45 min pour le
niveau 80 et **2 h 30 de coups en continu** pour le niveau 100.

Les bonus dépendent du **niveau de l'ensemble** = moyenne des 4 pièces (une pièce manquante compte 0),
donc il faut porter l'armure complète. Les effets sont **permanents** tant qu'on la porte :

| Niveau d'ensemble | Bonus |
|------------------:|-------|
| 20 / 45 / 70 / 95 | Résistance I à **IV** |
| 35 / 75 | **Force** I et II (c'est l'armure qui rend fort) |
| 25 / 65 | Vitesse I et II |
| 55  | Célérité I (minage plus rapide) |
| 40 / 85 | Régénération I et II |
| 80  | **+4 cœurs** |
| 100 | **+6 cœurs** au total |

Chaque pièce doit être **évoluée avec R** à chaque nouvelle forme (sans rien en main, R fait évoluer toutes
les pièces portées qui sont prêtes).

### Armure abyssale — jusqu'au niveau 150

Dans la **dimension évolutive** se cache un nouveau minerai noir, l'**abyssite** (sous terre, dans la pierre
évolutive, pioche en diamant). À l'**enclume**, une pièce d'armure **niveau 100** + une abyssite (30 niveaux
d'XP) = pièce **abyssale** : elle devient **noire** et continue d'évoluer **jusqu'au niveau 150**, avec
**11 nouvelles formes** (une tous les 5 niveaux, toujours avec **R**). Les pièces abyssales gagnent aussi de l'XP
quand tu tues des monstres. Avec un plastron abyssal, les **ailes de l'infini deviennent noires**.

| Niveau d'ensemble | Bonus en plus |
|------------------:|---------------|
| 105 | Célérité II, Résistance au feu |
| 110 | Force III, Apnée |
| 115 | Vitesse III |
| 120 | Régénération III |
| 125 | **Résistance V** (plus aucun dégât, sauf /kill et le vide) |
| 130 | **+10 cœurs** |
| 140 | Force IV |
| 150 | **+15 cœurs** et les **tentacules** |

**Tentacules** (ensemble abyssal complet au niveau 150) : 4 tentacules noires (vrais modèles 3D) ondulent dans
ton dos.
- **Clic gauche à main nue** : une tentacule part du dos et file (on la voit partir, en un tiers de seconde)
  jusqu'à 14 blocs, droit sur ce que tu vises, frappe fort tout ce qu'elle traverse (« tchouf ») puis se rétracte.
- **Clic droit à main nue** : une tentacule **attrape** le monstre, l'animal **ou le joueur** visé (jusqu'à
  14 blocs) et le tient en l'air devant toi (un joueur attrapé ne peut plus bouger). Elle **ne le lâche jamais toute seule** : tu peux le ramener à ta base, même en courant ou sous
  terre (il n'étouffe pas). **Clic droit** encore :
  - en regardant **devant** : elle le **projette** là où tu regardes, et il se fracasse sur le premier mur ou au
    sol (10 dégâts) ;
  - en regardant **par terre** : elle le **pose doucement** là où tu regardes, sans dégâts.

  Les boss ne se laissent pas attraper.
- **Touche J** : **mode déplacement**. Les 4 **longues tentacules te portent au-dessus du sol** (tu es surélevé
  de 2,5 blocs, comme sur des pattes d'araignée) : tu suis le relief, tu **grimpes les murs**, tu te baisses sous
  les plafonds et les portes, et **espace** te hausse encore plus. Tu cours **3,5 fois plus vite** : les
  tentacules se plantent dans le sol loin devant (« tchouf »), te **tirent d'un coup** puis se détachent quand tu
  les dépasses, chacune à son tour. À l'arrêt, elles restent plantées autour de toi. Tu les vois aussi **en vue à
  la première personne**.
  - **Accroupi** : elles t'**enfoncent sous terre** ; tu traverses la roche dans toutes les directions (espace
    pour monter, accroupi pour descendre) et tu ressors en remontant à travers le sol.
  - **Sous l'eau** : elles nagent comme une pieuvre (grâce du dauphin) et tirent dans toutes les directions.
  - Tu respires, tu vois dans le noir, et ni la lave, ni la noyade, ni l'étouffement, ni les chutes ne te
    touchent. La bedrock reste infranchissable. **J** à nouveau pour les ranger (si tu es dans la roche, tu remontes
    à la surface).
- `/upgrade armure 150` donne directement l'ensemble abyssal niveau 150.

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
| Plastron | **Ailes de l'infini** : de vraies ailes cosmiques en 3D poussent dans ton dos (repliées au sol, déployées et battantes en vol, symbole ∞ lumineux entre les deux) ; **double saut** pour voler, plus vite qu'en créatif, sans dégâts de chute |
| Jambières | **Jambes célestes** : monte les blocs sans sauter, plus de dégâts de chute |
| Bottes | **Plongée souterraine** : reste **accroupi** sur le sol et tu plonges dedans ; sous terre tu traverses la roche à toute vitesse (saut pour monter, accroupi pour descendre, vision nocturne, ni étouffement ni lave) et tu ressors en remontant. La bedrock reste infranchissable |

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
| **Évolium** | Ardoise des abîmes uniquement (Y -64 à -16), environ 3 fois plus rare que le diamant (plus fréquent vers Y -40) | Pioche en **diamant** | Évolium (directement, comme le diamant — Fortune marche) |
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

**Livre de recettes** : toutes les recettes du mod se débloquent dans le livre de la table de craft
dès que tu ramasses un de leurs ingrédients (évolium, rubis, saphir, évolite…), comme les recettes vanilla.

## Armes moddées

Huit armes avec leur **propre modèle 3D** (rien à voir avec les armes vanilla), des lignes lumineuses,
un **passif** à chaque coup et une **attaque spéciale au clic droit**. Toutes se fabriquent à la **table de craft**.

**Elles sont évolutives**, comme l'épée : elles gagnent de l'XP en tuant des monstres (niveau 0 à 100,
21 formes : runes, cristaux flottants, ailes d'énergie, anneau puis halo doré à la forme 20), débloquent
Tranchant I à III, Butin I et II, Aura de feu, vol de vie, onde de choc et foudre, et leur **attaque spéciale grandit**
avec le niveau (dégâts x0,75 au niveau 0 jusqu'à x1,5 au niveau 100, recharge 40 % plus courte au niveau 100).
Améliorées à l'évolite (enclume), elles deviennent **sublimées** : attaque spéciale 30 % plus forte et recharge
30 % plus courte. `/upgrade faux 100`,
`/upgrade dague 50`… marchent aussi.

| Arme | Dégâts / vitesse | Passif | Clic droit (recharge) | Recette (lignes de la grille) |
|------|-----------------|--------|-----------------------|-------------------------------|
| **Faux d'Évolium** | 8 / 1,0, allonge +1 | vol de vie 15 % | **Moisson** : tourbillon de 4,5 blocs, attire et soigne (6 s) | `EEE` / `.E\|` / `..\|` |
| **Katana de Rubis** | 6 / 2,0 | saignement | **Iaijutsu** : ruée de 8 blocs qui tranche tout sur le passage (5 s) | `..R` / `.R.` / `E..` |
| **Marteau de Guerre Saphir** | 10,5 / 0,7 | soulève et ralentit | **Séisme** : onde de choc de 6 blocs, projette en l'air (8 s) | `SBS` / `.\|.` / `.\|.` |
| **Espadon d'Évolite** | 12 / 0,8, allonge +1 | fendoir : 40 % aux ennemis autour | **Lame céleste** : vague d'énergie sur 16 blocs (7 s) | `.V.` / `.V.` / `E\|E` |
| **Dague d'Ombre** | 4,5 / 2,8 | coup dans le dos : dégâts x2 | **Pas de l'ombre** : téléportation dans le dos de la cible visée jusqu'à **32 blocs**, puis **invisible 20 secondes** (4 s) | `E` / `P` / `\|` |
| **Hallebarde Royale** | 8,5 / 0,9, allonge +2 | transperce l'ennemi suivant (70 %) | **Fauchage royal** : balayage en demi-cercle de 5,5 blocs (5 s) | `EEG` / `E\|.` / `.\|.` |
| **Sceptre de Cristal** | 3,5 / 1,6 | cristallise (ralentit) | **Éclat de cristal** : projectile magique à 32 blocs (0,8 s) | `.C.` / `G\|G` / `.\|.` |
| **Lame Détonante** | 8 / 1,2 | **chaque coup d'épée explose** devant toi (même dans le vide), sans casser le sol | **Détonation** : une ligne d'explosions sur 14 blocs (7 s) | `TET` / `.E.` / `.\|.` |

E = évolium, R = rubis, S = saphir, B = bloc de saphir, V = lingot d'évolite, P = perle de l'Ender,
G = lingot d'or, C = cristal évolutif (dimension évolutive), T = TNT, | = bâton, . = case vide.
Les attaques ne touchent ni les joueurs, ni les animaux apprivoisés, ni les villageois, et aucune explosion
ne casse de bloc.

## Armes magiques (à charges)

Sept armes de mage, elles aussi évolutives. Chaque sort coûte **une charge** ; les charges **reviennent toutes
seules** avec le temps (barre colorée sous l'objet, même dans l'inventaire). Au corps à corps, chaque coup a
20 % de chances de rendre une charge. **Accroupi + clic droit** lance la variante puissante.
Tous les 50 niveaux : + 1 charge ; au niveau 100 la recharge est 40 % plus rapide. Leurs sorts suivent les
mêmes règles que les attaques spéciales des armes (x0,75 au niveau 0, x1,5 au niveau 100).

| Arme | Charges | Clic droit | Accroupi + clic droit | Recette |
|------|--------:|-----------|-----------------------|---------|
| **Baguette Arcanique** | 6 (une / 2 s) | missile arcanique à tête chercheuse | salve : toutes les charges d'un coup | `..A` / `.E.` / `B..` |
| **Gantelet du Tonnerre** | 3 (une / 4 s) | éclair en chaîne sur 5 ennemis | onde de tonnerre autour de soi | `ELE` / `IEI` / `.I.` |
| **Orbe Céleste** | 2 (une / 12 s) | 4 éclats d'étoile en orbite (10 s) | lance tous les éclats | `.T.` / `TET` / `.G.` |
| **Grimoire du Néant** | 2 (une / 12 s) | trou noir qui aspire puis implose | trou noir sur soi | `.P.` / `OBO` / `.E.` |
| **Chakram Solaire** | 3 (une / 3 s) | disque boomerang enflammé | trois disques (2 charges) | `.B.` / `GEG` / `.B.` |
| **Lanterne des Âmes** | 4 (une / 5 s) | 3 feux follets qui drainent la vie | soin des âmes + régénération | `.E.` / `SLS` / `.G.` |
| **Canon d'Évolium** | 8 (une / 1 s) | rayon perforant de 40 blocs | surcharge : toutes les charges en un rayon géant | `EEA` / `IRI` / `I..` |

A = éclat d'améthyste, B = bâton de blaze (baguette) ou poudre de blaze (chakram), L = paratonnerre,
I = lingot de fer, T = poudre de pierre lumineuse, P = perle de l'Ender, O = obsidienne, B = livre (grimoire),
S = sable des âmes, L = lanterne des âmes (lanterne), R = bloc de redstone, G = lingot d'or, E = évolium.

## Générateur de bouclier

Un bloc qui tient un **dôme d'énergie** (nid d'abeilles lumineux qui tourne doucement) autour de lui.
Dans le dôme, les **monstres sont rejetés dehors**, ceux qui s'approchent sont **repoussés** (les creepers
n'y explosent pas) et les **projectiles des monstres** (flèches, boules de feu…) **se désintègrent** en le
touchant. Les joueurs passent librement.

**Clic droit** sur le générateur ouvre son menu :
- **une case à évolium** : elle avale tout ce qu'on y met, **jusqu'à 1 milliard d'évolium** (un bloc d'évolium
  vaut 9) ; le menu affiche le stock et l'autonomie ;
- le **rayon** du bouclier, de **3 à 64 blocs**, avec les boutons -10 / -1 / +1 / +10 (le dôme grandit ou
  rapetisse en direct) ;
- un bouton **Allumé / Éteint**.

Il brûle **1 évolium toutes les 10 minutes** quand il est allumé. Cassé à la pioche, il **garde son évolium et
son rayon** et les retrouve une fois reposé.

Recette (table de craft), visible dans le livre de recettes dès le début :

```
[    Diamant     ] [    Diamant     ] [    Diamant     ]
[ Bloc d'évolium ] [    Bouclier    ] [ Bloc d'évolium ]
[ Bloc d'évolium ] [ Bloc d'évolium ] [ Bloc d'évolium ]
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

**Lignes lumineuses** : toutes les armes, outils et pièces d'armure ont des lignes d'énergie animées
(gorge de l'épée, tranchants, veines dans les bras de la pioche, runes du manche, yeux du heaume…)
qui **brillent dans le noir** et coulent comme de l'énergie. Plus la forme est haute, plus elles brillent ;
les gemmes s'allument aussi. Tranchants polis, métal gravé de filigranes, dorures, halo à la forme 100.
L'armure portée est peinte en haute définition (4x) : plaques, rivets, cotte de mailles, gemmes serties
et circuits lumineux ; le plastron, les jambières et les bottes ont aussi une icône 3D.

Les textures et modèles sont générés par `tools/generate_assets.py` (Python 3 + Pillow) :

```
python3 tools/generate_assets.py
```

## Dimension évolutive

**Y aller** : construis un cadre de **blocs de rubis** comme un portail du Nether (intérieur de 2x3 à 21x21,
les coins sont facultatifs) et allume-le avec le **briquet d'évolium**. Sa recette est **secrète** : elle
marche à la table de craft, mais le livre de recettes ne la montre jamais (1 évolium + 1 silex, ne le dis à
personne). On en trouve aussi dans les coffres de certaines ruines… Le portail
évolutif (rouge rubis et violet) t'emmène dans la dimension évolutive ; un portail de retour est créé à l'arrivée.

**La dimension** : ciel violet, collines, forêts de cristal et champs prismatiques, faits de **blocs évolutifs**
(pierre, herbe, terre, bois, feuilles, cristal, minerai). Chaque bloc a **6 niveaux d'évolution**.

**Plus tu restes, plus tout évolue** : toutes les **5 minutes** passées dans la dimension (barre violette en haut
de l'écran, réglable avec `/dimension speed`), la dimension passe une **phase**. Une onde d'évolution part de toi et les blocs autour de toi
évoluent (veines d'énergie, cristaux, dorures, puis arc-en-ciel), et ils brillent de plus en plus.
Le minerai évolutif donne 1 évolium de plus par niveau d'évolution ; la pierre au niveau 5 lâche parfois de l'évolium.
Le temps repart à zéro quand tu quittes la dimension.

**Les sanctuaires** : il faut **explorer** la dimension pour les trouver (un par région de 320 x 320 blocs).
Ce sont des temples ronds en quartz, or et améthyste, entourés de 8 colonnes couronnées de cristaux lumineux.
Au centre, un **clic droit sur l'Autel du Sanctuaire** réveille le boss.
L'autel se **fabrique** aussi à la table de craft (et se récupère à la pioche) pour réveiller le boss où tu veux :

```
[      ] [ Cloche ] [      ]
[  Or  ] [Bloc d'évolium] [  Or  ]
[Quartz] [ Quartz ] [Quartz]
```

**Le boss : le Slime Cloche**. Une fois réveillé, il **évolue toutes les 5 minutes** (le temps restant est
affiché dans sa barre) :

| Temps | Forme | Apparence | Attaques |
|------:|-------|-----------|----------|
| 0 min  | Slime Évolutif   | gelée translucide, cœur d'évolium, petite couronne d'or | bondit sur toi |
| 5 min  | Slime Cloche     | cloche de gelée lisse cerclée d'or, masque doré | + onde de son (DONG) qui repousse |
| 10 min | Cloche de Guerre | cloche de bronze à pointes, crocs, 2 cloches de guerre en orbite | + invoque des slimes |
| 15 min | Cloche Colosse   | bras et jambes musclés (biceps, épaulières, poings dorés) | coups de poing, bonds écrasants |
| 20 min | Cloche Titan     | armure, couronne de pointes, masse-cloche dans la main | + rayon sonique |
| 25 min | Cloche Divine    | flotte dans une robe de lumière, cloche de marbre et d'or, 3e œil, grandes ailes à plumes, mains divines, halo, auréole en soleil, 4 cloches en orbite | + foudre, régénération |

Toutes les formes sont de vraies formes arrondies (cloches lisses, muscles galbés, plumes, anneaux), animées :
ailes qui battent, halo et auréole qui tournent, mains qui flottent, cloches qui gravitent.

**Commande `/dimension`** (opérateurs) :

| Commande | Effet |
|----------|-------|
| `/dimension spawn [forme]` | fait apparaître le boss devant soi (forme 0 à 5) |
| `/dimension locate` | coordonnées du sanctuaire le plus proche (cliquer dessus pour y aller) |
| `/dimension speed [minutes]` | minutes entre deux évolutions (blocs et boss), 5 par défaut ; sauvegardé avec le monde |
| `/dimension tp` | aller dans la dimension évolutive (ou en revenir) |
| `/dimension tp sanctuaire` | aller directement au sanctuaire le plus proche |
| `/dimension phase <0-5>` | règle ta phase d'évolution dans la dimension |
| `/dimension evolve` | le boss le plus proche évolue tout de suite |

**3 phases de combat** (barre de boss en 3 parties) :

| Vie | Phase | Changements |
|----:|-------|-------------|
| 100 % | Phase 1 | violet royal et or |
| 66 %  | Phase 2 — **Colère** | transformation (invulnérable 2,5 s, onde de choc), devient **cramoisi et or**, attaques 1,5 fois plus fréquentes, plus fort et plus rapide, onde de son même en Slime |
| 33 %  | Phase 3 — **Ascension dorée** | nouvelle transformation, devient **entièrement doré** et lumineux, attaques 2 fois plus fréquentes, **colonnes de lumière** annoncées par des cercles au sol |

Chaque forme a plus de vie et frappe plus fort. Vaincu, il lâche de l'évolium, des blocs de rubis, une cloche,
des lingots d'évolite et des blocs d'évolium (selon sa forme) ; l'autel peut alors en réveiller un autre.

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
