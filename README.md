# OneClick — Backend (Spring Boot)

Monolithe modulaire (**Spring Modulith**) qui sert l'API REST + WebSocket de la
plateforme OneClick : fidélité, réservations, multi-tenant whitelabel (PCC, HOMU…),
RBAC fin (`hasAuthority('VERB:RESOURCE')`).

- **API** : `http://localhost:8083` · **Swagger** : `http://localhost:8083/swagger-ui.html`
- **Stack** : Spring Boot · Spring Modulith · PostgreSQL 17 · Redis · Flyway · OAuth2 Resource Server (JWT HS256)
- **Profil par défaut** : `enterprise` (DB `oneclick_enterprise`, port 8083). `secure` ajoute l'auth JWT.

## Démarrage rapide (clone → run)

> Procédure complète et faisant foi (Docker **ou** Homebrew, restauration de la base,
> création du user applicatif, étape `BYPASSRLS`) : **[`SETUP-LOCAL.md`](SETUP-LOCAL.md)**.

```bash
# 1. Postgres 17 + Redis 7 en local (Docker) — + Adminer sur http://localhost:8082
docker compose up -d

# 2. Base + user applicatif : suivre SETUP-LOCAL.md

# 3. Lancer le backend
./run.sh                 # profil enterprise (port 8083)
./run.sh --secure        # + OAuth2 JWT
```

**Avec IntelliJ** : `File > Open` sur le `pom.xml` (projet Maven), puis une run config
sur la classe `OneclickApplication` avec `--spring.profiles.active=enterprise` (port 8083).

## Build & tests

```bash
./mvnw clean package     # build le JAR → target/oneclick-0.0.1-SNAPSHOT.jar
./mvnw test              # suite unitaire + intégration
```

## Configuration

Tous les secrets sont externalisés via variables d'environnement ; en dev local des
valeurs par défaut suffisent (rien à configurer pour démarrer). Voir
`application-enterprise.yml`, et pour la prod `deploy/oneclick-prod.env.template` +
`deploy/DEPLOYMENT-ENV.md`. Le runbook de déploiement VPS est dans
`../OneClick_iOS_Native/docs/ENVIRONMENTS-WORKFLOW.md`.

## Chatbot IA (module `core/ai`)

Chatbot d'orchestration : à chaque question, un orchestrateur décide des **sources de
contexte** à activer (RAG, mémoire de conversation) et expose au modèle des **outils
métier** (Tool Calling — ex. réserver une table). L'identité de l'utilisateur est
**toujours lue côté serveur depuis le JWT**, jamais depuis le corps de la requête : la
mémoire est scopée par utilisateur et une réservation se fait forcément « pour le user
connecté ».

### Technologies

| Rôle | Techno |
|---|---|
| Framework LLM | **LangChain4j 1.4.0** (BOM) — même `ChatModel` quel que soit le provider |
| Provider de chat | **Ollama** (local self-hosted, défaut) **ou Groq** (managé, API OpenAI-compatible). Bascule via `AI_PROVIDER=ollama\|groq` |
| Modèle chat (Ollama) | `llama3.2:3b` (CPU, défaut local) — surchargeable en prod |
| Modèle chat (Groq) | `llama-3.3-70b-versatile` |
| Embeddings | **Ollama `nomic-embed-text`** (toujours, quel que soit le provider de chat) |
| RAG / vector store | **pgvector** (PostgreSQL) — ingestion (chunk → embed → store) + retrieval top-K |
| Mémoire multi-tours | table JPA `ai_conversation_messages`, scopée par `userId` |
| Tool Calling | outils métier (ex. réservation) exécutés dans le thread de la requête (JWT dispo) |
| Sécurité | OAuth2 Resource Server (JWT HS256) — `@PreAuthorize("isAuthenticated()")` |

### API

**`POST /api/ai/chat`** — point d'entrée unique (authentification requise).

Corps de la requête (`application/json`) :

| Champ | Type | Requis | Description |
|---|---|---|---|
| `question` | string (1–8000) | ✅ | Question de l'utilisateur |
| `conversationId` | string | — | Fil de discussion pour la mémoire multi-tours (scopé au user courant) |
| `sources` | string[] | — | Force les sources de contexte (ex. `["rag"]`). Absent = AUTO (l'orchestrateur décide) |
| `useTools` | boolean | — | Force le Tool Calling. `null` = AUTO |

Réponse :

| Champ | Type | Description |
|---|---|---|
| `answer` | string | Réponse générée |
| `contextSourcesUsed` | string[] | Sources ayant contribué (ex. `["rag","memory"]`) |
| `toolsAvailable` | string[] | Outils métier exposés au modèle |
| `contextFragmentCount` | int | Nombre de fragments de contexte injectés |

Exemple (dev, profil `enterprise` sans `--secure` → pas de token nécessaire) :

```bash
curl -X POST http://localhost:8083/api/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Réserve une table pour 2 ce soir à 20h au restaurant X"}'
```

Avec auth activée (`./run.sh --secure` ou prod), ajouter le Bearer JWT :

```bash
curl -X POST http://localhost:8083/api/ai/chat \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{"question":"Quels sont mes points de fidélité ?","conversationId":"conv-123"}'
```

> Endpoint de diagnostic bas niveau (appel LLM direct, sans orchestration) :
> `POST /api/ai/ollama/chat`. À ne pas confondre avec le chatbot ci-dessus.

### Configuration & clé Groq

Le provider par défaut est **Ollama** (local, aucune clé requise). Pour utiliser **Groq**
il faut fournir une clé API — **jamais en dur**, uniquement via variables d'environnement :

| Variable | Défaut | Rôle |
|---|---|---|
| `AI_PROVIDER` | `ollama` | Provider de chat : `ollama` \| `groq` |
| `GROQ_API_KEY` | *(vide)* | **Clé API Groq** (obligatoire si `AI_PROVIDER=groq`) — obtenue sur https://console.groq.com |
| `GROQ_MODEL` | `llama-3.3-70b-versatile` | Modèle de chat Groq |
| `GROQ_BASE_URL` | `https://api.groq.com/openai/v1` | Base URL (API OpenAI-compatible, `ChatModel` LangChain4j) |
| `GROQ_TIMEOUT` | `PT60S` | Timeout des appels |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Serveur Ollama (chat + embeddings) |
| `OLLAMA_CHAT_MODEL` | `llama3.2:3b` | Modèle de chat local |
| `OLLAMA_EMBEDDING_MODEL` | `nomic-embed-text` | Modèle d'embeddings (RAG) |

```bash
# Activer Groq pour une session
export AI_PROVIDER=groq
export GROQ_API_KEY=gsk_xxxxxxxxxxxxxxxxxxxxxxxx
./run.sh
```

> ⚠️ Ne commite jamais `GROQ_API_KEY`. En dev, exporte-la dans ton shell (ou un `.env`
> local non versionné) ; en prod, elle vit dans l'`EnvironmentFile` systemd
> (cf. `deploy/DEPLOYMENT-ENV.md`). Les embeddings passent **toujours** par Ollama,
> même quand le chat est servi par Groq.

Bloc de config complet : `app.ai` dans `application.yml`.
Détails API : `http://localhost:8083/swagger-ui.html`.

## Frontend

Le frontend React (portails client / **OneClick Business** / super-admin) vit dans le repo
[`OneClick_Spring_FrontEnd`](https://github.com/hiham/OneClick_Spring_FrontEnd) et consomme cette API.
