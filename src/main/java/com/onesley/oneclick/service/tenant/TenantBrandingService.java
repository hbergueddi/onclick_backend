package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantBrandingDto;
import com.onesley.oneclick.mapper.tenant.TenantBrandingMapper;
import com.onesley.oneclick.repository.tenant.TenantBrandingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TenantBranding} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TenantBrandingService {

    private final TenantBrandingRepository repository;
    private final TenantBrandingMapper mapper;

    public TenantBrandingService(TenantBrandingRepository repository, TenantBrandingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TenantBrandingDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TenantBrandingDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
