package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
import com.onesley.oneclick.shared.events.ResourceBookingStatusChangedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.*;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.BookingUpdateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.GuestDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.PricingDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceCreateDto;
import com.onesley.oneclick.modules.resource_booking.api.ResourceBookingDtos.ResourceDto;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ResourceBookingService {

    private final ResourceRepository resourceRepo;
    private final ResourcePricingRepository pricingRepo;
    private final ResourceBookingRepository bookingRepo;
    private final ResourceBookingGuestRepository guestRepo;
    /** Horloge injectée → la fenêtre d'annulation H-2 est testable (mockée en test). */
    private final Clock clock;
    /**
     * Publication d'events inter-modules (frontière Modulith). Sur un changement de statut
     * de booking, on publie {@link ResourceBookingStatusChangedEvent} consommé par le
     * module loyalty (auto-punch des cartes de fidélité). JAMAIS d'appel direct
     * resource_booking → loyalty.
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Annuaire utilisateur (contrat typé {@code core.identity} — frontière Modulith).
     * Sert au dashboard staff à résoudre, en un seul batch (anti-N+1), le NOM d'affichage
     * de l'organisateur de chaque booking ({@code namesByIds}) et le TENANT du staff
     * appelant ({@code tenantIdById}) pour scoper le listing au parc de SON tenant. Jamais
     * de lecture SQL native de la table {@code users} hors du module identity.
     */
    private final UserDirectoryApi userDirectory;

    /**
     * Périmètre tenant <b>visible</b> du caller (module {@code security}, déjà autorisé) — ferme la
     * fuite de périmètre sur les <b>listes/lectures de découverte</b> client-facing (ressources +
     * tarifs + créneaux occupés). Un client « oneclick » non-membre d'un programme (PCC/HOMU) ne
     * doit JAMAIS voir/probe les ressources d'un tenant dont il n'est pas membre actif. Le bypass
     * SUPERADMIN ({@link TenantScope#visibleTenantIdsOrNull()} == {@code null}) n'applique aucun
     * filtre. N'IMPACTE PAS l'ABAC self-scope des bookings (un membre ne voit déjà que les siens).
     */
    private final TenantScope tenantScope;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Statuts qui occupent réellement un créneau (busy-slots). Les annulées / no_show
     * libèrent le créneau et sont donc exclues. Aligné sur le CHECK de la table
     * {@code resource_bookings} (migration V5).
     */
    private static final Set<String> OCCUPYING_STATUSES = Set.of("pending", "confirmed");

    /** Fenêtre minimale d'annulation membre : H-2 (un membre ne peut pas annuler &lt; 2h avant). */
    private static final Duration MIN_CANCEL_LEAD = Duration.ofHours(2);

    // ─── Resources ───────────────────────────────────────────────────────────

    public Page<ResourceDto> findAllResources(UUID tenantId, String resourceType, Boolean enabledOnly, int page, int size) {
        // Périmètre tenant (fuite de périmètre) : un tenantId explicite n'est honoré que si le
        // caller peut le voir ({tenant public} ∪ memberships actives) ; sinon 403 (le client
        // ne « devine » jamais les ressources d'un programme dont il n'est pas membre).
        if (tenantId != null && !tenantScope.canSeeTenant(tenantId)) {
            throw new ForbiddenException(
                "Accès interdit : ce programme ne fait pas partie de votre périmètre");
        }
        Specification<Resource> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (resourceType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceType"), resourceType));
        if (Boolean.TRUE.equals(enabledOnly)) {
            spec = spec.and((root, q, cb) -> cb.isTrue(root.get("enabled")));
        }
        // Sans tenantId explicite : scoper la liste au périmètre visible. SUPERADMIN (null) →
        // aucun filtre (le set inclut toujours le tenant public, donc jamais vide pour un client).
        Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
        if (visible != null) {
            final Set<UUID> scoped = visible;
            spec = spec.and((root, q, cb) -> root.get("tenantId").in(scoped));
        }
        return resourceRepo.findAll(spec, PageRequest.of(page, size, Sort.by("name").ascending()))
            .map(Resource::toDto);
    }

    public ResourceDto findResourceById(UUID id) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        // Hors périmètre : 404 (ne pas divulguer l'existence d'une ressource d'un programme non accessible).
        if (!tenantScope.canSeeTenant(r.getTenantId())) {
            throw new NotFoundException("Resource", id);
        }
        return r.toDto();
    }

    @Transactional
    public ResourceDto createResource(ResourceCreateDto dto) {
        Tenant tenantRef = entityManager.getReference(Tenant.class, dto.tenantId());
        Resource r = new Resource(UUID.randomUUID(), tenantRef, dto.resourceType(), dto.name());
        if (dto.description() != null) r.setDescription(dto.description());
        if (dto.capacity() != null)    r.setCapacity(dto.capacity());
        return resourceRepo.save(r).toDto();
    }

    @Transactional
    public void softDeleteResource(UUID id) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        r.markDeleted();
        resourceRepo.save(r);
    }

    // ─── Pricings ────────────────────────────────────────────────────────────

    public List<PricingDto> findPricingsByResource(UUID resourceId) {
        // Découverte tarifaire d'une ressource : gate par le tenant de la ressource (hors périmètre
        // → 404, on ne divulgue pas l'existence/les tarifs d'un programme non accessible).
        requireResourceInScope(resourceId);
        return pricingRepo.findAllByResourceId(resourceId).stream().map(ResourcePricing::toDto).toList();
    }

    @Transactional
    public PricingDto createPricing(PricingCreateDto dto) {
        Resource resourceRef = entityManager.getReference(Resource.class, dto.resourceId());
        ResourcePricing p = new ResourcePricing(UUID.randomUUID(), resourceRef, dto.name(), dto.price());
        if (dto.durationMinutes() != null) p.setDurationMinutes(dto.durationMinutes());
        return pricingRepo.save(p).toDto();
    }

    // ─── Bookings ────────────────────────────────────────────────────────────

    public Page<BookingDto> findAllBookings(UUID resourceId, UUID organizerId, String status, int page, int size) {
        Specification<ResourceBooking> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (resourceId != null)  spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceId"), resourceId));
        if (organizerId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("organizerId"), organizerId));
        if (status != null)      spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return bookingRepo.findAll(spec, PageRequest.of(page, size, Sort.by("startAt").descending()))
            .map(ResourceBooking::toDto);
    }

    /**
     * Listing <b>staff</b> : TOUS les bookings (non soft-deleted) des ressources du tenant de
     * l'appelant — board opérationnel du dashboard staff PCC ({@code GET /bookings?scope=tenant}).
     *
     * <p><b>ABAC</b> : réservé au staff/admin ({@link SecurityHelper#isStaffOrAdmin()}). Un CLIENT
     * est refusé ({@link ForbiddenException} → 403) : il n'a aucune raison de voir les réservations
     * des autres membres (le membre garde {@code GET /bookings} self-scopé). Le tenant n'est PAS un
     * paramètre client (anti-spoof) : il est résolu serveur depuis le sub du JWT via
     * {@link UserDirectoryApi#tenantIdById(UUID)} → le staff ne voit QUE le parc de SON tenant. Si
     * l'appelant n'a pas de tenant (admin plateforme global sans tenant), le scope tenant n'a pas de
     * sens → liste vide (un SUPERADMIN global passe par les filtres explicites, pas par ce board).</p>
     *
     * <p>Scoping SQL natif sur {@code resources.tenant_id} via un JOIN ({@code root.join("resource")})
     * — exactement la « read-view native sur resources.tenant_id » attendue. Enrichissement noms
     * d'affichage (organizer) + noms de ressources en deux batchs (anti-N+1), sans PII superflue.</p>
     */
    public Page<StaffBookingDto> findTenantBookings(UUID resourceId, String status, int page, int size) {
        if (!SecurityHelper.isStaffOrAdmin()) {
            throw new ForbiddenException(
                "Accès interdit : le périmètre tenant est réservé au staff/admin");
        }
        UUID current = SecurityHelper.currentUserId();
        UUID tenantId = current != null ? userDirectory.tenantIdById(current).orElse(null) : null;
        if (tenantId == null) {
            // Staff/admin sans tenant (admin plateforme global) → aucun parc tenant à afficher.
            return Page.empty(PageRequest.of(page, size));
        }

        final UUID scopedTenantId = tenantId;
        Specification<ResourceBooking> spec = (root, q, cb) -> {
            // Évite un produit cartésien sur les count queries (le join n'est utile que pour le filtre).
            if (q != null && q.getResultType() != Long.class && q.getResultType() != long.class) {
                root.fetch("resource", jakarta.persistence.criteria.JoinType.INNER);
            }
            return cb.and(
                cb.isNull(root.get("deletedAt")),
                cb.equal(root.join("resource").get("tenantId"), scopedTenantId));
        };
        if (resourceId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceId"), resourceId));
        if (status != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));

        Page<ResourceBooking> bookings = bookingRepo.findAll(
            spec, PageRequest.of(page, size, Sort.by("startAt").descending()));
        return enrichForStaff(bookings);
    }

    /**
     * Enrichit une page de bookings pour le staff : nom d'affichage organizer (via
     * {@link UserDirectoryApi#namesByIds}) + nom de la ressource (repo local du module).
     * Deux requêtes batch au total (anti-N+1), aucune PII contact (téléphone/email) exposée.
     */
    private Page<StaffBookingDto> enrichForStaff(Page<ResourceBooking> bookings) {
        List<ResourceBooking> content = bookings.getContent();
        List<UUID> organizerIds = content.stream()
            .map(ResourceBooking::getOrganizerId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, UserDirectoryApi.UserName> names = organizerIds.isEmpty()
            ? Map.of()
            : userDirectory.namesByIds(organizerIds).stream()
                .collect(Collectors.toMap(UserDirectoryApi.UserName::id, Function.identity(), (a, b) -> a));

        List<UUID> resourceIds = content.stream()
            .map(ResourceBooking::getResourceId)
            .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, String> resourceNames = resourceIds.isEmpty()
            ? Map.of()
            : resourceRepo.findAllById(resourceIds).stream()
                .collect(Collectors.toMap(Resource::getId, Resource::getName, (a, b) -> a));

        List<StaffBookingDto> dtos = new ArrayList<>(content.size());
        for (ResourceBooking b : content) {
            UserDirectoryApi.UserName n = b.getOrganizerId() != null ? names.get(b.getOrganizerId()) : null;
            String organizerName = n != null
                ? java.util.stream.Stream.of(n.firstName(), n.lastName())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" ")).trim()
                : null;
            dtos.add(new StaffBookingDto(
                b.getId(), b.getResourceId(), resourceNames.get(b.getResourceId()),
                b.getOrganizerId(), (organizerName == null || organizerName.isBlank()) ? null : organizerName,
                b.getPricingId(), b.getStartAt(), b.getEndAt(), b.getStatus(), b.getNotes(), b.getCreatedAt()));
        }
        return new org.springframework.data.domain.PageImpl<>(dtos, bookings.getPageable(), bookings.getTotalElements());
    }

    public BookingDto findBookingById(UUID id) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        // Owner OU staff/admin : le staff doit pouvoir consulter un booking pour le confirmer.
        requireOwnerOrStaff(b.getOrganizerId());
        return b.toDto();
    }

    @Transactional
    public BookingDto createBooking(BookingCreateDto dto) {
        // ABAC self-scope : un membre (non staff/admin) ne réserve QUE pour lui-même.
        // On force organizer_id = currentUserId même si le DTO porte un autre id (anti-spoof).
        // Le staff/admin peut réserver pour le compte d'un membre → organizer du DTO respecté.
        UUID organizerId = resolveOrganizerId(dto.organizerId());
        Resource resourceRef = entityManager.getReference(Resource.class, dto.resourceId());
        User organizerRef = entityManager.getReference(User.class, organizerId);
        ResourceBooking b = new ResourceBooking(UUID.randomUUID(), resourceRef, organizerRef, dto.startAt(), dto.endAt());
        if (dto.pricingId() != null) {
            b.setPricing(entityManager.getReference(ResourcePricing.class, dto.pricingId()));
        }
        if (dto.status() != null) b.setStatus(dto.status());
        if (dto.notes() != null)  b.setNotes(dto.notes());
        ResourceBooking saved = bookingRepo.save(b);
        // Les colonnes FK en lecture seule (organizer_id/resource_id/pricing_id, insertable=false)
        // ne sont peuplées par Hibernate qu'après un refresh : on flush+refresh pour que le DTO
        // renvoyé porte bien organizer_id (= self forcé) — cohérent avec un GET ultérieur.
        entityManager.flush();
        entityManager.refresh(saved);
        return saved.toDto();
    }

    @Transactional
    public BookingDto updateBooking(UUID id, BookingUpdateDto dto) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        // ABAC : owner du booking OU staff/admin. Le staff confirme/annule/marque
        // (demandee→confirmee, honoree, no_show, annulee) ; le membre gère le sien.
        requireOwnerOrStaff(b.getOrganizerId());
        String oldStatus = b.getStatus();
        if (dto.status() != null) b.setStatus(dto.status());
        if (dto.notes() != null)  b.setNotes(dto.notes());
        ResourceBooking saved = bookingRepo.save(b);

        // Publication inter-modules : UNIQUEMENT si le statut change réellement.
        // Consommé par loyalty (ResourceBookingPunchListener) — auto-punch sur 'completed'.
        // tenantId + resourceType viennent de la ressource du booking (le module
        // resource_booking les connaît), pour éviter au listener loyalty de résoudre
        // la ressource cross-module.
        String newStatus = saved.getStatus();
        if (dto.status() != null && !dto.status().equals(oldStatus)) {
            Resource resource = saved.getResource();
            eventPublisher.publishEvent(new ResourceBookingStatusChangedEvent(
                saved.getId(),
                saved.getOrganizerId(),
                saved.getResourceId(),
                resource != null ? resource.getTenantId() : null,
                resource != null ? resource.getResourceType() : null,
                oldStatus, newStatus,
                Instant.now()
            ));
        }
        return saved.toDto();
    }

    @Transactional
    public void softDeleteBooking(UUID id) {
        ResourceBooking b = bookingRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", id));
        // ABAC : owner du booking OU staff/admin.
        requireOwnerOrStaff(b.getOrganizerId());
        // Règle H-2 : un MEMBRE ne peut pas annuler moins de 2h avant le début (le staff,
        // lui, peut toujours — annulation administrative). 422 Unprocessable sinon.
        if (!SecurityHelper.isStaffOrAdmin()) {
            requireCancellableByMember(b);
        }
        b.markDeleted();
        bookingRepo.save(b);
    }

    // ─── Guests ──────────────────────────────────────────────────────────────

    public List<GuestDto> findGuestsByBooking(UUID bookingId) {
        ResourceBooking b = bookingRepo.findById(bookingId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", bookingId));
        // Owner OU staff/admin (cohérent avec findBookingById).
        requireOwnerOrStaff(b.getOrganizerId());
        return guestRepo.findAllByBookingId(bookingId).stream().map(ResourceBookingGuest::toDto).toList();
    }

    @Transactional
    public GuestDto addGuest(GuestCreateDto dto) {
        ResourceBooking b = bookingRepo.findById(dto.bookingId())
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("ResourceBooking", dto.bookingId()));
        // Owner du booking OU staff/admin : le membre ajoute SES invités, le staff peut aussi.
        requireOwnerOrStaff(b.getOrganizerId());
        ResourceBooking bookingRef = entityManager.getReference(ResourceBooking.class, dto.bookingId());
        User guestUserRef = dto.guestUserId() != null
            ? entityManager.getReference(User.class, dto.guestUserId())
            : null;
        ResourceBookingGuest g = new ResourceBookingGuest(UUID.randomUUID(), bookingRef, guestUserRef, dto.guestName());
        return guestRepo.save(g).toDto();
    }

    // ─── Busy slots (disponibilité calendrier, sans PII) ───────────────────────

    /**
     * Créneaux occupés d'une ressource pour un jour donné — projection {@link BusySlotDto}
     * (start/end uniquement, aucune PII).
     *
     * <p>Fenêtre = [date 00:00 UTC, date+1 00:00 UTC). On retient les bookings dont le statut
     * occupe le créneau ({@link #OCCUPYING_STATUSES}) ; les annulées / no_show le libèrent.
     * Aucun contrôle d'ownership : tout membre détenant {@code VIEW:BOOKINGS} (garde du
     * contrôleur) peut consulter la disponibilité, mais ne voit jamais QUI a réservé.</p>
     */
    public List<BusySlotDto> findBusySlots(UUID resourceId, LocalDate date) {
        // Disponibilité d'une ressource : gate par le tenant de la ressource — un non-membre ne
        // doit pas pouvoir sonder les créneaux (et donc l'existence/l'usage) d'un programme PCC/HOMU
        // dont il n'est pas membre. Hors périmètre → 404.
        requireResourceInScope(resourceId);
        Instant from = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return bookingRepo.findActiveInRange(resourceId, from, to, OCCUPYING_STATUSES).stream()
            .map(b -> new BusySlotDto(b.getStartAt(), b.getEndAt()))
            .toList();
    }

    // ─── ABAC helpers ──────────────────────────────────────────────────────────

    /**
     * Périmètre tenant (fuite de périmètre) pour les lectures de découverte rattachées à une
     * ressource (tarifs, créneaux occupés) : charge la ressource (non soft-deleted) et vérifie que
     * son tenant est dans le périmètre visible du caller ({@link TenantScope#canSeeTenant(UUID)}).
     * Hors périmètre → {@link NotFoundException} (404) : on ne divulgue pas l'existence d'une
     * ressource d'un programme non accessible. SUPERADMIN (cross-tenant) → aucun filtre.
     */
    private void requireResourceInScope(UUID resourceId) {
        Resource r = resourceRepo.findById(resourceId)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", resourceId));
        if (!tenantScope.canSeeTenant(r.getTenantId())) {
            throw new NotFoundException("Resource", resourceId);
        }
    }

    /**
     * Résout l'organisateur d'un booking selon l'appelant : un membre (non staff/admin)
     * est TOUJOURS organisateur de son propre booking (anti-spoof) ; le staff/admin peut
     * réserver pour le compte d'un membre (organizer du DTO respecté).
     */
    private UUID resolveOrganizerId(UUID requestedOrganizerId) {
        if (SecurityHelper.isStaffOrAdmin()) {
            return requestedOrganizerId;
        }
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return current; // self-scope forcé pour le membre
    }

    /**
     * 403 si l'appelant n'est ni l'organisateur du booking, ni staff/admin.
     * <p>Pour le membre : seul SON booking. Pour le staff/admin : tous (confirme/annule/marque).</p>
     */
    private void requireOwnerOrStaff(UUID organizerId) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (current.equals(organizerId)) return;
        if (SecurityHelper.isStaffOrAdmin()) return;
        throw new ForbiddenException(
            "Accès interdit : vous n'êtes pas l'organisateur de cette réservation");
    }

    /**
     * Règle H-2 (annulation membre) : refuse si on est à moins de 2h du début du créneau.
     * Le staff/admin n'est PAS soumis à cette règle (annulation administrative).
     *
     * @throws UnprocessableException (422) si le délai H-2 n'est pas respecté
     */
    private void requireCancellableByMember(ResourceBooking b) {
        Instant now = Instant.now(clock);
        if (now.plus(MIN_CANCEL_LEAD).isAfter(b.getStartAt())) {
            throw new UnprocessableException(
                "Annulation impossible à moins de 2h du début (H-2). Contactez l'accueil.");
        }
    }
}
