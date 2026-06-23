# Charte_graphique / generated/ — manques générés (Claude)

> 🆕 Maquettes **générées par Claude**, alignées 1:1 sur le système de design de la charte (voir `OneClick_Spring/docs/REBRANDING-CHARTE-GRAPHIQUE.md`).
> **Ce ne sont PAS des fichiers du designer.** Statut : **à valider** designer/métier avant implémentation iOS.
> Tokens, composants, recettes d'ombre et fonts (Poltawski Nowy + Nunito Sans) repris à l'identique de la charte. Tout est SVG bien formé (validé `xmllint`).

## Écrans (viewBox 393×852)
| Fichier | Manque comblé |
|---------|----------------|
| `fiche-restaurant.svg` | Fiche resto **complète** (hero, note Google, horaires, adresse, offres, à propos, CTA Réserver) — la charte ne couvrait que l'étape réservation |
| `profil.svg` | Hub Profil (avatar, tier, grille 6 modules, réglages) |
| `promos.svg` | Liste Offres / Promos |
| `snap2earn.svg` | Scanner ticket/QR → points |
| `notifications.svg` | Panneau Notifications (groupé, typé, non-lues) |
| `dark-mode-enseignes.svg` | Déclinaison **dark mode** de l'écran Enseignes |

## Planches specs (design system)
| Fichier | Contenu |
|---------|---------|
| `board-statuts-reservation.svg` | 8 badges de statut résa + hex (texte/fond/bordure) |
| `board-etats.svg` | États empty / error / loading (skeleton) |
| `board-semantique-darkmode.svg` | Tokens sémantiques + palette dark mode + rappel marque |

## Référence
- `_kit-preview.svg` — kit composant validé (status bar, GlassHeader, GlassBottomNav 5 onglets, boutons, carte, chips). Source des snippets réutilisés dans tous les écrans.

## Polish restant (mineur, pour le designer)
Quelques détails cosmétiques à affiner (n'affectent pas le système) :
- Sur certains CTA, un glyphe d'icône se superpose légèrement au label centré (Explorer, Réessayer, Prendre une photo, toggle Scanner) → décaler l'icône à gauche du texte.
- `promos.svg` : la 1ʳᵉ carte répète « -25% » dans le titre **et** dans le badge → retirer du titre.
- `fiche-restaurant.svg` : le bloc « À propos » et le CTA bas se chevauchent en Y (en prod l'écran scrolle, CTA fixé en bas) → resserrer si vue figée.
- Rendu local : `qlmanage` ne possède pas Poltawski Nowy/Nunito Sans → fallback Georgia/sans à l'aperçu ; les `font-family` déclarées sont correctes pour iOS/Android.

## Rendu en pleine page (qlmanage recadre les portraits)
```
G=$(pwd)
cat > /tmp/wrap.svg <<EOF
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink" width="880" height="880" viewBox="0 0 880 880">
<rect width="880" height="880" fill="#fff"/><image x="243" y="14" width="393" height="852" xlink:href="file://$G/fiche-restaurant.svg"/></svg>
EOF
qlmanage -t -s 1100 -o /tmp /tmp/wrap.svg
```
