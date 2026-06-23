# Rebranding OneClick — Document de référence (charte graphique)

> **Source de vérité unique du rebranding.** Synthèse de l'étude approfondie de `OneClick_Spring/Charte_graphique/` (maquettes SVG d'un UI/graphic designer senior) + les décisions validées par le client.
> **Cible** : on rebrande **iOS d'abord** (`OneClick_iOS_Native`, app Win/client). Une fois le rendu validé, **export Android** (`android-native`). Backend Spring inchangé sauf l'étape « tiers » (migration `tiers`).
> **Provenance des données** : 📐 = extrait de la charte (designer, fait foi) · 🆕 = généré/dérivé par Claude pour combler un manque (à valider designer/métier). · 🎯 = **spec exacte issue des exports CSS Figma** (`Charte_graphique/css/*.txt`, valeurs px/hex/rayon/ombre au pixel près).
> **Dernière mise à jour** : 14 juin 2026 (soir) — intégration des **exports CSS Figma** (`css/*.txt`) : recette exacte carte statut, correction rayon bouton (secondaire **r12**, pas r10), couleur eyebrow par palier, ombres. Phase 0 corrigée + **Phase 1 livrée** (vérifiées live au simu).

---

## 0. SVG vs PNG — tranché

**On travaille en SVG.** Le vectoriel fournit tout ce qui compte pour un rebrand fidèle : hex exacts, géométries (rayons, tailles, offsets), **dégradés stop par stop**, **recettes d'ombre** (offset/blur/couleur/opacité), et assets réutilisables (monogramme, rosace, icônes) → exportables en **PDF vectoriel** dans l'asset catalog Xcode et `res/drawable` Android.

