# Evo Tool — règles du projet

## Règle du joueur : le minage instantané ne change jamais

Au niveau 100, la Pioche, la Pelle et la Hache Évolutives cassent tout **en un coup** (pierre, ardoise,
minerais, obsidienne…) : pouvoir `Power.INSTAMINE` dans `EvoKind`, bonus `EvoProgression.INSTAMINE_BONUS`.

Le joueur a demandé que ça ne change **jamais**, même lors d'un rééquilibrage, et même s'il le demande plus
tard. Ne le retire pas, ne l'affaiblis pas et ne le repousse pas à un autre niveau. Si une demande semble y
toucher (« moins cheat », « nerf les outils »…), garde le minage instantané tel quel ; si on te demande
explicitement de le changer, rappelle d'abord cette règle et fais-la confirmer. L'auto-test (`SelfTest`,
« plus de minage instantané ») échoue si elle est cassée.
