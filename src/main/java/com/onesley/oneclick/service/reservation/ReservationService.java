package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.ReservationDto;
import com.onesley.oneclick.entity.shared.ReservationStatus;
import com.onesley.oneclick.mapper.reservation.ReservationMapper;
import com.onesley.oneclick.repository.reservation.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ReservationService {

    private final ReservationRepository repository;
    private final ReservationMapper mapper;

    public ReservationService(ReservationRepository repository, ReservationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ReservationDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ReservationDto> findByClient(UUID clientId) {
        return mapper.toDtoList(repository.findAllByClientIdOrderByDateDesc(clientId));
    }

    public List<ReservationDto> findByRestaurantAndDate(UUID restaurantId, LocalDate date) {
        return mapper.toDtoList(repository.findAllByRestaurantIdAndDate(restaurantId, date));
    }

    public List<ReservationDto> findByStatus(ReservationStatus status) {
        return mapper.toDtoList(repository.findAllByStatus(status));
    }
}
