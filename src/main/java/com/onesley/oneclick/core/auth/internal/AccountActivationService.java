package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.shared.events.MemberEnrollmentInvitedEvent;
import com.onesley.oneclick.shared.events.MemberEnrollmentRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Invitations d'activation de compte membre (Gap #10, V88).
 *
 * <p>Pendant du {@code TenantAdminInviteService} (E2) pour le flux « inscrire membre » :
 * <ul>
 *   <li><b>Création</b> — écoute {@link MemberEnrollmentRequestedEvent} (publié par
 *       {@code modules.loyalty} quand un staff inscrit un NOUVEAU membre avec
 *       {@code sendInvite=true}), persiste une invitation (token clair → SHA-256,
 *       single-use, TTL 7j) et ré-émet {@link MemberEnrollmentInvitedEvent} (porteur
 *       du token clair) pour le listener email.</li>
 *   <li><b>Acceptation</b> — {@link #findRedeemable}/{@link #redeem} consommés par
 *       {@link AuthService#acceptActivationInvite} (même module {@code internal}).</li>
 * </ul>
 *
 * <p>Découplage Modulith : loyalty → auth → email passent exclusivement par
 * {@code shared.events} (module OPEN). Aucune dépendance compile-time croisée.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class AccountActivationService {

    /** Durée de validité du lien magique. */
    static final Duration TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountActivationInviteRepository repo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ─── Création (déclenchée par l'enrôlement) ─────────────────────────────────

    /**
     * Crée l'invitation d'activation après l'inscription d'un nouveau membre, puis
     * publie l'event email. {@link ApplicationModuleListener} → exécution APRÈS commit
     * de la transaction d'enrôlement, dans sa propre transaction (frontière respectée).
     *
     * <p>Idempotent best-effort : si une invitation {@code pending} existe déjà pour ce
     * user, on n'en recrée pas (re-déclenchement éventuel de l'enrôlement). Le token
     * précédent reste valide.</p>
     */
    @ApplicationModuleListener // amène déjà une transaction REQUIRES_NEW après commit
    public void onMemberEnrollmentRequested(MemberEnrollmentRequestedEvent ev) {
        if (repo.existsByUserIdAndStatus(ev.userId(), "pending")) {
            log.info("[account-activation] pending invite already exists user={} — skip", ev.userId());
            return;
        }
        String rawToken = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(TTL);

        AccountActivationInvite invite = new AccountActivationInvite(
            UUID.randomUUID(), ev.userId(), ev.email(), sha256Hex(rawToken),
            ev.invitedBy(), expiresAt);
        AccountActivationInvite saved = repo.save(invite);

        events.publishEvent(new MemberEnrollmentInvitedEvent(
            saved.getId(), ev.userId(), ev.email(), ev.firstName(),
            ev.tenantSlug(), ev.tenantName(), rawToken, expiresAt, now));

        log.info("[account-activation] invite created id={} user={} email={}",
            saved.getId(), ev.userId(), ev.email());
    }

    // ─── Acceptation (consommé par AuthService, même module) ────────────────────

    /** Vue minimale d'une invitation exploitable (pending + non expirée). */
    record RedeemableInvite(UUID inviteId, UUID userId, String email) {}

    /**
     * Résout une invitation exploitable depuis le token <b>clair</b> (hashé en interne).
     * @return l'invitation si elle existe, est {@code pending} et non expirée ; sinon vide.
     */
    Optional<RedeemableInvite> findRedeemable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return repo.findByTokenHash(sha256Hex(rawToken))
            .filter(i -> i.isRedeemable(clock.instant()))
            .map(i -> new RedeemableInvite(i.getId(), i.getUserId(), i.getEmail()));
    }

    /** Marque l'invitation comme acceptée (single-use). */
    @Transactional
    void redeem(UUID inviteId) {
        AccountActivationInvite invite = repo.findById(inviteId)
            .orElseThrow(() -> new NotFoundException("AccountActivationInvite", inviteId));
        invite.setStatus("accepted");
        invite.setAcceptedAt(clock.instant());
        repo.save(invite);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private static String randomToken() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /** SHA-256 hex d'un token clair (lookup + stockage). Package-private pour les tests. */
    static String sha256Hex(String raw) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(h.length * 2);
            for (byte b : h) sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e); // jamais en pratique
        }
    }
}
