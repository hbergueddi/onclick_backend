package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.AdminNotificationDto;
import com.onesley.oneclick.mapper.admin.AdminNotificationMapper;
import com.onesley.oneclick.repository.admin.AdminNotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AdminNotification} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AdminNotificationService {

    private final AdminNotificationRepository repository;
    private final AdminNotificationMapper mapper;

    public AdminNotificationService(AdminNotificationRepository repository, AdminNotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<AdminNotificationDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<AdminNotificationDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
