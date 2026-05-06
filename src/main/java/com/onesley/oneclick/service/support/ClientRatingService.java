package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.ClientRatingDto;
import com.onesley.oneclick.mapper.support.ClientRatingMapper;
import com.onesley.oneclick.repository.support.ClientRatingRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ClientRating} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ClientRatingService {

    private final ClientRatingRepository repository;
    private final ClientRatingMapper mapper;

    public ClientRatingService(ClientRatingRepository repository, ClientRatingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ClientRatingDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ClientRatingDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
