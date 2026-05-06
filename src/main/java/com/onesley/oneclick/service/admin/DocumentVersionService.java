package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.DocumentVersionDto;
import com.onesley.oneclick.mapper.admin.DocumentVersionMapper;
import com.onesley.oneclick.repository.admin.DocumentVersionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link DocumentVersion} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class DocumentVersionService {

    private final DocumentVersionRepository repository;
    private final DocumentVersionMapper mapper;

    public DocumentVersionService(DocumentVersionRepository repository, DocumentVersionMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<DocumentVersionDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<DocumentVersionDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
