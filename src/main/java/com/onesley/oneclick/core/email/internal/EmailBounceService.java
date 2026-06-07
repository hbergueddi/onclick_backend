package com.onesley.oneclick.core.email.internal;

import com.onesley.oneclick.core.email.api.EmailDtos.EmailBounceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service suppression-list emails (Gap #4) — porte legacy {@code record_email_bounce} /
 * {@code is_email_suppressed}.
 *
 * <ul>
 *   <li>{@link #recordBounce} — upsert par adresse (auto-suppress si permanent/complaint),
 *       appelé par le webhook Resend.</li>
 *   <li>{@link #isSuppressed} — check pré-envoi (skip si bounce permanent/complaint).</li>
 *   <li>{@link #listRecent} — lecture admin monitoring.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailBounceService {

    private final EmailBounceRepository repository;

    /** Normalise une adresse (lowercase + trim) — clé d'unicité. */
    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(String email) {
        String e = normalize(email);
        return !e.isEmpty() && repository.existsByEmailAndSuppressedTrue(e);
    }

    @Transactional(readOnly = true)
    public List<EmailBounceDto> listRecent() {
        return repository.findAllByOrderByLastBouncedAtDesc().stream()
            .map(b -> new EmailBounceDto(
                b.getEmail(), b.getBounceType(), b.getBounceReason(),
                b.isSuppressed(), b.getBounceCount(), b.getLastBouncedAt()))
            .toList();
    }

    /**
     * Enregistre un bounce (upsert par adresse). {@code permanent} ou {@code complaint}
     * → {@code suppressed = true} (jamais redescendu : une suppression reste suppressed).
     */
    @Transactional
    public void recordBounce(String email, String bounceType, String bounceReason,
                             String sourceEf, String rawEvent) {
        String e = normalize(email);
        if (e.isEmpty()) return;
        boolean shouldSuppress = "permanent".equals(bounceType) || "complaint".equals(bounceType);

        EmailBounce b = repository.findByEmail(e).orElse(null);
        if (b == null) {
            b = new EmailBounce(UUID.randomUUID(), e, bounceType);
            b.setSuppressed(shouldSuppress);
            b.setBounceReason(bounceReason);
            b.setSourceEf(sourceEf);
            b.setRawEvent(rawEvent);
            b.setLastBouncedAt(Instant.now());
            b.setBounceCount(1);
        } else {
            b.setBounceType(bounceType);
            if (bounceReason != null) b.setBounceReason(bounceReason);
            b.setSuppressed(b.isSuppressed() || shouldSuppress);
            b.setLastBouncedAt(Instant.now());
            b.setBounceCount(b.getBounceCount() + 1);
            if (rawEvent != null) b.setRawEvent(rawEvent);
        }
        repository.save(b);
        log.info("[email/bounce] recorded type={} suppressed={} email={}", bounceType, b.isSuppressed(), e);
    }
}
