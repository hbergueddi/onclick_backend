package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.modules.loyalty.api.RedemptionOtpRequestDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.RedemptionOtpRequestedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link RedemptionOtpService} (Gap #2). Repos + EM +
 * publisher + Clock mockés, {@link SecurityHelper} stubbé en statique.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RedemptionOtpServiceTest {

    @Mock RedemptionOtpRequestRepository repository;
    @Mock ApplicationEventPublisher events;
    @Mock EntityManager em;
    @InjectMocks RedemptionOtpService service;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-06-07T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "clock", fixedClock);
        ReflectionTestUtils.setField(service, "em", em);
    }

    private static String sha256(String v) throws Exception {
        return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));
    }

    private RedemptionOtpRequest req(String codeHash, String status, int attempts, Instant expiresAt,
                                     UUID clientId, UUID restaurantId, int pointsRequested) {
        RedemptionOtpRequest r = new RedemptionOtpRequest(
            UUID.randomUUID(), fixedClock.instant(), expiresAt,
            clientId, restaurantId, UUID.randomUUID(),
            pointsRequested, new BigDecimal("200"), new BigDecimal("10"), codeHash);
        r.setStatus(status);
        r.setAttempts(attempts);
        return r;
    }

    @Test
    void requestOtp_generatesCode_savesAndPublishesEvent() {
        UUID client = UUID.randomUUID();
        UUID resto = UUID.randomUUID();
        UUID staff = UUID.randomUUID();
        Query q = org.mockito.Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(q);
        when(q.setParameter(anyString(), any())).thenReturn(q);
        when(q.getSingleResult()).thenReturn("Le Resto");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(staff);

            RedemptionOtpRequestDto dto = service.requestOtp(
                client, resto, 300, new BigDecimal("400"), new BigDecimal("30"));

            assertThat(dto.requestId()).isNotNull();
            assertThat(dto.expiresAt()).isEqualTo(fixedClock.instant().plusSeconds(300));
            verify(repository).cancelPending(client, resto);

            ArgumentCaptor<RedemptionOtpRequest> saved = ArgumentCaptor.forClass(RedemptionOtpRequest.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getStaffId()).isEqualTo(staff);
            assertThat(saved.getValue().getCodeHash()).hasSize(64); // SHA-256 hex

            ArgumentCaptor<RedemptionOtpRequestedEvent> ev = ArgumentCaptor.forClass(RedemptionOtpRequestedEvent.class);
            verify(events).publishEvent(ev.capture());
            assertThat(ev.getValue().clientId()).isEqualTo(client);
            assertThat(ev.getValue().code()).hasSize(6).matches("\\d{6}");
            assertThat(ev.getValue().restaurantName()).isEqualTo("Le Resto");
        }
    }

    @Test
    void verify_okOnCorrectCode_marksConsumed() throws Exception {
        UUID client = UUID.randomUUID();
        UUID resto = UUID.randomUUID();
        RedemptionOtpRequest r = req(sha256("123456"), "pending", 0,
            fixedClock.instant().plusSeconds(120), client, resto, 50);
        when(repository.findById(any())).thenReturn(Optional.of(r));

        String verdict = service.verify(UUID.randomUUID(), "123456", client, resto, 40, "T-1");

        assertThat(verdict).isEqualTo(RedemptionOtpService.OK);
        assertThat(r.getStatus()).isEqualTo("consumed");
        assertThat(r.getConsumedForTicketRef()).isEqualTo("T-1");
    }

    @Test
    void verify_wrongCode_incrementsAttempts() throws Exception {
        RedemptionOtpRequest r = req(sha256("123456"), "pending", 0,
            fixedClock.instant().plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 50);
        UUID client = r.getClientId();
        UUID resto = r.getRestaurantId();
        when(repository.findById(any())).thenReturn(Optional.of(r));

        String verdict = service.verify(UUID.randomUUID(), "000000", client, resto, 40, "T");

        assertThat(verdict).isEqualTo(RedemptionOtpService.WRONG_CODE);
        assertThat(r.getAttempts()).isEqualTo(1);
        assertThat(r.getStatus()).isEqualTo("pending");
    }

    @Test
    void verify_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThat(service.verify(UUID.randomUUID(), "1", UUID.randomUUID(), UUID.randomUUID(), 1, "T"))
            .isEqualTo(RedemptionOtpService.NOT_FOUND);
    }

    @Test
    void verify_alreadyConsumed() throws Exception {
        RedemptionOtpRequest r = req(sha256("123456"), "consumed", 0,
            fixedClock.instant().plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 50);
        when(repository.findById(any())).thenReturn(Optional.of(r));
        assertThat(service.verify(UUID.randomUUID(), "123456", r.getClientId(), r.getRestaurantId(), 40, "T"))
            .isEqualTo(RedemptionOtpService.ALREADY_CONSUMED);
    }

    @Test
    void verify_expired_setsExpiredStatus() throws Exception {
        RedemptionOtpRequest r = req(sha256("123456"), "pending", 0,
            fixedClock.instant().minusSeconds(1), UUID.randomUUID(), UUID.randomUUID(), 50);
        when(repository.findById(any())).thenReturn(Optional.of(r));
        assertThat(service.verify(UUID.randomUUID(), "123456", r.getClientId(), r.getRestaurantId(), 40, "T"))
            .isEqualTo(RedemptionOtpService.EXPIRED);
        assertThat(r.getStatus()).isEqualTo("expired");
    }

    @Test
    void verify_tooManyAttempts() throws Exception {
        RedemptionOtpRequest r = req(sha256("123456"), "pending", 3,
            fixedClock.instant().plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 50);
        when(repository.findById(any())).thenReturn(Optional.of(r));
        assertThat(service.verify(UUID.randomUUID(), "123456", r.getClientId(), r.getRestaurantId(), 40, "T"))
            .isEqualTo(RedemptionOtpService.TOO_MANY_ATTEMPTS);
    }

    @Test
    void verify_mismatchOnClientOrPoints() throws Exception {
        RedemptionOtpRequest r = req(sha256("123456"), "pending", 0,
            fixedClock.instant().plusSeconds(120), UUID.randomUUID(), UUID.randomUUID(), 50);
        when(repository.findById(any())).thenReturn(Optional.of(r));
        // points demandés (60) > points autorisés (50) → mismatch
        assertThat(service.verify(UUID.randomUUID(), "123456", r.getClientId(), r.getRestaurantId(), 60, "T"))
            .isEqualTo(RedemptionOtpService.MISMATCH);
        // client différent → mismatch
        assertThat(service.verify(UUID.randomUUID(), "123456", UUID.randomUUID(), r.getRestaurantId(), 40, "T"))
            .isEqualTo(RedemptionOtpService.MISMATCH);
    }
}
