package com.onesley.oneclick.service.pcc;

import com.onesley.oneclick.dto.pcc.ResourceBookingDto;
import com.onesley.oneclick.mapper.pcc.ResourceBookingMapper;
import com.onesley.oneclick.repository.pcc.ResourceBookingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ResourceBooking} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ResourceBookingService {

    private final ResourceBookingRepository repository;
    private final ResourceBookingMapper mapper;

    public ResourceBookingService(ResourceBookingRepository repository, ResourceBookingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ResourceBookingDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ResourceBookingDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
