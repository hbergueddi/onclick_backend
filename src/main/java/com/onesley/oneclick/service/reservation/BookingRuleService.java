package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.BookingRuleDto;
import com.onesley.oneclick.mapper.reservation.BookingRuleMapper;
import com.onesley.oneclick.repository.reservation.BookingRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link BookingRule} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class BookingRuleService {

    private final BookingRuleRepository repository;
    private final BookingRuleMapper mapper;

    public BookingRuleService(BookingRuleRepository repository, BookingRuleMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<BookingRuleDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<BookingRuleDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
