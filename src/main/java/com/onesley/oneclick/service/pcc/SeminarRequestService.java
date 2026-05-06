package com.onesley.oneclick.service.pcc;

import com.onesley.oneclick.dto.pcc.SeminarRequestDto;
import com.onesley.oneclick.mapper.pcc.SeminarRequestMapper;
import com.onesley.oneclick.repository.pcc.SeminarRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link SeminarRequest} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class SeminarRequestService {

    private final SeminarRequestRepository repository;
    private final SeminarRequestMapper mapper;

    public SeminarRequestService(SeminarRequestRepository repository, SeminarRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<SeminarRequestDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<SeminarRequestDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
