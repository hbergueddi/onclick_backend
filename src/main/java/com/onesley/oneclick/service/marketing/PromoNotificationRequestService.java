package com.onesley.oneclick.service.marketing;

import com.onesley.oneclick.dto.marketing.PromoNotificationRequestDto;
import com.onesley.oneclick.mapper.marketing.PromoNotificationRequestMapper;
import com.onesley.oneclick.repository.marketing.PromoNotificationRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link PromoNotificationRequest} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class PromoNotificationRequestService {

    private final PromoNotificationRequestRepository repository;
    private final PromoNotificationRequestMapper mapper;

    public PromoNotificationRequestService(PromoNotificationRequestRepository repository, PromoNotificationRequestMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<PromoNotificationRequestDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<PromoNotificationRequestDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
