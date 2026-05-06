package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.FriendshipDto;
import com.onesley.oneclick.mapper.reservation.FriendshipMapper;
import com.onesley.oneclick.repository.reservation.FriendshipRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link Friendship} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class FriendshipService {

    private final FriendshipRepository repository;
    private final FriendshipMapper mapper;

    public FriendshipService(FriendshipRepository repository, FriendshipMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<FriendshipDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<FriendshipDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
