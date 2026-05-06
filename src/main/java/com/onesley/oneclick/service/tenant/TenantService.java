package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.TenantDto;
import com.onesley.oneclick.mapper.tenant.TenantMapper;
import com.onesley.oneclick.repository.tenant.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository repository;
    private final TenantMapper mapper;

    public TenantService(TenantRepository repository, TenantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TenantDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public Optional<TenantDto> findBySlug(String slug) {
        return repository.findBySlug(slug).map(mapper::toDto);
    }

    public List<TenantDto> findAllActive() {
        return mapper.toDtoList(repository.findAllByStatus("actif"));
    }

    public List<TenantDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
