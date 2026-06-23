package com.onesley.oneclick.modules.store.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserProvisioningApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.restaurant.api.RestaurantProvisioningApi;
import com.onesley.oneclick.modules.store.api.StoreOnboardingDtos.*;
import com.onesley.oneclick.shared.events.StoreOnboardingDecidedEvent;
import com.onesley.oneclick.shared.events.StoreOnboardingRequestedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
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

    /** Tenant public par défaut quand la demande n'en porte pas (onboarding OneClick standard). */
    private static final String DEFAULT_TENANT_SLUG = "oneclick";
    /** Alphabet du mot de passe temporaire (sans I/O/0/1/l ambigus). */
    private static final String TEMP_PWD_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StoreOnboardingRepository repo;
    private final UserDirectoryApi userDirectory;
    private final UserProvisioningApi userProvisioning;
    private final RestaurantProvisioningApi restaurantProvisioning;
    private final TenantDirectoryApi tenantDirectory;
    private final StoreOnboardingPublisher publisher;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Base URL du portail OneClick Business (restaurateur) pour le lien de connexion de l'email
     * d'approbation (B5) — override prod via APP_FRONTEND_BUSINESS_BASE_URL.
     */
    @Value("${app.frontend.business-base-url:https://business.app-oneclick.net}")
    private String businessBaseUrl;

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
        // Champs enrollment legacy (V58) — restaurés depuis le formulaire public.
        r.setBudget(dto.budget());
        r.setDescription(dto.description());
        r.setOwnerRole(dto.ownerRole());
        r.setIce(dto.ice());
        r.setIfNumber(dto.ifNumber());
        r.setRc(dto.rc());
        r.setPatente(dto.patente());
        r.setCapacity(dto.capacity());
        r.setServices(dto.services() == null ? null : dto.services().toArray(new String[0]));

        OnboardingRequestDto saved = OnboardingRequestDto.from(repo.save(r));
        log.info("[store/onboarding] new request: id={} restaurant={} email={}",
            saved.id(), saved.restaurantName(), saved.ownerEmail());

        // Lot B4 — notif in-app aux ADMINS plateforme (parité legacy admin_notifications « nouvelle
        // demande d'enseigne »). Destinataires (SUPERADMIN) résolus ICI (le module a la dépendance
        // core.identity) et portés sur l'event ; core.notification reste sans dépendance identity et
        // n'a qu'à itérer. Pattern ContractExpiringSoonEvent / RestaurantReferralActivatedEvent.
        // BE-1 — emails de soumission : l'event porte aussi ownerEmail/ownerFirstName/city pour que
        // core.email envoie (a) la copie interne à contact@onesley et (b) l'accusé au gérant, sans
        // lire modules.store (frontière Modulith).
        List<UUID> admins = userDirectory.adminUserIds();
        String contactName = fullName(saved.ownerFirstName(), saved.ownerLastName());
        eventPublisher.publishEvent(new StoreOnboardingRequestedEvent(
            saved.id(), saved.restaurantName(), contactName,
            saved.ownerEmail(), saved.ownerFirstName(), saved.city(),
            admins, Instant.now()));

        // BE-5 — file admin temps réel : pousse la nouvelle demande sur /topic/admin/onboarding
        // (best-effort, hors transaction métier).
        publisher.publishNew(saved);

        return saved;
    }

    public OnboardingRequestDto decide(UUID id, OnboardingDecisionDto dto) {
        StoreOnboardingRequest r = repo.findById(id)
            .orElseThrow(() -> new NotFoundException("StoreOnboardingRequest", id));

        boolean approved = "approved".equalsIgnoreCase(dto.status());

        // BE-2 — idempotence : une demande déjà approuvée (compte + resto provisionnés) ne se
        // re-provisionne pas (sinon collision email au 2e clic « approuver »). No-op renvoyant l'état.
        if (approved && r.getProvisionedUserId() != null) {
            log.info("[store/onboarding] decision idempotente: id={} déjà approuvée (user={} resto={}) — no-op",
                id, r.getProvisionedUserId(), r.getProvisionedRestaurantId());
            return OnboardingRequestDto.from(r);
        }

        // BE-2 — à l'approbation : provisionne SYNCHRONEMENT le compte gérant puis le restaurant.
        // Synchrone (pas event-driven) pour que l'admin reçoive un 409 immédiat en cas d'email déjà
        // pris, et pour n'envoyer l'email (avec mdp temporaire) qu'après un provisioning réussi.
        String tempPassword = null;
        if (approved) {
            UUID tenantId = resolveTenantId(r.getTenantId());
            tempPassword = generateTempPassword();
            // 1) compte gérant (identity) : rôle RESTAURATEUR, mdp temporaire, password_must_change=true.
            UUID ownerUserId = userProvisioning.provisionOwner(new UserProvisioningApi.ProvisionOwnerCommand(
                tenantId, r.getOwnerEmail(), r.getOwnerFirstName(), r.getOwnerLastName(),
                r.getOwnerPhone(), tempPassword));
            // 2) restaurant + restaurant_staffs(owner) (restaurant).
            UUID restaurantId = restaurantProvisioning.provisionWithOwner(new RestaurantProvisioningApi.ProvisionCommand(
                tenantId, r.getRestaurantName(), r.getCity(), r.getAddress(), r.getPhone(),
                r.getCuisine(), ownerUserId));
            r.setProvisionedUserId(ownerUserId);
            r.setProvisionedRestaurantId(restaurantId);
        }

        r.setStatus(dto.status());
        r.setRejectionReason(approved ? null : dto.rejectionReason());
        r.setReviewedBy(dto.reviewedBy());
        r.setReviewedAt(Instant.now());
        r.setDecisionEmailSentAt(Instant.now());
        OnboardingRequestDto saved = OnboardingRequestDto.from(repo.save(r));
        log.info("[store/onboarding] decision: id={} status={} reviewedBy={} provisionedUser={} provisionedResto={}",
            saved.id(), saved.status(), saved.reviewedBy(), r.getProvisionedUserId(), r.getProvisionedRestaurantId());

        // Lot B5 + BE-2 — email branded au gérant (verdict + lien connexion + mdp temporaire si
        // approuvé). La frontière Modulith interdit à store de dépendre de core.email : on publie
        // StoreOnboardingDecidedEvent qui porte TOUT le payload (email/nom/verdict/motif/lien/mdp) ;
        // le listener core.email envoie via Resend (kill-switch/stub si indisponible).
        String loginUrl = approved ? businessBaseUrl + "/login" : null;
        eventPublisher.publishEvent(new StoreOnboardingDecidedEvent(
            saved.id(), saved.ownerEmail(),
            fullName(saved.ownerFirstName(), saved.ownerLastName()),
            saved.restaurantName(), approved,
            approved ? null : saved.rejectionReason(),
            loginUrl, tempPassword, Instant.now()));

        return saved;
    }

    /**
     * Résout le tenant cible du restaurant provisionné : celui porté par la demande, sinon le tenant
     * public {@code oneclick} (onboarding OneClick standard). Le restaurant DOIT avoir un tenant
     * (colonne NOT NULL) — d'où le fallback explicite plutôt qu'un null qui casserait la création.
     */
    private UUID resolveTenantId(UUID requestTenantId) {
        if (requestTenantId != null) return requestTenantId;
        return tenantDirectory.findIdBySlug(DEFAULT_TENANT_SLUG)
            .orElseThrow(() -> new BadRequestException(
                "Tenant public « " + DEFAULT_TENANT_SLUG + " » introuvable — impossible de provisionner le restaurant"));
    }

    /** Mot de passe temporaire (12 caractères, alphabet sans ambiguïtés) — encodé BCrypt par identity. */
    private static String generateTempPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PWD_ALPHABET.charAt(RANDOM.nextInt(TEMP_PWD_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Concatène prénom + nom en un libellé contact ; null si les deux sont vides. */
    private static String fullName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        String joined = (f + " " + l).trim();
        return joined.isEmpty() ? null : joined;
    }
}
