package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantAdminDto;
import com.onesley.oneclick.mapper.tenant.TenantAdminMapper;
import com.onesley.oneclick.repository.tenant.TenantAdminRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantAdminService {

    private final TenantAdminRepository repository;
    private final TenantAdminMapper mapper;

    public TenantAdminService(TenantAdminRepository repository, TenantAdminMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<TenantAdminDto> findByTenant(UUID tenantId) {
        return mapper.toDtoList(repository.findAllByTenantId(tenantId));
    }

    public List<TenantAdminDto> findByUser(UUID userId) {
        return mapper.toDtoList(repository.findAllByUserId(userId));
    }

    public Optional<TenantAdminDto> find(UUID tenantId, UUID userId) {
        return repository.findByTenantIdAndUserId(tenantId, userId).map(mapper::toDto);
    }

    public boolean isAdmin(UUID tenantId, UUID userId) {
        return repository.existsByTenantIdAndUserId(tenantId, userId);
    }
}
