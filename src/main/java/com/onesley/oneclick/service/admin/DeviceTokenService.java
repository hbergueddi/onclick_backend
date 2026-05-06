package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.DeviceTokenDto;
import com.onesley.oneclick.mapper.admin.DeviceTokenMapper;
import com.onesley.oneclick.repository.admin.DeviceTokenRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link DeviceToken} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class DeviceTokenService {

    private final DeviceTokenRepository repository;
    private final DeviceTokenMapper mapper;

    public DeviceTokenService(DeviceTokenRepository repository, DeviceTokenMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<DeviceTokenDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<DeviceTokenDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
