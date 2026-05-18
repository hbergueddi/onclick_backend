package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.modules.loyalty.api.ExpiredPointsSummaryDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import com.onesley.oneclick.modules.loyalty.api.GainRulePatchDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnResultDto;
import com.onesley.oneclick.modules.loyalty.api.TierDto;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service métier loyalty — gestion comptable points.
 *
 * <p>Pattern : {@link #earnPoints} fait dans la même transaction :
 * <ol>
 *   <li>Récupère ou crée le {@link LoyaltyAccount} pour (client, restaurant)</li>
 *   <li>INSERT {@link LoyaltyTransaction} type=earn</li>
 *   <li>UPDATE {@code account.balance += points}</li>
 * </ol>
 * Garantit consistency : SUM(transactions.points) = balance.
 */
@Service
@Transactional(readOnly = true)
public class LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final GainRuleRepository gainRuleRepository;
    private final TierRepository tierRepository;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public LoyaltyService(LoyaltyAccountRepository accountRepository,
                          LoyaltyTransactionRepository transactionRepository,
                          GainRuleRepository gainRuleRepository,
                          TierRepository tierRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.gainRuleRepository = gainRuleRepository;
        this.tierRepository = tierRepository;
        this.eventPublisher = eventPublisher;
    }

    public LoyaltyAccountDto findAccount(UUID accountId) {
        LoyaltyAccount a = accountRepository.findById(accountId)
            .orElseThrow(() -> new NotFoundException("LoyaltyAccount", accountId));
        return a.toDto();
    }

    public LoyaltyAccountDto findOrCreate(UUID clientId, UUID restaurantId) {
        LoyaltyAccount account = findOrCreateInternal(clientId, restaurantId);
        return account.toDto();
    }

    public List<LoyaltyAccountDto> findByClient(UUID clientId) {
        return accountRepository.findAllByClientId(clientId).stream()
            .map(LoyaltyAccount::toDto)
            .toList();
    }

    public List<LoyaltyTransactionDto> findTransactionsByAccount(UUID accountId) {
        LoyaltyAccount a = accountRepository.findById(accountId)
            .orElseThrow(() -> new NotFoundException("LoyaltyAccount", accountId));
        SecurityHelper.requireOwnerOrAdmin(a.getClientId());
        return transactionRepository.findAllByAccountId(accountId).stream()
            .map(LoyaltyTransaction::toDto)
            .toList();
    }

    @Transactional
    public LoyaltyTransactionDto earnPoints(LoyaltyEarnDto dto) {
        LoyaltyAccount account = findOrCreateInternal(dto.clientId(), dto.restaurantId());

        LoyaltyTransaction tx = new LoyaltyTransaction(
            UUID.randomUUID(), account.getId(), "earn", dto.points(), dto.reason()
        );
        if (dto.amount() != null) tx.setAmount(dto.amount());
        transactionRepository.save(tx);

        account.addPoints(dto.points());
        accountRepository.save(account);

        // Publish event → Kafka topic 'loyalty.earned'
        eventPublisher.publishEvent(new LoyaltyEarnedEvent(
            tx.getId(), account.getId(),
            dto.clientId(), dto.restaurantId(), account.getTenantId(),
            dto.points(), dto.amount(), dto.reason(),
            Instant.now()
        ));

        return tx.toDto();
    }

    /**
     * Snap2Earn — orchestrateur (porté depuis l'Edge Function Supabase {@code snap2earn}).
     *
     * <p>Flow :
     * <ol>
     *   <li>Anti-doublon par ({@code restaurantId}, {@code ticketRef}) si fourni</li>
     *   <li>Lookup {@link GainRule} du restaurant (fallback conversion {@code 0.10} si absent)</li>
     *   <li>Calcul points = {@code floor(amount * conversionRate)} avec cap (capPerVisit)
     *       et plancher (minAmount)</li>
     *   <li>Délégation à {@link #earnPoints(LoyaltyEarnDto)} qui fait INSERT transaction +
     *       UPDATE balance dans la même tx Spring + publie l'event {@link LoyaltyEarnedEvent}</li>
     * </ol>
     *
     * <p>Le {@code ticketRef} est encodé dans {@code reason} sous forme
     * {@code "snap2earn|<ticket_ref>|<photo_url?>"} pour permettre l'anti-doublon
     * sans table {@code scanned_tickets} dédiée (cf. {@link LoyaltyTransactionRepository
     * #existsSnap2EarnByRestaurantAndTicketRef}).
     */
    @Transactional
    public Snap2EarnResultDto snap2earn(Snap2EarnDto dto) {
        // 1. Anti-doublon (si ticketRef fourni)
        if (dto.ticketRef() != null && !dto.ticketRef().isBlank()) {
            String prefix = "snap2earn|" + dto.ticketRef() + "|%";
            if (transactionRepository.existsSnap2EarnByRestaurantAndTicketRef(dto.restaurantId(), prefix)) {
                throw new BadRequestException(
                    "Doublon détecté : ce ticket a déjà été scanné pour ce restaurant"
                );
            }
        }

        // 2. Lookup gain rule (fallback : taux conversion 0.10, pas de cap, pas de min)
        Optional<GainRule> rule = gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(dto.restaurantId());
        BigDecimal conversionRate = rule.map(GainRule::getConversionRate)
            .orElse(new BigDecimal("0.10"));
        BigDecimal minAmount = rule.map(GainRule::getMinAmount).orElse(BigDecimal.ZERO);
        Integer capPerVisit = rule.map(GainRule::getCapPerVisit).orElse(null);
        String gainRuleApplied = rule.isPresent() ? "restaurant" : "default";

        // 3. Calcul points : floor(amount * rate), cap, plancher min_amount
        int points = 0;
        if (dto.amount().compareTo(minAmount) >= 0) {
            BigDecimal raw = dto.amount().multiply(conversionRate)
                .setScale(0, RoundingMode.FLOOR);
            points = raw.intValueExact();
            if (capPerVisit != null && points > capPerVisit) {
                points = capPerVisit;
            }
        }

        if (points <= 0) {
            // Pas de points crédités — on retourne le solde existant sans transaction
            LoyaltyAccount account = findOrCreateInternal(dto.clientId(), dto.restaurantId());
            return new Snap2EarnResultDto(0, account.getBalance(), null, gainRuleApplied);
        }

        // 4. Reason encodé : "snap2earn|<ticket_ref>|<photo_url?>"
        String reason = "snap2earn|"
            + (dto.ticketRef() != null ? dto.ticketRef() : "")
            + "|"
            + (dto.photoUrl() != null ? dto.photoUrl() : "");

        // 5. Délégation à earnPoints — INSERT tx + UPDATE balance + publish event
        LoyaltyEarnDto earnDto = new LoyaltyEarnDto(
            dto.clientId(), dto.restaurantId(), points, dto.amount(), reason
        );
        LoyaltyTransactionDto tx = earnPoints(earnDto);

        // 6. Solde courant après crédit (relecture car earnPoints retourne uniquement la tx)
        LoyaltyAccount account = findOrCreateInternal(dto.clientId(), dto.restaurantId());
        return new Snap2EarnResultDto(points, account.getBalance(), tx.id(), gainRuleApplied);
    }

    /**
     * Sprint G.5 — Gift points (port EF gift-points Supabase).
     *
     * <p>Un sender offre des points à un receiver pour un restaurant donné.
     * Débit sender + crédit receiver dans la même tx, avec validation cap
     * (max 200/jour) via legacy frontend.
     *
     * @param senderId    UUID du donneur (vérification owner via SecurityHelper)
     * @param dto         Payload validé (receiverId, restaurantId, points, message)
     * @return            La transaction de crédit côté receiver
     */
    @Transactional
    public LoyaltyTransactionDto giftPoints(UUID senderId, com.onesley.oneclick.modules.loyalty.api.GiftPointsDto dto) {
        if (senderId.equals(dto.receiverId())) {
            throw new BadRequestException("Sender et receiver doivent être différents");
        }
        // 1. Débit côté sender
        LoyaltyAccount senderAccount = findOrCreateInternal(senderId, dto.restaurantId());
        if (senderAccount.getBalance() < dto.points()) {
            throw new BadRequestException(
                String.format("Solde insuffisant : %d points demandés, %d disponibles",
                    dto.points(), senderAccount.getBalance())
            );
        }
        LoyaltyTransaction debit = new LoyaltyTransaction(
            UUID.randomUUID(), senderAccount.getId(), "gift",
            -dto.points(), "gift:to:" + dto.receiverId()
        );
        transactionRepository.save(debit);
        senderAccount.deductPoints(dto.points());
        accountRepository.save(senderAccount);

        // 2. Crédit côté receiver
        LoyaltyAccount receiverAccount = findOrCreateInternal(dto.receiverId(), dto.restaurantId());
        String reason = "gift:from:" + senderId + (dto.message() != null ? " | " + dto.message() : "");
        LoyaltyTransaction credit = new LoyaltyTransaction(
            UUID.randomUUID(), receiverAccount.getId(), "gift",
            dto.points(), reason
        );
        transactionRepository.save(credit);
        receiverAccount.addPoints(dto.points());
        accountRepository.save(receiverAccount);

        return credit.toDto();
    }

    @Transactional
    public LoyaltyTransactionDto spendPoints(UUID clientId, UUID restaurantId, int points, String reason) {
        if (points <= 0) throw new BadRequestException("points doit être > 0");
        LoyaltyAccount account = findOrCreateInternal(clientId, restaurantId);
        if (account.getBalance() < points) {
            throw new BadRequestException(
                String.format("Solde insuffisant : %d points demandés, %d disponibles", points, account.getBalance())
            );
        }
        LoyaltyTransaction tx = new LoyaltyTransaction(
            UUID.randomUUID(), account.getId(), "spend", -points, reason
        );
        transactionRepository.save(tx);
        account.deductPoints(points);
        accountRepository.save(account);

        // Publish event → Kafka topic 'loyalty.redeemed' (notification consumer)
        eventPublisher.publishEvent(new LoyaltyRedeemedEvent(
            tx.getId(), account.getId(),
            clientId, restaurantId, account.getTenantId(),
            points, null,  // discount_amount calculé séparément si redemption
            Instant.now()
        ));

        return tx.toDto();
    }

    // ─── Gain rules (par-restaurant) ─────────────────────────────────────────

    /**
     * Récupère la règle de gain active d'un restaurant.
     *
     * <p>Retourne {@code null} si aucune règle n'existe — un resto sans gain
     * rule est un état NORMAL (pas encore configuré). 404 serait sémantiquement
     * faux : ressource "restaurant" existe, juste pas de child "rule". Le
     * front affiche "Règle non configurée" gracieusement.
     */
    public GainRuleDto findGainRuleByRestaurant(UUID restaurantId) {
        return gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(restaurantId)
            .map(GainRule::toDto)
            .orElse(null);
    }

    /**
     * Crée une nouvelle règle de gain pour un restaurant.
     *
     * <p>Échoue si une règle non-supprimée existe déjà (contrainte UNIQUE
     * sur {@code restaurant_id} en DB). Le tenant_id est rempli par trigger DB V13.
     */
    @Transactional
    public GainRuleDto createGainRule(GainRuleCreateDto dto) {
        if (gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(dto.restaurantId()).isPresent()) {
            throw new BadRequestException(
                "Une règle de gain existe déjà pour ce restaurant — utiliser PATCH pour modifier"
            );
        }
        GainRule rule = new GainRule(UUID.randomUUID(), dto.restaurantId(), dto.conversionRate());
        if (dto.capPerVisit() != null) rule.setCapPerVisit(dto.capPerVisit());
        if (dto.capPerMonth() != null) rule.setCapPerMonth(dto.capPerMonth());
        if (dto.minAmount() != null) rule.setMinAmount(dto.minAmount());
        GainRule saved = gainRuleRepository.save(rule);

        // tenant_id rempli par trigger DB V13 → refresh pour récupérer la valeur
        entityManager.flush();
        entityManager.refresh(saved);
        return saved.toDto();
    }

    /** Patch partiel — seuls les champs non-null sont appliqués. */
    @Transactional
    public GainRuleDto patchGainRule(UUID id, GainRulePatchDto dto) {
        GainRule rule = gainRuleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("GainRule", id));
        if (dto.conversionRate() != null) rule.setConversionRate(dto.conversionRate());
        if (dto.capPerVisit() != null) rule.setCapPerVisit(dto.capPerVisit());
        if (dto.capPerMonth() != null) rule.setCapPerMonth(dto.capPerMonth());
        if (dto.minAmount() != null) rule.setMinAmount(dto.minAmount());
        if (dto.isActive() != null) rule.setActive(dto.isActive());
        if (dto.welcomePointsDefault() != null) rule.setWelcomePointsDefault(dto.welcomePointsDefault());
        if (dto.welcomePointsMax() != null) rule.setWelcomePointsMax(dto.welcomePointsMax());
        // Garde-fou applicatif redondant avec CHECK DB — meilleur message d'erreur.
        if (rule.getWelcomePointsMax() < rule.getWelcomePointsDefault()) {
            throw new BadRequestException(
                "welcomePointsMax (" + rule.getWelcomePointsMax() + ") doit être >= welcomePointsDefault ("
                + rule.getWelcomePointsDefault() + ")"
            );
        }
        return gainRuleRepository.save(rule).toDto();
    }

    /** Soft delete d'une règle de gain — la ligne reste en DB ({@code deleted_at = now()}). */
    @Transactional
    public void deleteGainRule(UUID id) {
        GainRule rule = gainRuleRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("GainRule", id));
        rule.markDeleted();
        gainRuleRepository.save(rule);
    }

    // ─── Helpers loyalty pour Pocket ─────────────────────────────────────────

    /**
     * Toutes les transactions d'un client cross-comptes (anti-N+1).
     * Tri {@code created_at DESC}, {@code limit} default 50 (cf controller).
     */
    public List<LoyaltyTransactionDto> findTransactionsByClient(UUID clientId, int limit) {
        SecurityHelper.requireOwnerOrAdmin(clientId);
        return transactionRepository.findAllByClientId(clientId, PageRequest.of(0, limit)).stream()
            .map(LoyaltyTransaction::toDto)
            .toList();
    }

    /**
     * Sprint G.2.8 — Toutes les transactions d'un restaurant (anti-N+1).
     *
     * <p>Utilisé par ProDesk ClientSummary/StaffSummary qui aggrègent les points
     * crédités/consommés au niveau restaurant. Pas de filtre client_id.
     *
     * <p>RBAC : owner du restaurant ou admin (cf controller @PreAuthorize).
     */
    public List<LoyaltyTransactionDto> findTransactionsByRestaurant(UUID restaurantId, int limit) {
        return transactionRepository.findAllByRestaurantId(restaurantId, PageRequest.of(0, limit)).stream()
            .map(LoyaltyTransaction::toDto)
            .toList();
    }

    /** Résumé des points expirés d'un client (cross-comptes). */
    public ExpiredPointsSummaryDto findExpiredPointsByClient(UUID clientId) {
        SecurityHelper.requireOwnerOrAdmin(clientId);
        List<LoyaltyTransaction> expired = transactionRepository.findExpiredByClientId(clientId);
        // points négatifs en DB pour les transactions 'expire' → on remet en positif pour l'UI
        int totalExpired = expired.stream().mapToInt(t -> Math.abs(t.getPoints())).sum();
        List<LoyaltyTransactionDto> dtos = expired.stream().map(LoyaltyTransaction::toDto).toList();
        return new ExpiredPointsSummaryDto(totalExpired, dtos);
    }

    /** Liste tous les paliers de fidélité (toutes tenants confondus — usage SUPERADMIN). */
    public List<TierDto> listTiers() {
        return tierRepository.findAll().stream()
            .map(Tier::toDto)
            .toList();
    }

    /** Liste les paliers d'un tenant spécifique. */
    public List<TierDto> listTiersByTenant(UUID tenantId) {
        return tierRepository.findAllByTenantId(tenantId).stream()
            .map(Tier::toDto)
            .toList();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private LoyaltyAccount findOrCreateInternal(UUID clientId, UUID restaurantId) {
        // Recherche par couple (client, restaurant) — UNIQUE constraint en DB
        Optional<LoyaltyAccount> existing = accountRepository.findAllByClientId(clientId).stream()
            .filter(a -> a.getRestaurantId().equals(restaurantId))
            .findFirst();
        if (existing.isPresent()) return existing.get();

        // Pattern microservice : insert direct des UUID (pas de getReference cross-aggregate)
        LoyaltyAccount account = new LoyaltyAccount(UUID.randomUUID(), clientId, restaurantId);
        LoyaltyAccount saved = accountRepository.save(account);

        // tenant_id est rempli par trigger DB V10 → refresh pour récupérer la valeur
        // (sinon getTenantId() renvoie null car Hibernate ne re-lit pas après INSERT)
        entityManager.flush();
        entityManager.refresh(saved);
        return saved;
    }
}
