package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.shared.events.OfferCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.onesley.oneclick.modules.promotion.api.OfferCreateDto;
import com.onesley.oneclick.modules.promotion.api.OfferDto;
import com.onesley.oneclick.modules.promotion.api.OfferImpressionDto;
import com.onesley.oneclick.modules.promotion.api.OfferPatchDto;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OfferService {

    /** Valeurs autorisées pour Offer.type (miroir du CHECK V15 côté DB). */
    private static final Set<String> ALLOWED_TYPES = Set.of("promo", "bonus", "reco");

    private final OfferRepository repository;
    private final OfferImpressionRepository impressionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantScope tenantScope;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<OfferDto> findAll(UUID restaurantId, Boolean activeOnly, int page, int size) {
        // Périmètre tenant (fuite de périmètre) : l'entité Offer ne mappe pas tenantId — le tenant se
        // déduit via offers.restaurant_id → restaurants.tenant_id (source de vérité). Un client oneclick
        // non-membre ne voit que les offres des restos de {tenant public} ∪ ses memberships. SUPERADMIN
        // (null) → aucun filtre (finder Specification legacy non scopé).
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        if (visible != null) {
            return repository.findAllScoped(
                    visible, restaurantId, Boolean.TRUE.equals(activeOnly), Instant.now(),
                    PageRequest.of(page, size))
                .map(Offer::toDto);
        }
        Specification<Offer> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (restaurantId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("restaurantId"), restaurantId));
        if (Boolean.TRUE.equals(activeOnly)) {
            Instant now = Instant.now();
            spec = spec
                .and((root, q, cb) -> cb.isTrue(root.get("enabled")))
                .and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), now))
                .and((root, q, cb) -> cb.greaterThan(root.get("expiresAt"), now));
        }
        return repository.findAll(spec, PageRequest.of(page, size, Sort.by("startsAt").descending()))
            .map(Offer::toDto);
    }

    public OfferDto findById(UUID id) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));
        // Hors périmètre : 404 (ne pas divulguer l'existence d'une offre d'un programme non accessible).
        // Le tenant de l'offre se résout via son restaurant (JOIN restaurants — source de vérité). Pas de
        // requête tenant inutile pour l'acteur cross-tenant (SUPERADMIN, visibleTenantIdsOrNull()==null).
        if (tenantScope.visibleTenantIdsOrNull() != null
            && !tenantScope.canSeeTenant(repository.findTenantIdOfOffer(id))) {
            throw new NotFoundException("Offer", id);
        }
        return o.toDto();
    }

    /**
     * Recherche dynamique scopée au périmètre tenant — délégué par le controller {@code POST /search}.
     *
     * <p>Les offres n'ont pas de {@code tenant_id} filtrable côté Specification JPA : on contraint
     * d'abord le résultat aux restaurants visibles ({@code restaurantId IN (…)}) puis on exécute la
     * Specification utilisateur (whitelist controller). SUPERADMIN (null) → aucune contrainte (recherche
     * globale). Si le caller n'a aucun restaurant visible → page vide (pas d'énumération cross-tenant).
     */
    public Page<OfferDto> search(
        com.onesley.oneclick.search.SearchRequest req,
        Set<String> allowedFields
    ) {
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        if (visible == null) {
            // Acteur cross-tenant (SUPERADMIN) → recherche non scopée (comportement legacy).
            return com.onesley.oneclick.search.Searchable.execute(repository, req, allowedFields, Offer::toDto);
        }
        List<UUID> visibleRestaurantIds = repository.findRestaurantIdsInTenants(visible);
        if (visibleRestaurantIds.isEmpty()) {
            return Page.empty(PageRequest.of(req.pageOrZero(), req.sizeOrDefault()));
        }
        Specification<Offer> userSpec =
            com.onesley.oneclick.search.SpecificationBuilder.build(req.criteriaOrEmpty(), allowedFields);
        Specification<Offer> scoped = (root, q, cb) -> root.get("restaurantId").in(visibleRestaurantIds);
        Specification<Offer> spec = userSpec == null ? scoped : userSpec.and(scoped);
        return repository.findAll(spec, buildSearchPageable(req, allowedFields)).map(Offer::toDto);
    }

    /** Pageable + tri whitelisté pour {@link #search} (calque {@code Searchable.buildPageable}). */
    private static org.springframework.data.domain.Pageable buildSearchPageable(
        com.onesley.oneclick.search.SearchRequest req, Set<String> allowedFields
    ) {
        Sort sort = Sort.unsorted();
        String s = req.sort();
        if (s != null && !s.isBlank()) {
            String[] parts = s.split(",");
            String field = parts[0].trim();
            if (!allowedFields.contains(field)) {
                throw new BadRequestException("sort field non autorisé : '" + field + "'. Autorisés : " + allowedFields);
            }
            Sort.Direction dir = (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim()))
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            sort = Sort.by(dir, field);
        }
        return PageRequest.of(req.pageOrZero(), req.sizeOrDefault(), sort);
    }

    @Transactional
    public OfferDto create(OfferCreateDto dto) {
        if (dto.expiresAt().isBefore(dto.startsAt())) {
            throw new BadRequestException("expiresAt doit être > startsAt");
        }
        String type = dto.type() != null ? dto.type() : "promo";
        validateType(type);
        validatePts(type, dto.pts());

        Offer o = new Offer(UUID.randomUUID(), dto.restaurantId(), dto.title(), dto.startsAt(), dto.expiresAt());
        o.setDescription(dto.description());
        o.setDiscountPct(dto.discountPct());
        o.setDiscountAmount(dto.discountAmount());
        o.setType(type);
        o.setPts(dto.pts());
        if (dto.pushNotify() != null) o.setPushNotify(dto.pushNotify());
        o.setImage(dto.image());
        if (dto.segments() != null) o.setSegments(dto.segments().toArray(new String[0]));
        o.setCampaignId(dto.campaignId());
        Offer saved = repository.save(o);

        // Publish event for downstream consumers (notification campaign trigger, etc.)
        // tenant_id auto-rempli par trigger DB V10 — on lookup pour l'event payload
        UUID tenantId = (UUID) entityManager.createNativeQuery(
            "SELECT tenant_id FROM restaurants WHERE id = ?")
            .setParameter(1, dto.restaurantId())
            .getSingleResult();
        eventPublisher.publishEvent(new OfferCreatedEvent(
            saved.getId(), dto.restaurantId(), tenantId,
            dto.title(), dto.description(),
            dto.startsAt(), dto.expiresAt(),
            dto.discountPct(), dto.discountAmount()
        ));

        return saved.toDto();
    }

    /**
     * Mise à jour partielle (PATCH) — applique uniquement les champs non null
     * du DTO. Re-valide le couple {@code startsAt} / {@code expiresAt} après
     * application, et le couple {@code type} / {@code pts}.
     */
    @Transactional
    public OfferDto patch(UUID id, OfferPatchDto dto) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));

        if (dto.title() != null) o.setTitle(dto.title());
        if (dto.description() != null) o.setDescription(dto.description());
        if (dto.startsAt() != null) o.setStartsAt(dto.startsAt());
        if (dto.expiresAt() != null) o.setExpiresAt(dto.expiresAt());
        if (dto.discountPct() != null) o.setDiscountPct(dto.discountPct());
        if (dto.discountAmount() != null) o.setDiscountAmount(dto.discountAmount());
        if (dto.enabled() != null) o.setEnabled(dto.enabled());
        if (dto.type() != null) {
            validateType(dto.type());
            o.setType(dto.type());
        }
        if (dto.pts() != null) o.setPts(dto.pts());
        if (dto.pushNotify() != null) o.setPushNotify(dto.pushNotify());
        if (dto.image() != null) o.setImage(dto.image());
        if (dto.segments() != null) o.setSegments(dto.segments().toArray(new String[0]));
        if (dto.isPinned() != null) o.setPinned(dto.isPinned());

        // Invariants finaux (post-merge)
        if (o.getExpiresAt().isBefore(o.getStartsAt())) {
            throw new BadRequestException("expiresAt doit être > startsAt");
        }
        validatePts(o.getType(), o.getPts());

        return repository.save(o).toDto();
    }

    @Transactional
    public void softDelete(UUID id) {
        Offer o = repository.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", id));
        o.markDeleted();
        repository.save(o);
    }

    // ─── Impressions (tracking vues offres — offer_impressions V21) ──────────

    /**
     * Enregistre une vue d'offre par l'utilisateur courant (log append-only).
     * Appelé quand un client consulte une offre (Pocket). {@code type} défaut "view".
     * RBAC VIEW:OFFERS (controller) — le client voit l'offre, donc peut tracer sa vue.
     */
    @Transactional
    public void recordImpression(UUID offerId, String type) {
        if (!repository.existsById(offerId)) {
            throw new NotFoundException("Offer", offerId);
        }
        // Périmètre tenant : ne pas tracer (ni divulguer l'existence d') une impression sur une offre
        // d'un programme non accessible au caller → 404. SUPERADMIN (visibleTenantIdsOrNull()==null) non
        // restreint — et on évite la requête tenant inutile dans ce cas.
        if (tenantScope.visibleTenantIdsOrNull() != null
            && !tenantScope.canSeeTenant(repository.findTenantIdOfOffer(offerId))) {
            throw new NotFoundException("Offer", offerId);
        }
        String t = (type == null || type.isBlank()) ? "view" : type;
        impressionRepository.save(new OfferImpression(
            UUID.randomUUID(), offerId, SecurityHelper.currentUserId(), t, Instant.now()));
    }

    /**
     * Impressions des {@code sinceDays} derniers jours (analytics admin — dashboard
     * exécutif Promotions). Lignes brutes ; l'agrégation période/sparkline est front.
     */
    @Transactional(readOnly = true)
    public List<OfferImpressionDto> listImpressions(int sinceDays) {
        Instant since = Instant.now().minus(sinceDays, ChronoUnit.DAYS);
        return impressionRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtDesc(since)
            .stream().map(OfferImpression::toDto).toList();
    }

    private static void validateType(String type) {
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BadRequestException(
                "type doit être ∈ " + ALLOWED_TYPES + " (reçu: " + type + ")");
        }
    }

    private static void validatePts(String type, Integer pts) {
        if (pts != null && pts <= 0) {
            throw new BadRequestException("pts doit être strictement positif");
        }
        // pts attendu uniquement pour type=bonus ; on logge sans bloquer si renseigné
        // sur une promo classique (use case: bonus combiné à une réduction futur).
    }
}
