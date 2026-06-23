package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.core.tenant.api.TenantDirectoryApi;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.modules.loyalty.api.ClientNameDto;
import com.onesley.oneclick.modules.loyalty.api.ExpiredPointsSummaryDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.GainRuleDto;
import com.onesley.oneclick.modules.loyalty.api.GainRulePatchDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyParamsDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnResultDto;
import com.onesley.oneclick.modules.loyalty.api.TierDto;
import com.onesley.oneclick.modules.loyalty.api.TierUpdateDto;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import com.onesley.oneclick.shared.events.PointsGiftedEvent;
import com.onesley.oneclick.shared.events.TierReachedEvent;
import static com.onesley.oneclick.shared.Temporals.toInstant;
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
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

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
@RequiredArgsConstructor
public class LoyaltyService {

    private final LoyaltyAccountRepository accountRepository;
    private final LoyaltyTransactionRepository transactionRepository;
    private final GainRuleRepository gainRuleRepository;
    private final LoyaltyRuleRepository loyaltyRuleRepository; // source de vérité point_value (loyalty_rules)
    private final TierRepository tierRepository;
    private final LoyaltyTierResolver tierResolver; // CL-2 — source unique du palier (DB + fallback canonique)
    private final TenantDirectoryApi tenantDirectory; // CL-3 — slug du tenant (gate enrollment)
    private final MembershipDirectoryApi membershipDirectory; // CL-3 — adhésion active (gate enrollment)
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository; // domaine identity (API publique) — résolution noms clients
    private final RedemptionOtpService redemptionOtpService; // Gap #2 — OTP grosses conversions

