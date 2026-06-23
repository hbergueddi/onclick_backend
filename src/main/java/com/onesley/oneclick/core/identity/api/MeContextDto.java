package com.onesley.oneclick.core.identity.api;

import java.util.List;
import java.util.UUID;

/**
 * Contexte d'amorçage du front pour l'utilisateur courant, <strong>consolidé</strong>
 * en une seule réponse : profil + rôle + menus (sidebar) + permissions.
 *
 * <p>Servi par {@code GET /api/users/me/context}. Construit par le bounded
 * context <em>identity</em> depuis son propre {@code UserRepository} (graphe
 * {@code role → permissions → menu/action}) — <strong>sans</strong> dépendre de
 * l'adaptateur Spring Security ({@code OneClickUserDetails}). La sécurité reste
 * une infra qui consomme identity, jamais l'inverse.
 */
public record MeContextDto(
    UserSummary user,
    RoleSummary role,
    List<MenuSummary> menus,
    List<String> permissions
) {
    /** Profil (sans mot de passe ni champs auth techniques). */
    public record UserSummary(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        String avatarUrl,
        String city,
        List<String> allergens,
        String language,
        String status,
        UUID tenantId,
        /**
         * Slug stable du tenant du compte (ex. {@code oneclick}, {@code palmeraie}, {@code homu}),
         * ou {@code null} pour un user global (admin plateforme). <b>Additif</b> au {@link #tenantId}
         * (UUID) : permet aux clients (ex. app Store staff multi-tenant) de faire du gating UI par
         * slug sans figer le tenant au build, et sans embarquer une table UUID→slug côté client.
         * La sécurité reste serveur (ABAC par JWT) — ce champ ne sert qu'au confort d'affichage.
         */
        String tenantSlug,
        /**
         * BE-3 — {@code true} si le compte a été provisionné avec un mot de passe temporaire et
         * doit en définir un nouveau avant d'accéder à l'app (1re connexion d'un restaurateur
         * approuvé). Le client force alors l'écran « définir mon mot de passe ». Remis à
         * {@code false} dès le changement effectif (cf. {@code UserService.changePassword}).
         */
        boolean passwordMustChange
    ) {}

    public record RoleSummary(String code, String name) {}

    /** Item de menu accessible au rôle (sidebar) — {@code parentId} pour l'arbre. */
    public record MenuSummary(
        UUID id,
        String code,
        String name,
        String icon,
        String path,
        UUID parentId,
        Integer sortOrder
    ) {}
}
