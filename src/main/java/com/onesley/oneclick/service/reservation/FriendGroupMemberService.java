package com.onesley.oneclick.service.reservation;

import com.onesley.oneclick.dto.reservation.FriendGroupMemberDto;
import com.onesley.oneclick.mapper.reservation.FriendGroupMemberMapper;
import com.onesley.oneclick.repository.reservation.FriendGroupMemberRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service {@link FriendGroupMember} (généré par scripts/scaffold-jpa.mjs).
 * Méthodes find* read-only par défaut. Étendre selon les besoins métier.
 */
@Service
@Transactional(readOnly = true)
public class FriendGroupMemberService {

    private final FriendGroupMemberRepository repository;
    private final FriendGroupMemberMapper mapper;

    public FriendGroupMemberService(FriendGroupMemberRepository repository, FriendGroupMemberMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<FriendGroupMemberDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public List<FriendGroupMemberDto> findAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
