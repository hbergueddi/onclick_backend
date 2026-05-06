package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.ContactImportEventDto;
import com.onesley.oneclick.mapper.admin.ContactImportEventMapper;
import com.onesley.oneclick.repository.admin.ContactImportEventRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ContactImportEvent} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ContactImportEventService {

    private final ContactImportEventRepository repository;
    private final ContactImportEventMapper mapper;

    public ContactImportEventService(ContactImportEventRepository repository, ContactImportEventMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ContactImportEventDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ContactImportEventDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
