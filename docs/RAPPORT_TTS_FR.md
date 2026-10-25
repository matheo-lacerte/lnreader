# LNReader × HayaiTTS — révision 2

La première APK préchargeait les paragraphes séparément. L’utilisateur a
confirmé sur son téléphone que les longues pauses persistaient.

## Nouveau correctif

- Les retours de ligne, lignes vides, tabulations et espaces Unicode sont
  remplacés par un seul espace uniquement dans le texte envoyé au moteur.
- Plusieurs paragraphes sont réunis dans une même requête, avec leur
  ponctuation d’origine. Le chapitre affiché et ses identifiants sont conservés.
- Les requêtes sont limitées à 1800 caractères UTF-16 : HayaiTTS coupe
  silencieusement les requêtes au-delà de 2000 caractères. Un chapitre entier
  envoyé d’un coup ferait donc perdre du texte.
- Le découpage privilégie les fins de phrase, puis les espaces. Les très longs
  paragraphes et les caractères Unicode sont conservés sans troncature.
- La file prépare le bloc courant et deux suivants, sans arrêt du moteur aux
  transitions naturelles. Pause, arrêt, déplacement et réglages invalident les
  anciens événements. HayaiTTS reste inchangé.

## Vérifications

15 tests natifs réussis, 0 échec et 0 test ignoré. Ils vérifient le texte envoyé
au moteur, ses limites et son intégrité, le regroupement, le suivi des plages,
les déplacements et l’annulation. ESLint et TypeScript réussissent; les 30
avertissements de lint existants restent présents. Le contrôle global de
formatage conserve ses problèmes antérieurs. Les 459 tests React Native de la
première révision n’ont pas été relancés pour cette modification native.

L’APK utilise le package debug `com.rajarsheechatterjee.LNReader.debug`, pour
arm64-v8a, Android 7.0 minimum. Elle intègre le JavaScript et utilise la clé debug.

## Limites connues

Aucun appareil ou émulateur connecté n’était disponible ici. Ni l’installation
ni la fluidité sonore de cette révision n’ont été testées réellement. Les tests
utilisent un moteur simulé. Le regroupement peut augmenter le délai au départ,
puisque Hayai génère tout le bloc avant de transmettre son audio. Une pause peut
encore subsister entre blocs ou dans la prosodie du modèle.

HayaiTTS ne rapporte pas les positions des mots : le surlignage et la position
retenue lors d’une pause/relecture peuvent donc avancer par bloc. La sélection
manuelle d’un paragraphe original fonctionne toujours. Les moteurs qui émettent
`onRangeStart` permettent un suivi plus précis; aucun minutage fictif n’est utilisé.

Les identifiants OAuth AniList et MyAnimeList ne sont pas configurés dans ce
build; leurs connexions restent indisponibles sans configuration/recompilation.

## Test sur téléphone

Installer la révision 2 par-dessus la première APK debug. Choisir la même voix
HayaiTTS et le même chapitre. Tester surtout plusieurs paragraphes courts
séparés par des lignes vides, puis un chapitre dépassant 2000 caractères en
vérifiant que sa dernière phrase est lue. Comparer le délai initial et les
pauses aux limites de blocs. Vérifier pause/reprise, déplacement, réglages,
notifications, lecture écran éteint et fin de chapitre. Le protocole technique
et les commandes se trouvent dans `docs/TTS_PREFETCH.md`.
