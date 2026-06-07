package com.onesley.oneclick.core.tenant.api;

import java.util.Optional;
import java.util.UUID;

/**
 * API publique du domaine « invitations tenant-admin » (E2 — V78), consommée par
 * {@code core.auth} lors de l'<b>acceptation</b> d'une invitation (création du compte + JWT).
 *
 * <p>Pourquoi une API et pas l'accès direct au repo : {@code core.auth} est CLOSED et
 * n'a pas accès aux {@code internal} de {@code core.tenant}. L'acceptation (qui crée le
 * user, émet le JWT et persiste le refresh token — concerns d'auth) vit donc dans
 * {@code core.auth}, et délègue ici la lecture/écriture de l'invitation et l'assignation
 * de l'admin (concerns de {@code core.tenant}).</p>
 */
public interface TenantAdminInviteApi {

    /** Vue minimale d'une invitation exploitable (pending + non expirée). */
    record RedeemableInvite(UUID inviteId, UUID tenantId, String email, String tenantRole, UUID invitedBy) {}

    /**
     * Résout une invitation exploitable à partir du token <b>clair</b> (hashé en interne).
     * @return l'invitation si elle existe, est {@code pending} et non expirée ; sinon vide.
     */
    Optional<RedeemableInvite> findRedeemable(String rawToken);

    /** Marque l'invitation comme acceptée (single-use) par l'utilisateur donné. */
    void redeem(UUID inviteId, UUID acceptedUserId);

    /**
     * Assigne l'utilisateur comme administrateur du tenant (idempotent : no-op si déjà admin).
     * @param tenantRole rôle tenant-scope ({@code owner|admin|viewer})
     */
    void assignAdmin(UUID tenantId, UUID userId, String tenantRole, UUID invitedBy);
}
