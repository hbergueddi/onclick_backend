package com.onesley.oneclick.core.tenant.internal;

import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteApi;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteDtos.CreateInviteDto;
import com.onesley.oneclick.core.tenant.api.TenantAdminInviteDtos.InviteDto;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.TenantAdminInvitedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Invitations tenant-admin par email (E2 — V78). Implémente {@link TenantAdminInviteApi}
 * (consommée par {@code core.auth} à l'acceptation) + création/liste/révocation (SUPERADMIN).
 *
 * <p><b>Sécurité du token</b> : on génère un token clair de 32 octets ({@link SecureRandom}),
 * on ne persiste que son SHA-256, et on ne renvoie le token clair que dans l'event
 * {@link TenantAdminInvitedEvent} (consommé par le listener email pour composer le lien).
 * Single-use (statut {@code pending → accepted}) + expiration 7 jours.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class TenantAdminInviteService implements TenantAdminInviteApi {

    private static final Duration TTL = Duration.ofDays(7);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TenantAdminInviteRepository repo;
    private final TenantRepository tenantRepository;
    private final TenantAdminRepository tenantAdminRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    // ─── Côté SUPERADMIN (portail) ────────────────────────────────────────────

    @Transactional
    public InviteDto createInvite(UUID tenantId, CreateInviteDto dto) {
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new NotFoundException("Tenant", tenantId));
        String role = (dto.role() == null || dto.role().isBlank()) ? "admin" : dto.role();
        String rawToken = randomToken();
        Instant now = clock.instant();
        Instant expiresAt = now.plus(TTL);

        TenantAdminInvite invite = new TenantAdminInvite(
            UUID.randomUUID(), tenantId, dto.email().trim(), sha256Hex(rawToken),
            role, SecurityHelper.currentUserId(), expiresAt);
        TenantAdminInvite saved = repo.save(invite);

        // Email envoyé après commit par le listener core/email (frontière Modulith respectée).
        events.publishEvent(new TenantAdminInvitedEvent(
            saved.getId(), tenantId, tenant.getSlug(), tenant.getName(),
            saved.getEmail(), rawToken, expiresAt, now));

        log.info("[tenant-admin-invite] created id={} tenant={} email={}",
            saved.getId(), tenantId, saved.getEmail());
        return toDto(saved);
    }

    public List<InviteDto> listInvites(UUID tenantId) {
        return repo.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(this::toDto).toList();
    }

    @Transactional
    public void revokeInvite(UUID tenantId, UUID inviteId) {
        TenantAdminInvite invite = repo.findById(inviteId)
            .filter(i -> i.getTenantId().equals(tenantId))
            .orElseThrow(() -> new NotFoundException("TenantAdminInvite", inviteId));
        if ("accepted".equals(invite.getStatus())) {
            throw new ConflictException("Invitation déjà acceptée — révocation impossible.");
        }
        invite.setStatus("revoked");
        repo.save(invite);
    }

    // ─── TenantAdminInviteApi (consommé par core.auth à l'acceptation) ─────────

    @Override
    public Optional<RedeemableInvite> findRedeemable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return Optional.empty();
        return repo.findByTokenHash(sha256Hex(rawToken))
            .filter(i -> i.isRedeemable(clock.instant()))
            .map(i -> new RedeemableInvite(
                i.getId(), i.getTenantId(), i.getEmail(), i.getTenantRole(), i.getInvitedBy()));
    }

    @Override
    @Transactional
    public void redeem(UUID inviteId, UUID acceptedUserId) {
        TenantAdminInvite invite = repo.findById(inviteId)
            .orElseThrow(() -> new NotFoundException("TenantAdminInvite", inviteId));
        invite.setStatus("accepted");
        invite.setAcceptedAt(clock.instant());
        invite.setAcceptedUserId(acceptedUserId);
        repo.save(invite);
    }

    @Override
    @Transactional
    public void assignAdmin(UUID tenantId, UUID userId, String tenantRole, UUID invitedBy) {
        if (tenantAdminRepository.existsByTenantIdAndUserId(tenantId, userId)) return; // idempotent
        tenantAdminRepository.save(new TenantAdmin(
            UUID.randomUUID(), tenantId, userId, tenantRole, invitedBy));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private InviteDto toDto(TenantAdminInvite i) {
        return new InviteDto(i.getId(), i.getTenantId(), i.getEmail(), i.getTenantRole(),
            i.getStatus(), i.getInvitedBy(), i.getExpiresAt(), i.getAcceptedAt(), i.getCreatedAt());
    }

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
