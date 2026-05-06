package com.onesley.oneclick.service.tenant;

import com.onesley.oneclick.dto.tenant.AnnouncementReadDto;
import com.onesley.oneclick.mapper.tenant.AnnouncementReadMapper;
import com.onesley.oneclick.repository.tenant.AnnouncementReadRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link AnnouncementRead} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class AnnouncementReadService {

    private final AnnouncementReadRepository repository;
    private final AnnouncementReadMapper mapper;

    public AnnouncementReadService(AnnouncementReadRepository repository, AnnouncementReadMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<AnnouncementReadDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
