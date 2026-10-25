# LNReader × HayaiTTS — rapport du correctif

## Résultat vérifié

- LNReader 2.1.4, base `dc394e8540d44902efc17b5a35485bdb0724bb00`.
- HayaiTTS analysé à `7d51b0f92f1f1fddba0dcfc082efdf1bf2ff1e34`, sans modification.
- APK Android debug compilée avec succès, architecture arm64-v8a.
- Identifiant : `com.rajarsheechatterjee.LNReader.debug`.
- Android minimum : 7.0 (API 24); cible : API 36.
- Signature APK v2 vérifiée avec `apksigner`; certificat Android Debug.
- Bundle JavaScript intégré : `assets/index.android.bundle`.
- Intégrité ZIP de l’APK vérifiée.
- Sept tests natifs Robolectric/Mockito : 7 réussis, 0 ignoré, 0 échec.
- Tests React Native existants : 91 suites, 459 tests réussis.
- ESLint : 0 erreur, 30 avertissements existants. TypeScript : réussi.
- `pnpm run check` complet : non réussi, car Prettier signale 20 fichiers
  existants/générés. Les fichiers JS/JSON modifiés ont leur formatage vérifié.

## Cause et modifications

Le lecteur attendait `onDone` avant de soumettre le paragraphe suivant et
utilisait `QUEUE_FLUSH` à chaque transition. HayaiTTS génère un tableau audio
complet avant de le transmettre à Android; la génération du paragraphe suivant
commençait donc seulement après la lecture du précédent.

Le correctif soumet le paragraphe courant et jusqu’à deux paragraphes suivants
avec `QUEUE_ADD`. La fin naturelle renouvelle cette fenêtre sans arrêter le
moteur. Les réglages de voix/vitesse/tonalité ne sont plus réappliqués à chaque
transition naturelle. La progression garde l’index du paragraphe courant.

Les actions explicites (pause, arrêt, déplacement, relecture, changement des
réglages) invalident les anciens événements et annulent la file appropriée.
Les erreurs d’un paragraphe préchargé arrêtent la lecture au lieu de laisser
les suivants continuer. Une initialisation différée ne doit pas relancer une
ancienne demande annulée.

Les sept tests couvrent le préchargement initial, le renouvellement sans arrêt,
les anciens événements après pause/déplacement, le changement des réglages,
l’arrêt complet, la fin de chapitre et une erreur de synthèse préchargée.

`pnpm run build:tts:debug` prépare un build autonome avec JS intégré, sans
lanceur de développement Expo. Le script élimine aussi les icônes WebP générées
lorsqu’une icône PNG du même nom existe, car ces doublons bloquaient la fusion
des ressources Android. Les ressources sources ne sont pas supprimées.

## Limites : aucune validation audio réelle

Aucun appareil ou émulateur connecté n’était disponible. L’APK n’a pas été
installée ni exécutée ici. La fluidité avec une vraie voix HayaiTTS, l’absence
complète de pauses et le fonctionnement de toutes les commandes sur téléphone
ne sont donc pas validés. Le fait que les tests passent ne prouve pas la
fluidité audio ni l’absence de toute régression sur appareil.

La file permet à Android de préparer la synthèse suivante pendant la lecture,
mais une génération plus lente que la lecture, de très courts paragraphes ou
la contre-pression audio peuvent encore causer des pauses. Le chapitre n’est
pas concaténé en une requête; les limites de longueur Android restent celles
de l’application de départ.

## Installation et essai

Installer `LNReader-Hayai-prefetch-debug-arm64.apk` sur un appareil arm64.
Le package debug peut coexister avec la version officielle; ses données sont
séparées. Une autre installation debug signée avec une autre clé peut exiger
une sauvegarde puis une désinstallation avant installation.

Choisir HayaiTTS et la même voix, puis comparer le même chapitre avec la version
officielle. Mesurer au moins 30 transitions, avec des dialogues courts et des
paragraphes longs; rapporter médiane, p95, maximum et délai initial. Tester aussi
pause/reprise, arrêt, précédent/suivant, sélection d’un paragraphe, réglages,
changement de voix/moteur, écran éteint, notifications et fin de chapitre.
Le protocole détaillé et les commandes sont dans `docs/TTS_PREFETCH.md`.
