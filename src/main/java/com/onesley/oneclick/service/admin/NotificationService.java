package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.NotificationDto;
import com.onesley.oneclick.mapper.admin.NotificationMapper;
import com.onesley.oneclick.repository.admin.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link Notification} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationMapper mapper;

    public NotificationService(NotificationRepository repository, NotificationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<NotificationDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<NotificationDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
