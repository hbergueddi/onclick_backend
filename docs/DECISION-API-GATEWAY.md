# Décision — API Gateway (Spring Cloud Gateway)

> **Statut : REPORTÉ** (différé, non rejeté définitivement)
> **Date : 2026-05-20**
> **Contexte : retour dev senior — « installer et configurer Spring Cloud en tant que
> gateway pour entrer le backend. Le front ne connaîtra pas le backend, il connaîtra
> l'adresse de Spring Cloud. »**

## La demande

Mettre une **Spring Cloud Gateway** devant le backend OneClick. Le frontend ne
connaîtrait que l'adresse de la gateway, jamais celle du backend directement.

Topologie cible proposée :

```
Front → Spring Cloud Gateway (:8080) → Backend (:8083)
```

## Analyse (revue senior)

### Le besoin est légitime, mais déjà couvert

Le but réel — *point d'entrée unique + masquage du backend + découplage front/back*
— est le rôle d'un **reverse-proxy**. OneClick **en a déjà un : Nginx**.

En prod aujourd'hui : `Front → Nginx (app-oneclick.net) → Backend`. Le front ne
connaît déjà que Nginx ; le backend est déjà non exposé (localhost:8083, UFW).
**Le but est donc déjà atteint sans gateway.**

Ajouter SCG donnerait `Front → Nginx → Gateway → Backend` = **3 sauts**, la gateway
**dupliquant** ce que Nginx fait déjà (entrée unique, masquage, TLS).

### Spring Cloud Gateway est un outil de microservices

SCG prend sa valeur avec **plusieurs services** : routing dynamique, service discovery
(Eureka/Consul), load-balancing, circuit-breaking inter-services.

Or OneClick est un **monolithe modulaire assumé** (cf `pom.xml` : *« 1 seul JAR,
communication 100% in-process, PAS Kafka, isolation via Spring Modulith »*). La
trajectoire va **vers le modulith, pas vers les microservices**. Mettre une gateway
microservices devant un monolithe = **over-engineering**.

### Coûts vs bénéfices

| Coût | Détail |
|---|---|
| +1 process JVM | ~300-500 MB RAM, à déployer/monitorer/redémarrer |
| **Couplage Spring Cloud release-train** ⚠️ | Le plus structurant : chaque upgrade Spring Boot devra attendre la version Spring Cloud compatible (Boot 4.0 n'a aujourd'hui QUE Spring Cloud 2025.1.x / Gateway 5.0) |
| +1 hop réseau | latence faible mais bénéfice nul |
| Restructuration multi-module | déplacer ~470 fichiers, réimport IDE, scripts deploy |
| Redondance | rate-limit / JWT / CORS viennent d'être mis dans le backend (Bug 35/36) → gateway « fine » n'ajoute rien ; gateway « intelligente » = refaire ce travail |

**Sur un monolithe déjà derrière Nginx, le bénéfice concret est quasi nul.**

## Décision

**Reporté.** On ne met PAS de Spring Cloud Gateway pour l'instant. Le besoin
« le front ne touche pas le backend » est déjà couvert par Nginx (au besoin :
durcissement de la config Nginx, ~1h, 0 process, 0 couplage).

## Conditions de réexamen

Reprendre cette décision si l'un de ces drivers devient réel :

1. **Split en microservices planifié** — OneClick éclate en plusieurs services
   déployables (contredit la trajectoire modulith actuelle → à acter explicitement).
2. **Edge features Spring concrètes** que Nginx fait mal :
   - agrégation OpenAPI multi-services,
   - routing par claim JWT,
   - API-key management centralisé pour partenaires externes,
   - transformation requête/réponse en Java.
3. **Contrainte corporate** (« tout transite par une gateway Spring maison »).

## Si on le fait un jour — pré-décisions déjà validées (2026-05-20)

Pour ne pas re-discuter ces points le moment venu, les choix suivants avaient été
retenus avec l'utilisateur :

- **Saveur** : `spring-cloud-starter-gateway-server-webmvc` (servlet/Tomcat, aligné
  sur le backend MVC — pas la version réactive WebFlux/Netty).
- **Release train** : Spring Cloud **2025.1.x (Oakwood)** = Spring Boot 4 + Gateway 5.0
  (2025.0.x est incompatible Boot 4.0.1+).
- **Périmètre** : proxy fin (routing + CORS au gateway), sécu (JWT/RBAC) reste au backend.
- **Rate-limiting** : reste **Bucket4j dans le backend** (ne pas migrer vers le
  `RequestRateLimiter` du gateway en v1).
- **Structure** : multi-module Maven (parent + `backend/` + `gateway/`).
- **Discovery** : AUCUNE (route statique `uri: http://localhost:8083` — pas d'Eureka
  pour 1 seul backend).
- **VPS** : Nginx garde SPA + TLS, proxie `/api` → gateway → backend.

## Références compatibilité

- Spring Cloud 2025.1.0 (Oakwood) — basé Spring Framework 7 + Spring Boot 4 (4.0.2+).
- Spring Cloud Gateway 5.0.0 — deux saveurs : `gateway-server-webmvc` (servlet) et
  `gateway-server-webflux` (réactif Netty).
