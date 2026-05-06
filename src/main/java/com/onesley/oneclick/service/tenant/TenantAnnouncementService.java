package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantAnnouncementDto;
import com.onesley.oneclick.mapper.tenant.TenantAnnouncementMapper;
import com.onesley.oneclick.repository.tenant.TenantAnnouncementRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TenantAnnouncement} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TenantAnnouncementService {

    private final TenantAnnouncementRepository repository;
    private final TenantAnnouncementMapper mapper;

    public TenantAnnouncementService(TenantAnnouncementRepository repository, TenantAnnouncementMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TenantAnnouncementDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TenantAnnouncementDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
