# OneClick — Brief fonctionnel produit

> Description fonctionnelle complète du **produit en production** (mai 2026).
> Document basé sur le code réel + 30+ docs internes + l'historique de 48 sessions de dev.
> Public visé : développeurs / architectes qui rejoignent l'équipe et ont besoin de comprendre le **quoi** et le **pourquoi** avant de coder.
>
> Document maintenu par l'équipe OneClick · Version v2.0 · 8 mai 2026

---

## Table des matières

1. [Vision & positionnement](#1-vision--positionnement)
2. [Acteurs & rôles](#2-acteurs--rôles)
3. [Architecture fonctionnelle — apps & portails](#3-architecture-fonctionnelle--apps--portails)
4. [Modules métier — côté client final](#4-modules-métier--côté-client-final)
   - 4.1 [Compass — exploration & catalogue](#41-compass--exploration--catalogue)
   - 4.2 [Réservation](#42-réservation)
   - 4.3 [Vault — fidélité & Lounge points](#43-vault--fidélité--lounge-points)
   - 4.4 [Snap2Earn — capitalisation par OCR](#44-snap2earn--capitalisation-par-ocr)
   - 4.5 [Circle — communauté](#45-circle--communauté)
   - 4.6 [Moments — promotions & offres](#46-moments--promotions--offres)
   - 4.7 [Élite Club](#47-élite-club)
   - 4.8 [Care — assistance IA & tickets](#48-care--assistance-ia--tickets)
   - 4.9 [Bookable resources — pattern PCC](#49-bookable-resources--pattern-pcc)
   - 4.10 [Member Circle — feed social privé](#410-member-circle--feed-social-privé)
   - 4.11 [Ma Famille & Donner mon avis](#411-ma-famille--donner-mon-avis)
   - 4.12 [Wallet pkpass — carte de membre Apple Wallet](#412-wallet-pkpass--carte-de-membre-apple-wallet)
5. [Modules métier — côté restaurateur](#5-modules-métier--côté-restaurateur)
6. [Modules métier — côté admin (Command Center)](#6-modules-métier--côté-admin-command-center)
7. [Whitelabel — système OneLabel multi-tenant](#7-whitelabel--système-onelabel-multi-tenant)
8. [Workflows critiques](#8-workflows-critiques)
9. [Notation, fiabilité & contestation](#9-notation-fiabilité--contestation)
10. [Notifications & communication](#10-notifications--communication)
11. [Modèle économique](#11-modèle-économique)
12. [Conformité & confiance](#12-conformité--confiance)
13. [Données en production](#13-données-en-production)
14. [Glossaire](#14-glossaire)
15. [Annexe — roadmap & non-prod](#15-annexe--roadmap--non-prod)

---

## 1. Vision & positionnement

**OneClick** est une plateforme SaaS marocaine de **fidélisation, réservation et gestion** pour restaurants et clubs de loisir premium. Anciennement nommée *M3AK*. Opérée par la société **Onesley** depuis fin 2025.

### Promesses

| Pour le client final | Pour le restaurateur |
|---|---|
| « Une seule app pour découvrir des restaurants, réserver une table, accumuler des points en payant l'addition, et profiter d'offres ciblées. » | « Je remplis mes salles, je fidélise mes clients sans imprimer de cartes, je suis ma performance en temps réel. » |

### Le marché et le problème résolu

Au Maroc, **aucun acteur** ne combinait avant OneClick :
- Système de fidélité **dématérialisé** (pas de carte plastique)
- Réservation en ligne avec workflow d'acceptation restaurant
- Outils de gestion staff + analytics + facturation automatisée
- Communauté + parrainage + recommandations IA

OneClick adresse les ~3000 restaurants de la classe moyenne+ marocaine (Casablanca, Rabat, Marrakech…) et leurs ~2 millions de clients potentiels.

### Différenciateurs

- **Snap2Earn** — la fidélité se déclenche au scan d'un ticket de caisse (OCR), pas via une carte ni une app à montrer. Aucune friction au moment du paiement.
- **OneLabel** — système whitelabel : la même infrastructure se décline sous plusieurs marques (HOMU, Palmeraie Country Club, Restopro). 1 codebase × N clients premium.
- **Pattern bookable resources** — au-delà du restaurant, OneClick gère ressources réservables : courts de padel, spa, golf, coiffeur, salle de sport. Pattern générique réutilisable.
- **IA intégrée native** — OneClick Care peut créer une réservation, ajouter un ami, créer un squad, ouvrir un ticket support — directement depuis la conversation.
- **Communauté & effet réseau** — amis, parrainage, groupes (squads), feed social privé pour clubs premium (Member Circle).

### Échelle (mai 2026)

- ~**1 002 restaurants** partenaires actifs sur 10 villes marocaines
- ~**17 000 comptes** utilisateurs (clients + staff + admins)
- ~**3 552 réservations** historisées + 1 497 distributions de points
- **5 tenants** opérationnels : OneClick + HOMU + PCC + La Grillardière + Restopro (ce dernier en attente activation)
- **11 bundles iOS** distincts (5 OneClick standard + 6 whitelabel)

---

## 2. Acteurs & rôles

OneClick distingue **2 systèmes de rôles** complémentaires : applicatifs (haut niveau) et staff (intra-restaurant).

### 2.1 Rôles applicatifs (`app_role` — 4 valeurs)

#### Client final (`client`)
- Particulier marocain, smartphone iOS / Android
- Découvre, réserve, accumule des points, parraine
- Pas de relation contractuelle directe avec OneClick (gratuit à vie)
- Persona : 25–45 ans, urbain, classe moyenne+

#### Restaurateur (`restaurateur`)
- Propriétaire ou manager d'un ou plusieurs restaurants
- Signe un contrat partenaire avec OneClick (commissions)
- Utilise quotidiennement l'app **Store** (mobile) ou le portail web **Pro Desk**
- Personas : indépendants ou groupes (10-50 restaurants pour les chaînes type La Grillardière)

#### Tenant Admin (`tenant_admin`)
- Persona introduite avec OneLabel (HOMU, PCC, Restopro)
- Gère son **sous-écosystème** uniquement (les restos de **son** tenant)
- Cas typique : le directeur du Palmeraie Country Club qui pilote ses 7 services internes

#### Admin OneClick (`admin`)
- Équipe interne Onesley (3-5 personnes)
- Supervise contrats, facturation, support, onboarding restaurants, modération
- Accès au **Command Center** (8 modules — cf §6)

### 2.2 Rôles staff (`staff_role` — 5 valeurs, intra-restaurant)

Pour un restaurateur multi-employés, on distingue :

| Rôle staff | Permissions typiques |
|---|---|
| **owner** | Tout : staff, finances, contrat, paramètres |
| **directeur** | Stratégie, équipe, finances |
| **manager** | Opérations, équipe, KPIs (sans finances) |
| **responsable_resa** | Réservations + clients (pas finances ni équipe) |
| **serveur** | Calendrier du jour + Snap2Earn uniquement |

Matrice **52 permissions × 5 rôles = 260 grants** dans la table `staff_role_permissions`. Chaque permission = `<menu>.<action>` (ex: `reservations.delete`, `users.read`).

### 2.3 Pourquoi 2 systèmes parallèles

`app_role` filtre **l'accès à l'app** (une cliente n'ouvre pas Pro Desk). `staff_role` filtre **les actions à l'intérieur** d'un restaurant donné. Un même `auth.users` peut avoir plusieurs `staff_role` s'il travaille pour plusieurs restos.

---

## 3. Architecture fonctionnelle — apps & portails

OneClick se décline en **3 univers d'utilisation**, déployés sur **11 bundles iOS distincts** (+ 4 APK Android + portails web responsive).

### 3.1 Univers Client — apps "Win" (multi-tenants)

**5 bundles iOS** :

| Bundle | Tenant | Distribution | Spécificité |
|---|---|---|---|
| `ma.oneclick.win` | OneClick standard | TestFlight | Public — tous les ~1000 restos |
| `ma.homu.win` | HOMU | TestFlight | Members-only (resto Casablanca) |
| `ma.palmeraie.win` (My Palmeraie) | PCC | TestFlight | Members-only (country club) |
| `ma.restopro.group` | Restopro | (prêt, jamais déployé prod) | Réservé aux restos Restopro |
| (legacy) `ma.oneclick.pocket` | OneClick | déprécié | Ancienne app fusionnée dans Win |

### 3.2 Univers Restaurateur — apps "Store"

**4 bundles iOS** :

| Bundle | Tenant | Public |
|---|---|---|
| `ma.oneclick.store` | OneClick standard | Restaurateurs partenaires |
| `ma.homu.store` | HOMU | Staff HOMU exclusivement |
| `ma.palmeraie.store` (PCC Store) | PCC | Staff PCC (avec un owner par module : Padel/Spa/Golf/etc.) |
| `ma.restopro.store` | Restopro | (prêt, jamais déployé prod) |

### 3.3 Univers Admin — accès web + bundle iOS

**1 bundle iOS** + 1 portail web :

| Bundle | Tenant | Public |
|---|---|---|
| `ma.oneclick.admin` | OneClick | Admin Onesley (Command Center) |

### 3.4 Apps Group (B2B chaînes)

Pour les chaînes propriétaires de plusieurs restaurants :

| Bundle | Usage |
|---|---|
| `ma.oneclick.group` | Multi-restaurants : vue consolidée, transferts staff cross-resto |
| `ma.restopro.group` | Idem mais scopé sur tenant Restopro |

---

## 4. Modules métier — côté client final

### 4.1 Compass — exploration & catalogue

**Page** : `OneClickCompass` (anciennement page Explore)

- Carte interactive du Maroc avec restaurants géolocalisés
- Filtres multicritères : ville (10 villes), cuisine (12 catégories), gamme de prix (€/€€/€€€), tags (terrasse, vegan, etc.), favoris, "ouvert maintenant"
- Chaque restaurant affiche photo principale, note Google + reviews, distance, tags, prix
- Pull-to-refresh, lazy loading, search bar avec autocomplete

**Page détail** : `OneClickSpotlight`

- Photos restaurant, horaires complets par jour, menu, services (brunch / déjeuner / dîner)
- **Lounge points** : système qualité interne pour le scoring d'affichage
- Promotions actives sur ce restaurant
- Bouton « Réserver » + bouton « Snap2Earn » + favori
- Avis Google embarqués

**Page liste favoris** : `OneClickFavorites`

- Synchronisé en temps réel sur tous les devices du client

---

### 4.2 Réservation

**C'est le cœur métier opérationnel** d'OneClick.

#### Workflow d'état (machine à 10 états)

```
[client] soumet la résa
            │
            ▼
       ┌─demandée─┐
       │          │
   [resto refuse] │  [resto accepte]
       │          │
       ▼          ▼
    refusée   confirmée
                   │
       ┌───────────┼─────────────┐
       │           │             │
  [resto contre]   │       [client annule]
       │           │             │
       ▼           ▼             ▼
contre_proposition │         annulée
       │           │
[client accepte]   │
       │           │
       └──► confirmée
                   │
       ┌───────────┴────────────────────┐
       │              │                  │
   [resto place]   [client annule]   [no-show]
       │              │                  │
       ▼              ▼                  ▼
    placée         annulée            no_show
       │
       ▼
   terminée
       │
   [client présent]
       │
       ▼
   honorée  (++0.1 score, points crédités)
```

#### Données d'une réservation

- Date (`LocalDate`) + heure (texte "HH:MM" en heure Maroc)
- Couverts (1-20)
- Service : brunch / déjeuner / dîner
- Zone et numéro de table (assignés par le resto au moment de "placer")
- Notes du client (ex: "anniversaire de ma femme", "allergie aux fruits de mer")
- Motif de refus si refusée
- Date/heure proposée si contre-proposition (avec délai d'expiration)

#### Règles métier

- **Auto-cancel H-2** : une réservation `demandée` non confirmée par le resto **2 heures avant** l'heure prévue est automatiquement annulée (cron `expire-unanswered-reservations`)
- **Rappels** :
  - **J-1 9h heure Maroc** (cron quotidien) — push + email
  - **H-2** (cron 15 min) — push uniquement
- **Late cancellation** : annulation **< 1 h avant** l'heure → pénalité immédiate, **non contestable**
- **No-show** : marqué par le resto **entre H-1 et H+1**, fenêtre de **48h** pour contestation (cf §9)
- **Multi-invités** : invitation d'amis (table `reservation_guests`) avec relances automatiques par push
- **Anti-overlap PCC** : trigger DB qui bloque la création d'une résa si l'organisateur a déjà une réservation chevauchant le créneau

---

### 4.3 Vault — fidélité & Lounge points

**Page** : `OneClickVault` (centre de fidélité du client)

#### Mécaniques de gain

| Source | Crédit |
|---|---|
| **Snap2Earn** (cf §4.4) | montant TTC × `taux_conversion` du restaurant (typique 10 %) |
| **Réservation honorée** | bonus configurable par tenant (souvent +5 pts) |
| **Parrainage activé** | +50 pts au parrain ET +50 pts au filleul |
| **Bonus admin** | +N pts à la discrétion d'un admin Onesley (compensation, gestes commerciaux) |

#### Système FIFO

Les points sont consommés **First In First Out** : les plus anciens d'abord. Implementation : `loyalty_points` table avec `earned_at`, `expires_at`, et logique de débit ordonnée par expiration croissante (SKIP LOCKED pour la concurrence).

#### Expiration

- **1 an** par défaut, configurable par tenant
- **Notification J-30** avant expiration (« 50 pts expirent le 15 juin »)
- Cron quotidien `process-expired-points` qui débite les points expirés

#### Tiers — Élite Club (4 niveaux)

| Tier | Seuil | Avantages typiques |
|---|---|---|
| **Ruby** | 0 pts (entrée) | Accès basique, taux conversion standard |
| **Sapphire** | 1 000+ pts cumulés sur 12 mois glissants | Taux majoré, accès Spotlight |
| **Émeraude** | seuil supérieur (configurable) | Offres VIP exclusives, support prioritaire |
| **Black** | **invitation uniquement** | Brunch privé mercredi, événements DJ Élite |

Promotion automatique mensuelle ; rétrogradation si seuil non maintenu.

#### Affichage Vault

- Solde total + progression vers prochain tier (barre)
- Historique : gains / dépenses / expirations (filtres par mois)
- Vue par restaurant (« 200 pts gagnés chez Le Cabestan »)
- QR code statique du wallet (scanné par le staff lors d'une dépense)
- Liste des points qui expirent dans les 30 jours

---

### 4.4 Snap2Earn — capitalisation par OCR

**Page** : `Snap2EarnScan`

Le différenciateur le plus visible d'OneClick : pas de carte fidélité, pas de QR à afficher au moment du paiement. Le client paie normalement, **après** il photographie le ticket dans l'app et reçoit ses points.

#### Workflow

1. **Client** : ouvre l'app Win → bouton "Snap2Earn" → photo du ticket via la caméra native (Capacitor)
2. **OCR** : l'image est envoyée à `OCR.space API` (ou alternative configurable). Patterns reconnus : `NET A PAYER`, `TOTAL TTC`, `TOTAL`, montants en `MAD` ou `DH`
3. **Extraction** : montant + niveau de confiance (high/medium/low). Si low, le client peut ajuster manuellement avant validation
4. **Vérification staff** : le scan apparaît dans l'app Store du restaurateur → un employé staff (rôle `manager` ou `serveur`) approuve d'un clic
5. **Crédit** : application de la `gain_rule` du restaurant → points crédités au client → push notification
6. **Si tier franchi** : push « Félicitations, vous êtes Sapphire ! »

#### Anti-fraude

- **Doublons** : `ticket_ref` unique par restaurant. Un même ticket ne peut être scanné qu'une fois
- **Plafonds** :
  - `min_ticket_amount` (ex: 50 MAD min)
  - `max_points_per_ticket` (ex: 500 pts max)
  - `loyalty_plafond` (ex: 1000 pts/jour/client)
- **Validation staff obligatoire** : aucun crédit auto sans approbation humaine
- **Velocity checks** : module **TrustWatch** (admin) détecte les patterns suspects (ex: 10 scans en 1h, multiples comptes même téléphone)

#### Modes d'identification du client

L'app Store propose **3 façons** d'identifier le client à crédittex :

1. **Numéro de téléphone** (le client le donne au staff)
2. **QR Wallet** (client montre son QR depuis l'app Win)
3. **Wallet card** (Apple Wallet pkpass tier-based — cf §4.12)

#### OTP pour redemption

Si le client veut **dépenser** ses points (réduction immédiate sur son addition), un OTP lui est envoyé par push pour valider le débit (anti-vol de points en cas de smartphone perdu).

---

### 4.5 Circle — communauté

**Page** : `OneClickCircle`

OneClick mise sur l'effet de réseau pour la croissance virale.

#### Amis (`friendships`)

- Ajouter un ami par : **numéro de téléphone**, **code parrain `OC-XXXXXX`**, **scan QR**, **import contacts du téléphone**
- Demande symétrique (accepté / en attente / refusé / bloqué)
- **Auto-friendship parrainage** : quand un filleul s'inscrit via un code, une amitié `accepted` est créée automatiquement entre les 2

#### Groupes / Squads (`friend_groups`)

- Le client crée un "Crew" (typique 4-10 personnes) avec emoji + nom
- Permet d'inviter d'un coup tout le groupe sur une réservation
- Visibilité aux groupes Spotify-like : « Mon ami Karim sera aussi au resto X jeudi »

#### Parrainage

- Code unique par utilisateur, format `OC-XXXXXX` (max 20 chars)
- Partageable par lien direct, SMS, QR code, email
- Bonus : +50 pts parrain + +50 pts filleul **après la 1re réservation honorée du filleul**
- Cron `gift-points` mensuel : check filleuls actifs → bonus récurrent

#### Invitations sur réservation

- Au moment de réserver une table de N couverts, le client peut inviter jusqu'à **N-1 amis**
- Chaque invité reçoit push + peut **accepter / décliner / Peut-être**
- Si accepté → ajout dans `reservation_guests`
- Si N-1 réponses négatives, la résa peut être ajustée à la baisse (couverts)

---

### 4.6 Moments — promotions & offres

**Page** : `OneClickMoments`

Feed de promotions actives, conçu comme un Instagram-feed.

#### Types d'offres

| Type | Description |
|---|---|
| **Promo** | Pourcentage / montant fixe / X-1 offert |
| **Bonus** | Points multipliés (ex: « 2× pts ce week-end ») |
| **Reco** | Recommandation éditoriale (« Notre coup de cœur ») |

#### Ciblage par segment

- **Tous les clients**
- **Fidèles** (visite < 30 jours)
- **Nouveaux** (signup < 30 jours, 0 réservation)
- **Inactifs** (> 90 jours sans visite)
- **Par tier** : Ruby / Sapphire / Émeraude / Black exclusif

#### Cycle de vie

- Restaurateur crée l'offre dans son app Store → état `pending`
- Admin Forge approuve → état `active`
- Validité : date début → date fin, jours/services applicables
- Cron `expire-promotions` désactive les offres dépassées
- Tracking : `useTrackOfferView` enregistre les impressions pour l'analytique restaurateur

---

### 4.7 Élite Club

**Page** : `OneClickEliteClub`

Programme événementiel pour les utilisateurs des tiers Sapphire / Émeraude / Black.

#### Événements

- Soirées DJ, dégustations chef, tournois padel, brunchs privés mercredi (Black tier)
- Spots limités (typique 20-50)
- RSVP en 3 modes : `J'y vais` / `Peut-être` / `Pas dispo`
- FIFO : les premiers à confirmer obtiennent leur place

#### Tier-gating

- Chaque événement a un `min_tier`
- Les utilisateurs de tier inférieur voient l'événement avec overlay « Réservé aux Émeraudes »

#### Candidature au tier Black (sur invitation)

- Page `EliteApply` : photo, motivations, sponsor (Black member parraine la candidature)
- Review par admin Onesley + assistance IA `elite-ai-review` (matching profil/sponsor)

---

### 4.8 Care — assistance IA & tickets

**Page** : `OneClickCare` — chat conversationnel + historique tickets

L'IA OneClick Care est un **assistant complet**, pas juste un FAQ bot. Elle peut **agir** sur le compte du client.

#### Capacités de l'IA

L'agent (Groq Llama 3.3 70B + prompt engineering) reconnaît des intentions et exécute :

- **`[CREATE_RESERVATION]`** : « Réserve-moi chez Le Cabestan jeudi 20h pour 4 » → parse + confirm + POST API
- **`[CREATE_REFERRAL]`** : « Parraine mon amie Sara au 06.55.55.55 » → crée le filleul potentiel
- **`[CREATE_SQUAD]`** : « Crée le squad "Foodies Casa" avec emoji 🍔 »
- **`[ADD_FRIEND]`** : « Ajoute l'ami avec le code OC-A3F5B2 »
- **`[OPEN_TICKET]`** : escalade vers support humain si problème dépasse FAQ
- **`[TRIAGE_N0/N1/N2/N3]`** : niveau de gravité

#### Tickets support

- Catégories : technique, réservation, paiement, autre
- Routing : `oneclick` → admin Onesley | `pcc/homu` → tenant_admin du tenant
- Conversation chat (`chat_messages`) entre client et support
- Statuts : ouvert / en cours / résolu / clos
- Escalade : auto → support → manager → légal (TrustWatch)

#### Quotas

- **100 messages / mois gratuit** par client
- Au-delà : message « Limite atteinte, réessayez le 1er du mois prochain »
- Reset automatique J-1 du mois suivant

---

### 4.9 Bookable resources — pattern PCC

**Pages** : `PccPadel`, `PccSpa`, `PccGolf`, `PccCoiffeur`, `PccPalmGym`, `PccLeResto`, `PccKamoun`, `PccMyBookings`

Au-delà des restaurants, OneClick gère des **ressources réservables** via un pattern générique réutilisable. C'est ce qui permet à PCC (Palmeraie Country Club) d'utiliser OneClick pour ses 7 services internes.

#### 7 modules PCC en production

| Module | Ressources | Slot | Capacité | Fidélité |
|---|---|---|---|---|
| **Padel** | 6 courts | 90 min | max 4 joueurs (organizer + 3 invités) | Punch card 10/1 |
| **Spa** | Hammam / Massage / Argan (3 prestations) | 60 ou 90 min | 1 personne | Punch card 10/1 |
| **Golf** | 9 trous + Palm Golf 18 trous | 60 min | 1-4 | Punch card 10/1 |
| **Coiffeur** | Hassan + Issam (2 coiffeurs) | 30 min | 1 | Punch card V2 |
| **Palm Gym** | Coachs 1-5 | 60 min | 1 | Punch card V2 |
| **Le Resto** | Tables resto | 120-150 min | 1-N | Points OneClick |
| **Kamoun** | Tables resto | 120-150 min | 1-N | Points OneClick |
| **Boutique** | (pas de booking, juste Snap2Earn) | — | — | Points OneClick |
| **Séminaires** | Form B2B avec workflow devis manuel | — | — | — |
| **Événements** | RSVP events | — | — | — |

#### Données d'une bookable resource

- Type (`bookable_resource_type` enum : padel_court, spa_treatment, golf_course, hairdresser, gym_coach, restaurant_table)
- Capacité par slot
- Pricing dynamique (week-end vs semaine, member vs visiteur)
- Horaires d'ouverture (`opening_hours` JSONB)
- Punch card associée si applicable

#### Workflow de réservation bookable

```
demandée (organizer initie)
    │
[staff confirme]   [staff refuse]
    │                   │
    ▼                   ▼
confirmée            annulée
    │
[organizer présent]
    │
    ▼
honorée  ──► incrémente punch_card si activity dans (padel, spa, golf, séminaire)
```

À 10/10 punch card → 1 séance offerte (le staff marque comme utilisée à la prochaine résa).

---

### 4.10 Member Circle — feed social privé

**Pages** : `PccCircle` (feed), `PccCircleCompose` (créer un post)

Introduit en mai 2026 pour PCC. **Feed social privé entre membres** d'un même tenant — uniquement.

#### Fonctionnalités

- Posts **photo + texte** (max 500 caractères)
- Bucket Storage `member-post-photos` (public read pour les membres du tenant)
- **Modération** : tout post passe en `pending` → un owner du club (Adil pour PCC) approuve / rejette
- **Likes idempotents** par membre (1 like max par post par membre)
- **Story views Instagram-style** (mai 2026) : quand un membre voit un post, c'est tracé pour l'auteur (« 12 personnes ont vu votre post »)

#### Pourquoi feed privé ?

PCC est un club premium fermé. Le feed renforce le sentiment d'appartenance, génère du contenu spontané, et désamorce le besoin que les membres aillent sur Instagram (où le club n'est plus en contrôle de la modération).

---

### 4.11 Ma Famille & Donner mon avis

Deux features **spécifiques à PCC** introduites en avril 2026.

#### Ma Famille (`pcc_family_members`)

- Un membre principal peut ajouter **jusqu'à 10 proches** : conjoint, enfants, parents
- Identifiés par : email, téléphone, ou code de parrainage
- Visibilité : le membre principal voit les points fidélité cumulés de toute sa famille
- Booking joint : un parent peut réserver pour son enfant sans créer un compte enfant
- Conformité RGPD : tous les proches doivent accepter explicitement (lien magique par email)

#### Donner mon avis (`pcc_feedbacks`)

- Page dédiée dans l'app My Palmeraie
- Le membre soumet un feedback (texte libre + 5 catégories : padel, spa, restaurant, accueil, autre)
- **5 owners PCC** reçoivent une notification + email branded (Resend)
- **Adil** (manager principal PCC) peut répondre dans un thread → notification au membre
- Conversation à 2 sens (`pcc_feedback_replies`)
- Statut : ouvert / répondu / clos

---

### 4.12 Wallet pkpass — carte de membre Apple Wallet

**Feature spécifique tenants whitelabel premium (HOMU, PCC).**

Quand un membre s'inscrit, il reçoit automatiquement une **carte de membre virtuelle Apple Wallet** (.pkpass) avec :

- Logo + couleur du tenant
- Nom du membre
- Tier (Ruby / Sapphire / Émeraude / Black)
- QR code lié à l'identité (lu par le staff via Snap2Earn → mode "wallet card")

#### 4 tiers PCC avec couleurs signature

| Tier | Couleur de la carte | Public |
|---|---|---|
| **Ruby** | Cuir bordeaux | Default — entrée club |
| **Sapphire** | Vert palmier | Membres réguliers |
| **Émeraude** | Émeraude | Membres VIP |
| **Black** | Noir + or | Exclusif sur invitation |

La carte se met à jour **automatiquement** quand le membre change de tier (push pkpass).

---

## 5. Modules métier — côté restaurateur

L'app **Store** + portail web Pro Desk regroupent tous les outils restaurateur.

### 5.1 CockpitStaff — dashboard quotidien

**Page** : `CockpitStaff`

- Réservations du jour (par heure, par zone, par statut)
- CA scanné cumulé du jour + comparaison J-1, J-7
- Top staff (qui a scanné le plus de tickets, conversion réservations)
- Conversion réservation → présentation
- Real-time via Supabase Realtime (les nouvelles résa apparaissent live)

### 5.2 Calendrier des réservations

**Page** : `CalendrierReservations`

- Vue jour / semaine / mois
- Drag & drop pour réassigner table
- Boutons d'action : confirmer / refuser / contre-proposer / placer
- Filtres : statut, zone, taille de groupe

### 5.3 Snap2Earn (côté staff)

**Page** : `Snap2EarnScan`

- Validation des scans des clients (queue à approuver)
- 3 modes d'identification (téléphone / QR / wallet card)
- Saisie manuelle si OCR échoue ou si le client n'a pas l'app

### 5.4 Gestion équipe (`GestionEquipe`)

- Inviter un nouveau staff par email (lien magique Resend)
- Affecter un rôle staff (5 rôles, cf §2)
- Activer / désactiver un compte
- Transférer entre restaurants si propriétaire de plusieurs (app Group)

### 5.5 Zones et tables (`ZonesEtTables`)

- Configurer les zones de service (terrasse, salon principal, salle privée…)
- Tables avec nombre de places, état (active / désactivée)
- Plan visuel optionnel

### 5.6 PulsePro — métriques détaillées

**Page** : `PulsePro`

- CA scanné mensuel + courbe annuelle
- Top 10 clients (anonymisés "Karim B.") avec fréquence + dépense moyenne
- Distribution des points distribués par segment client
- Taux de no-show
- Heatmap des heures les plus chargées

### 5.7 Promotions (vue restaurateur)

- Créer une offre → soumission admin
- Sélectionner segment cible (tous, fidèles, nouveaux, inactifs, par tier)
- Date de début et fin, jours/services applicables
- Statistiques d'engagement (impressions, conversions)

### 5.8 Inscrire un membre (HOMU & PCC)

Workflow spécifique aux tenants members-only, où il n'y a **pas de signup public** :

1. Staff Store → menu Plus → "Inscrire un membre"
2. Recherche par email / téléphone → si trouvé, lien existant ; sinon création
3. Welcome points (configurable, ex: 100 pts d'accueil PCC)
4. Email branded Resend (template HOMU ou PCC) avec lien magique
5. Membre clique → arrive sur l'app du tenant (HOMU Win ou My Palmeraie)

---

## 6. Modules métier — côté admin (Command Center)

L'admin OneClick Onesley dispose de **8 modules** distincts dans le Command Center.

### 6.1 Pulse — monitoring & KPIs

- Santé système (uptime, latence, erreurs)
- KPIs OneClick globaux : clients actifs, CA mensuel scanné, top restaurants
- Cohort analysis (acquisition, rétention, churn)
- Anti-fraude KPIs (anomalies détectées, comptes suspendus)
- Support KPIs (volume tickets, temps de résolution)
- Sentry intégration (alertes 5xx)

### 6.2 Forge — pilotage loyalty & marketing

- Pilotage loyalty (règles de gain par restaurant, plafonds)
- Audit redemption (qui a dépensé combien et où)
- Restitutions restaurants (refund de points si resto fermé / suspendu)
- Promotions OneClick (validation des offres soumises par les restos)
- Push notifications campaigns (segmentation, scheduling)
- No-show disputes (review des contestations Phase 2)
- Élite Club admin (validation candidatures Black, gestion événements)

### 6.3 Shield — sécurité & rôles

- Gestion des `custom_roles` et permissions
- Audit logs (qui a fait quoi quand)
- Politiques de session (timeouts, IP whitelisting)
- Configuration 2FA pour admins

### 6.4 Galaxy — governance & contrats

- Templates de contrats partenaires
- Renouvellements / expirations
- Modération médias (photos restaurants flagged)
- Cycle de vie restaurant (draft → active → suspended → closed)
- Pilotage Explore (featured restaurants, algo de tri)

### 6.5 TrustWatch — fraude & escalade

- Dashboard anomalies (velocity checks, comptes suspects)
- Flux d'alertes en temps réel
- File des tickets escaladés (no-show contestés Phase 2, chargebacks)
- Workflow d'escalade : auto → support → manager → légal

### 6.6 OneClick HI Pro — facturation Hospitality Intelligence

- Brouillons mensuels par restaurant
- Calcul automatique : commission OneClick + wallet admin + TVA
- Envoi PDF Resend
- Tracking statuts : brouillon → validée → envoyée → payée → en retard → rappel envoyé
- Cron `notify-expiring-contracts` (J-30 avant expiration)

### 6.7 Élite admin

- Validation des candidatures Black tier
- Création / édition événements VIP
- Gestion des règles de tier (seuils, jours d'inactivité)

### 6.8 Super Admin — gestion tenants

- CRUD tenants whitelabel (HOMU, PCC, Restopro)
- Configuration contracts par tenant
- Tenant-admin shell (impersonation pour debug)
- Feature toggles globaux par tenant

---

## 7. Whitelabel — système OneLabel multi-tenant

OneLabel est le **différenciateur stratégique B2B** d'OneClick : la même infrastructure technique sert plusieurs marques distinctes, chacune avec son branding, son public, ses features activées/désactivées.

### 7.1 Tenants en production (mai 2026)

| Slug | Nom commercial | Type | Restos / Modules | Public | Statut |
|---|---|---|---|---|---|
| `oneclick` | OneClick (par défaut) | Grand public | 1 002 restos publics | Ouvert | Prod |
| `homu` | HOMU | Resto premium Casablanca | 1 resto | Members-only | Prod (avr 2026) |
| `palmeraie` | Palmeraie Country Club | Country club Marrakech | 7 modules + 2 restos | Members-only | Prod (avr 2026 — démo réussie 28/04) |
| `lagrillardiere` | La Grillardière | Chaîne grillades | 2 villes | Public | Prod |
| `restopro` | Restopro | Groupe restos | (apps prêtes, jamais lancé) | Public | En attente activation |

### 7.2 Modèle d'isolation

#### Au niveau des données

- Colonnes `tenant_id` (UUID FK) sur les tables critiques : `profiles`, `restaurants`, `bookable_resources`, `loyalty_punch_cards`, `pcc_family_members`, `pcc_feedbacks`, `tenant_events`, `tenant_announcements`
- **OneClick standard** : `tenant_id = NULL` (legacy, public)
- **Tenants whitelabel** : `tenant_id = <UUID fixe>` (fixé en migration : `00000000-...-001` pour OneClick, `0cccc000-...-001` pour PCC, etc.)
- **Row Level Security (RLS)** active sur toutes les tables sensibles

#### Au niveau du frontend

- 1 codebase React / Capacitor commune
- `src/whitelabel/{slug}/config.json` définit branding + features
- `src/whitelabel/{slug}/LoginHeader.tsx` (composant custom par tenant)
- Variable d'env `VITE_WHITELABEL=<slug>` au build → pickup auto
- Build pipeline : `npm run build` (web monolithe) + `cap sync` par tenant + bundle iOS dédié

### 7.3 Branding configurable par tenant

- Logo (SVG / PNG)
- Couleurs primaires (HSL)
- Splash screen variant (curseur 13h ou 16h)
- Police principale
- Login header custom (peut afficher diaporama photos, dégradé)
- Email templates Resend (logo + couleurs)
- Wallet pkpass (palette tier-based — cf §4.12)

### 7.4 Feature flags par tenant (`tenants.features` JSONB)

Chaque tenant active/désactive des modules. Exemple PCC :

```json
{
  "ai_chat": true,
  "referral": true,
  "elite_club": false,
  "moments": false,
  "snap2earn": true,
  "bookable_resources": true,
  "member_circle": true,
  "ma_famille": true,
  "donner_mon_avis": true,
  "has_announcements": true
}
```

Restopro a un profil minimal (`elite_club: false`, `member_circle: false`, etc.) — pour matcher le besoin d'une chaîne classique sans fanfare.

### 7.5 Bénéfices business

- Onboarding rapide d'un nouveau client B2B premium : ~4 semaines pour PCC (1 sénior + 1 N+1)
- Économie d'échelle : 1 codebase, N clients
- Personnalisation forte sans fork du code
- Modèle revenue prédictif (setup fee + abonnement mensuel — cf §11)

---

## 8. Workflows critiques

### 8.1 Inscription d'un nouveau client (OneClick standard)

1. Téléchargement de l'app Win
2. Saisie : téléphone + email + prénom/nom + ville (10 villes)
3. Vérification SMS (à venir, provider Twilio / OVH) + email
4. Optionnel : code de parrainage `OC-XXXXXX`
5. Compte créé → solde 0 pts → tier Ruby
6. Welcome screen : recommandations restaurants par ville
7. **Si parrainage** : `auto_friendship` créée parrain ↔ filleul

### 8.2 Inscription d'un membre HOMU / PCC

**Pas de signup public**. Workflow inversé :

1. Le membre demande son inscription au club (offline ou via formulaire B2B)
2. Le staff (HOMU manager / PCC owner) ouvre l'app Store
3. Menu Plus → "Inscrire un membre" → recherche / création
4. Welcome points crédités (configurable par tenant)
5. Email branded Resend (template tenant) avec lien magique
6. Le membre clique → arrive directement sur l'app du tenant
7. Wallet pkpass généré + envoyé au téléphone

### 8.3 Première réservation

1. Client choisit un restaurant dans Compass / Spotlight
2. Sélectionne date + heure + couverts + service
3. Optionnel : invite des amis (depuis Circle)
4. Soumet → `demandée`
5. **Push au restaurateur** "Nouvelle demande de réservation"
6. Restaurateur dans son Store : accepte / refuse / contre-propose
7. Si accept → `confirmée` → push au client "Confirmé !"
8. Rappels J-1 9h Maroc + H-2
9. Client se présente → resto place → place → terminée → honorée
10. **Bonus** : +0.1 reliability_score + bonus points si configurable

### 8.4 Cycle Snap2Earn complet

1. Client paie l'addition normalement (cash / CB / chèque resto)
2. Ouvre l'app Win → "Snap2Earn"
3. Photo du ticket → upload Storage
4. OCR.space extrait montant TTC + confidence
5. Client confirme / ajuste
6. Soumission → status `pending`
7. Push au staff Store : "Nouveau ticket à valider"
8. Staff approuve d'un clic
9. Application gain_rule : `points = montant × taux`
10. Crédit `loyalty_points` avec `expires_at = now() + 1 year`
11. Push au client : "+200 pts crédités"
12. Si seuil tier franchi : push "Félicitations, vous êtes Sapphire !"
13. Si carte pkpass active : update remote (le tier change visuellement)

### 8.5 Onboarding d'un nouveau restaurant partenaire

1. Restaurateur fait demande commerciale (mail / formulaire / appel)
2. Admin Onesley (Galaxy) crée son compte staff + sa fiche resto
3. Restaurateur télécharge l'app Store, login
4. Onboarding wizard : photos, horaires, menus, services, zones, tables
5. Configuration des `gain_rules` (taux de conversion DH → points)
6. Signature du contrat partenaire (`partner_contracts`) avec admin
7. Publication → restaurant visible dans Compass
8. Audit régulier des KPIs (CA scanné, taux no-show)

### 8.6 Workflow de facturation mensuelle

Cron `generate-invoices` (1er du mois) :

1. Pour chaque restaurant actif, agréger les `scanned_tickets` du mois précédent
2. Calculer :
   - Commission OneClick = CA × `oneclick_commission_rate` (défaut 3 %)
   - Wallet admin = CA × `wallet_admin_rate` (défaut 2 %)
   - TVA 20 % sur (Commission + Wallet admin)
3. Générer brouillon dans `oneclick_hi_invoices`
4. Notification admin : "X invoices à valider"
5. Admin valide → PDF généré + email Resend au restaurateur
6. Statut → `envoyée`
7. Délai de paiement (typique le 5 du mois suivant)
8. Si non payé : `en_retard` → cron `send-invoice-reminders` (J-15, J-7, J+3 après due_date)
9. Si toujours non payé J+30 : escalade Onesley

---

## 9. Notation, fiabilité & contestation

OneClick maintient un **score de fiabilité** par client pour permettre aux restaurateurs de filtrer les clients à risque.

### 9.1 Reliability score (`profiles.reliability_score`)

- Échelle 0.0 – 5.0
- Démarre à **5.0** (présomption de fiabilité)
- **+0.1** à chaque réservation `honorée`
- **−0.5** à chaque `no_show` non contesté
- **−0.5** à chaque `late_cancellation` (annulation < 1 h)

### 9.2 Visibilité différée côté client

Le client **ne voit pas immédiatement** son nouveau score. Il y a une **fenêtre de 48 h** :

- Tables : `client_ratings` (score brut, visible admin/resto) + `client_visible_ratings` (score affiché client, mis à jour J+2)
- Raison : permet la contestation sans angoisse — le client ne voit pas son score dégradé tant qu'il a la possibilité de contester

### 9.3 Workflow de contestation no-show (3 phases)

```
Heure résa passée, client absent
            │
[Phase 1 : 0h – 1h après l'heure prévue]
   Le restaurateur peut marquer no_show
   ou ne rien faire (non-marquage = pas de pénalité)
            │
            ▼
État `no_show`
   Push au client : "Vous avez été marqué absent. Contester ?"
            │
[Phase 2 : 1h – 48h]
   Le client peut contester via app Win (motif obligatoire + photo si 2e contestation)
   Ouverture d'un dispute → review admin TrustWatch
            │
   Admin tranche :
   - Client a raison → reliability_score restauré + warning au resto
   - Resto a raison → score appliqué irréversiblement
            │
[Phase 3 : 48h+]
   Cron `apply-no-show-penalties` :
   - Score définitivement dégradé sur client_visible_ratings
   - Plus de contestation possible
```

### 9.4 Notation client → restaurant

- Optionnel : après une réservation honorée, le client peut noter le restaurant (1-5 étoiles + commentaire)
- Visible côté admin Galaxy uniquement (modération avant publication éventuelle)

### 9.5 Notation restaurant → client

- Le staff peut noter ses clients post-visite : sympathique / problématique / VIP / no-show
- Visible **uniquement** au sein de son restaurant
- Sert à anticiper les conflits, identifier les fidèles, alerter les staff

### 9.6 Late cancellation

- Annulation **< 1 h avant l'heure de réservation** → flag `late_cancellation = true`
- Pénalité immédiate : −0.5 reliability_score
- **Non contestable** (à dessein — éviter le contournement du no-show)

---

## 10. Notifications & communication

OneClick utilise 4 canaux complémentaires.

### 10.1 Canaux

| Canal | Provider | Usage |
|---|---|---|
| **Push notification** | FCM (Firebase Cloud Messaging) HTTP v1 | Confirmations, points, promos, no-show, événements |
| **Email** | Resend (HTTP API) | Bienvenue, factures, alertes admin, contestations, feedbacks PCC |
| **In-app** | Storage DB local | Centre de notifs accessible dans l'app |
| **SMS** | (à déterminer — Twilio / OVH) | Vérification téléphone, OTP critiques |

### 10.2 Edge functions notifications (Deno)

Plus de **15 fonctions de notification** déployées :

- `send-reservation-reminders` — cron J-1 9h + H-2
- `send-promo-push` — campagne marketing ciblée
- `send-friend-request-push` — demande d'ami
- `send-pcc-reservation-reminders` — équivalent PCC pour bookables
- `send-pcc-seminar-status-update` — événement PCC
- `send-pcc-feedback` — soumission feedback PCC
- `send-pcc-feedback-reply` — réponse staff
- `send-pcc-enrollment-invite` — invitation membre PCC
- `send-homu-enrollment-invite` — invitation membre HOMU
- `send-onboarding-request` — onboarding restaurant
- `send-onboarding-decision` — réponse admin onboarding

### 10.3 Préférences utilisateur

- Le client peut désactiver granulairement chaque catégorie de notification (réservations, points, promos, communauté, élite)
- Catégories **critiques non désactivables** : réservation refusée, no-show notifié, support escaladé

### 10.4 Notifications staff (`staff_notification_preferences`)

Introduit en avril 2026 — chaque staff peut :
- Activer / désactiver les notifs pour son rôle
- Régler heures de silence (ex: pas de push entre 23h et 7h)
- Choisir push ou email seulement

---

## 11. Modèle économique

### 11.1 Côté client final

**Gratuit, à vie.** Aucun frais, aucun abonnement. Le service vit sur les revenus B2B.

### 11.2 Côté restaurateur OneClick standard — 3 taux

Contrat partenaire (`partner_contracts`) avec **3 taux** distincts :

| Composante | Taux par défaut | Bénéficiaire |
|---|---|---|
| `oneclick_commission_rate` | **3 %** | OneClick (revenue) |
| `wallet_admin_rate` | **2 %** | Pool wallet redistribuable (promo, gestes commerciaux) |
| `client_commission_rate` | **10 %** | Clients (en points fidélité — 1 pt ≈ 1 MAD valeur perçue) |
| TVA 20 % | sur (commission + wallet) | État marocain |

#### Exemple concret

Restaurant qui scanne 100 000 MAD de CA en mai :

- Commission OneClick HT : 100 000 × 3 % = **3 000 MAD**
- Wallet admin HT : 100 000 × 2 % = 2 000 MAD
- TVA 20 % sur (3 000 + 2 000) = 1 000 MAD
- **Facture totale TTC** : 3 000 + 2 000 + 1 000 = **6 000 MAD**

Côté client : 100 000 × 10 % = **10 000 pts** distribués au pool clients (FIFO).

### 11.3 Plafonds & exceptions

- `plafond_commission_mensuel` : optionnel, plafonne la commission mensuelle (ex: 5 000 MAD/mois max)
- Tarifs négociés au cas par cas pour les chaînes (La Grillardière a un taux différent)

### 11.4 Cycle de facturation

- **Mensuel** : 1er du mois, cron génère les brouillons
- **Validation** : admin Onesley valide → PDF généré + email Resend
- **Délai paiement** : typique le 5 du mois suivant
- **Réconciliation** : auditable (tickets scannés = ligne facture)

### 11.5 OneLabel — modèle B2B premium

Pour les tenants whitelabel (HOMU, PCC, Restopro), modèle **distinct** :

- **Setup fee** initial (ex: 30 000 – 100 000 MAD selon complexité)
- **Abonnement mensuel** fixe (ex: 5 000 – 20 000 MAD/mois)
- **Maintenance + support** inclus
- **Custom developments** facturés au prorata

### 11.6 Pistes futures (non en prod)

- Push sponsorisés
- Placement premium dans Compass (« featured »)
- Événements Élite sponsorisés (marques food)
- API publique payante
- Analytics premium (benchmarks marché)

---

## 12. Conformité & confiance

### 12.1 Légal Maroc

- **CNDP** (Commission Nationale de protection des Données Personnelles) — déclaration en cours
- **Loi 09-08** sur la protection des données — conformité
- **TVA 20 %** appliquée
- Mentions ICE / IF / RC obligatoires sur les contrats partenaires

### 12.2 RGPD-friendly

- Hébergement Supabase **Central Europe Zurich** — données européennes possibles
- Politique de confidentialité accessible dans l'app
- CGU acceptées au signup
- Export des données utilisateur (RGPD article 15)
- Suppression du compte avec délai 30 j pour annuler (article 17)

### 12.3 Sécurité

- JWT signés HS256 (Supabase Auth) — migration vers RS256 + service auth maison prévue
- PII (Personally Identifiable Information) **non exposée** côté staff : un manager voit "Karim B." pas "Karim Bennani 06.55.55.55.55"
- **Audit log** complet (`admin_audit_log`) pour toute action sensible
- Sentry monitoring (région EU) — replays anonymisés en cas de crash
- Backups quotidiens, conservation 30 j

### 12.4 Politique anti-fraude

- TrustWatch détecte velocity, comptes liés (même téléphone, même IP), patterns suspects
- Velocity checks Snap2Earn (max scans/heure, max scans/jour)
- 2FA admin obligatoire (à activer)
- Suspension automatique d'un compte si > 3 alertes en 24h

---

## 13. Données en production

### 13.1 OneClick standard

- **17 222 comptes auth.users** : 1 admin, 7 groupes, ~134 restaurateurs (owners), ~15 000 staff
- **1 002 restaurants** sur 10 villes (~100 par ville)
- **3 552 réservations** historisées
- **1 497 transactions de points** (loyalty_points)
- **284 invitations** envoyées (`reservation_guests`)
- **22 contrats** signés
- **84 offres** marketing (toutes périodes confondues)

### 13.2 PCC (Palmeraie Country Club)

- **7 restaurants** PCC (5 modules bookable + 2 restos : Le Resto + Kamoun)
- **3 membres test** + 7 owners (1 par module)
- **48 resource_bookings** (mix de statuts)
- **14 réservations restos** futures
- **675 pts fidélité** cumulés (15 tickets Snap2Earn)
- **8 événements** futurs (Tournoi padel, Brunch, soirée DJ)
- **5 promotions** actives
- **7 punch cards** (membre 1 a Golf 10/10 — récompense due)

### 13.3 HOMU

- **1 restaurant** Casablanca
- **1 owner** + 1 manager
- **1 membre** test

### 13.4 Apps & déploiement

- **3 prod TestFlight** : OneClick Win/Store/Admin (beta.16, avril 2026)
- **2 prod TestFlight** : HOMU Win/Store (beta.27, 23/04)
- **2 prod TestFlight** : My Palmeraie / PCC Store (beta.31 / beta.3, 28/04 démo réussie)
- **3 ready non déployés** : Restopro Win/Store/Group

---

## 14. Glossaire

| Terme | Définition |
|---|---|
| **Adil** | Manager principal PCC, réceptionne les feedbacks Donner-mon-avis |
| **Auto-cancel** | Annulation automatique d'une réservation `demandée` non confirmée 2 h avant |
| **App ID** | Identifiant Apple/Google d'une app (ex: `ma.oneclick.win`) |
| **BestPro** | (legacy) ancien nom envisagé pour un tenant whitelabel — remplacé par Restopro |
| **Black** | Tier le plus haut de l'Élite Club, sur invitation uniquement |
| **Bookable resource** | Ressource réservable (court padel, soin spa, créneau golf…) |
| **Care** | App d'assistance IA + tickets support |
| **Circle** | Module communauté (amis, squads, parrainage) |
| **Cockpit** | Tableau de bord (CockpitStaff côté resto, CockpitHI côté admin) |
| **Command Center** | Interface admin Onesley (8 modules) |
| **Compass** | Module exploration / découverte des restaurants |
| **Crew** | Synonyme de Squad — groupe d'amis nommé par l'utilisateur |
| **CR (Custom Role)** | Rôle staff personnalisé au-delà des 5 standards |
| **Émeraude** | Tier 3 de l'Élite Club |
| **Élite Club** | Programme événementiel par tier |
| **FIFO** | First In First Out — ordre de consommation des points |
| **Forge** | Module admin pilotage loyalty + marketing |
| **Galaxy** | Module admin governance + contrats |
| **Gain rule** | Règle de conversion DH → points par restaurant |
| **HI (Hospitality Intelligence)** | Module admin facturation Pro Desk |
| **HOMU** | Tenant whitelabel — resto premium Casablanca, members-only |
| **Honorée** | Statut final positif d'une réservation (client venu) |
| **Lounge** | Synonyme du programme de fidélité OneClick (points + tiers) |
| **Loyalty plafond** | Limite de points gagnables par jour / par session |
| **Ma Famille** | Feature PCC : ajouter jusqu'à 10 proches |
| **Member Circle** | Feed social privé entre membres d'un tenant (PCC mai 2026) |
| **Members-only** | Tenants sans signup public (HOMU, PCC) |
| **Moments** | Module promotions / offres côté client |
| **No-show** | Client absent à sa réservation confirmée |
| **OCR** | Optical Character Recognition — extraction du montant du ticket |
| **OC-XXXXXX** | Format du code de parrainage |
| **OneLabel** | Nom commercial du système whitelabel |
| **Onesley** | Société éditrice d'OneClick |
| **Palm Gym** | Salle de sport du Palmeraie Country Club |
| **Parrainage** | Système de bonus mutuel parrain/filleul (50 pts) |
| **PCC** | Palmeraie Country Club — tenant whitelabel à Marrakech |
| **pkpass** | Format de carte Apple Wallet |
| **Pocket** | (legacy) ancien nom interne du portail client |
| **Pro Desk** | Portail web restaurateur (équivalent web de l'app Store) |
| **Pulse** | Module admin monitoring & KPIs |
| **PulsePro** | Module restaurateur — métriques détaillées |
| **Punch card** | Carte virtuelle "10 séances = 1 offerte" (PCC modules) |
| **Reliability score** | Note de fiabilité du client (0-5), influence acceptation des résa |
| **Resa** | Abréviation de "réservation" |
| **Restopro** | Tenant whitelabel — chaîne restos (apps prêtes) |
| **Ruby** | Tier d'entrée de l'Élite Club (défaut) |
| **Sapphire** | Tier 2 de l'Élite Club (à 1 000+ pts) |
| **Shield** | Module admin sécurité & rôles |
| **Snap2Earn** | Mécanique de fidélité par scan de ticket OCR |
| **Spotlight** | Page détail d'un restaurant côté client |
| **Squad** | Groupe d'amis nommé pour invitations groupées |
| **Staff role** | Rôle d'un employé dans un restaurant (5 valeurs) |
| **Story view** | Vue Instagram-like sur les posts Member Circle (mai 2026) |
| **Super Admin** | Module admin gestion des tenants whitelabel |
| **Tenant** | Marque whitelabel autonome partageant l'infrastructure |
| **Tenant_admin** | Rôle d'admin scopé sur un tenant |
| **TestFlight** | Plateforme Apple beta distribution iOS |
| **Tier** | Niveau de fidélité Élite Club (Ruby → Sapphire → Émeraude → Black) |
| **TrustWatch** | Module admin fraude & escalade |
| **Vault** | Module client de fidélité (points + historique + tier) |
| **Velocity check** | Détection de patterns suspects (scans rapides multiples) |
| **Whitelabel** | Adaptation du produit sous une marque tierce |
| **Win** | Nom commercial de l'app client (ex-Pocket) |

---

## 15. Annexe — roadmap & non-prod

### 15.1 Pas encore en production

- ❌ **Service auth maison** avec login + refresh token (en cours via le backend Spring — Phase 11+)
- ❌ **Paiement intégré** (Stripe / CMI Maroc) — le client paie directement le restaurant aujourd'hui
- ❌ **Internationalisation complète** (anglais + arabe) — i18n V1 en cours (sessions 47-48)
- ❌ **App Group native fully optimisée UX** — fonctionnel mais perfectible
- ❌ **SMS verification** (Twilio / OVH) — actuellement seulement email
- ❌ **2FA admin obligatoire** — disponible mais non enforcé
- ❌ **Punch cards V2 pour Coiffeur + Palm Gym** — en cours
- ❌ **Story views Instagram-style sur Member Circle** — mai 2026, en cours

### 15.2 Migration backend en cours

OneClick migre actuellement de **Supabase** (BaaS) vers **un backend Spring Boot 4 self-hosted** :

- Phase 1-2 : Postgres self-hosted + Spring squelette (terminé)
- Phase 3 : 7 entités pilotes (terminé)
- Phase 4 : 580 fichiers scaffoldés pour 86 tables (terminé)
- Phase 5 : ControllerAdvice + OAuth2 + permissions + events (terminé)
- Phase 6 : audit + validators + permissions fines + recherche dynamique (terminé)
- **Phase 11 (à venir)** : porter les 53 Edge Functions Supabase vers Spring services

Objectif : autonomie technique totale (pas de vendor lock-in Supabase) + service auth maison + refresh token natif.

### 15.3 Roadmap business proche

- Ouverture progressive de Restopro (apps prêtes, attente activation client)
- Onboarding 2-3 nouveaux country clubs sur le pattern PCC
- Internationalisation arabe + anglais (i18n V1 → V2)
- API publique pour intégrations tierces (POS restaurant, CRM)
- Marketplace d'offres (au-delà des promos resto, expérience cross-categories)

---

*Document maintenu par l'équipe OneClick. Version v2.0 du 8 mai 2026, basée sur le code en production + 30+ docs internes.*
*Pour questions ou mises à jour : contact@onesley.com.*
