package com.onesley.oneclick.modules.reservation.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleCreateDto;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRuleDto;
import com.onesley.oneclick.modules.reservation.api.BookingRuleDtos.BookingRulePatchDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service CRUD pour {@link BookingRule} — règles de réservation par restaurant.
 *
 * <p>Endpoints exposés via {@code /api/reservations/booking-rules/*} (voir
 * {@code ReservationController}). On ne cross-module pas vers {@code modules.restaurant}
 * pour vérifier l'existence du restaurant : le FK Postgres
 * {@code booking_rules.restaurant_id → restaurants(id)} fait foi à l'insertion.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookingRuleService {

    private final BookingRuleRepository repository;

    public List<BookingRuleDto> findByRestaurant(UUID restaurantId) {
        return repository.findAllByRestaurantId(restaurantId).stream()
            .map(BookingRule::toDto)
            .toList();
    }

    @Transactional
    public BookingRuleDto create(UUID restaurantId, BookingRuleCreateDto dto) {
        BookingRule rule = new BookingRule(UUID.randomUUID(), restaurantId);
        if (dto.maxGuest() != null) rule.setMaxGuest(dto.maxGuest());
        if (dto.slotDuration() != null) rule.setSlotDuration(dto.slotDuration());
        if (dto.cancellationWindowHours() != null) rule.setCancellationWindowHours(dto.cancellationWindowHours());
        return repository.save(rule).toDto();
    }

    @Transactional
    public BookingRuleDto patch(UUID id, BookingRulePatchDto dto) {
        BookingRule rule = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("BookingRule", id));
        if (dto.maxGuest() != null) rule.setMaxGuest(dto.maxGuest());
        if (dto.slotDuration() != null) rule.setSlotDuration(dto.slotDuration());
        if (dto.cancellationWindowHours() != null) rule.setCancellationWindowHours(dto.cancellationWindowHours());
        return repository.save(rule).toDto();
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("BookingRule", id);
        }
        repository.deleteById(id);
    }
}
