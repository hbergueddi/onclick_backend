package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantFeatureDto;
import com.onesley.oneclick.mapper.tenant.TenantFeatureMapper;
import com.onesley.oneclick.repository.tenant.TenantFeatureRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TenantFeature} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TenantFeatureService {

    private final TenantFeatureRepository repository;
    private final TenantFeatureMapper mapper;

    public TenantFeatureService(TenantFeatureRepository repository, TenantFeatureMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<TenantFeatureDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
