package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.ProfileDto;
import com.onesley.oneclick.dto.auth.ProfileUpdateDto;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.mapper.auth.ProfileMapper;
import com.onesley.oneclick.repository.auth.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final ProfileRepository repository;
    private final ProfileMapper mapper;

    public ProfileService(ProfileRepository repository, ProfileMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public Optional<ProfileDto> findById(UUID id) {
        return repository.findById(id).map(mapper::toDto);
    }

    public Optional<ProfileDto> findByEmail(String email) {
        return repository.findByEmail(email).map(mapper::toDto);
    }

    public Optional<ProfileDto> findByReferralCode(String code) {
        return repository.findByReferralCode(code).map(mapper::toDto);
    }

    public List<ProfileDto> findByTenant(UUID tenantId) {
        return mapper.toDtoList(repository.findAllByTenantId(tenantId));
    }

    @Transactional
    public ProfileDto patch(UUID id, ProfileUpdateDto patch) {
        Profile entity = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Profile", id));
        mapper.applyPatch(patch, entity);
        return mapper.toDto(repository.save(entity));
    }
}
