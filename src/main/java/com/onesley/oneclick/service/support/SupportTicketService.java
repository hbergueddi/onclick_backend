package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.SupportTicketDto;
import com.onesley.oneclick.mapper.support.SupportTicketMapper;
import com.onesley.oneclick.repository.support.SupportTicketRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link SupportTicket} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class SupportTicketService {

    private final SupportTicketRepository repository;
    private final SupportTicketMapper mapper;

    public SupportTicketService(SupportTicketRepository repository, SupportTicketMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<SupportTicketDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<SupportTicketDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
