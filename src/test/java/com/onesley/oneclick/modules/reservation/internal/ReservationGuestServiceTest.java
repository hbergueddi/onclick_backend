package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.ReservationGuestDto;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link ReservationGuestService} (L3 — modules.reservation). */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ReservationGuestServiceTest {

    @Mock ReservationGuestRepository repository;
    @Mock ReservationRepository reservationRepository;
    @Mock EntityManager em;
    @InjectMocks ReservationGuestService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ReservationGuest guest(String status) {
        return new ReservationGuest(UUID.randomUUID(), mock(Reservation.class), null, "Invité", null, null, status);
    }

    @Test
    void findByReservation_andByGuestUser_map() {
        when(repository.findAllByReservationId(any())).thenReturn(List.of(guest("invited")));
        when(repository.findAllByGuestUserId(any())).thenReturn(List.of(guest("accepted")));
        assertThat(service.findByReservation(UUID.randomUUID())).hasSize(1);
        assertThat(service.findByGuestUser(UUID.randomUUID())).hasSize(1);
    }

    @Test
    void invite_noIdentifier_throwsBadRequest() {
        assertThatThrownBy(() -> service.invite(UUID.randomUUID(),
            new ReservationGuestDto.CreateDto(null, null, null, null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void invite_reservationNotFound_throwsNotFound() {
        when(reservationRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.invite(UUID.randomUUID(),
            new ReservationGuestDto.CreateDto(null, "Invité L4", null, null, "invited")))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void invite_duplicateGuestUser_throwsConflict() {
        UUID resId = UUID.randomUUID(), guestUserId = UUID.randomUUID();
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(mock(Reservation.class)));
        ReservationGuest existing = mock(ReservationGuest.class);
        when(existing.getGuestUserId()).thenReturn(guestUserId);
        when(repository.findAllByReservationId(resId)).thenReturn(List.of(existing));
        assertThatThrownBy(() -> service.invite(resId,
            new ReservationGuestDto.CreateDto(guestUserId, null, null, null, "invited")))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void invite_success() {
        UUID resId = UUID.randomUUID();
        when(reservationRepository.findById(resId)).thenReturn(Optional.of(mock(Reservation.class)));
        when(repository.findAllByReservationId(resId)).thenReturn(List.of());
        assertThat(service.invite(resId,
            new ReservationGuestDto.CreateDto(UUID.randomUUID(), "Invité L4", "0600", UUID.randomUUID(), "invited"))).isNotNull();
    }

    @Test
    void updateStatus_invalid_notFound_success() {
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
            new ReservationGuestDto.StatusUpdateDto("statut_bidon"))).isInstanceOf(BadRequestException.class);

        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
            new ReservationGuestDto.StatusUpdateDto("accepted"))).isInstanceOf(NotFoundException.class);

        ReservationGuest g = guest("invited");
        when(repository.findById(g.getId())).thenReturn(Optional.of(g));
        service.updateStatus(g.getId(), new ReservationGuestDto.StatusUpdateDto("accepted"));
        assertThat(g.getStatus()).isEqualTo("accepted");
    }

    @Test
    void markSeen_andDelete() {
        when(repository.findAllByReservationId(any())).thenReturn(List.of(guest("invited")));
        service.markSeen(UUID.randomUUID());

        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        ReservationGuest g = guest("invited");
        when(repository.findById(g.getId())).thenReturn(Optional.of(g));
        service.delete(g.getId());
        verify(repository).delete(g);
    }
}
