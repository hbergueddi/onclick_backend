package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyAccountDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyTransactionDto;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    public LoyaltyService(LoyaltyAccountRepository accountRepository,
                          LoyaltyTransactionRepository transactionRepository,
                          ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
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
