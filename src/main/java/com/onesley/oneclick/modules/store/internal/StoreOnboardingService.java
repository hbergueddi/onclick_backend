package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class StoreOnboardingService {

    private final StoreOnboardingRepository repo;

    @Transactional(readOnly = true)
    public List<OnboardingRequestDto> findAll(String status) {
        if (status != null) {
            return repo.findByStatus(status).stream().map(OnboardingRequestDto::from).toList();
        }
        return repo.findAllActive().stream().map(OnboardingRequestDto::from).toList();
    }

    @Transactional(readOnly = true)
    public OnboardingRequestDto findById(UUID id) {
        return OnboardingRequestDto.from(
            repo.findById(id).orElseThrow(() -> new NotFoundException("StoreOnboardingRequest", id))
        );
    }

    public OnboardingRequestDto create(OnboardingCreateDto dto) {
        StoreOnboardingRequest r = new StoreOnboardingRequest();
        r.setTenantId(dto.tenantId());
        r.setRestaurantName(dto.restaurantName());
        r.setCuisine(dto.cuisine());
        r.setCity(dto.city());
        r.setAddress(dto.address());
        r.setPhone(dto.phone());
        r.setOwnerFirstName(dto.ownerFirstName());
        r.setOwnerLastName(dto.ownerLastName());
        r.setOwnerEmail(dto.ownerEmail());
        r.setOwnerPhone(dto.ownerPhone());

        OnboardingRequestDto saved = OnboardingRequestDto.from(repo.save(r));
        log.info("[store/onboarding] new request: id={} restaurant={} email={}",
            saved.id(), saved.restaurantName(), saved.ownerEmail());
        // TODO Sprint suivant : envoyer email de confirmation reception au candidat
        return saved;
    }

    public OnboardingRequestDto decide(UUID id, OnboardingDecisionDto dto) {
        StoreOnboardingRequest r = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("StoreOnboardingRequest", id));
        r.setStatus(dto.status());
        r.setRejectionReason(dto.rejectionReason());
        r.setReviewedBy(dto.reviewedBy());
        r.setReviewedAt(Instant.now());
        // TODO : envoyer email branded (Resend) avec le verdict + lien activation
        // Pour MVP on log seulement.
        r.setDecisionEmailSentAt(Instant.now());
        OnboardingRequestDto saved = OnboardingRequestDto.from(repo.save(r));
        log.info("[store/onboarding] decision: id={} status={} reviewedBy={}",
            saved.id(), saved.status(), saved.reviewedBy());
        return saved;
    }
}
