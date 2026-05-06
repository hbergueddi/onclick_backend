package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.FriendGroupDto;
import com.onesley.oneclick.mapper.reservation.FriendGroupMapper;
import com.onesley.oneclick.repository.reservation.FriendGroupRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link FriendGroup} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class FriendGroupService {

    private final FriendGroupRepository repository;
    private final FriendGroupMapper mapper;

    public FriendGroupService(FriendGroupRepository repository, FriendGroupMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<FriendGroupDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<FriendGroupDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
