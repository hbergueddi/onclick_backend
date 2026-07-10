/**
 * Module {@code core/ai} — wrappers Groq LLM (Sprint B.1 G.4-bis).
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/ai/care-chat} — chat support client (port EF oneclick-care-chat)</li>
 *   <li>{@code POST /api/ai/assistant} — assistant Pocket (port EF oneclick-ai-assistant)
 *     avec rate-limit 5/jour côté frontend</li>
 *   <li>{@code POST /api/ai/elite-review} — résumé review (port EF elite-ai-review)</li>
 *   <li>{@code POST /api/ai/plan} — génération plan hebdo (port EF generate-plan)</li>
 * </ul>
 *
 * <h3>Stub mode</h3>
 * <p>Si {@code app.ai.groq.api-key} n'est pas configurée, le service retourne
 * une réponse stub ({@code "[AI not configured]"}) + log warn. Permet d'avoir
 * une API fonctionnelle en dev sans clé Groq.
 *
 * <h3>RBAC</h3>
 * <p>Tous les endpoints sont {@code @PreAuthorize("isAuthenticated()")} — pas
 * d'usage anonyme (cap rate-limit déjà côté frontend via ai_usage table legacy
 * en attendant le port).
 */
@ApplicationModule(
    // OPEN comme les autres contextes socles core/* (identity, tenant, membership…) : permet aux
    // modules métier de fournir des AiTool (core.ai.api.AiTool) sans que core/ai ne dépende d'eux.
    // Cross-module = api/ (ports/DTOs) ou events ; internal/ (Ollama, LangChain4j, pgvector) reste
    // hors contrat par convention.
    type = org.springframework.modulith.ApplicationModule.Type.OPEN,
    id = "core.ai",
    displayName = "core/ai",
    allowedDependencies = {"core.identity", "exception", "security", "shared"}
)
package com.onesley.oneclick.core.ai;

import org.springframework.modulith.ApplicationModule;
