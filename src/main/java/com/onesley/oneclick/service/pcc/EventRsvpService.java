package com.onesley.oneclick.service.pcc;

import com.onesley.oneclick.dto.pcc.EventRsvpDto;
import com.onesley.oneclick.mapper.pcc.EventRsvpMapper;
import com.onesley.oneclick.repository.pcc.EventRsvpRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link EventRsvp} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class EventRsvpService {

    private final EventRsvpRepository repository;
    private final EventRsvpMapper mapper;

    public EventRsvpService(EventRsvpRepository repository, EventRsvpMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<EventRsvpDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<EventRsvpDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
