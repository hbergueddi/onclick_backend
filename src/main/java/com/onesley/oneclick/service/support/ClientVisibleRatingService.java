package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.ClientVisibleRatingDto;
import com.onesley.oneclick.mapper.support.ClientVisibleRatingMapper;
import com.onesley.oneclick.repository.support.ClientVisibleRatingRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ClientVisibleRating} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ClientVisibleRatingService {

    private final ClientVisibleRatingRepository repository;
    private final ClientVisibleRatingMapper mapper;

    public ClientVisibleRatingService(ClientVisibleRatingRepository repository, ClientVisibleRatingMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<ClientVisibleRatingDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
