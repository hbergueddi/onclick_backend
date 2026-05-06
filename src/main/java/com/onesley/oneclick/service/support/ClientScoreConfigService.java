package com.onesley.oneclick.service.support;

import com.onesley.oneclick.dto.support.ClientScoreConfigDto;
import com.onesley.oneclick.mapper.support.ClientScoreConfigMapper;
import com.onesley.oneclick.repository.support.ClientScoreConfigRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link ClientScoreConfig} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class ClientScoreConfigService {

    private final ClientScoreConfigRepository repository;
    private final ClientScoreConfigMapper mapper;

    public ClientScoreConfigService(ClientScoreConfigRepository repository, ClientScoreConfigMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ClientScoreConfigDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<ClientScoreConfigDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
