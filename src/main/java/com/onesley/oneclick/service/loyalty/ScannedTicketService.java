package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.ScannedTicketDto;
import com.onesley.oneclick.mapper.loyalty.ScannedTicketMapper;
import com.onesley.oneclick.repository.loyalty.ScannedTicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ScannedTicket} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ScannedTicketService {

    private final ScannedTicketRepository repository;
    private final ScannedTicketMapper mapper;

    public ScannedTicketService(ScannedTicketRepository repository, ScannedTicketMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ScannedTicketDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ScannedTicketDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
