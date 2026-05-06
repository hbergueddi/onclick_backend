package com.onesley.oneclick.service.admin;

import com.onesley.oneclick.dto.admin.TeamInvitationDto;
import com.onesley.oneclick.mapper.admin.TeamInvitationMapper;
import com.onesley.oneclick.repository.admin.TeamInvitationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link TeamInvitation} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class TeamInvitationService {

    private final TeamInvitationRepository repository;
    private final TeamInvitationMapper mapper;

    public TeamInvitationService(TeamInvitationRepository repository, TeamInvitationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<TeamInvitationDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<TeamInvitationDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
