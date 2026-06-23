package com.onesley.oneclick.core.auth.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Purge des comptes restés {@code pending_email_verification} (A3 — anti-bot signup).
 *
 * <p>Quand {@code app.auth.email-verification-required=true}, un signup public crée le compte
 * en {@code pending_email_verification} jusqu'à validation de l'OTP email. Un bot (ou un user
 * qui abandonne) laisse une ligne {@code users} jamais activée. Ce cron supprime ces comptes
 * au-delà de {@code app.auth.pending-verification-ttl-hours} (défaut 24 h).
 *
 * <p><b>Hard delete</b> assumé : un compte pending n'a jamais pu se connecter (login gaté par
 * {@code EmailNotVerifiedException}) ni rien créer ; ses seules lignes liées sont ses
 * {@code otp_requests} (purgées d'abord pour la FK). Bénéfices : libère l'email/téléphone
 * (uniques) pour une ré-inscription légitime + évite le bloat DB par les bots.
 *
 * <p>Inerte tant que le flag est {@code false} : aucun compte n'atteint le statut pending,
 * donc {@code findStalePendingVerification} renvoie toujours vide (no-op, 0 row loggé).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PendingSignupPurgeJob {

    private final UserRepository userRepository;
    private final OtpRequestRepository otpRequestRepository;

    @Value("${app.auth.pending-verification-ttl-hours:24}")
    private long ttlHours;

    /** Toutes les heures à HH:17 (offset arbitraire anti thundering-herd). */
    @Scheduled(cron = "0 17 * * * *")
    @Transactional
    public void purgeStalePendingSignups() {
        Instant cutoff = Instant.now().minus(ttlHours, ChronoUnit.HOURS);
        List<User> stale = userRepository.findStalePendingVerification(
            User.STATUS_PENDING_EMAIL_VERIFICATION, cutoff);
        if (stale.isEmpty()) {
            return;
        }
        log.info("[purge-pending-signup] {} compte(s) pending_email_verification > {}h → suppression",
            stale.size(), ttlHours);
        for (User u : stale) {
            otpRequestRepository.deleteByUserId(u.getId());
            userRepository.delete(u);
        }
        log.info("[purge-pending-signup] terminé — {} compte(s) supprimé(s)", stale.size());
    }
}
