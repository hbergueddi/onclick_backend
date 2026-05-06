package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.StaffNotificationPreferenceDto;
import com.onesley.oneclick.mapper.admin.StaffNotificationPreferenceMapper;
import com.onesley.oneclick.repository.admin.StaffNotificationPreferenceRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link StaffNotificationPreference} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class StaffNotificationPreferenceService {

    private final StaffNotificationPreferenceRepository repository;
    private final StaffNotificationPreferenceMapper mapper;

    public StaffNotificationPreferenceService(StaffNotificationPreferenceRepository repository, StaffNotificationPreferenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<StaffNotificationPreferenceDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<StaffNotificationPreferenceDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
