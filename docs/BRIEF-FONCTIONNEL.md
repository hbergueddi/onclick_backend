# OneClick — Brief fonctionnel

> Description fonctionnelle complète du projet, niveau spécifications métier.
> Public visé : développeurs / architectes qui rejoignent l'équipe et ont besoin de comprendre le **quoi** et le **pourquoi** avant le **comment**.
>
> Date : 7 mai 2026 · Version : v1.0

---

## Table des matières

1. [Vision & positionnement](#1-vision--positionnement)
2. [Acteurs et personas](#2-acteurs-et-personas)
3. [Architecture fonctionnelle — 3 portails](#3-architecture-fonctionnelle--3-portails)
4. [Modules métier](#4-modules-métier)
   - 4.1 [Catalogue & exploration](#41-catalogue--exploration)
   - 4.2 [Réservation](#42-réservation)
   - 4.3 [Fidélité — Snap2Earn](#43-fidélité--snap2earn)
   - 4.4 [Communauté](#44-communauté)
   - 4.5 [Marketing & promotions](#45-marketing--promotions)
   - 4.6 [Notation & fiabilité](#46-notation--fiabilité)
   - 4.7 [Support & litiges](#47-support--litiges)
   - 4.8 [Country club (PCC) & ressources réservables](#48-country-club-pcc--ressources-réservables)
5. [Multi-tenant & whitelabel](#5-multi-tenant--whitelabel)
6. [Workflows critiques](#6-workflows-critiques)
7. [Notifications & communication](#7-notifications--communication)
8. [Modèle économique](#8-modèle-économique)
9. [Conformité & confiance](#9-conformité--confiance)
10. [Glossaire](#10-glossaire)

---

## 1. Vision & positionnement

**OneClick** est une plateforme SaaS marocaine de **fidélisation, réservation et gestion** pour restaurants. Anciennement nommée *M3AK*. Le projet vit en production depuis fin 2025, opéré par la société **Onesley**.

### Promesse côté client final
"En un clic, je trouve un restaurant proche, je réserve, je gagne des points en payant l'addition, et je profite de réductions sur ma prochaine visite."

### Promesse côté restaurateur
"Je remplis mes salles, je fidélise mes clients sans imprimer de cartes, je suis ma performance, et je laisse OneClick gérer la relation client (rappels, notations, réservations)."

### Différenciateurs
- **Mobile-first** : 4 apps natives iOS + Android distribuées via App Store / TestFlight
- **Whitelabel multi-tenant** : un même produit décliné sous différentes marques (cf §5)
- **Snap2Earn** : la fidélité se déclenche au scan d'un ticket physique, pas via une carte dédiée
- **IA intégrée** : assistant chat, recommandations, recherche en langage naturel
- **Communauté** : amis, parrainage, groupes pour réserver à plusieurs

### Échelle actuelle (mai 2026)
- ~1 000 restaurants partenaires actifs (Maroc — 10 villes)
- ~17 000 utilisateurs (clients + staff)
- 3 500 réservations historisées
- 4 tenants en production : OneClick standard, HOMU, Palmeraie Country Club (PCC), Restopro

---

## 2. Acteurs et personas

### 2.1 Client final (rôle `client`)
- Particulier marocain, équipé d'un smartphone iOS ou Android
- Cherche à découvrir des restaurants, réserver, accumuler des avantages
- N'a pas de relation contractuelle directe avec OneClick (gratuit pour lui)
- Persona type : 25–45 ans, urbain (Casablanca / Rabat / Marrakech), classe moyenne+

### 2.2 Restaurateur (rôle `restaurateur`)
- Propriétaire ou manager d'un ou plusieurs restaurants
- Signe un contrat partenaire avec OneClick (commission)
- Utilise quotidiennement l'app **Store** pour gérer son agenda et ses clients
- Persona type : indépendant ou groupe (10–50 restaurants pour les chaînes)

### 2.3 Staff restaurant (sous-rôles `owner / manager / directeur / responsable_resa / serveur`)
- Employé du restaurant invité par le restaurateur
- Permissions fines selon son poste (un serveur ne voit pas le CA, un manager si)
- 5 rôles staff distincts × ~7 actions par menu = matrice de permissions

### 2.4 Admin OneClick (rôle `admin`)
- Équipe interne Onesley (3-5 personnes)
- Supervise les contrats, la facturation, le support, l'onboarding restaurants
- Accès au **Command Center** (cf §3)

### 2.5 Tenant Admin (rôle `tenant_admin`)
- Persona introduite avec le whitelabel (PCC, HOMU…)
- Gère son sous-écosystème (les restos de **son** tenant uniquement)
- Cas typique : le directeur d'un country club qui pilote ses 7 services internes

---

## 3. Architecture fonctionnelle — 3 portails

OneClick est conçu autour de **3 portails distincts** qui se partagent la même base de données mais offrent des interfaces très différentes.

### 3.1 OneClick Pocket — l'app client

> **App mobile : "Win" (iOS + Android), avec variantes whitelabel "My Palmeraie", "HOMU"…**

Parcours principal :
- Explorer les restaurants par ville / cuisine / favoris
- Voir le détail d'un restaurant (Spotlight) avec photos, horaires, menu, avis Google
- Réserver en quelques taps
- Scanner son ticket de caisse pour gagner des points
- Consulter son solde, son tier (Saphir/Rubis/Émeraude), ses promos
- Inviter des amis, gérer son parrainage
- Discuter avec l'assistant IA pour recommandations / questions
- Recevoir des notifications push (confirmation réservation, points crédités, promos)

### 3.2 OneClick Pro Desk — l'app restaurateur

> **App mobile : "Store" + interface web miroir.**

Parcours principal :
- Recevoir et valider les demandes de réservation (accept / refuse / contre-proposer un autre créneau)
- Calendrier de service (vue jour/semaine, par zone et table)
- Gérer son catalogue : photos, horaires, menus, services (brunch/déjeuner/dîner)
- Inviter et administrer son équipe (5 rôles staff)
- Voir ses KPIs : CA, conversions, top clients, points distribués
- Créer des promotions ciblées (segments client : nouveaux, fidèles, élite, inactifs)
- Recevoir les notifications push (nouvelle réservation, ticket scanné, no-show alerté)

### 3.3 OneClick Command Center — l'app admin

> **App mobile : "Admin" + dashboard web.**

Parcours principal :
- Suivre l'état du parc (1 000 restos, alertes de santé)
- Gérer les contrats partenaires (signature, renouvellement, expiration)
- Facturation mensuelle automatisée (commission sur le CA scanné)
- Onboarding nouveaux restaurants
- Modération des litiges (no-show contestés)
- Audit log de toutes les actions sensibles
- Configuration globale (gain rules, tiers, plafonds)

---

## 4. Modules métier

### 4.1 Catalogue & exploration

#### Restaurants
- ~1 000 restaurants en base
- Attributs : nom, ville, type de cuisine, gamme de prix (€/€€/€€€), photo, statut (actif / inactif / suspendu)
- Géolocalisation (latitude / longitude)
- Horaires d'ouverture par jour de la semaine
- Tags libres : "marocain", "vegan-friendly", "terrasse", etc.
- Lien vers Google Places (note Google, photos, avis externes)
- Système de "Lounge points" pour le scoring qualité

#### Pages de découverte côté client
- **Explore** (ville sélectionnée → liste paginée des restos)
- **Compass** (filtres par cuisine, prix, tags)
- **Spotlight** (détail d'un restaurant avec photos, horaires, menu, promos en cours, bouton "Réserver")
- **Favoris** (le client sauvegarde ses restos préférés)

#### Recherche avancée
Système de recherche multicritères : nom partiel, ville, cuisine, plage de prix, tags, restaurant ouvert maintenant, fourchette de notation.

---

### 4.2 Réservation

C'est **le cœur métier** opérationnel de OneClick.

#### Workflow d'une réservation
```
Le client soumet :  demandée
                          │
              ┌───────────┼─────────────────┐
              ▼           ▼                 ▼
        confirmée    refusée       contre-proposition
              │                              │
              │       (le client accepte) ◄──┤
              ▼                              ▼
            placée                       confirmée
              │                              │
              ▼                              ▼
          terminée                       ...
              │
              ▼
          honorée   ◄── client présent (gagne points + score +0.1)
              │
              └────────► no_show (pénalité -0.5 score, sauf contestation)

Branche d'annulation à toute étape : annulée
```

#### Données clés
- Date + heure (format "HH:MM" texte, fuseau Maroc)
- Nombre de couverts
- Service (brunch / déjeuner / dîner)
- Zone et numéro de table (optionnel — assigné par le resto au moment de "placer")
- Notes du client (ex: "anniversaire de ma femme")
- Motif de refus si refusée
- Date/heure proposée si contre-proposition (avec délai d'expiration)

#### Règles métier importantes
- **Auto-cancel** : une réservation `demandée` non répondue par le resto **2h avant** l'heure prévue est automatiquement annulée (cron H-2)
- **Rappels** : J-1 à 9h heure Maroc + H-2 (push notification + mail)
- **Late cancellation** : annulation < 1h avant l'heure → pénalité, **non contestable**
- **No-show** : marqué par le resto entre H-1 et H+1 après l'heure prévue → 48h pour contester
- **Multi-invités** : un client peut inviter ses amis (reservation_guests) avec relance automatique

---

### 4.3 Fidélité — Snap2Earn

Le système de fidélité **différenciateur** d'OneClick : le client n'a pas de carte. Il **scanne son ticket de caisse** après son repas, l'OCR extrait le montant, et OneClick crédite des points.

#### Mécanique
1. Le client paie l'addition normalement (cash / CB / autre)
2. Il prend une photo du ticket dans l'app Win
3. L'OCR (OCR.space ou équivalent) extrait le montant TTC
4. Le client confirme (ajustable manuellement si OCR imprécis)
5. Validation : un staff du restaurant approuve (anti-fraude)
6. Points crédités selon les **gain rules** du restaurant (typique 10% du montant en points)
7. Notification push au client + ajustement du **tier** si seuil franchi

#### Règles de gain (`gain_rules`)
- Configurables **par restaurant** (taux de conversion DH → points)
- Plafonnement (`loyalty_plafond`) pour éviter les abus
- Bonus parrainage à part (50 pts au parrain + parrainé à l'activation)

#### Tiers de fidélité
- **Saphir** (entrée) → **Rubis** → **Émeraude** (top)
- Seuils en points cumulés sur 12 mois glissants
- Avantages par tier : taux de conversion plus élevé, accès offres VIP, événements Élite Club

#### Expiration des points
- Points expirent **1 an après acquisition** (FIFO — First In First Out)
- Cron quotidien `process-expired-points` qui debit les points expirés
- Notification au client J-30 avant expiration
- Affichage "X points expirent le ..." dans l'app

#### Punch cards
- Variant : carte fidélité "10 visites = 1 offerte"
- Activé par tenant (ex: PCC pour ses services Coiffeur, Palm Gym)
- Distinct du système de points classique

---

### 4.4 Communauté

OneClick mise sur l'effet réseau pour la croissance.

#### Amis
- Ajouter un ami par numéro de téléphone, code de parrainage, ou QR code
- Demande / acceptation symétrique (pattern Facebook 2010)
- Voir les réservations partagées (ex: "Mon ami Karim sera aussi au resto X jeudi")

#### Groupes d'amis
- Le client crée un "Crew" (4-10 personnes typiquement)
- Permet d'inviter d'un coup tout le groupe sur une réservation

#### Parrainage
- Chaque utilisateur a un code unique `OC-XXXXXX`
- Partageable par lien, SMS, QR code
- À l'activation : 50 pts au parrain + 50 pts au filleul, après la 1re réservation honorée du filleul

#### Invitations sur réservation
- Lors d'une réservation, le client peut inviter N amis (max = couverts - 1)
- Chacun reçoit notification + peut accepter/décliner
- Si accepté : ajout dans `reservation_guests`

---

### 4.5 Marketing & promotions

#### Offres / Promotions (`offers`)
- Créées par le restaurateur, validées par admin
- Types : pourcentage, montant fixe, % majoré sur tier, "X-1 offert"
- Ciblage par segment : tous, fidèles, nouveaux clients, inactifs (>3 mois sans visite), Saphir/Rubis/Émeraude
- Plage de validité (date début, date fin, jours/services applicables)
- Visibles dans Pocket (page "Promos") + détail restaurant

#### Campagnes push (`promo_notification_requests`)
- Le restaurateur prépare un message ciblé sur un segment
- Approbation admin
- Envoi push FCM en lot (100-1000 clients) avec fallback in-app si push refusé

#### Vue "à la une"
- `explore_featured` : restaurants en avant sur la home (rotation hebdomadaire, sponsorisé ou éditorialisé)

---

### 4.6 Notation & fiabilité

#### Score de fiabilité client (`reliability_score`)
- Échelle 0–5, démarre à 5.0
- **+0.1** à chaque réservation honorée
- **−0.5** à chaque no-show non contesté
- Affiché côté restaurateur pour décider d'accepter ou non un client à risque
- **Visibilité différée côté client** : le client ne voit son nouveau score qu'après 48h (anti-anxiété + permet contestation)

#### Notation client → restaurant
- Optionnel : après une réservation honorée, le client peut noter le restaurant (1-5 étoiles + commentaire)
- Visible côté admin uniquement (modération avant publication)

#### Notation restaurant → client
- Le staff peut noter ses clients post-visite (sympathique / problématique / VIP)
- Visible **uniquement** au sein de son restaurant
- Sert à anticiper les conflits, identifier les fidèles

---

### 4.7 Support & litiges

#### Tickets support (`support_tickets`)
- Le client ouvre un ticket depuis l'app (catégorie : technique, réservation, paiement, autre)
- Routé vers admin (pour OneClick standard) ou tenant_admin (pour PCC/HOMU)
- Conversation chat dans l'app (`chat_messages`)
- Statuts : ouvert / en cours / résolu / clos

#### Contestation no-show (`no_show_disputes`)
- Workflow en 3 phases :
  1. **0–1h après l'heure de réservation** : le restaurateur peut marquer no-show (ou pas)
  2. **1h–48h** : le client peut contester (ex: "j'étais là, accueil m'a refusé") → review admin
  3. **48h+** : la décision devient finale, score appliqué irréversiblement
- Pénalité **uniquement** sur l'organisateur, jamais les invités

#### Punch cards et règles métier (`rule_templates`)
- Templates de règles configurables (ex: "1 point par dirham, max 100 pts/jour")
- Versionnés et historisés

---

### 4.8 Country club (PCC) & ressources réservables

PCC (Palmeraie Country Club, Marrakech) est un **cas particulier** : un tenant qui vend des services autres que la restauration. OneClick a généralisé son modèle pour devenir polyvalent.

#### Ressources réservables (`bookable_resources`)
- Padel (90 min)
- Spa (60 ou 90 min)
- Golf 9/18 trous (60 min)
- Coiffeur (30 min)
- Palm Gym (60 min)
- Restaurant Le Resto (réservation classique via le système réservations)

#### Différences par rapport au resto classique
- Plages horaires divisées en **slots fixes** (vs créneau libre)
- Tarification dynamique (week-end vs semaine, member vs visiteur)
- Carte de membre Apple Wallet 4 tiers (Bronze / Silver / Gold / Platinum)
- Family management : un membre principal gère sa famille (10 personnes max)
- Workflow d'expiration H-2 spécifique aux ressources

#### Annonces (`tenant_announcements`)
- Le tenant_admin diffuse des messages (ex: "Tournoi padel dimanche 15h")
- Push aux membres concernés + bannière in-app
- Système de `announcement_reads` pour suivre qui a lu

---

## 5. Multi-tenant & whitelabel

OneClick est **multi-tenant nativement** : la même infrastructure technique sert plusieurs marques distinctes, avec branding personnalisé.

### Tenants en production
| Slug | Nom | Restos | Apps | Statut |
|---|---|---|---|---|
| `oneclick` | OneClick (marque générique) | 1002 publics | Win, Store, Admin | Actif |
| `homu` | HOMU | 1 (Casablanca) | HOMU Win, HOMU Store | Actif (avr 2026) |
| `palmeraie` | Palmeraie Country Club | 7 services | My Palmeraie, PCC Store | Actif (avr 2026) |
| `restopro` | Restopro | — | (apps prêtes) | En attente activation |

### Modèle d'isolation
- **Catalogue restaurants** filtré par `tenant_id` quand le client utilise une app whitelabel
- **Profils clients** rattachés à un `tenant_group_id` (un client HOMU n'a accès qu'aux restos HOMU)
- **Branding** (logo, couleurs, polices) configurable par tenant
- **Feature flags** par tenant (`tenant_features`) : ex. PCC active "annonces" et "ressources réservables" mais désactive "communauté"

### Bénéfices business
- Un partenaire stratégique peut "louer" la techno OneClick sous sa marque
- Onboarding rapide d'un nouveau client : ~1 jour pour décliner l'app
- Économie d'échelle : 1 codebase, N clients

---

## 6. Workflows critiques

### 6.1 Inscription d'un nouveau client
1. L'utilisateur télécharge l'app Win
2. Saisit téléphone + email + prénom/nom + ville
3. Reçoit SMS / email de vérification
4. Optionnel : saisie d'un code de parrainage (`OC-XXXXXX`)
5. Compte créé → solde 0 points → tier Saphir (entrée)
6. Accueil avec recommandations basées sur sa ville

### 6.2 Première réservation
1. Client choisit un restaurant dans Explore / Spotlight
2. Sélectionne date + heure + couverts + service
3. Optionnel : invite des amis (`reservation_guests`)
4. Soumet → état `demandée`
5. **Push au restaurateur** "Nouvelle demande de réservation"
6. Restaurateur accepte → état `confirmée` → push au client "Confirmé !"
7. Rappel J-1 9h + H-2
8. Client se présente → resto marque honorée → +0.1 reliability score

### 6.3 Fidélisation post-visite
1. Client paie l'addition (sans interaction OneClick)
2. Ouvre l'app Win → bouton "Snap2Earn"
3. Photo du ticket → OCR extrait le montant (90% des cas correct, 10% ajustement manuel)
4. Restaurateur (staff) reçoit notif "Nouveau ticket à valider"
5. Validation 1-clic
6. Points crédités au client (typiquement 10% du montant)
7. Si seuil tier franchi (Saphir → Rubis) → notification "Félicitations, vous êtes Rubis"

### 6.4 Détection no-show + contestation
1. Heure de réservation passée, client absent
2. Restaurateur (entre H-1 et H+1) marque "no-show"
3. État → `no_show`, déduction de 0.5 du reliability_score (visible 48h plus tard pour le client)
4. Push au client "Vous avez été marqué absent. Contester ?"
5. Si contestation : ouverture d'un dispute → review admin
6. Admin tranche : si client a raison, score restauré + warning au resto

### 6.5 Onboarding d'un nouveau restaurant
1. Restaurateur fait une demande sur le site / mail commercial
2. Admin crée son compte staff + son restaurant fiche
3. Restaurateur télécharge l'app Store, se logue
4. Complète onboarding : photos, horaires, menus, services
5. Configure ses gain rules (taux DH → points)
6. Publication → visible dans le catalogue Win
7. Audit régulier des KPIs (CA scanné, taux de no-show)

---

## 7. Notifications & communication

### 7.1 Canaux
| Canal | Usage | Provider |
|---|---|---|
| **Push notification** | Confirmations réservation, points crédités, promos, no-show | FCM (Firebase Cloud Messaging) |
| **Email** | Bienvenue, factures, alertes administratives, contestations | Resend (HTTP API) |
| **In-app** | Notifications consultables dans l'app (centre de notifs) | Stockage DB local |
| **SMS** | Vérification téléphone, OTP critiques | À déterminer (Twilio / OVH envisagés) |

### 7.2 Types de notifications principales
- **Réservation** : reçue, confirmée, refusée, contre-proposée, rappel J-1, rappel H-2, no-show
- **Points** : crédités, sur le point d'expirer (J-30), expirés
- **Promo** : nouvelle offre disponible, offre qui expire bientôt
- **Parrainage** : votre filleul s'est inscrit, le parrainage est activé
- **Communauté** : nouvelle demande d'ami, ami a réservé un resto que vous suivez
- **Tier** : montée de niveau (Saphir → Rubis), bénéfices nouveaux
- **Système** : annonce tenant, mise à jour application, maintenance

### 7.3 Préférences utilisateur
Le client peut désactiver granulairement chaque catégorie de notification (sans pouvoir désactiver les notifs critiques type "réservation refusée").

---

## 8. Modèle économique

### 8.1 Côté client final
**Gratuit, toujours.** Aucun frais, aucun abonnement. Le service vit sur la commission restaurateur.

### 8.2 Côté restaurateur
Contrat partenaire signé à l'onboarding (`partner_contracts`).

| Composante | Description |
|---|---|
| **Commission OneClick** | % sur le CA scanné via Snap2Earn (typiquement 3 %) |
| **Wallet client** | % redistribué au client en points fidélité (typiquement 2 %) |
| **TVA 20 %** | Sur la commission OneClick |
| **Total facturé** | Commission + TVA = ~3.6 % du CA scanné |

### 8.3 Cycle de facturation
- **Mensuel** : un cron `generate-invoices` produit chaque 1er du mois la facture du mois précédent
- Format : PDF stocké, email au restaurateur via Resend
- Réconciliation : ce qui a été scanné = ce qui est facturé (auditable)

### 8.4 Tenants whitelabel
- Modèle B2B : Onesley vend une licence + maintenance par tenant
- PCC, HOMU, Restopro paient des abonnements mensuels distincts
- Détails contractuels confidentiels mais existence du modèle révélée par la table `tenants`

---

## 9. Conformité & confiance

### 9.1 Conformité légale
- **CNDP Maroc** (Commission Nationale de protection des Données Personnelles) : déclaration en cours / déposée
- **Loi marocaine 09-08** sur la protection des données personnelles : conforme
- Hébergement DB en Europe (Supabase Central Europe) — RGPD-friendly pour les utilisateurs européens éventuels

### 9.2 Sécurité des données
- **Données sensibles chiffrées** : mots de passe (hash bcrypt), JWT avec secret fort
- **PII (informations personnelles)** non exposées côté restaurant : un staff voit "Karim B." pas "Karim Bennani 06.55.55.55.55"
- **Audit log** : toute action sensible (création, modification, suppression, export) tracée
- **Backups** quotidiens automatisés, conservation 30 jours

### 9.3 Transparence client
- Politique de confidentialité accessible dans l'app
- Conditions Générales d'Utilisation (CGU) acceptées au signup
- Possibilité d'export de ses données (RGPD-like)
- Possibilité de suppression du compte (avec délai de 30 jours pour annuler)

### 9.4 Sentry monitoring
- Toutes les erreurs côté apps remontent vers Sentry (région EU)
- Alertes automatiques si pic d'erreurs
- Replays vidéo en cas de crash (anonymisés)

---

## 10. Glossaire

| Terme | Définition |
|---|---|
| **App ID** | Identifiant Apple/Google d'une app (ex: `ma.oneclick.win`) |
| **Auto-cancel** | Annulation automatique d'une réservation `demandée` non confirmée 2h avant |
| **BestPro** | (legacy) ancien nom envisagé pour un tenant whitelabel restaurant — finalement renommé Restopro |
| **Crew** | Groupe d'amis créé par un client pour réserver à plusieurs |
| **Élite Club** | Programme événementiel pour les clients tier Émeraude |
| **FIFO** | First In First Out — ordre de consommation des points (les plus anciens d'abord) |
| **Gain rule** | Règle de conversion DH → points configurée par restaurant |
| **HOMU** | Tenant whitelabel — restaurant unique à Casablanca, depuis avril 2026 |
| **Honorée** | Statut final positif d'une réservation (client venu) |
| **Loyalty plafond** | Limite de points gagnables par jour / par session |
| **No-show** | Client absent à sa réservation confirmée |
| **OCR** | Optical Character Recognition — extraction du montant depuis la photo du ticket |
| **OneLabel** | Nom commercial du système whitelabel multi-tenant |
| **Onesley** | Société éditrice d'OneClick |
| **Palm Gym** | Salle de sport du country club Palmeraie |
| **Parrainage** | Système de bonus mutuel parrain/filleul (50 pts) |
| **PCC** | Palmeraie Country Club — tenant whitelabel à Marrakech |
| **Pocket** | Nom interne du portail client (utilisé dans le code) |
| **Pro Desk** | Nom interne du portail restaurateur (utilisé dans le code) |
| **Punch card** | Carte virtuelle "10 visites = 1 offerte", alternative au système de points |
| **Reliability score** | Note de fiabilité du client (0-5), influence l'acceptation des futures réservations |
| **Resa** | Abbréviation interne pour "réservation" |
| **Restopro** | Tenant whitelabel — chaîne de restaurants (apps prêtes, en attente activation) |
| **Snap2Earn** | Mécanique de fidélisation par scan de ticket |
| **Spotlight** | Page détail d'un restaurant côté client |
| **Staff role** | Rôle d'un employé dans un restaurant (owner / manager / directeur / responsable_resa / serveur) |
| **Tenant** | Marque whitelabel autonome partageant la même infrastructure technique |
| **Tier** | Niveau de fidélité (Saphir → Rubis → Émeraude) |
| **TestFlight** | Plateforme Apple de distribution beta des apps iOS |
| **Tier_admin** | Administrateur d'un tenant whitelabel (gère son sous-écosystème) |
| **Win** | Nom commercial de l'app client (ex-Pocket) |
| **Wallet pkpass** | Carte de membre Apple Wallet (4 tiers pour PCC) |
| **Whitelabel** | Adaptation du produit sous une marque tierce avec branding propre |

---

## Annexe : ce qui n'est PAS encore en production

- ❌ Service auth maison avec refresh token (pour l'instant on s'appuie sur Supabase Auth — migration prévue)
- ❌ Paiement intégré (Stripe / CMI) — le client paie directement le restaurant aujourd'hui
- ❌ Internationalisation (i18n) au-delà du français — anglais/arabe en cours (Session 47-48)
- ❌ App native pour les groupes de restaurants (multi-restos pour 1 propriétaire) — fonctionnel mais pas optimisé UX

---

*Document maintenu par l'équipe OneClick. Pour toute question, contacter contact@onesley.com.*
