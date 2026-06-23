package com.onesley.oneclick.modules.resource_booking.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.security.TenantScope;
import com.onesley.oneclick.shared.events.ResourceBookingCreatedEvent;
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
        // ── Détermination du périmètre tenant (anti-fuite) ──────────────────────────────────────
        // Le STAFF (gestion du parc, autorité {CREATE|UPDATE}:RESOURCE_BOOKINGS) consulte SON propre
        // parc — qui vit dans son tenant HOME (résolu serveur depuis le JWT), PAS dans ses memberships :
        // un owner PCC est STAFF de palmeraie, jamais MEMBRE → palmeraie ∉ visibleTenantIds. Sans cet
        // alignement, son écran de gestion listait 0 ressource (cause-racine du 403/liste vide).
        //
        // On distingue donc deux intentions :
        //   • DÉCOUVERTE client (tenantId explicite passé par PccHome reveal, ou liste publique sans
        //     tenantId) → périmètre VISIBLE membership-scopé inchangé (NE PAS dégrader l'anti-fuite client).
        //   • GESTION staff (autorité parc + AUCUN tenantId explicite) → scope sur le tenant HOME du staff.
        final boolean parcManager = SecurityHelper.hasAuthority("CREATE:RESOURCE_BOOKINGS")
            || SecurityHelper.hasAuthority("UPDATE:RESOURCE_BOOKINGS");
        final UUID staffTenant = parcManager ? resolveStaffTenantOrNull() : null;

        if (tenantId != null) {
            // tenantId explicite : honoré si le caller peut le voir (client membre / public) OU s'il
            // s'agit du tenant HOME du staff gestionnaire (sinon 403 — le client ne « devine » jamais
            // les ressources d'un programme dont il n'est pas membre).
            boolean allowed = tenantScope.canSeeTenant(tenantId)
                || (staffTenant != null && staffTenant.equals(tenantId));
            if (!allowed) {
                throw new ForbiddenException(
                    "Accès interdit : ce programme ne fait pas partie de votre périmètre");
            }
        }
        Specification<Resource> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (tenantId != null)     spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), tenantId));
        if (resourceType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceType"), resourceType));
        if (Boolean.TRUE.equals(enabledOnly)) {
            spec = spec.and((root, q, cb) -> cb.isTrue(root.get("enabled")));
        }
        // Sans tenantId explicite : choisir le périmètre de scoping.
        if (tenantId == null) {
            if (staffTenant != null) {
                // GESTION staff : son parc = son tenant HOME (board admin des ressources PCC).
                final UUID scopedStaff = staffTenant;
                spec = spec.and((root, q, cb) -> cb.equal(root.get("tenantId"), scopedStaff));
            } else {
                // DÉCOUVERTE client : périmètre VISIBLE (oneclick ∪ memberships). SUPERADMIN (null)
                // → aucun filtre (le set inclut toujours le tenant public, donc jamais vide pour un client).
                Set<UUID> visible = tenantScope.visibleTenantIdsOrNull();
                if (visible != null) {
                    final Set<UUID> scoped = visible;
                    spec = spec.and((root, q, cb) -> root.get("tenantId").in(scoped));
                }
            }
        }
        return resourceRepo.findAll(spec, PageRequest.of(page, size, Sort.by("name").ascending()))
            .map(Resource::toDto);
    }

    public ResourceDto findResourceById(UUID id) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        // Visible si dans le périmètre membership du caller (découverte client : lecture par id d'un
        // programme dont il est membre) OU si la ressource appartient au tenant HOME du staff appelant
        // (board de gestion : pré-remplir le form d'édition d'une ressource PCC, où l'owner est STAFF
        // non membre). Hors de ces deux périmètres → 404 (ne pas divulguer l'existence).
        UUID staffTenant = resolveStaffTenantOrNull();
        boolean visible = tenantScope.canSeeTenant(r.getTenantId())
            || (staffTenant != null && staffTenant.equals(r.getTenantId()));
        if (!visible) {
            throw new NotFoundException("Resource", id);
        }
        return r.toDto();
    }

    @Transactional
    public ResourceDto createResource(ResourceCreateDto dto) {
        // Anti-spoof : une ressource est TOUJOURS créée sous le tenant HOME du staff appelant
        // (résolu serveur depuis le JWT), JAMAIS sous un tenantId arbitraire fourni par le client.
        // Un acteur cross-tenant (SUPERADMIN — VIEW:TENANTS) peut viser un tenant arbitraire (vue
        // globale) ; tout autre caller est limité à son propre tenant.
        final UUID tenantId;
        if (isCrossTenant()) {
            // SUPERADMIN : tenantId du DTO respecté (création pour le compte d'un tenant donné).
            tenantId = dto.tenantId();
        } else {
            UUID staffTenant = resolveStaffTenantOrNull();
            if (staffTenant == null) {
                // Pas de tenant HOME (admin plateforme global sans tenant) → la création de parc
                // n'a pas de tenant de rattachement : réservée au staff/admin d'un tenant.
                throw new ForbiddenException(
                    "Accès interdit : la création de ressources est réservée au staff d'un programme");
            }
            // Si un tenantId est fourni ET diffère du tenant HOME → spoof tenté → refus.
            if (dto.tenantId() != null && !dto.tenantId().equals(staffTenant)) {
                throw new ForbiddenException(
                    "Accès interdit : vous ne pouvez créer des ressources que dans votre propre programme");
            }
            tenantId = staffTenant; // forcé au tenant du staff (même si le DTO en porte un autre/null)
        }
        Tenant tenantRef = entityManager.getReference(Tenant.class, tenantId);
        Resource r = new Resource(UUID.randomUUID(), tenantRef, dto.resourceType(), dto.name());
        if (dto.description() != null) r.setDescription(dto.description());
        if (dto.capacity() != null)    r.setCapacity(dto.capacity());
        // Parité création (P1.3) : paramètres de génération de créneaux fournis directement
        // (jusqu'ici peuplés par seed). L'entité/la table les supportent (migration V96).
        if (dto.slotDurationMinutes() != null) r.setSlotDurationMinutes(dto.slotDurationMinutes());
        if (dto.maxInvitees() != null)         r.setMaxInvitees(dto.maxInvitees());
        if (dto.openingHours() != null)        r.setOpeningHours(dto.openingHours());
        return resourceRepo.save(r).toDto();
    }

    /**
     * P1.3 — patch partiel d'une ressource (parc admin). Charge la ressource non soft-deleted,
     * vérifie le périmètre tenant (même mécanisme {@link TenantScope#canSeeTenant} que les autres
     * lectures — hors périmètre → 404), valide les champs <b>fournis</b> (métier) puis applique
     * uniquement ceux-ci (COALESCE). Le <b>type</b> de ressource n'est jamais modifié (verrouillé).
     */
    @Transactional
    public ResourceDto updateResource(UUID id, ResourceUpdateDto dto) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        // Gestion du parc : autorisée si acteur cross-tenant (SUPERADMIN) OU si la ressource appartient
        // au tenant HOME du staff appelant. Hors de ce périmètre → 404 (ne pas divulguer l'existence).
        requireManageableByStaff(r, id);
        // Validations métier sur les champs FOURNIS (les bornes structurelles sont déjà gardées par
        // les annotations du DTO ; ici on défend la cohérence sémantique côté service).
        if (dto.name() != null && dto.name().isBlank()) {
            throw new UnprocessableException("Le nom de la ressource ne peut pas être vide.");
        }
        if (dto.capacity() != null && dto.capacity() < 1) {
            throw new UnprocessableException("La capacité doit être au moins de 1.");
        }
        if (dto.slotDurationMinutes() != null && dto.slotDurationMinutes() < 15) {
            throw new UnprocessableException("La durée d'un créneau doit être d'au moins 15 minutes.");
        }
        if (dto.maxInvitees() != null && dto.maxInvitees() < 0) {
            throw new UnprocessableException("Le nombre d'invités ne peut pas être négatif.");
        }
        // Patch partiel (COALESCE) — seuls les champs non null sont appliqués ; le type reste verrouillé.
        if (dto.name() != null)                r.setName(dto.name());
        if (dto.description() != null)         r.setDescription(dto.description());
        if (dto.capacity() != null)            r.setCapacity(dto.capacity());
        if (dto.slotDurationMinutes() != null) r.setSlotDurationMinutes(dto.slotDurationMinutes());
        if (dto.maxInvitees() != null)         r.setMaxInvitees(dto.maxInvitees());
        if (dto.openingHours() != null)        r.setOpeningHours(dto.openingHours());
        return resourceRepo.save(r).toDto();
    }

    /**
     * P1.3 — toggle d'activation d'une ressource (active/désactive le parc). Même périmètre tenant
     * que {@link #updateResource} (hors périmètre → 404). Une ressource désactivée ({@code enabled=false})
     * reste réservable côté staff mais disparaît de la découverte membre ({@code enabledOnly}).
     */
    @Transactional
    public ResourceDto setResourceEnabled(UUID id, boolean enabled) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        requireManageableByStaff(r, id);
        r.setEnabled(enabled);
        return resourceRepo.save(r).toDto();
    }

    @Transactional
    public void softDeleteResource(UUID id) {
        Resource r = resourceRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Resource", id));
        // Gestion du parc : suppression réservée au staff du tenant propriétaire (ou cross-tenant) —
        // hors de ce périmètre → 404 (ne divulgue pas l'existence d'une ressource d'un autre programme).
        requireManageableByStaff(r, id);
        // P1.3 — refuser (409) la suppression d'une ressource ayant des réservations VIVANTES
        // (non soft-deleted) : on ne casse pas un parc encore réservé. Les bookings annulés
        // (soft-deleted) ne comptent pas → la ressource redevient supprimable une fois purgée.
        if (bookingRepo.countActiveByResourceId(id) > 0) {
            throw new ConflictException(
                "Désactivez la ressource au lieu de la supprimer : des réservations y sont rattachées.");
        }
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

    /**
     * P1.4 — statistiques de no-show <b>par organisateur</b> du tenant de l'appelant, sur la fenêtre
     * {@code [from, to)}. C'est le seul vrai trou des exports bookable : le board staff peut voir
     * QUI accumule les no-shows pour adapter la politique (relances, dépôts de garantie…).
     *
     * <p><b>ABAC identique à {@link #findTenantBookings}</b> (calque exact) : réservé au staff/admin
     * ({@link SecurityHelper#isStaffOrAdmin()} → un CLIENT est refusé 403) ; le tenant vient TOUJOURS
     * du contexte sécurité (sub du JWT → {@link UserDirectoryApi#tenantIdById}), JAMAIS d'un paramètre
     * client (anti-spoof) → le staff ne voit QUE l'assiduité de SON tenant. Staff sans tenant (admin
     * plateforme global) → liste vide. Agrégation en UNE requête (anti-N+1), enrichie du nom
     * d'affichage de l'organisateur en un seul batch, triée no-shows DESC.</p>
     *
     * @param from borne basse incluse (sur {@code startAt})
     * @param to   borne haute exclue
     */
    public List<NoShowStatsDto> noShowStats(Instant from, Instant to) {
        if (!SecurityHelper.isStaffOrAdmin()) {
            throw new ForbiddenException(
                "Accès interdit : les statistiques d'assiduité sont réservées au staff/admin");
        }
        UUID current = SecurityHelper.currentUserId();
        UUID tenantId = current != null ? userDirectory.tenantIdById(current).orElse(null) : null;
        if (tenantId == null) {
            // Staff/admin sans tenant (admin plateforme global) → aucun parc tenant à agréger.
            return List.of();
        }

        List<Object[]> rows = bookingRepo.aggregateNoShowStatsByOrganizer(tenantId, from, to);
        if (rows.isEmpty()) return List.of();

        // Enrichissement nom d'affichage organizer en un seul batch (anti-N+1), comme enrichForStaff.
        List<UUID> organizerIds = rows.stream()
            .map(r -> (UUID) r[0]).filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, UserDirectoryApi.UserName> names = organizerIds.isEmpty()
            ? Map.of()
            : userDirectory.namesByIds(organizerIds).stream()
                .collect(Collectors.toMap(UserDirectoryApi.UserName::id, Function.identity(), (a, b) -> a));

        List<NoShowStatsDto> stats = new ArrayList<>(rows.size());
        for (Object[] r : rows) {
            UUID organizerId = (UUID) r[0];
            long total     = ((Number) r[1]).longValue();
            long honored   = r[2] == null ? 0L : ((Number) r[2]).longValue();
            long noShows   = r[3] == null ? 0L : ((Number) r[3]).longValue();
            long cancelled = r[4] == null ? 0L : ((Number) r[4]).longValue();
            Instant lastNoShowAt = (Instant) r[5];
            // Taux = no-shows / (honorés + no-shows). Les annulations N'entrent PAS au dénominateur.
            long denom = honored + noShows;
            double rate = denom == 0 ? 0.0 : (noShows * 100.0) / denom;

            UserDirectoryApi.UserName n = organizerId != null ? names.get(organizerId) : null;
            String organizerName = n != null
                ? java.util.stream.Stream.of(n.firstName(), n.lastName())
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.joining(" ")).trim()
                : null;
            stats.add(new NoShowStatsDto(
                organizerId, (organizerName == null || organizerName.isBlank()) ? null : organizerName,
                total, honored, noShows, cancelled, rate, lastNoShowAt));
        }
        // Tri no-shows DESC (les pires assiduités en tête du board).
        stats.sort(java.util.Comparator.comparingLong(NoShowStatsDto::noShows).reversed());
        return stats;
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
        // Gap #3 — notifier le staff du tenant d'une nouvelle demande (parité legacy
        // send-pcc-staff-notification). Destinataires résolus ici (frontière Modulith) et portés
        // sur l'event ; core.notification n'a qu'à itérer.
        Resource resource = saved.getResource();
        if (resource != null) {
            List<UUID> staffRecipientIds =
                bookingRepo.findStaffRecipientIdsForTenant(resource.getTenantId(), saved.getOrganizerId());
            eventPublisher.publishEvent(new ResourceBookingCreatedEvent(
                saved.getId(), saved.getOrganizerId(), saved.getResourceId(),
                resource.getTenantId(), resource.getResourceType(), staffRecipientIds, Instant.now()));
        }
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
                SecurityHelper.currentUserId(),   // acteur : staff/admin OU membre (anti self-notify cancel, R3-bis)
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
        String oldStatus = b.getStatus();
        b.markDeleted();
        ResourceBooking saved = bookingRepo.save(b);

        // Lot B9 — l'annulation d'un booking (soft-delete) publie un event « cancelled » consommé par
        // core.notification. L'acteur (changedBy) route la notif (R3-bis) : membre lui-même
        // (changedBy == organizerId) → notif au STAFF du tenant (et pas de push redondant au membre) ;
        // staff/admin → push d'annulation au CLIENT (comportement existant du listener).
        // Frontière Modulith : on résout les destinataires staff ici (uniquement quand le membre
        // s'annule) et on les porte sur l'event ; core.notification n'a qu'à itérer.
        UUID changedBy = SecurityHelper.currentUserId();
        Resource resource = saved.getResource();
        boolean memberSelfCancel = changedBy != null && changedBy.equals(saved.getOrganizerId());
        List<UUID> staffRecipientIds = null;
        if (memberSelfCancel && resource != null) {
            staffRecipientIds = bookingRepo.findStaffRecipientIdsForTenant(
                resource.getTenantId(), saved.getOrganizerId());
        }
        eventPublisher.publishEvent(new ResourceBookingStatusChangedEvent(
            saved.getId(),
            saved.getOrganizerId(),
            saved.getResourceId(),
            resource != null ? resource.getTenantId() : null,
            resource != null ? resource.getResourceType() : null,
            oldStatus, "cancelled",
            changedBy,
            staffRecipientIds,
            Instant.now()));
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
     * Acteur <b>cross-tenant</b> (SUPERADMIN) — identifié par l'autorité {@code VIEW:TENANTS} (que
     * seul le SUPERADMIN détient, cf. {@link TenantScope}). Un tel acteur gère le parc de N'IMPORTE
     * quel tenant (vue globale), sans restriction au tenant HOME. Aligné sur la sémantique de
     * {@link TenantScope#visibleTenantIdsOrNull()} == {@code null}. {@code hasAuthority} only.
     */
    private boolean isCrossTenant() {
        return SecurityHelper.hasAuthority("VIEW:TENANTS");
    }

    /**
     * Tenant <b>HOME</b> du staff appelant (résolu serveur depuis le sub du JWT via
     * {@link UserDirectoryApi#tenantIdById(UUID)}), ou {@code null} si non authentifié / admin
     * plateforme global sans tenant. C'est le périmètre de GESTION du parc — un owner PCC est STAFF
     * de palmeraie (tenant home), <b>pas membre</b> : son parc n'est donc PAS résolu via les
     * memberships ({@link TenantScope}), mais via son tenant home (calque exact de
     * {@link #findTenantBookings} / {@link #noShowStats}).
     */
    private UUID resolveStaffTenantOrNull() {
        UUID current = SecurityHelper.currentUserId();
        return current != null ? userDirectory.tenantIdById(current).orElse(null) : null;
    }

    /**
     * Gate de GESTION du parc (édition / toggle / suppression) : autorise si l'appelant est
     * cross-tenant (SUPERADMIN — vue globale) OU si la ressource appartient à son tenant HOME de
     * staff. Sinon {@link NotFoundException} (404) — on ne divulgue jamais l'existence d'une ressource
     * d'un programme que l'appelant ne gère pas (cohérent avec le 404 hors-périmètre des lectures).
     */
    private void requireManageableByStaff(Resource r, UUID id) {
        if (isCrossTenant()) return;
        UUID staffTenant = resolveStaffTenantOrNull();
        if (staffTenant == null || !staffTenant.equals(r.getTenantId())) {
            throw new NotFoundException("Resource", id);
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
