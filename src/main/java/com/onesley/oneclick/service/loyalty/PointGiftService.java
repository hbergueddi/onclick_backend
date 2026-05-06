package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.PointGiftDto;
import com.onesley.oneclick.mapper.loyalty.PointGiftMapper;
import com.onesley.oneclick.repository.loyalty.PointGiftRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link PointGift} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class PointGiftService {

    private final PointGiftRepository repository;
    private final PointGiftMapper mapper;

    public PointGiftService(PointGiftRepository repository, PointGiftMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<PointGiftDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<PointGiftDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
