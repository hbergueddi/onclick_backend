package com.onesley.oneclick.service.loyalty;

import com.onesley.oneclick.dto.loyalty.UserFavoriteDto;
import com.onesley.oneclick.mapper.loyalty.UserFavoriteMapper;
import com.onesley.oneclick.repository.loyalty.UserFavoriteRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link UserFavorite} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class UserFavoriteService {

    private final UserFavoriteRepository repository;
    private final UserFavoriteMapper mapper;

    public UserFavoriteService(UserFavoriteRepository repository, UserFavoriteMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<UserFavoriteDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<UserFavoriteDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
