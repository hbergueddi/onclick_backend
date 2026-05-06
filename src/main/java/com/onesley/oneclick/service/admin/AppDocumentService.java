package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.AppDocumentDto;
import com.onesley.oneclick.mapper.admin.AppDocumentMapper;
import com.onesley.oneclick.repository.admin.AppDocumentRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AppDocument} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AppDocumentService {

    private final AppDocumentRepository repository;
    private final AppDocumentMapper mapper;

    public AppDocumentService(AppDocumentRepository repository, AppDocumentMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AppDocumentDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AppDocumentDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
