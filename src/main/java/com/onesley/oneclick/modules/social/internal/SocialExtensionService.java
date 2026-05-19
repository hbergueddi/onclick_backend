package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.social.api.SocialExtensionDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class SocialExtensionService {

    private final EliteApplicationRepository eliteRepo;
    private final RestaurantGroupRepository groupRepo;

    // ─── Elite applications ─────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<EliteApplicationDto> findAllApplications() {
        return eliteRepo.findAllActive().stream().map(EliteApplicationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EliteApplicationDto> findUserApplications(UUID userId) {
        return eliteRepo.findByUser(userId).stream().map(EliteApplicationDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EliteApplicationDto> findApplicationsByStatus(String status) {
        return eliteRepo.findByStatus(status).stream().map(EliteApplicationDto::from).toList();
    }

    public EliteApplicationDto createApplication(EliteApplicationCreateDto dto) {
        EliteApplication a = new EliteApplication();
        a.setUserId(dto.userId());
        a.setMotivation(dto.motivation());
        a.setReferrerId(dto.referrerId());
        a.setStatus("pending");
        return EliteApplicationDto.from(eliteRepo.save(a));
    }

    public EliteApplicationDto reviewApplication(UUID id, EliteApplicationReviewDto dto) {
        EliteApplication a = eliteRepo.findById(id).orElseThrow(() -> new NotFoundException("EliteApplication", id));
        a.setStatus(dto.status());
        a.setReviewedBy(dto.reviewedBy());
        a.setReviewedAt(Instant.now());
        a.setRejectionReason(dto.rejectionReason());
        return EliteApplicationDto.from(eliteRepo.save(a));
    }

    // ─── Restaurant groups ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<RestaurantGroupDto> findAllGroups() {
        return groupRepo.findAllActive().stream().map(RestaurantGroupDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantGroupDto> findOwnerGroups(UUID ownerId) {
        return groupRepo.findByOwner(ownerId).stream().map(RestaurantGroupDto::from).toList();
    }

    @Transactional(readOnly = true)
    public RestaurantGroupDto findGroup(UUID id) {
        var g = groupRepo.findById(id).orElseThrow(() -> new NotFoundException("RestaurantGroup", id));
        return RestaurantGroupDto.from(g);
    }

    public RestaurantGroupDto createGroup(RestaurantGroupCreateDto dto) {
        RestaurantGroup g = new RestaurantGroup();
        g.setTenantId(dto.tenantId());
        g.setName(dto.name());
        g.setDescription(dto.description());
        g.setOwnerId(dto.ownerId());
        g.setLogoUrl(dto.logoUrl());
        return RestaurantGroupDto.from(groupRepo.save(g));
    }

    public RestaurantGroupDto updateGroup(UUID id, RestaurantGroupCreateDto dto) {
        RestaurantGroup g = groupRepo.findById(id).orElseThrow(() -> new NotFoundException("RestaurantGroup", id));
        if (dto.name() != null) g.setName(dto.name());
        if (dto.description() != null) g.setDescription(dto.description());
        if (dto.ownerId() != null) g.setOwnerId(dto.ownerId());
        if (dto.logoUrl() != null) g.setLogoUrl(dto.logoUrl());
        return RestaurantGroupDto.from(groupRepo.save(g));
    }

    public void deleteGroup(UUID id) {
        RestaurantGroup g = groupRepo.findById(id).orElseThrow(() -> new NotFoundException("RestaurantGroup", id));
        g.setDeletedAt(Instant.now());
        groupRepo.save(g);
    }
}
