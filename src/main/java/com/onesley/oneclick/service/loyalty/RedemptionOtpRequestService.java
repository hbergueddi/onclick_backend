package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.RedemptionOtpRequestDto;
import com.onesley.oneclick.mapper.loyalty.RedemptionOtpRequestMapper;
import com.onesley.oneclick.repository.loyalty.RedemptionOtpRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link RedemptionOtpRequest} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class RedemptionOtpRequestService {

    private final RedemptionOtpRequestRepository repository;
    private final RedemptionOtpRequestMapper mapper;

    public RedemptionOtpRequestService(RedemptionOtpRequestRepository repository, RedemptionOtpRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<RedemptionOtpRequestDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<RedemptionOtpRequestDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
