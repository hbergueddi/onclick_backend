package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.ReservationGuestDto;
import com.onesley.oneclick.mapper.reservation.ReservationGuestMapper;
import com.onesley.oneclick.repository.reservation.ReservationGuestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ReservationGuest} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ReservationGuestService {

    private final ReservationGuestRepository repository;
    private final ReservationGuestMapper mapper;

    public ReservationGuestService(ReservationGuestRepository repository, ReservationGuestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ReservationGuestDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ReservationGuestDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