Deux limites du SVG, comblées par les décisions ci-dessous :
1. **Polices** : tout le texte des maquettes est vectorisé en `<path>` → aucune `font-family` lisible. ✅ **Résolu** : voir §3 (Poltawski Nowy + Nunito Sans, fournies par le client).
2. **Photos hero** : ce sont des placeholders (base64). En prod = photo resto dynamique. On ne reproduit pas l'image mais **l'overlay** (dégradé d'assombrissement) et le placement.

---

## 1. Décisions validées (journal)

| # | Sujet | Décision |
|---|-------|----------|
| 1 | **Palette canonique** | **Bordeaux `#681237` + Champagne/or `#E0AF7C` + Crème `#F9F6F4`.** `Color Guide.svg` est **IGNORÉ** (ancienne maquette erronée : son Primary moka `#A8715A` / Secondary terracotta `#DD8560` n'est utilisé sur aucun écran). |
| 2 | **Polices** | **Poltawski Nowy** = titres des headers de page + boutons + textes éditoriaux (noms de resto, noms de statut, code parrainage). **Nunito Sans** = tout le reste (corps, meta, labels, PTS). `Typography.svg` est **IGNORÉ** (ancienne maquette). |
| 3 | **Tiers** | Ordre + noms **confirmés** : **Connaisseur → Grand Cru → Signature → Ambassadeur → Table Secrète**. (Seuils = à confirmer métier, cf §6.) |
| 4 | **Onglet IA** | **« Majordome »** validé. Nom produit **« OneClick »** conservé. |
| 5 | **App icon** | **OneClick = variante sombre** (`App_Icon.svg`). **Business = variante claire** (`App_icon_2.svg`). |
| 6 | **Rayon CTA** | 🎯 `button.txt` (CSS) tranche : **primaire ET secondaire = r12** (148×52). Le secondaire diffère uniquement par fond blanc + **bordure 4px `#E0AF7C`** (pas par le rayon). ⚠️ Ancienne lecture « secondaire r10 » **corrigée → r12**. Label = Poltawski Nowy **Bold (700)** 16px/21px. |
| 7 | **Responsive** | Design **adaptatif sur tous les écrans** (SE 375 / standard 393 / Pro Max 430), Dynamic Island + safe areas. Cf §9. |
| 8 | **Manques** | Claude **génère ce qui manque**, aligné 1:1 à la charte (cf §8). |

---

## 2. Positionnement (l'intention design)

Virage net : on quitte le **glassmorphic plum SaaS** (#714B67) actuel pour un registre **« club privé gastronomique / maison de luxe »**. Codes assumés et cohérents sur les 11 écrans :

- Fond **crème nacré chaud** (`#F9F6F4`, jamais de blanc pur) → lumière chaude, appétissante ; les surfaces blanches (cartes, cloche) ressortent par contraste.
- **Bi-registre typographique** : serif display (Poltawski Nowy) pour l'identité/émotion + sans humaniste (Nunito Sans) pour le fonctionnel.
- **Cartes-membre métal** façon Amex Centurion / sceau de domaine viticole (dégradés matière + filigrane zellige marocain).
- Nomenclature **sommelière** (Grand Cru, Signature, Connaisseur…).
- **Craft d'ombres** soigné : ombres **toujours teintées, jamais noires** (cf §4.4).

---

## 3. Typographie 📐+✅

| Rôle | Police | Usage |
|------|--------|-------|
| **Display / titres** | **Poltawski Nowy** (serif, contraste élevé) | Titres de header de page · **boutons** · noms de restaurant · noms de statut/tier · code parrainage gravé · tagline logo |
| **Texte courant** | **Nunito Sans** (sans humaniste) | Corps, meta (date/heure/couverts), labels de nav, chips, descriptions, « X PTS », compteurs, badges |

- **Licences** : les deux sont **Google Fonts / SIL OFL** → embarquables librement sur **iOS** (`UIAppFonts` + `Font.custom`) et **Android** (`res/font` + downloadable fonts). **Aucun blocage de licence.**
- **iOS — graisses à embarquer** : Poltawski Nowy Regular/Medium/SemiBold/Bold (+ Italic si besoin du nom de resto en italique) ; Nunito Sans Regular/Medium/SemiBold/Bold/ExtraBold.
- **Échelle typographique 🆕** (à caler sur device, points iOS) :
  | Token | Police | Taille / graisse | Exemple |
  |-------|--------|------------------|---------|
  | `display.xl` | Poltawski Nowy Bold | 30–34 | Nom de tier sur carte statut |
  | `title.page` | Poltawski Nowy SemiBold | 26–28 | « Casablanca », « Mes Réservations » |
  | `title.card` | Poltawski Nowy Medium | 18–20 | Nom de restaurant |
  | `button` | Poltawski Nowy **Bold (700)** 🎯 | 16 (lh 21) | « Réserver », « Postuler » |
  | `body` | Nunito Sans Regular | 15–16 | Descriptions |
  | `meta` | Nunito Sans Medium | 13–14 | Date/heure/couverts, sous-titres |
  | `overline` | Nunito Sans SemiBold, +tracking, caps | 11–12 | « VOTRE STATUT », « COMMENT ÇA MARCHE » |
  | `label.value` | Nunito Sans Bold | 13–14 | « 280 PTS » |

- ⚠️ **Déprécié** : Tenor Sans (figurait dans `Typography.svg`, désormais ignoré).

---

## 4. Système de design (tokens) 📐

### 4.1 Couleurs — marque

| Token | Hex | Usage |
|-------|-----|-------|
| `brand.bordeaux` (primary) | **`#681237`** | Action de sélection/état : chip actif, onglet nav actif, **liseré 3px** des cartes résa, stepper +, liseré bloc code, pin localisation, icônes meta |
| `brand.ink` (aubergine) | **`#3B2338`** | Encre titres serif, barre d'action pied de carte, icônes de liste, label onglet actif |
| `brand.gold` (champagne) | **`#E0AF7C`** | **CTA focal** (Réserver/Ajouter/Postuler), barre de progression, eyebrow statut, valeurs « gagnées », bordure bouton secondaire |
| `brand.burgundyDeep` | `#3B011A` / `#46021D` | Fond app icon sombre, logo |
| `brand.champagneLight` | `#F9ECE1` | Fond app icon clair, stop crème des dégradés tier |
| `brand.mauveMuted` | `#9B8598` | Texte secondaire, texte des pills inactives |

> **Système dual-accent (clé du design)** : **or `#E0AF7C`** = LA grosse action chaude (unique par écran) ; **bordeaux `#681237`** = autorité de marque (sélection, état, structure). **Les deux ne cohabitent jamais sur le même composant.**

### 4.2 Couleurs — surfaces & neutres

| Token | Hex | Usage |
|-------|-----|-------|
| `bg.app` | **`#F9F6F4`** | Fond global (crème nacré chaud) + bande header |
| `surface.card` | `#FFFFFF` | Cartes, cloche, boutons header, conteneurs |
| `surface.muted` | `#F5F5F5` | Tuiles d'onglet inactives, bouton − stepper |
| `surface.input` | `#F9F9F9` | Champs de saisie |
| `hairline.pill` | `#E8DFE5` | Bord 1px des pills inactives |
| `hairline.divider` | `#EDEDED` | Séparateurs de liste |
| `hairline.cardInner` | `#F0E8E8` | Bord interne 1px (carte date) |

### 4.3 Couleurs — texte (échelle 4+ niveaux)

| Token | Hex | Usage |
|-------|-----|-------|
| `text.ink` | `#242424` | Titres de ligne, labels forts |
| `text.title` | `#3B2338` / `#303030` | Titres serif / corps fort |
| `text.secondary` | `#666666` / `#808080` | Sous-titres, cuisine |
| `text.muted` | `#999999` | Meta (compteur, avis, ville), labels nav inactifs |
| `text.tertiary` | `#A6A6A6` | 2e ligne carte heure, état vide |
| `text.disabled` | `#CCCCCC` | Icônes inactives, dividers |

### 4.4 Ombres (TOUJOURS teintées — jamais noir pur) 📐

| Token | Couleur | x / y / blur | Usage |
|-------|---------|--------------|-------|
| `shadow.floatBtn` | `#8A959E` @40% | 0 / 4 / 20 | Cloche, bouton retour, boutons d'action flottants |
| `shadow.card` | `#101828` @40% | 0 / 0 / 3 | Cartes blanches (halo serré) |
| `shadow.cardSoft` | `#3B2338` @8% | 0 / 2 / 4–5 | Cartes date/stepper (aubergine très subtil) |
| `shadow.cta` | `#303030` @30% | 0 / 8 / 15 (→30 grand CTA) | CTA focal (point de focus) |
| `shadow.pillActive` | `#3B2338` @25% | 0 / 4 / 6 | Pill/segment actif |
| `shadow.bottomNav` | `#000000` @5% | 0 / **−2** / 25 | Bottom nav (ombre **inversée** vers le haut) |
| `shadow.memberCard` (double) | `#101828` @23% (0/0/3) **+** `#101828` @32% (0/12/8) | — | Carte-membre / carte statut (empiler 2 `.shadow`) |
| `shadow.eventsCard` | `#101828` @40% | 0 / 0 / 3 | Carte événements Elite |

> iOS : mapper chaque recette → `.shadow(color:radius:x:y:)` (`radius` ≈ `stdDeviation`). **Ne jamais substituer une ombre noire générique** — ça tue le rendu premium.

### 4.5 Rayons

| Token | Valeur | Usage |
|-------|--------|-------|
| `radius.screen` | 16 | Coins de l'écran |
| `radius.card` | 12 | Cartes, tuiles nav, boutons header, **CTA primaire ET secondaire** 🎯, **carte-membre/statut** 🎯, chips carrés |
| `radius.btnSecondary` | ~~10~~ → **12** 🎯 | ⚠️ Corrigé : `button.txt` donne r12 pour le secondaire aussi (cf Décision #6). Token conservé pour compat mais = 12. |
| `radius.pill` | h/2 (≈18 pour h36, ≈10 pour h19) | Chips filtres, segments, badges statut |
| `radius.progress` | 6 | Barre de progression (h12) |
| `radius.photo` | 8 | Vignette photo resto |
| `border.resaCard` | **3px inset `#681237`** | Liseré signature des cartes de réservation |

### 4.6 Espacement / grille 🆕

- Marge latérale écran : **20px** (cartes pleine largeur = `screenWidth − 40`).
- Gap vertical inter-cartes : **14px**.
- Bande header : ~122px (sous safe-area).
- Bottom nav : **98px** (inclut home-indicator).
- Cibles tactiles : **44×44** minimum (HIG).
- Icônes : header 16 · lignes carte 14 · nav 24 · glyphe dans chip 20 ; **stroke 1.5px**, linecap/linejoin round (set type Lucide/Feather).

---

## 5. Composants 📐 (extraits du kit : `button.svg`, `Icon_nav.svg`, `Page_navigation.svg`, `Menu_flottant.svg`, logos)

### 5.1 Boutons (`button.svg` / 🎯 `button.txt`)
> 🎯 Exact CSS : conteneur 148×52, label Poltawski Nowy **700 / 16px / lh 21** centré.
- **Primaire** : fill `#E0AF7C`, **r12**, `box-shadow 0 8 30 rgba(48,48,48,.3)` (= `shadow.cta`), label **blanc**. (ex. « Postuler », « Réserver », « + Ajouter un ami »)
- **Secondaire (outline)** : fill **blanc**, **bordure 4px `#E0AF7C`**, **r12** 🎯 (⚠️ pas r10), label `#E0AF7C`. (ex. « Parrainer ») — pas d'ombre dans le CSS.
- **Inline bordeaux** : fill `#681237`, label blanc, r12. (ex. « Réserver » par carte sur Enseignes, stepper +)

### 5.2 GlassHeader (`Page_navigation.svg` — 3 variantes)
Bouton retour (40×40 r12 blanc, chevron `#242424`) à gauche + **titre serif centré optionnel** (`#3B2338`, Poltawski Nowy) + cloche (40×40 r12 blanc, icône `#242424`, **pastille `#DB4545` r2.5**) à droite. Posé sur la bande crème, `shadow.floatBtn`. Variantes : retour seul / retour+cloche / retour+titre+cloche.

### 5.3 GlassBottomNav (`Menu_flottant.svg`) — **5 onglets, 1:1**

| Onglet | Icône | Route actuelle mappée |
|--------|-------|------------------------|
| **Enseignes** | toque de chef | Explore / restaurants |
| **Réservations** | calendrier | Reservations |
| **Majordome** | cloche de service | **Assistant IA / Care** |
| **Cercle** | groupe de personnes | Communauté / Circle |
| **Profil** | carte d'identité | Profil |

Barre blanche 393×98, `shadow.bottomNav` (inversée). Tuile **active** = `#681237` plein + icône blanche + label `#3B2338`. Tuiles **inactives** = `#F5F5F5` + icône `#3B2338` + label `#999999`. Tuiles 44×44 r12, pas ≈74.75px.

### 5.4 Cartes
- **Carte resto (Enseignes)** : ligne pleine largeur sur crème (pas de carte blanche encadrée), thumbnail 80×80 r8, nom serif `#3B2338`, « Cuisine · €€ » `#666666`, ★ `#FFC107` + note + « (avis) » `#999999`, cœur favori + bouton Réserver.
- **Carte réservation** : 353×139 r12 blanc, **liseré 3px `#681237` inset**, thumbnail 56×76 r8, badge statut pill en haut-droite, **barre d'action intégrée en pied** (`#3B2338`) : « Annuler » (blanc) | « Inviter (0/N) » (`#E0AF7C`), divider blanc 20%.
- **Carte-membre / statut** : cf §6.

### 5.5 Chips & segments
- Chip filtre **actif** : pill plein `#681237`, label blanc. **Inactif** : pill blanc, label `#9B8598`/`#666666`, sans bordure dure (relief par contraste + ombre subtile).
- Segment statut (3 pills, ex. En attente/Confirmés/Historique) : actif `#681237` + `shadow.pillActive`, inactifs blancs bord `#E8DFE5`.

### 5.6 Badge statut (recette réutilisable) 📐+🆕
Triplet **texte / fond pâle / bordure** ; h≈19, pill. Référence 📐 = CONFIRMÉ (vert). Les autres statuts 🆕 sont dérivés sur la même recette (cf §8.2).

### 5.7 Logo & app icons 📐
- **Logo** : monogramme **OC entrelacé avec fourchette intégrée** (dégradé métal bronze→argent) + « OneClick » (Poltawski Nowy) + tagline « RÉSERVATIONS & LOYAUTÉ » (`#681237`, caps, Nunito Sans). Variantes : lockup complet + monogramme seul (blanc pour fonds sombres).
- **App icon OneClick** (`App_Icon.svg`) : fond **bordeaux profond** + monogramme OC-fourchette **blanc** (variante **sombre**).
- **App icon Business** (`App_icon_2.svg`) : fond **crème `#F9ECE1`** + monogramme OC-fourchette **aubergine** (variante **claire**).

---

## 6. Système de statuts (tiers) 📐 — ✅ ordre/noms confirmés

5 paliers, nomenclature sommelière, ordre **verrouillé** :

| # | Palier | Seuil cible (maquette) | Dégradé matière (3 stops diagonaux, axe identique) | ≈ ancien |
|---|--------|------------------------|-----------------------------------------------------|----------|
| 1 | **Connaisseur** | 200 | `#C4C4C4 → #1A0610 → #E0AF7C` (argent→noir-bordeaux→or) | Ruby |
| 2 | **Grand Cru** | 500 | `#BC9369 → #211C1F → #F9ECE1` (bronze→charbon→ivoire) | Sapphire |
| 3 | **Signature** | 1 000 | `#63385D → #211C1F → #E0AF7C` (aubergine→charbon→or) | Émeraude |
| 4 | **Ambassadeur** | 10 000 | `#681237 → #1A0610 → #721932` (bordeaux→noir vineux→bordeaux) | **net-new** |
| 5 | **Table Secrète** | ~50 000 (sommet, pas de « prochain ») | `#9F9F9F → #000000 → #000000 → #721932` (acier→noir laqué→bordeaux ; **4 stops**) | Black |

- Accent transverse : **barre de progression** (fill aplat) = **`#E0AF7C`** sur tous les paliers ; rail blanc opaque ; tout le texte = **blanc**.
- 🎯 **Eyebrow « VOTRE STATUT » — couleur PAR palier** (vérifié `css/Statut=*.txt`) : **blanc** pour **Connaisseur** + **Grand Cru** ; **champagne `#E0AF7C`** pour **Signature** + **Ambassadeur** + **Table Secrète**. (⚠️ corrige l'ancienne note « eyebrow toujours champagne ».) Règle code = `rang ≥ Signature(2) ? champagne : blanc`.
- **Rosace zellige** marocaine en filigrane coin haut-droit, opacité par palier : Connaisseur 0.22 · Grand Cru 0.36 · Signature 0.21 · Ambassadeur 0.64 · Table Secrète 0.16. → exporter en **1 asset PDF/PNG @3x**, ne pas porter les centaines de paths.
- `shadow.memberCard` (double). Monogramme OC blanc en haut-gauche.
- **Carte Apple Wallet** (pkpass) : 358×225, même matière, eyebrow **« ONECLICK CLUB » blanc** (pas or), nom de tier serif, **pas** de barre de progression. Le dégradé diagonal n'est pas un solid Wallet → **background image** requise dans le pass.

⚠️ **À confirmer métier avant migration `tiers` (Spring)** : les **seuils** sont des valeurs de maquette (le saut **1 000 → 10 000** pour Ambassadeur est à challenger). Source de palier unique côté app = `useClientTier` / table `tiers` (jamais de hardcode). Le pkpass passe de 4 → 5 tiers.

### 6.1 🎯 Recette exacte carte statut (`css/Statut=Carte_Connaisseur.txt`, identique structurellement aux 5)

Carte **353×186**, `padding 16px 20px`, `gap 25`, flex column align-start, `border-radius 12`, dégradé `linear-gradient(225deg, …)` par palier (cf table ci-dessus), **double ombre** `0px 12px 16px -4px rgba(16,24,40,.32)` **+** `0px 0px 6px -2px rgba(16,24,40,.23)` (= `shadow.memberCard`).

| Élément | Spec exacte |
|---------|-------------|
| **Rosace « Vector »** (filigrane) | 131.01×132.67, `right -43.88`, `top -42.25`, gradient `204.18deg #3B0B20 32.38% → rgba(31,7,19,.3) 89.65%`, **opacité par palier** (cf §6) |
| **Frame 67** (logo + statut) | row, `gap 25`, 313×**53** |
| → **Logo OneClick blanc** | 89×**53**, blanc (asset vectoriel à extraire ; stand-in wordmark Poltawski blanc en attendant) |
| → **Frame 66** (bloc droite) | column align-**end**, `gap 6`, 199×31 |
| → → « **VOTRE STATUT** » | Nunito Sans **700 / 11px / lh 15**, `letter-spacing 0.05em`, uppercase, align right, **couleur par palier** (§6) |
| → → **nom de palier** | Poltawski Nowy **500 / 24px / lh 31**, `letter-spacing 0.1em`, capitalize, align right, **blanc** |
| **Frame 69** (progression) | column, `gap 12`, 313×57 |
| → « **Prochain palier : X** » | Nunito Sans **400 / 13px / lh 18**, `letter-spacing 0.05em`, blanc (masqué au sommet) |
| → **Frame 68** (barre + labels) | column, `gap 7` |
| → → **rail** (Frame 64) | 313×**12**, `background #FFFFFF`, **`border-radius 39`** (≈ capsule) |
| → → **fill** (Rectangle 443) | h12, `background #E0AF7C`, r39, largeur = ratio progression (24px ≈ 40/200 pour Connaisseur) |
| → → **labels** (Frame 65) | row space-between : « **X PTS** » (gauche, points courants) / « **Y pts** » (droite, seuil suivant), Nunito Sans **700 / 11px / lh 15**, ls 0.05em, uppercase, blanc |

> **Mode catalogue** (« Programme de fidélité ») : la même carte sert au catalogue des paliers, mais SANS sémantique de progression membre (parité legacy `LoyaltyCard` = logo + palier + seuil). Variante = `catalogThreshold` non-nil → masque « Prochain palier », barre et double-label ; affiche le **seuil d'accès** une seule fois (« SEUIL D'ACCÈS / X PTS »). Évite l'incohérence « barre pleine + X/Y identiques » d'une réutilisation naïve.

---

## 7. Écrans (résumé + delta vs app actuelle) 📐

| Écran | Points clés | Delta vs actuel |
|-------|-------------|------------------|
| **Enseignes** (Explore) | sélecteur ville serif + chevron, cloche flottante, chips pills (actif plum), cartes resto pleine largeur sur crème, Réserver inline par carte | glass plum → éditorial chaud |
| **Spotlight / Réserver** | hero photo + overlay (0.28→1.0 @0.44), nom resto **serif blanc aligné droite**, stepper (− gris / + plum), cartes date/heure, **CTA RÉSERVER champagne** non pleine largeur | « menu de grande maison » |
| **Mes Réservations** | titre serif, segment 3 pills, cartes **liseré 3px** + barre d'action pied (Annuler / Inviter) | liseré bordeaux signature |
| **Cercle & Communauté** | hero + CTA « + Ajouter un ami » champagne à cheval, raccourcis en cartes icône | — |
| **Elite Club** | carte-membre hero (dégradé+rosace+monogramme+progression), CTA Postuler/Parrainer, carte événements (vide verrouillé) | pièce de bravoure |
| **Parrainage** | carte stats, **bloc code liseré bordeaux** + carte-membre sombre (trame code-barres `plus-lighter` @0.16) + code serif gravé + 3 boutons (Partager/Copier/QR), « Comment ça marche » | — |

---

## 8. Manques de la charte → à générer 1:1 (Claude)

> Marqueur : 🆕 = production Claude alignée sur le système ci-dessus, **à valider** designer/métier. Livrés dans `Charte_graphique/generated/` (jamais d'écrasement des SVG du designer).

### 8.1 Écrans absents (SVG 393×852, mêmes composants/tokens)
- **Fiche restaurant complète** (Spotlight détail) : hero + nom + note Google + horaires + offres/promos + sections + CTA Réserver. (La maquette ne montre que l'étape réservation.)
- **Profil** (hub : avatar, tier, modules grille, liens).
- **Promos / Offres** (liste des offres actives).
- **Snap2Earn** (scan ticket/QR → crédit points).
- **Panneau Notifications** (liste, types, non-lues).

### 8.2 Couleurs des statuts de réservation 🆕 (badge = texte / fond / bordure)
| Statut | Texte | Fond | Bordure | Logique |
|--------|-------|------|---------|---------|
| Confirmée 📐 | `#00881E` | `#EEF7F1` | `#B8DFC8` | succès (référence) |
| En attente / demandée | `#B07A22` | `#FBF3E4` | `#EAD6AE` | ambre = en cours |
| Contre-proposition | `#681237` | `#F6EAF0` | `#E2C6D5` | marque = action requise |
| Honorée | `#9A6A2B` | `#FAF1E5` | `#E9D3B2` | or = accompli/célébré |
| Refusée | `#C0143C` | `#FBECEF` | `#F0CBD4` | rouge = refus |
| Annulée | `#808080` | `#F2F0F0` | `#E2DCDC` | neutre |
| No-show | `#B3261E` | `#FBE9E7` | `#F1C9C4` | rouge profond = pénalité |
| Annulation tardive | `#9B8598` | `#F3EFF2` | `#E3D9E0` | mauve atténué |

### 8.3 Tokens sémantiques 🆕 (absents de la charte, dérivés dans la palette chaude)
| Token | Hex | Note |
|-------|-----|------|
| `semantic.success` | `#00881E` | repris du badge confirmé |
| `semantic.danger` | `#DB4545` | **unifier** les deux rouges charte (`#DB4545` notif / `#DC3545` cœur) en **un seul** ; pressed `#B92D2D` |
| `semantic.warning` | `#E8A33D` | ambre distinct du champagne marque |
| `semantic.info` | `#8A959E` | gris-bleu (déjà présent dans les ombres) — reste dans le registre chaud/désaturé |
| `semantic.star` | `#FFC107` | note Google (inchangé) |

### 8.4 Dark mode — ⛔ HORS SCOPE (décision client 14/06/26)
**Pas de dark mode dans les applications OneClick.** L'écran `dark-mode-enseignes.svg` a été supprimé ; aucun token `dark.*` n'est défini côté iOS. Le toggle « Mode sombre » est à retirer du Profil. (La planche `board-semantique-darkmode.svg` conserve une section dark à titre informatif uniquement — non retenue.)

### 8.5 États 🆕 (empty / error / loading / pressed)
- **Empty** : illustration légère + titre serif + sous-titre muted + CTA (ex. « Aucun favori » → « Explorer »).
- **Error** : icône `semantic.danger` + message + bouton « Réessayer ».
- **Loading / skeleton** : blocs `#F1ECEF` (crème désaturé) en shimmer doux ; pas de spinner brut.
- **Pressed** : scale 0.97 + assombrir le fill de ~6 % (ou opacité 0.9) ; haptique léger.

---

## 9. Responsive 🆕 (principe directeur — tous les écrans)

- **Maquettes = 393pt** (iPhone 14/15). Ne **jamais** figer les positions absolues du SVG.
- **Largeurs cibles** : SE/mini **375** · standard **390/393** · Plus/Pro Max **430**.
- **Règles** : marge latérale fixe **20px** ; cartes = `width − 40` ; CTA centré largeur fixe (look « bijou ») mais bornée (max ~340) ; carte-membre ratio **~1.9** (largeur fluide, hauteur dérivée) ; 3 boutons d'action parrainage en **flex centré** (pas de x absolus 112.5/176.5/240.5).
- **Safe areas** : header sous **Dynamic Island** (`safeAreaInset(.top)`), bottom nav au-dessus du **home indicator** (`safeAreaInset(.bottom)`).
- iOS : layout en `GeometryReader`/`maxWidth: .infinity` + `padding(.horizontal, 20)`, **jamais** de frames en dur dérivées de 393.

---

## 10. Plan de rebranding iOS → Android

> iOS d'abord (`OneClick_iOS_Native`, app Win/client). Backend Spring inchangé sauf §6 (tiers). Rien n'est codé tant que ce doc n'est pas validé.

- **Phase 0 — Design system.** ✅ **Tokens LIVRÉS** (`OneClick_iOS_Native/OneClickWin/DesignSystem/`) : `OneClickColors` (palette charte, dual-accent, noms stables + statuts via `OneClickStatusColors`), `OneClickMetrics` (rayons 16/12/10/8/6), `OneClickShadows` (ombres teintées + double ombre carte-membre), `OneClickTypography` (Poltawski Nowy + Nunito Sans), `OneClickButtonStyle` (primary champagne / secondary outline / inline plum), `OneClickTheme.oneClick`, `OneClickTierStyle` (5 dégradés tiers). Polices OFL **embarquées** (`Resources/Fonts/` + `UIAppFonts`). **Assets** : AppIcon OneClick sombre (1024, alpha retiré), `AccentColor` → `#681237`, `LaunchBackground` → `#F9F6F4`, `LaunchLogo` + `OCMonogram` (template) = monogramme OC-fourchette, app icon Business clair (source dans `generated/`). **BUILD SUCCEEDED** + lancé sur simu : fond crème + titre serif Poltawski + bordeaux confirmés à l'écran. WIP non committé.
  - 🎯 **Corrections CSS (14/06 soir)** : `OneClickShadows` float radius 10→**20** (CSS blur 40), cta 12→**15** (CSS blur 30) ; `OneClickButtonStyle` secondaire r10→**r12** (cf §5.1/Décision #6) ; **icônes nav** extraites en assets template depuis `Menu_flottant.svg` (5 glyphes `nav_*`, `nav_profil` recadré `translate(-384,-80)`).
  - 🐛 **Fix login** (vérifié live) : `KeychainTokenStore` échouait en `errSecMissingEntitlement (-34018)` sur simu non signé → la session ne persistait pas (retour Welcome). Ajout d'un **miroir mémoire** (write+read prioritaire) ; Keychain best-effort. (cf MEMORY `feedback_ios_native_base_url`.)
  - **Reste Phase 0** : rosace zellige (asset PDF/PNG) + logo OneClick blanc 89×53 (asset vectoriel pour carte statut) + monogramme OC transparent/centré (OCMonogram rendait un carré) + wiring app `.business`.
- **Phase 1 — Atomes/composants.** ✅ **LIVRÉ** (vérifié live au simu, `client2@client2.com`) : `OneClickButtonStyle` (primaire or / secondaire outline-or r12 / inline plum), `OneClickChip` (pill actif `#681237`/blanc · inactif surface + label `#9B8598` + `cardSoft`, **label Nunito Sans 600** 🎯), `OneClickStatusBadgeView` (triplet texte/fond/bordure), `GlassHeaderView` (retour/titre serif/cloche + pastille), `GlassBottomNav` (5 onglets glyphes réels), `.oneClickShadow(.card/.cta/.pillActive/.cardSoft/.bottomNav/.member)`, carte résa **liseré 3px inset `#681237`**. **Carte statut/membre `TierPremiumCard` réécrite 1:1** (recette §6.1 : dégradé matière par rang DB, double ombre, logo blanc, VOTRE STATUT eyebrow par palier, barre champagne, X PTS/Y pts) + **mode catalogue** `catalogThreshold` (Programme de fidélité). Tier = **source unique** (`TierProgress` depuis seuils serveur, jamais `getTier(points)` local).
  - 🎯 **Clôture P0/P1 (14/06 nuit)** : pastille cloche **5×5 `#DB4545`** (charte « Ellipse 70 », était 7×7) ; labels boutons **Poltawski Bold 700** (étaient `.semibold` → `button.txt` = 700) ; secondaire confirmé blanc + bordure **4px** `#E0AF7C` + r12 ; ancien asset mort `OCMonogram.imageset` **supprimé** (0 réf). App icon `.business` = **N/A** (pas de target Business — app Win seule ; à wirer quand l'app Business existera, Phase 2/5). **P0 + P1 = 100%.** WIP non committé.
- **Phase 2 — Écrans, dans l'ordre charte**, 1:1 : Enseignes → Spotlight/Réserver (+ fiche complète §8.1) → Mes Réservations → Cercle → Elite → Parrainage → cartes Statut/Wallet → Profil/Promos/Snap2Earn/Notifications (§8.1). **⚠️ Les écrans natifs existent déjà (portage P3) mais portent encore le thème générique** — la charte n'est appliquée qu'aux tokens (P0) + atomes (P1). **Backlog priorisé par l'audit CSS↔code : cf §11.**
- **Phase 3 — Tiers (data).** ✅ **MIGRÉ (14/06 soir, vérifié live)** — migration **`V98__rebrand_charte_tiers.sql`** (idempotente) : Ruby→Connaisseur 200, Sapphire→Grand Cru 500, Émeraude→Signature 1000, **+ Ambassadeur 10000 (net-new)**, Black→Table Secrète 50000, sur les 5 tenants (corrige le seed cassé « 1234 partout »). Consommateurs du nom littéral réalignés : `LoyaltyTierResolver` (fallback canonique) + `PromoAudienceResolver` (segments, clés legacy conservées→palier renommé) + iOS `TierVisuals`/`TierCardPalette` + catalogue `VaultFidelite` (filtre `known` + descriptions). Tests Spring **70 verts**. Appliqué sur `oneclick_enterprise` (Flyway success). **Carte statut/wallet** : monogramme OC + rosace zellige = assets vectoriels extraits 1:1 (cf Phase 1). **Reste** : pkpass 5 tiers (régénérer le pass Apple/Google) + `bonus_percent` par palier (resté 7.5 — à confirmer métier).
- **Phase 4 — Polish.** Couleurs statuts (§8.2), états (§8.5), responsive (§9), motion. (Pas de dark mode — cf §8.4.)
- **Phase 5 — Export Android.** Une fois iOS validé, porter **le même système de tokens + composants** sur `android-native` (mêmes hex, ordres, recettes d'ombre).

---

## 11. Audit CSS↔code (backlog écrans, phases 2-4) 🎯

> Audit 1:1 des **exports CSS Figma** vs l'implémentation SwiftUI (12 éléments, 175 divergences : **23 🔴 blocker / 64 🟠 major / 88 🟡 minor**). Rapport JSON complet (specs charte + fichiers Swift + fix proposé par divergence) : `tasks/w97mg9jq9.output` (session). Snapshot **antérieur** à la réécriture P1 de la carte statut + corrections P0/P1 du 14/06 soir → les lignes « Cartes statut » (noms/dégradés faux) et « Icônes nav » sont **déjà partiellement résolues** (carte membre + catalogue 1:1, glyphes nav réels).

| Élément | P | 🔴 / 🟠 / 🟡 | Blockers (résumé) |
|---------|---|------|-------------------|
| Explore / Enseignes | 2 | ~~1~~ / 10 / 17 | ✅ **RÉSOLU (P2, 14/06 — décision user)** : titre serif « Enseignes » (Poltawski) en header + sélecteur de ville en **sous-ligne** (pin champagne + Nunito) ; recherche/chips/empty/toast + noms resto serif (card) en polices charte |
| Mes Réservations | 2 | ~~1~~ / 8 / 10 | ✅ **RÉSOLU (P2, 14/06)** : segment 3 onglets charte (`Picker(.segmented)` natif → pastilles, actif = bordeaux + label Poltawski blanc + ombre `.pillActive`, piste muted) ; empty-state + toast polices charte |
| Spotlight / Réserver | 2 | ~~1~~ / 5 / 7 | ✅ **RÉSOLU (P2, 14/06)** : CTA « Réserver une table » = `PrimaryButtonStyle` champagne (ombre `.cta`, Poltawski Bold) ; titres sections serif (Infos/À propos/Horaires/Offres) ; pts card rosette champagne ; toutes polices charte |
| **Elite Club** | 2 | ~~4~~ / 6 / 8 | ✅ **RÉSOLU (P2, 14/06)** : titre serif « Le Club Élite », hero bordeaux + ombre `.card` + rosette champagne, CTA dual-accent champagne (Postuler plein / Parrainer outline) + submit champagne `.cta`, **toutes les polices charte** (Poltawski titres/boutons, Nunito Sans corps), cartes statut/avantages `.cardSoft` |
| Communauté / Cercle | 2 | 0 / 4 / 9 | hero événement absent, titre header, cartes nav |
| **Parrainage** | 2 | ~~2~~ / 8 / 7 | ✅ **RÉSOLU (P2, 14/06)** : bloc CODE = carte sombre bordeaux (dégradé + ombre `.card`), plaque à **trame code-barres** (`ReferralBarcodeTexture` Canvas), **code gravé serif champagne** (Poltawski kerné), CTA dual-accent (Partager champagne plein / Copier+QR outline), QR sheet code serif ; toutes polices charte |
| **Splash / Launch** | 2 | ~~6~~ / 4 / 1 | ✅ **RÉSOLU (P2, 14/06)** : splash charte (crème + monogramme matière + « OneClick » Poltawski + tagline bordeaux), ancien concept arc/curseur/emojis supprimé. **Fix bug launch (14/06 nuit)** : `UILaunchScreen.UIImageName=LaunchLogo` retiré (asset `LaunchLogo` malformé = monogramme dans le coin d'un carré blanc opaque 200×200 → boîte blanche au lancement). Launch screen natif = crème pur (`LaunchBackground`), le splash SwiftUI fait le branding. Imageset mort supprimé. Vérifié cold-launch live. |
| **Logo OneClick** | 2 | ~~2~~ / 7 / 5 | ✅ **RÉSOLU (P2, 14/06)** : lockup Login = monogramme matière (`OCMonogramCard` + `OneClickGradients.logoMatiere` partagé Splash/Login) + wordmark « OneClick » Poltawski + tagline Nunito ; portes welcome dual-accent (champagne signup / plum login, titres Poltawski) ; ancien `OnesleyBrandMark` supprimé |
| App Icon clair (Business) | 2 | 2 / 3 / 3 | variante claire/Business absente du catalog |
| Cartes Wallet / Statut | 3 | ~~4~~ / 6 / 4 | ⚠️ rendu carte **résolu** (P1) ; **reste** rename wine-tiers (P3 BE) + pkpass 5 tiers |
| App Icon (sombre) | 5 | 0 / 1 / 4 | variantes appearances (tinted) |
| Icônes nav | 1 | ~~0~~ / 2 / 13 | ⚠️ glyphes **livrés** ; reste stroke-width fin + Ø pastille |

**Lecture** : Phase 2 = gros du travail (appliquer la charte à chaque écran déjà porté). **TOUS les blockers §11 Phase 2 sont résolus** : Splash ✅, Elite ✅, Logo ✅, Parrainage ✅, Spotlight ✅, Mes Réservations ✅, Explore ✅ (App icon Business = N/A, pas de target). Chaque ligne du rapport JSON porte `charte` (spec exacte) + `current` (code) + `fix` (action).

**Avancement Phase 4 (14/06 nuit — quasi terminé, tout vérifié live)** : 2 workflows audit+reverif-adverse (12 puis 24 agents) → punch-lists persistées `docs/PHASE4-CHARTE-AUDIT.json` + `docs/PHASE4-CHARTE-AUDIT.json` (sweeps). ✅ **Cercle/Communauté** (CircleHub, Friends, Groups, SentInvites, AddFriend, FriendPicker : ~70 polices→charte, `.cardSoft`, hero `#9B6B8C`→`primaryGlow`). ✅ **`OutlinedTextField` partagé** (Nunito, leverage formulaires). ✅ **batch-2 livré + live** : Notifications (polices + empty serif + ombres rows) · Assistant (polices + Confirmer→`.oneClickInline` + Annuler→`.oneClickText` + ombre carte) · Welcome wizard (polices + OTP/titres serif + CTA helper→`.oneClickPrimary` **champagne**) · Legal (tints icônes→primary §8.2, titres serif, ombres) · `GlassBottomNav` ombre brute→`.oneClickShadow(.bottomNav)`. ✅ **sweeps (b) livrés + live** : 123 éditions 0 miss sur loyalty/ui (43), profile/ui (36), moments/ui (21), care/ui (23) — classification serif(display)=titres/noms/paliers/gros chiffres sinon Nunito(body), SF Symbols laissés ; build vert ; écrans live OK (Mes Points palier serif, PersonalInfo, Moments, Care). **Login/simu** : bundle id = `ma.oneclick` (renommage Phase 3, pas `ma.oneclick.ios.win`). ✅ **Phase 4 tail livrée (15/06)** : **états §8.5** — primitives `OneClickSkeleton`/`OneClickSkeletonList`/`OneClickSkeletonCard` (`DesignSystem/Components/OneClickSkeleton.swift`, blocs `#F1ECEF` + shimmer doux, respecte Reduce Motion, param `inset:false` quand le conteneur padde déjà) appliquées sur **27 sites de chargement-CONTENU** (26 écrans, SentInvites = 2 onglets) — listes + détails. ⚠️ **Re-vérif post-crash agents (15/06)** : grep arbre complet (App/+Core/+DesignSystem/, hors périmètre du grep d'audit initial `Features Shell`) → **GroupsView:109 rattrapé** (content-load oublié entre 2 batches). **Conservés volontairement** (≠ chargement-contenu, donc PAS de skeleton) : spinners d'ACTION (submit/typing/OTP/posting/saving/verifying) ; barre de progression **déterminée** (VaultView:96) ; overlay sur liste périmée pendant changement de ville (ExploreView:177, parité Android) ; 3 spinners de **section** dans une page déjà peuplée (EliteClubView statusSection, PccFeedback thread, PccSeminar mes-séminaires — 1:1 legacy) ; 2 **gates de boot/hydratation** plein écran (RootCoordinatorView `.unknown` + `AuthenticatedGate` — pas de forme de contenu à esquisser ; swap splash brandé écarté = risque flicker) ; **motion** — cross-fade `.animation(.easeInOut(0.28), value: vm.state.isLoading)` sur 6 écrans tab-root (Explore/Moments/Promos/Notifications/Vault/Favorites ; `UiState` non-Equatable → clé sur `.isLoading: Bool`). Build vert + skeleton vérifié LIVE (carte Spotlight pendant fetch → résolution propre en contenu). **Phase 4 = COMPLÈTE** (audit via workflow 34 fichiers, application manuelle). Faux-positifs actés : états §8.5 spinners d'action gardés (≠ chargement contenu) ; `shadow.bottomNav`=noir@5% (exception §4.4) ; pastilles notif per-type (taxonomie 1:1 legacy) ; chips/quick-rows Assistant pleine largeur (1:1 legacy, pas `OneClickChip`). `shadow.bottomNav`=noir@5% (exception §4.4) ; pastilles notif per-type (taxonomie 1:1 legacy) ; chips/quick-rows Assistant pleine largeur (1:1 legacy, pas `OneClickChip`).

**Avancement Phase 2 (14/06 nuit) — 7 lots blockers faits, vérifiés live** : ✅ Splash · ✅ Elite Club + Postuler · ✅ Logo OneClick (lockup Login + portes dual-accent) · ✅ Parrainage (bloc code sombre + trame code-barres + code gravé serif) · ✅ Spotlight (CTA Réserver champagne + titres serif) · ✅ Mes Réservations (segment charte 3 onglets) · ✅ Explore (titre serif « Enseignes » + ville en sous-ligne, **décision user**). **Tous les écrans blockers Phase 2 sont charte-conformes.** ⏭️ Reste = **majors/minors non-blockers** (Cercle hero/cartes, finitions stroke-width nav, états §8.5, motion §9 = Phase 4) + reliquats (pkpass 5 tiers P3, App icon Business quand target créée). **WIP iOS non committé** (commit différé — décision user ; + shared `OneClickGradients`, suppression `OnesleyBrandMark`).

---

## Annexe — Inventaire des fichiers `Charte_graphique/`

| Fichier | Statut | Contenu |
|---------|--------|---------|
| `App_Icon.svg` | 📐 actif | App icon **sombre** = OneClick |
| `App_icon_2.svg` | 📐 actif | App icon **clair** = Business |
| `OneClick.svg` / `Oneclick_2.svg` | 📐 actif | Logo (lockup + variante) |
| `button.svg` | 📐 actif | Boutons primaire/secondaire |
| `Icon_nav.svg` | 📐 actif | Boutons header (retour + cloche) |
| `Page_navigation.svg` | 📐 actif | GlassHeader (3 variantes) |
| `Menu_flottant.svg` | 📐 actif | GlassBottomNav (5 onglets) |
| `Explore.svg` | 📐 actif | Écran Enseignes |
| `Spotlight.svg` | 📐 actif | Écran Spotlight/Réserver (étape réservation) |
| `Reservation.svg` | 📐 actif | Écran Mes Réservations |
| `Communauté.svg` | 📐 actif | Écran Cercle & Communauté |
| `Elite.svg` | 📐 actif | Écran Elite Club |
| `Parrainage.svg` | 📐 actif | Écran Parrainage |
| `Statut=*.svg` (×5) | 📐 actif | Cartes de statut (5 tiers) |
| `Cartes_apple_wallet.svg` | 📐 actif | Cartes pkpass |
| `Splash.svg` | 📐 actif | Splash screen |
| `UI kit.svg` | 📐 actif | Kit UI consolidé |
| `Color Guide.svg` | ⛔ **IGNORÉ** | Ancienne maquette (Primary/Secondary moka erronés) |
| `Typography.svg` | ⛔ **IGNORÉ** | Ancienne maquette (Tenor Sans, déprécié) |
| `css/*.txt` | 🎯 **actif (fait foi)** | **Exports CSS Figma** (18 fichiers) = specs exactes px/hex/rayon/ombre/police par écran & composant. Source de vérité prioritaire sur la lecture visuelle des SVG. |
| `generated/` | 🆕 Claude | Écrans/specs manquants (§8) — à valider |
