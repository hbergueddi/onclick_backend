# OneClick — Inventaire des variables d'environnement (prép. déploiement)

> Préparé le 17/06/26 (lecture seule, rien appliqué au serveur). **En attente du
> document + instructions du dev senior** — superposer puis valider avant d'agir.

## Contexte runtime prod (constaté sur le VPS)
- Service : `systemd oneclick.service` → `java -jar /home/ubuntu/oneclick-0.0.1-SNAPSHOT-V1.jar --spring.profiles.active=enterprise,secure`
- **Profils PROD = `enterprise,secure`** — **DÉCISION 17/06 : c'est LA version prod**, le profil Spring `prod` (application-prod.yml) n'est pas utilisé. Config effective = `application.yml` (base) + `application-enterprise.yml` + `application-secure.yml`. ⇒ jeu de vars définitif (ci-dessous).
- Port d'écoute = **8083** (forcé par `application-enterprise.yml`).
- Liaison : tout `${VAR}` du YAML est résolu **depuis l'environnement du process** (= les `Environment=`/`EnvironmentFile=` du service). Le JAR ne contient que des placeholders. Les vars de la run-config IntelliJ sont **locales** (jamais dans le JAR). Aucun `System.getenv()` ni `@Value("${MAJUSCULE}")` direct côté Java → le YAML est la surface d'env complète.

## Légende statut
- ✅ **Déjà** dans le service (Environment= ou drop-in)
- ❌ **Manquant** → tombe sur le défaut du YAML
- 🔒 secret (ne pas committer la valeur)

## Matrice — profil ACTUEL (`enterprise,secure`)

| Variable | Rôle | Défaut YAML | Requis ? | Statut prod | Valeur cible |
|---|---|---|---|---|---|
| `ENTERPRISE_DB_URL` | DataSource | `…/oneclick_enterprise` | oui | ✅ | `jdbc:postgresql://localhost:5432/oneclick` |
| `ENTERPRISE_DB_USERNAME` | DataSource | `oneclick_app` | oui | ✅ | `postgres` |
| `ENTERPRISE_DB_PASSWORD` 🔒 | DataSource | `OneclickLocal2026` | oui | ✅ | (= `<DB_PASSWORD_PROD>`) |
| `ENTERPRISE_FLYWAY_USER` | Flyway | `hh` | oui | ✅ | `postgres` |
| `ENTERPRISE_FLYWAY_PASSWORD` 🔒 | Flyway | `oneclick_dev_pwd` | oui | ✅ | (= `<DB_PASSWORD_PROD>`) |
| `SPRING_DATA_REDIS_PASSWORD` 🔒 | Redis (host/port = localhost:6379 codés en dur) | — | oui | ✅ (drop-in) | (déjà posé) |
| `JWT_SECRET` 🔒 | Signature/validation HS256 | `onesley-…-dev-secret…` | **OUI prod** | ❌ → **utilise le défaut DEV public** ⚠️ | secret HS256 fort (dev senior) |
| `FCM_PROJECT_ID` | Push FCM | *(vide)* | pour push | ❌ | `oneclick-129bb` |
| `FCM_SERVICE_ACCOUNT_JSON` 🔒 | Push FCM | *(vide)* | pour push | ❌ | JSON service account (1 ligne) |
| `FCM_DISPATCH_ENABLED` | Garde-fou envoi réel | `false` | pour push | ❌ → false | `true` |
| `GROQ_API_KEY` 🔒 | IA (chat/assistant/elite/plan) | *(vide → stub)* | si IA voulue | ❌ | clé Groq (dev senior) |
| `OCR_SPACE_API_KEY` 🔒 | OCR tickets Snap2Earn | *(vide → stub)* | si OCR voulu | ❌ | `<CLE_OCR_SPACE>` (cf CLAUDE.md) |
| `GOOGLE_PLACES_API_KEY` 🔒 | Enrichissement Google | clé dev committée | optionnel | (défaut) | `AIzaSy…BiM8` (⚠️ sans restriction) |
| `RESEND_API_KEY` 🔒 | Emails brandés (onboarding partenaire, feedback, verdicts) via Resend | `*(vide → stub, aucun envoi)*` | **OUI** (sinon 0 email) | ❌ → stub | clé Resend `re_…` (domaine vérifié `app-oneclick.net`) |
| `APP_EMAIL_INTERNAL_ONBOARDING` | Boîte interne qui reçoit chaque demande d'inscription | `contact@app-oneclick.net` (OVH, ≠ M365) | non | (défaut OK) | — |
| `APP_FRONTEND_BASE_URL` | Liens emails/notifs | `https://app-oneclick.net` | non | (défaut OK) | — |
| `APP_RATE_LIMIT_ENABLED` | Anti-bruteforce | `false` (base) | recommandé | ❌ → false | `true` |
| `S3_ENDPOINT` / `S3_PUBLIC_BASE_URL` / `S3_BUCKET` / `S3_REGION` / `S3_ACCESS_KEY` 🔒 / `S3_SECRET_KEY` 🔒 / `S3_PATH_STYLE` | Stockage média (uploads) | MinIO `localhost:9000` (dev) | si uploads | ❌ → MinIO local | **À CONFIRMER** (cf questions) |

## Points de sécurité à trancher (avant prod)
1. **`JWT_SECRET`** : actuellement le **défaut DEV public** signe les tokens prod → à remplacer par un vrai secret. *(décision/valeur dev senior)*
2. **Clé Google Places** sans restriction HTTP referrer (audit E.1) — restreindre ou router via Edge Function.
3. Secrets en clair dans l'unit systemd → préférer un `EnvironmentFile` `chmod 600 root` (cf template).

## Questions ouvertes pour le doc du dev senior
- ~~Profil~~ → **TRANCHÉ (17/06)** : on reste sur `enterprise,secure` = version prod. Jeu de vars ci-dessus = définitif.
- **Médias** : MinIO sur le VPS, bucket S3 réel, ou uploads non utilisés en prod ?
- **Groq / OCR** : push ces features en prod ? (sinon stub silencieux, OK).
- **JWT_SECRET** prod : quelle valeur / source (secret manager) ?

## Procédure d'application recommandée (DOCUMENTÉE — non exécutée)
```bash
# 1) Déposer le fichier d'env (depuis le template, valeurs réelles remplies)
sudo install -m 600 -o root -g root oneclick-prod.env /etc/oneclick/oneclick.env

# 2) Drop-in systemd qui le référence
#   /etc/systemd/system/oneclick.service.d/env.conf
#   [Service]
#   EnvironmentFile=/etc/oneclick/oneclick.env

# 3) Recharger + redémarrer (⇒ décision/feu vert utilisateur ; ne PAS lancer sans validation)
sudo systemctl daemon-reload
sudo systemctl restart oneclick.service

# 4) Vérifs post-restart
sudo systemctl status oneclick.service
sudo journalctl -u oneclick.service -n 50 --no-pager   # 0 erreur boot, Flyway "Schema up to date" (V100)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8083/actuator/health
```

## Prérequis delivery push (hors env)
Pour que les push **arrivent** : des tokens dans `device_tokens` de la prod (les apps doivent pointer vers ce backend) + APNs key (iOS) côté Firebase projet `oneclick-129bb`.

## Fichiers de ce dossier
- `oneclick-prod.env.template` — gabarit `EnvironmentFile` complet (placeholders secrets).
- `DEPLOYMENT-ENV.md` — ce document.