    @PersistenceContext
    private EntityManager entityManager;

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
        // Projection JOIN restaurants (anti-N+1) → DTO enrichi nom/cuisine pour le Pocket
        // (Mes Points / historique). Le module CLOSED ne peut pas importer l'entité Restaurant.
        return accountRepository.findAllByClientIdWithRestaurant(clientId).stream()
            .map(v -> new LoyaltyAccountDto(
                v.getId(), v.getClientId(), v.getRestaurantId(), v.getTierId(),
                v.getBalance(), v.getCreatedAt(), v.getRestaurantName(), v.getRestaurantCuisine()))
            .toList();
    }

    /**
     * Bug 28 — Tous les comptes fidélité d'un restaurant (PulsePro Dashboard
     * Client a besoin de la balance par client pour le KPI 'Solde Disponible'
     * et la colonne SOLDE du classement Top 10).
     *
     * <p>Pourquoi ne pas réutiliser {@link #findTransactionsByRestaurant} ?
     * Parce que {@code SUM(transactions.points)} != {@code SUM(accounts.balance)}
     * sur les données seedées (les accounts.balance ont été remplis directement
     * sans transactions miroir équivalentes). Le solde live = balance.
     *
     * <p>RBAC : owner du restaurant ou admin (cf controller @PreAuthorize).
     */
    public List<LoyaltyAccountDto> findAccountsByRestaurant(UUID restaurantId) {
        return accountRepository.findAllByRestaurantId(restaurantId).stream()
            .map(LoyaltyAccount::toDto)
            .toList();
    }

    /** P1 (anti-N+1 shim) — comptes de PLUSIEURS restaurants en 1 requête. ABAC par resto au controller. */
    public List<LoyaltyAccountDto> findAccountsByRestaurants(List<UUID> restaurantIds) {
        if (restaurantIds == null || restaurantIds.isEmpty()) return List.of();
        return accountRepository.findAllByRestaurantIdIn(restaurantIds).stream()
            .map(LoyaltyAccount::toDto)
            .toList();
    }

    /**
     * Résout les noms des clients pour les dashboards staff (PulsePro — Top clients).
     *
     * <p>Alternative <b>scoped</b> à {@code POST /api/users/by-ids} (VIEW:USERS, refusé au
     * RESTAURATEUR/STAFF). Ne renvoie que les clients ayant un compte fidélité à l'un des
     * {@code restaurantIds} (intersection avec les {@code clientIds} demandés) — empêche
     * l'énumération d'utilisateurs arbitraires. Le contrôle d'accès par restaurant
     * (staff/admin) est fait au niveau du controller via {@code RestaurantAccessGuard}.</p>
     */
    public List<ClientNameDto> resolveClientNames(List<UUID> restaurantIds, List<UUID> clientIds) {
        if (restaurantIds == null || restaurantIds.isEmpty() || clientIds == null || clientIds.isEmpty()) {
            return List.of();
        }
        Set<UUID> requested = Set.copyOf(clientIds);
        Set<UUID> allowed = restaurantIds.stream()
            .flatMap(rid -> accountRepository.findAllByRestaurantId(rid).stream())
            .map(LoyaltyAccount::getClientId)
            .filter(requested::contains)
            .collect(Collectors.toSet());
        if (allowed.isEmpty()) return List.of();
        return userRepository.findAllByIds(allowed).stream()
            .map(u -> new ClientNameDto(u.getId(), u.getFirstName(), u.getLastName(), u.getPhone()))
            .toList();
    }

    /**
     * Recherche floue de clients (Snap2Earn) par téléphone / prénom / nom — réutilise
     * {@link ClientNameDto}. Le scoping (CREATE:LOYALTY + RestaurantAccessGuard) est
     * porté par le contrôleur. {@code limit} borné [1..20] pour éviter les gros scans.
     */
    public List<ClientNameDto> searchClients(String q, int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        return userRepository.searchClients(q, org.springframework.data.domain.PageRequest.of(0, capped)).stream()
            .map(u -> new ClientNameDto(u.getId(), u.getFirstName(), u.getLastName(), u.getPhone()))
            .toList();
    }

    /**
     * Résout un client par son <b>Code OneClick</b> ({@code referral_code}, 8 chars
     * uppercase encodés dans le QR / la Carte Wallet du client). Snap2Earn : le staff
     * scanne, on identifie le porteur du ticket. Remplace le RPC legacy Supabase
     * {@code find_client_by_code}.
     *
     * <p>Filtre soft-deletes ({@code deletedAt IS NULL}) <b>et</b> rôle {@code CLIENT} :
     * un code de restaurateur / admin ne doit pas résoudre vers un compte fidélité.
     * {@code 404} si le code ne correspond à aucun client actif — le front bascule sur
     * « Code invalide ». Le scoping (CREATE:LOYALTY + RestaurantAccessGuard) est porté
     * par le contrôleur.
     */
    public ClientNameDto resolveClientByCode(String code) {
        return userRepository.findByReferralCode(code)
            .filter(u -> u.getDeletedAt() == null
                && u.getRole() != null
                && "CLIENT".equals(u.getRole().getCode()))
            .map(u -> new ClientNameDto(u.getId(), u.getFirstName(), u.getLastName(), u.getPhone()))
            .orElseThrow(() -> new NotFoundException("Client", code));
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

        // CL-3 — gate enrollment : accumuler de la fidélité sur un resto d'un tenant À ADHÉSION
        // (PCC/HOMU/…) est réservé aux membres actifs. Le throw annule la transaction (compte
        // éventuellement créé par findOrCreateInternal compris). Tenant public (oneclick) = pas de gate.
        requireMembershipForGatedTenant(dto.clientId(), account.getTenantId());

        // CH-3 — agrégat de points du client DANS ce tenant, AVANT le crédit (base de détection
        // du franchissement de palier ; paliers = par tenant via la table `tiers`).
        int tierTotalBefore = accountRepository.sumBalanceByClientAndTenant(dto.clientId(), account.getTenantId());

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

        // CH-3 — si le palier (seuils DB par tenant) change à la HAUSSE → event dédié (jamais sur
        // chaque gain : on ne publie que lorsque le nom du palier diffère). Consommé par
        // NotificationEventHandler → notif + push « Nouveau palier atteint 🎉 ».
        int tierTotalAfter = tierTotalBefore + dto.points();
        String oldTier = tierResolver.tierNameFor(tierTotalBefore, account.getTenantId());
        String newTier = tierResolver.tierNameFor(tierTotalAfter, account.getTenantId());
        if (newTier != null && !newTier.equals(oldTier)) {
            eventPublisher.publishEvent(new TierReachedEvent(
                dto.clientId(), account.getTenantId(), newTier, tierTotalAfter, Instant.now()));
        }

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
     *   <li>Conversion optionnelle ({@code redeemPoints}) : débit {@code type='spend'} du solde
     *       APRÈS le crédit (solde insuffisant → 400), publie {@link LoyaltyRedeemedEvent}</li>
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

        // 4. Crédit (earn) si points > 0 — INSERT tx + UPDATE balance + publish event.
        //    reason encodé : "snap2earn|<ticket_ref>|<photo_url?>" (anti-doublon + audit).
        UUID earnTxId = null;
        if (points > 0) {
            String reason = "snap2earn|"
                + (dto.ticketRef() != null ? dto.ticketRef() : "")
                + "|"
                + (dto.photoUrl() != null ? dto.photoUrl() : "");
            LoyaltyTransactionDto tx = earnPoints(new LoyaltyEarnDto(
                dto.clientId(), dto.restaurantId(), points, dto.amount(), reason
            ));
            earnTxId = tx.id();
        }

        // 5. Conversion optionnelle (redeemPoints) — débit du solde dans la MÊME tx Spring.
        //    Appliquée APRÈS le crédit : les points gagnés sur ce ticket sont immédiatement
        //    utilisables. type='spend' (CHECK loyalty_transactions), refus si solde insuffisant.
        int redeem = dto.redeemPoints() != null ? dto.redeemPoints() : 0;
        if (redeem > 0) {
            // Gap #2 — OTP requise au-delà du seuil (anti-vidage de solde sans consentement
            // explicite du client). Seuil = otp_required_above_pts de la gain_rule (déf. 200).
            int otpThreshold = rule.map(GainRule::getOtpRequiredAbovePts).orElse(200);
            if (redeem > otpThreshold) {
                if (dto.otpRequestId() == null || dto.otpCode() == null || dto.otpCode().isBlank()) {
                    throw new BadRequestException(
                        "OTP_REQUIRED: cette conversion (" + redeem + " pts) dépasse le seuil de "
                        + otpThreshold + " pts. Demandez au client son code de confirmation.");
                }
                String verdict = redemptionOtpService.verify(
                    dto.otpRequestId(), dto.otpCode(), dto.clientId(), dto.restaurantId(), redeem, dto.ticketRef());
                if (!RedemptionOtpService.OK.equals(verdict)) {
                    throw new BadRequestException("otp_" + verdict + ": code de confirmation invalide");
                }
            }
            LoyaltyAccount account = findOrCreateInternal(dto.clientId(), dto.restaurantId());
            if (account.getBalance() < redeem) {
                throw new BadRequestException(String.format(
                    "Solde insuffisant pour la conversion : %d points demandés, %d disponibles",
                    redeem, account.getBalance()));
            }
            LoyaltyTransaction debit = new LoyaltyTransaction(
                UUID.randomUUID(), account.getId(), "spend", -redeem,
                "snap2earn-redeem|" + (dto.ticketRef() != null ? dto.ticketRef() : "")
            );
            transactionRepository.save(debit);
            account.deductPoints(redeem);
            accountRepository.save(account);
            eventPublisher.publishEvent(new LoyaltyRedeemedEvent(
                debit.getId(), account.getId(),
                dto.clientId(), dto.restaurantId(), account.getTenantId(),
                redeem, null, Instant.now()
            ));
        }

        // 6. Solde courant après crédit + conversion (relecture) + résultat
        LoyaltyAccount account = findOrCreateInternal(dto.clientId(), dto.restaurantId());
        return new Snap2EarnResultDto(
            points, account.getBalance(), earnTxId, gainRuleApplied, redeem > 0 ? redeem : null);
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

        // Lot B7 — notif in-app « Cadeau de points » au bénéficiaire (server-side : le donneur n'a
        // pas CREATE:NOTIFICATIONS). Frontière Modulith : event consommé par core.notification.
        // fromName non résolu ici (pas de dépendance identity) → null → handler affiche « un ami ».
        eventPublisher.publishEvent(new PointsGiftedEvent(
            senderId, dto.receiverId(), dto.points(), null, Instant.now()));

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
            .map(rule -> rule.toDto(resolvePointValueMad(restaurantId)))
            .orElse(null);
    }

    /**
     * Valeur du point (MAD) du restaurant — source de vérité {@code loyalty_rules.point_value}
     * (hors table {@code gain_rules}). Défaut {@link GainRule#DEFAULT_POINT_VALUE_MAD}
     * (1 pt = 1 MAD) si aucune règle de valeur n'est configurée.
     */
    private BigDecimal resolvePointValueMad(UUID restaurantId) {
        return loyaltyRuleRepository
            .findFirstByRestaurantIdAndEnabledTrueOrderByCreatedAtDesc(restaurantId)
            .map(LoyaltyRule::getPointValue)
            .orElse(GainRule.DEFAULT_POINT_VALUE_MAD);
    }

    /**
     * Paramètres fidélité <b>effectifs</b> d'un client pour un restaurant — port du
     * hook legacy {@code useLoyaltyParams.ts} consommé par l'écran « Vos avantages »
     * + {@code ConversionGuide} ({@code OneClickVault.tsx}).
     *
     * <p>Calcul (parité legacy) :
     * <ol>
     *   <li><b>Base conversion</b> = {@code gain_rules.conversion_rate} du restaurant
     *       (défaut {@code 0.10} si aucune règle).</li>
     *   <li><b>Bonus palier</b> = on résout le palier du client via
     *       {@link LoyaltyTierResolver} sur le total de ses soldes <i>dans ce tenant</i>,
     *       puis on lit {@code tiers.bonus_percent} du palier nommé (0 si fallback canonique
     *       sans ligne {@code tiers}). Conversion effective = {@code base × (1 + bonus/100)}.</li>
     *   <li><b>Valeur du point</b> = {@link #resolvePointValueMad} ({@code loyalty_rules.point_value},
     *       défaut 1.0).</li>
     *   <li><b>Durée de validité</b> = {@code loyalty_rules.expires_after_days} (défaut 365).</li>
     * </ol>
     *
     * <p>Self-scope strict : le palier/bonus est calculé pour {@code clientId} (le user
     * courant, imposé par le controller via {@link SecurityHelper#currentUserId()}). On ne
     * révèle que les params du client appelant — jamais ceux d'autrui.
     *
     * <p>Lecture pure (aucune écriture) → pas de {@code findOrCreate} : on agrège les soldes
     * existants. Si le client n'a aucun compte dans ce restaurant, le tenant est résolu depuis
     * le restaurant via la règle existante / défaut canonique (tenant null → fallback resolver).
     */
    public LoyaltyParamsDto resolveLoyaltyParams(UUID clientId, UUID restaurantId) {
        // 1. Tenant du restaurant via un compte fidélité du client (tenant_id rempli par trigger
        //    DB V10). Pas de compte → tenant null → le resolver applique le fallback canonique.
        UUID tenantId = accountRepository.findAllByClientId(clientId).stream()
            .filter(a -> a.getRestaurantId().equals(restaurantId))
            .map(LoyaltyAccount::getTenantId)
            .filter(Objects::nonNull)
            .findFirst()
            .orElseGet(() -> accountRepository.findAllByClientId(clientId).stream()
                .map(LoyaltyAccount::getTenantId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null));

        // 2. Total des points du client dans ce tenant → palier courant.
        int tenantTotal = tenantId != null
            ? accountRepository.sumBalanceByClientAndTenant(clientId, tenantId)
            : 0;
        String tierName = tierResolver.tierNameFor(tenantTotal, tenantId);

        // 3. Bonus du palier nommé (table tiers du tenant). Fallback canonique (pas de ligne
        //    tiers) → bonus 0 : on n'invente pas de bonus hors de la DB.
        BigDecimal tierBonusPct = tenantId != null
            ? tierRepository.findAllByTenantId(tenantId).stream()
                .filter(t -> t.getName() != null && t.getName().equals(tierName))
                .map(Tier::getBonusPercent)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(BigDecimal.ZERO)
            : BigDecimal.ZERO;

        // 4. Conversion de base (gain_rules) + valeur point + durée (loyalty_rules).
        BigDecimal baseConversion = gainRuleRepository
            .findByRestaurantIdAndDeletedAtIsNull(restaurantId)
            .map(GainRule::getConversionRate)
            .orElse(DEFAULT_CONVERSION_RATE);
        BigDecimal effectiveConversion = baseConversion
            .multiply(BigDecimal.ONE.add(tierBonusPct.movePointLeft(2)));

        BigDecimal pointValueMad = resolvePointValueMad(restaurantId);
        int benefitDurationDays = loyaltyRuleRepository
            .findFirstByRestaurantIdAndEnabledTrueOrderByCreatedAtDesc(restaurantId)
            .map(LoyaltyRule::getExpiresAfterDays)
            .orElse(DEFAULT_BENEFIT_DURATION_DAYS);

        return new LoyaltyParamsDto(
            effectiveConversion, pointValueMad, benefitDurationDays, tierName, tierBonusPct);
    }

    /** Conversion par défaut (parité {@code GainRule.conversionRate} = 0.1000) si aucune {@code gain_rules}. */
    private static final BigDecimal DEFAULT_CONVERSION_RATE = new BigDecimal("0.1000");

    /** Durée de validité par défaut (parité {@code LoyaltyRule.expiresAfterDays} = 365) si aucune {@code loyalty_rules}. */
    private static final int DEFAULT_BENEFIT_DURATION_DAYS = 365;

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
        // Lot 4b — champs RuleBuilder legacy (optionnels)
        if (dto.pointValueMad() != null) rule.setPointValueMad(dto.pointValueMad());
        if (dto.evalPeriodType() != null) rule.setEvalPeriodType(dto.evalPeriodType());
        if (dto.evalPeriodValue() != null) rule.setEvalPeriodValue(dto.evalPeriodValue());
        if (dto.benefitDurationDays() != null) rule.setBenefitDurationDays(dto.benefitDurationDays());
        if (dto.minSpendMonthly() != null) rule.setMinSpendMonthly(dto.minSpendMonthly());
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
        // Lot 4b — champs RuleBuilder legacy (optionnels, partial update)
        if (dto.pointValueMad() != null) rule.setPointValueMad(dto.pointValueMad());
        if (dto.evalPeriodType() != null) rule.setEvalPeriodType(dto.evalPeriodType());
        if (dto.evalPeriodValue() != null) rule.setEvalPeriodValue(dto.evalPeriodValue());
        if (dto.benefitDurationDays() != null) rule.setBenefitDurationDays(dto.benefitDurationDays());
        if (dto.minSpendMonthly() != null) rule.setMinSpendMonthly(dto.minSpendMonthly());
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
     * <p>Bug 28 — Délègue au repo enrichi {@code findAllByRestaurantIdEnriched}
     * qui JOIN {@link LoyaltyAccount} et expose {@code clientId} + {@code restaurantId}
     * directement dans le DTO (besoin PulsePro Dashboard Client pour agréger par
     * client sans N+1 lookup). Backward-compat : les autres consommateurs (ClientSummary,
     * StaffSummary) ignorent simplement les 2 nouveaux champs.
     *
     * <p>RBAC : owner du restaurant ou admin (cf controller @PreAuthorize).
     */
    public List<LoyaltyTransactionDto> findTransactionsByRestaurant(UUID restaurantId, int limit) {
        return transactionRepository.findAllByRestaurantIdEnriched(restaurantId, PageRequest.of(0, limit));
    }

    /** P1 (anti-N+1 shim) — transactions enrichies de PLUSIEURS restaurants en 1 requête. ABAC par resto au controller. */
    public List<LoyaltyTransactionDto> findTransactionsByRestaurants(List<UUID> restaurantIds, int limit) {
        if (restaurantIds == null || restaurantIds.isEmpty()) return List.of();
        return transactionRepository.findAllByRestaurantIdInEnriched(restaurantIds, PageRequest.of(0, limit));
    }

    /**
     * Agrégat plateforme des tickets scannés (Snap2Earn) — carte « Tickets &
     * Lounge » du PulseBoard admin. Cross-restaurant, donc réservé admin
     * (VIEW:ANALYTICS côté contrôleur).
     */
    public com.onesley.oneclick.modules.loyalty.api.ScannedTicketStatsDto scannedTicketStats() {
        LoyaltyTransactionRepository.ScannedTicketStats s = transactionRepository.aggregateScannedTickets();
        return new com.onesley.oneclick.modules.loyalty.api.ScannedTicketStatsDto(
            s.getTicketCount(),
            s.getPointsEmitted(),
            s.getTotalAmount() == null ? java.math.BigDecimal.ZERO : s.getTotalAmount()
        );
    }

    /**
     * Liste plateforme des tickets scannés (Snap2Earn) — page TrustWatch « File de
     * tickets ». Cross-restaurant → réservé admin (VIEW:ANALYTICS côté contrôleur).
     *
     * <p>Native SQL (besoin de {@code split_part} pour extraire le {@code ticketRef}
     * encodé dans {@code reason = "snap2earn|<ref>|..."}, + JOIN loyalty_accounts
     * pour le {@code restaurant_id}). Tri récents d'abord, borné par {@code limit}.
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<com.onesley.oneclick.modules.loyalty.api.ScannedTicketDto> findScannedTickets(int limit) {
        var q = entityManager.createNativeQuery("""
            SELECT lt.id,
                   split_part(lt.reason, '|', 2) AS ticket_ref,
                   la.restaurant_id,
                   lt.amount,
                   lt.points,
                   lt.created_at
              FROM loyalty_transactions lt
              JOIN loyalty_accounts la ON la.id = lt.account_id
             WHERE lt.type = 'earn' AND lt.reason LIKE 'snap2earn|%'
             ORDER BY lt.created_at DESC
             LIMIT :limit
            """);
        q.setParameter("limit", limit);
        List<Object[]> rows = q.getResultList();
        return rows.stream().map(r -> new com.onesley.oneclick.modules.loyalty.api.ScannedTicketDto(
            r[0] != null ? (UUID) r[0] : null,
            (String) r[1],
            r[2] != null ? (UUID) r[2] : null,
            r[3] != null ? (BigDecimal) r[3] : null,
            r[4] != null ? ((Number) r[4]).intValue() : 0,
            r[5] != null ? toInstant(r[5]) : null
        )).toList();
    }

    /**
     * Flux mensuels de points sur 12 mois (Gap #9 — pilotage fidélité, port RPC legacy
     * {@code get_loyalty_monthly_flows}). Par mois : points gagnés ({@code earn}),
     * utilisés ({@code spend}, en valeur absolue) et expirés ({@code expire}, en valeur absolue).
     *
     * <p>Native SQL : {@code generate_series} sur 12 mois × agrégats par type sur
     * {@code loyalty_transactions}. Platform-wide (pilotage exec SUPERADMIN — gate
     * {@code VIEW:ANALYTICS} au contrôleur). Mois sans mouvement → 0 (COALESCE).
     */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<com.onesley.oneclick.modules.loyalty.api.LoyaltyMonthlyFlowDto> monthlyFlows() {
        var q = entityManager.createNativeQuery("""
            WITH months AS (
                SELECT generate_series(
                    date_trunc('month', now() - interval '11 months'),
                    date_trunc('month', now()),
                    interval '1 month'
                )::date AS m
            )
            SELECT m.m AS month_start,
                COALESCE((SELECT SUM(lt.points) FROM loyalty_transactions lt
                          WHERE lt.type = 'earn'
                            AND lt.created_at >= m.m AND lt.created_at < (m.m + interval '1 month')), 0) AS earned,
                COALESCE((SELECT SUM(-lt.points) FROM loyalty_transactions lt
                          WHERE lt.type = 'spend'
                            AND lt.created_at >= m.m AND lt.created_at < (m.m + interval '1 month')), 0) AS redeemed,
                COALESCE((SELECT SUM(-lt.points) FROM loyalty_transactions lt
                          WHERE lt.type = 'expire'
                            AND lt.created_at >= m.m AND lt.created_at < (m.m + interval '1 month')), 0) AS expired
            FROM months m
            ORDER BY m.m
            """);
        List<Object[]> rows = q.getResultList();
        return rows.stream().map(r -> new com.onesley.oneclick.modules.loyalty.api.LoyaltyMonthlyFlowDto(
            // Hibernate 6 mappe une colonne SQL date → LocalDate ; certains drivers → java.sql.Date.
            r[0] instanceof java.time.LocalDate ld ? ld : ((java.sql.Date) r[0]).toLocalDate(),
            ((Number) r[1]).longValue(),
            ((Number) r[2]).longValue(),
            ((Number) r[3]).longValue()
        )).toList();
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

    /**
     * PATCH partiel d'un palier de fidélité (admin /forge/regles). Aligné sur le
     * modèle réel : name / minPoints / bonusPercent / sortOrder. RBAC LOYALTY_TIER
     * (controller). Seuls les champs présents sont appliqués.
     */
    @Transactional
    public TierDto updateTier(UUID id, TierUpdateDto dto) {
        Tier tier = tierRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("Tier", id));
        if (dto.name() != null && !dto.name().isBlank()) tier.setName(dto.name());
        if (dto.minPoints() != null) tier.setMinPoints(dto.minPoints());
        if (dto.bonusPercent() != null) tier.setBonusPercent(dto.bonusPercent());
        if (dto.sortOrder() != null) tier.setSortOrder(dto.sortOrder());
        return tierRepository.save(tier).toDto();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** Slug du tenant <b>public</b> (catalogue OneClick standard) — seul tenant SANS gate d'adhésion. */
    private static final String PUBLIC_TENANT_SLUG = "oneclick";

    /**
     * CL-3 — refuse l'accumulation de fidélité pour un non-membre sur un tenant à adhésion.
     * No-op sur le tenant public (oneclick) ou si le tenant est introuvable (défensif).
     */
    private void requireMembershipForGatedTenant(UUID clientId, UUID tenantId) {
        if (tenantId == null) return;
        String slug = tenantDirectory.slugById(tenantId).orElse(null);
        if (slug == null || PUBLIC_TENANT_SLUG.equals(slug)) return; // tenant public → pas de gate
        if (!membershipDirectory.isActiveMember(clientId, tenantId)) {
            throw new ForbiddenException(
                "Fidélité réservée aux membres de ce club — adhésion requise pour accumuler des points.");
        }
    }

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
