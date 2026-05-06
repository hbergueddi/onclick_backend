package com.onesley.oneclick.service.pcc;

import com.onesley.oneclick.dto.pcc.PccFeedbackDto;
import com.onesley.oneclick.mapper.pcc.PccFeedbackMapper;
import com.onesley.oneclick.repository.pcc.PccFeedbackRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link PccFeedback} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class PccFeedbackService {

    private final PccFeedbackRepository repository;
    private final PccFeedbackMapper mapper;

    public PccFeedbackService(PccFeedbackRepository repository, PccFeedbackMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<PccFeedbackDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<PccFeedbackDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
