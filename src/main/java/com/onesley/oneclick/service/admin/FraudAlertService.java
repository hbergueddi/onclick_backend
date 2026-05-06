package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.FraudAlertDto;
import com.onesley.oneclick.mapper.admin.FraudAlertMapper;
import com.onesley.oneclick.repository.admin.FraudAlertRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link FraudAlert} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class FraudAlertService {

    private final FraudAlertRepository repository;
    private final FraudAlertMapper mapper;

    public FraudAlertService(FraudAlertRepository repository, FraudAlertMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<FraudAlertDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<FraudAlertDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
