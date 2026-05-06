package com.onesley.oneclick.service.auth;

import com.onesley.oneclick.dto.auth.ProfileDto;
import com.onesley.oneclick.dto.auth.ProfileUpdateDto;
import com.onesley.oneclick.entity.auth.Profile;
import com.onesley.oneclick.event.ProfileUpdatedEvent;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.mapper.auth.ProfileMapper;
import com.onesley.oneclick.repository.auth.ProfileRepository;
import com.onesley.oneclick.search.SearchRequest;
import com.onesley.oneclick.search.SpecificationBuilder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProfileService {

    /**
     * Whitelist des champs exposés à la recherche dynamique côté API.
     * Inclus uniquement les champs publics utiles à l'usage métier — pas
     * d'identifiants techniques sensibles ni de timestamps audit.
     */
    private static final Set<String> SEARCHABLE_FIELDS = Set.of(
        "firstName", "lastName", "email", "phone", "city", "language",
        "tenantId", "tenantGroupId", "reliabilityScore", "referralCode"
    );

    private final ProfileRepository repository;
    private final ProfileMapper mapper;
    private final ApplicationEventPublisher events;

    public ProfileService(
        ProfileRepository repository,
        ProfileMapper mapper,
        ApplicationEventPublisher events
    ) {
        this.repository = repository;
        this.mapper = mapper;
        this.events = events;
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

    /**
     * Recherche dynamique paginée. Les critères sont validés contre
     * {@link #SEARCHABLE_FIELDS} pour éviter le scan arbitraire de colonnes.
     */
    public Page<ProfileDto> search(SearchRequest request) {
        Specification<Profile> spec = SpecificationBuilder.build(
            request.criteriaOrEmpty(), SEARCHABLE_FIELDS
        );
        Pageable pageable = PageRequest.of(
            request.pageOrZero(),
            request.sizeOrDefault(),
            parseSort(request.sort())
        );
        return repository.findAll(spec, pageable).map(mapper::toDto);
    }

    private static Sort parseSort(String sort) {
        if (sort == null || sort.isBlank()) return Sort.unsorted();
        // Format "field,direction" — ex "reliabilityScore,desc"
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SEARCHABLE_FIELDS.contains(field)) {
            return Sort.unsorted();  // sort silently dropped for unknown fields
        }
        Sort.Direction dir = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(dir, field);
    }

    @Transactional
    public ProfileDto patch(UUID id, ProfileUpdateDto patch) {
        Profile entity = repository.findById(id)
            .orElseThrow(() -> new NotFoundException("Profile", id));
        mapper.applyPatch(patch, entity);
        ProfileDto saved = mapper.toDto(repository.save(entity));
        // Event publié pendant la transaction. Les listeners @TransactionalEventListener
        // (AFTER_COMMIT) ne se déclencheront que si le commit réussit. Voir
        // AuditEventListener + NotificationEventListener.
        events.publishEvent(ProfileUpdatedEvent.of(id));
        return saved;
    }
}
