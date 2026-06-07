package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.RedemptionOtpRequestDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.RedemptionOtpRequestedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service OTP de conversion (Gap #2) — porte legacy {@code request_redemption_otp} /
 * {@code verify_redemption_otp}.
 *
 * <p>{@link #requestOtp} : génère un code à 6 chiffres, stocke son SHA-256 (5 min),
 * et publie {@link RedemptionOtpRequestedEvent} → notification in-app au client
 * (le code n'est jamais renvoyé au staff). {@link #verify} : appelé en interne par
 * {@code LoyaltyService.snap2earn} avant le débit ; renvoie un diagnostic textuel.
 */
@Service
@RequiredArgsConstructor
public class RedemptionOtpService {

    /** Diagnostics renvoyés par {@link #verify} (1:1 legacy). */
    public static final String OK = "ok";
    public static final String NOT_FOUND = "not_found";
    public static final String ALREADY_CONSUMED = "already_consumed";
    public static final String EXPIRED = "expired";
    public static final String TOO_MANY_ATTEMPTS = "too_many_attempts";
    public static final String MISMATCH = "mismatch";
    public static final String WRONG_CODE = "wrong_code";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedemptionOtpRequestRepository repository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @PersistenceContext
    private EntityManager em;

    /**
     * Crée une demande d'OTP : annule les précédentes en attente pour ce couple,
     * génère un code, le notifie au client, et renvoie l'id + l'expiration.
     */
    @Transactional
    public RedemptionOtpRequestDto requestOtp(
        UUID clientId, UUID restaurantId, int points, BigDecimal montant, BigDecimal discountDh
    ) {
        repository.cancelPending(clientId, restaurantId);

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        Instant now = clock.instant();
        Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);

        RedemptionOtpRequest req = new RedemptionOtpRequest(
            UUID.randomUUID(), now, expiresAt,
            clientId, restaurantId, SecurityHelper.currentUserId(),
            points, montant, discountDh, sha256(code)
        );
        repository.save(req);

        events.publishEvent(new RedemptionOtpRequestedEvent(
            clientId, code, resolveRestaurantName(restaurantId), points, discountDh, now));

        return new RedemptionOtpRequestDto(req.getId(), expiresAt);
    }

    /**
     * Vérifie un code contre une demande : statut, expiration, tentatives, cohérence.
     * Marque la demande {@code consumed} en cas de succès. Renvoie un diagnostic.
     */
    @Transactional
    public String verify(
        UUID requestId, String code, UUID clientId, UUID restaurantId, int points, String ticketRef
    ) {
        RedemptionOtpRequest req = repository.findById(requestId).orElse(null);
        if (req == null) return NOT_FOUND;
        if ("consumed".equals(req.getStatus())) return ALREADY_CONSUMED;

        if (!req.getExpiresAt().isAfter(clock.instant())) {
            if ("pending".equals(req.getStatus())) req.setStatus("expired");
            return EXPIRED;
        }
        if (req.getAttempts() >= 3) {
            if ("pending".equals(req.getStatus())) req.setStatus("cancelled");
            return TOO_MANY_ATTEMPTS;
        }
        if (!req.getClientId().equals(clientId) || !req.getRestaurantId().equals(restaurantId)
            || points > req.getPointsRequested()) {
            return MISMATCH;
        }
        if (!sha256(code).equals(req.getCodeHash())) {
            req.setAttempts(req.getAttempts() + 1);
            return WRONG_CODE;
        }

        req.setStatus("consumed");
        req.setConsumedAt(clock.instant());
        req.setConsumedForTicketRef(ticketRef);
        return OK;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String resolveRestaurantName(UUID restaurantId) {
        try {
            Object name = em.createNativeQuery("SELECT name FROM restaurants WHERE id = :id")
                .setParameter("id", restaurantId)
                .getSingleResult();
            return name != null ? name.toString() : "le restaurant";
        } catch (RuntimeException e) {
            return "le restaurant";
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
