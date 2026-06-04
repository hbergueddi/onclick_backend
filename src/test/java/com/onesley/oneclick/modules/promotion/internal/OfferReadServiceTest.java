package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.promotion.api.OfferReadDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link OfferReadService} (L3 — modules.promotion,
 * suivi lu/non-lu des offres, V63).
 *
 * <p>Couvre : upsert idempotent (création vs ré-écriture {@code read_at}),
 * self-scope (l'écriture/lecture portent TOUJOURS sur {@code currentUserId}, jamais
 * un id arbitraire → pas d'IDOR), 404 si l'offre n'existe pas.</p>
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class OfferReadServiceTest {

    @Mock OfferReadRepository readRepository;
    @Mock OfferRepository offerRepository;
    @InjectMocks OfferReadService service;

    private static final UUID CURRENT_USER = UUID.randomUUID();

    @BeforeEach
    void setup() {
        lenient().when(readRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private Offer existingOffer(UUID id) {
        Offer o = new Offer(id, UUID.randomUUID(), "Promo", Instant.now(), Instant.now().plusSeconds(86400));
        return o;
    }

    // ─── markRead : offre inexistante → 404 (et aucune écriture) ──────────────

    @Test
    void markRead_offerNotFound_throwsNotFound_noWrite() {
        when(offerRepository.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            assertThatThrownBy(() -> service.markRead(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
        }
        verify(readRepository, never()).save(any());
    }

    @Test
    void markRead_offerSoftDeleted_throwsNotFound() {
        UUID offerId = UUID.randomUUID();
        Offer deleted = existingOffer(offerId);
        deleted.markDeleted();
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(deleted));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            assertThatThrownBy(() -> service.markRead(offerId)).isInstanceOf(NotFoundException.class);
        }
        verify(readRepository, never()).save(any());
    }

    // ─── markRead : 1er marquage → crée une ligne pour le user COURANT ────────

    @Test
    void markRead_firstTime_createsRowForCurrentUser() {
        UUID offerId = UUID.randomUUID();
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(existingOffer(offerId)));
        when(readRepository.findByUserIdAndOfferId(CURRENT_USER, offerId)).thenReturn(Optional.empty());

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            OfferReadDto dto = service.markRead(offerId);

            assertThat(dto.offerId()).isEqualTo(offerId);
            // Self-scope : le user du DTO est l'utilisateur COURANT (jamais un arg).
            assertThat(dto.userId()).isEqualTo(CURRENT_USER);
            assertThat(dto.readAt()).isNotNull();
        }

        ArgumentCaptor<OfferRead> captor = ArgumentCaptor.forClass(OfferRead.class);
        verify(readRepository).save(captor.capture());
        OfferRead saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(CURRENT_USER);
        assertThat(saved.getOfferId()).isEqualTo(offerId);
        assertThat(saved.getReadAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // ─── markRead : ré-écriture idempotente → réutilise la ligne, MAJ read_at ─

    @Test
    void markRead_idempotent_reusesRow_updatesReadAt_noDuplicate() {
        UUID offerId = UUID.randomUUID();
        Instant firstRead = Instant.parse("2026-06-01T08:00:00Z");
        Instant created = Instant.parse("2026-06-01T08:00:00Z");
        OfferRead existing = new OfferRead(UUID.randomUUID(), CURRENT_USER, offerId, firstRead, created);

        when(offerRepository.findById(offerId)).thenReturn(Optional.of(existingOffer(offerId)));
        when(readRepository.findByUserIdAndOfferId(CURRENT_USER, offerId)).thenReturn(Optional.of(existing));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            OfferReadDto dto = service.markRead(offerId);
            // read_at avancé (re-lecture)…
            assertThat(dto.readAt()).isAfter(firstRead);
            // …mais created_at INCHANGÉ (1er marquage figé).
            assertThat(dto.createdAt()).isEqualTo(created);
        }

        // La MÊME ligne est ré-enregistrée (pas de nouvelle entité → pas de doublon).
        ArgumentCaptor<OfferRead> captor = ArgumentCaptor.forClass(OfferRead.class);
        verify(readRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getReadAt()).isAfter(firstRead);
    }

    // ─── markRead : ne lit JAMAIS la ligne d'un autre user (self-scope) ───────

    @Test
    void markRead_alwaysScopedToCurrentUser_neverArbitrary() {
        UUID offerId = UUID.randomUUID();
        when(offerRepository.findById(offerId)).thenReturn(Optional.of(existingOffer(offerId)));
        when(readRepository.findByUserIdAndOfferId(any(), any())).thenReturn(Optional.empty());

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            service.markRead(offerId);
        }
        // Le lookup d'upsert est borné au user courant, jamais à un autre id.
        verify(readRepository).findByUserIdAndOfferId(eq(CURRENT_USER), eq(offerId));
    }

    // ─── listReadOfferIds : délègue au user courant ──────────────────────────

    @Test
    void listReadOfferIds_delegatesToCurrentUser() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(readRepository.findOfferIdsByUserId(CURRENT_USER)).thenReturn(List.of(a, b));

        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(CURRENT_USER);
            assertThat(service.listReadOfferIds()).containsExactly(a, b);
        }
        verify(readRepository).findOfferIdsByUserId(CURRENT_USER);
    }
}
