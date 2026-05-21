package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.GainRuleCreateDto;
import com.onesley.oneclick.modules.loyalty.api.GainRulePatchDto;
import com.onesley.oneclick.modules.loyalty.api.GiftPointsDto;
import com.onesley.oneclick.modules.loyalty.api.LoyaltyEarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnDto;
import com.onesley.oneclick.modules.loyalty.api.Snap2EarnResultDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.LoyaltyEarnedEvent;
import com.onesley.oneclick.shared.events.LoyaltyRedeemedEvent;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link LoyaltyService} (L3 — modules.loyalty, cœur métier).
 * Couvre earn / snap2earn (anti-doublon, règle, cap, plancher, fallback) / gift /
 * spend / gain-rules CRUD (garde welcome-points) / tiers / comptes. Méthodes à
 * {@code SecurityHelper} statique neutralisées via mockStatic.
 */
@ExtendWith(MockitoExtension.class)
class LoyaltyServiceTest {

    @Mock LoyaltyAccountRepository accountRepository;
    @Mock LoyaltyTransactionRepository transactionRepository;
    @Mock GainRuleRepository gainRuleRepository;
    @Mock TierRepository tierRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock EntityManager entityManager;
    @InjectMocks LoyaltyService service;

    @BeforeEach
    void injectEm() { ReflectionTestUtils.setField(service, "entityManager", entityManager); }

    private LoyaltyAccount account(UUID clientId, UUID restaurantId, int balance) {
        LoyaltyAccount a = new LoyaltyAccount(UUID.randomUUID(), clientId, restaurantId);
        a.addPoints(balance);
        return a;
    }
    private GainRule gainRule(UUID restaurantId, String rate) {
        return new GainRule(UUID.randomUUID(), restaurantId, new BigDecimal(rate));
    }

    // ─── findAccount / findOrCreate / findByClient ──────────────────────────────

    @Test
    void findAccount_notFound_throwsNotFound() {
        when(accountRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findAccount(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void findAccount_found_returnsDto() {
        LoyaltyAccount a = account(UUID.randomUUID(), UUID.randomUUID(), 50);
        when(accountRepository.findById(a.getId())).thenReturn(Optional.of(a));
        assertThat(service.findAccount(a.getId())).isNotNull();
    }

    @Test
    void findOrCreate_existing_returnsIt() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 10)));
        assertThat(service.findOrCreate(client, resto)).isNotNull();
        verify(accountRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void findOrCreate_new_savesAccount() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of());
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.findOrCreate(client, resto)).isNotNull();
        verify(accountRepository).save(any(LoyaltyAccount.class));
    }

    @Test
    void findByClient_andByRestaurant_map() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 5)));
        when(accountRepository.findAllByRestaurantId(resto)).thenReturn(List.of(account(client, resto, 5)));
        assertThat(service.findByClient(client)).hasSize(1);
        assertThat(service.findAccountsByRestaurant(resto)).hasSize(1);
    }

    @Test
    void findTransactionsByAccount_found_maps() {
        LoyaltyAccount a = account(UUID.randomUUID(), UUID.randomUUID(), 0);
        when(accountRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(transactionRepository.findAllByAccountId(a.getId()))
            .thenReturn(List.of(new LoyaltyTransaction(UUID.randomUUID(), a.getId(), "earn", 10, "x")));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            assertThat(service.findTransactionsByAccount(a.getId())).hasSize(1);
        }
    }

    // ─── earnPoints ─────────────────────────────────────────────────────────────

    @Test
    void earnPoints_withAmount_savesTxUpdatesBalancePublishesEvent() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        LoyaltyAccount acc = account(client, resto, 0);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(i -> i.getArgument(0));

        service.earnPoints(new LoyaltyEarnDto(client, resto, 30, new BigDecimal("300.00"), "scan"));

        assertThat(acc.getBalance()).isEqualTo(30);
        verify(transactionRepository).save(any(LoyaltyTransaction.class));
        verify(eventPublisher).publishEvent(any(LoyaltyEarnedEvent.class));
    }

    @Test
    void earnPoints_nullAmount_stillCredits() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        LoyaltyAccount acc = account(client, resto, 0);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any(LoyaltyTransaction.class))).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any(LoyaltyAccount.class))).thenAnswer(i -> i.getArgument(0));

        service.earnPoints(new LoyaltyEarnDto(client, resto, 5, null, "manual"));
        assertThat(acc.getBalance()).isEqualTo(5);
    }

    // ─── snap2earn ──────────────────────────────────────────────────────────────

    @Test
    void snap2earn_duplicate_throwsBadRequest() {
        UUID resto = UUID.randomUUID();
        when(transactionRepository.existsSnap2EarnByRestaurantAndTicketRef(eq(resto), anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.snap2earn(new Snap2EarnDto(
            UUID.randomUUID(), resto, new BigDecimal("100"), "TICKET-1", null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void snap2earn_belowMinAmount_creditsZero() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        GainRule rule = gainRule(resto, "0.10");
        rule.setMinAmount(new BigDecimal("1000"));
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule));
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 7)));

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("50"), null, null));

        assertThat(r.pointsEarned()).isZero();
        assertThat(r.accountBalance()).isEqualTo(7);
        assertThat(r.gainRuleApplied()).isEqualTo("restaurant");
    }

    @Test
    void snap2earn_withRule_capApplied() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        GainRule rule = gainRule(resto, "0.10");
        rule.setCapPerVisit(5);
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule));
        LoyaltyAccount acc = account(client, resto, 0);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("1000"), null, null));

        assertThat(r.pointsEarned()).isEqualTo(5); // floor(1000*0.10)=100 -> cap 5
        assertThat(r.gainRuleApplied()).isEqualTo("restaurant");
    }

    @Test
    void snap2earn_noRule_fallbackRate() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 0)));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("100"), null, null));

        assertThat(r.pointsEarned()).isEqualTo(10); // floor(100*0.10)
        assertThat(r.gainRuleApplied()).isEqualTo("default");
    }

    // ─── giftPoints ─────────────────────────────────────────────────────────────

    @Test
    void giftPoints_sameSenderReceiver_throwsBadRequest() {
        UUID u = UUID.randomUUID();
        assertThatThrownBy(() -> service.giftPoints(u, new GiftPointsDto(u, UUID.randomUUID(), 10, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void giftPoints_insufficientBalance_throwsBadRequest() {
        UUID sender = UUID.randomUUID(), receiver = UUID.randomUUID(), resto = UUID.randomUUID();
        when(accountRepository.findAllByClientId(sender)).thenReturn(List.of(account(sender, resto, 5)));
        assertThatThrownBy(() -> service.giftPoints(sender, new GiftPointsDto(receiver, resto, 50, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void giftPoints_success_debitsSenderCreditsReceiver() {
        UUID sender = UUID.randomUUID(), receiver = UUID.randomUUID(), resto = UUID.randomUUID();
        LoyaltyAccount sa = account(sender, resto, 100), ra = account(receiver, resto, 0);
        when(accountRepository.findAllByClientId(sender)).thenReturn(List.of(sa));
        when(accountRepository.findAllByClientId(receiver)).thenReturn(List.of(ra));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.giftPoints(sender, new GiftPointsDto(receiver, resto, 30, "joyeux anniv"));

        assertThat(sa.getBalance()).isEqualTo(70);
        assertThat(ra.getBalance()).isEqualTo(30);
    }

    // ─── spendPoints ────────────────────────────────────────────────────────────

    @Test
    void spendPoints_invalidPoints_throwsBadRequest() {
        assertThatThrownBy(() -> service.spendPoints(UUID.randomUUID(), UUID.randomUUID(), 0, "x"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void spendPoints_insufficient_throwsBadRequest() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 5)));
        assertThatThrownBy(() -> service.spendPoints(client, resto, 50, "redeem"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void spendPoints_success_deducts_andPublishesEvent() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        LoyaltyAccount acc = account(client, resto, 100);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.spendPoints(client, resto, 40, "redeem");

        assertThat(acc.getBalance()).isEqualTo(60);
        verify(eventPublisher).publishEvent(any(LoyaltyRedeemedEvent.class));
    }

    // ─── gain rules ─────────────────────────────────────────────────────────────

    @Test
    void findGainRuleByRestaurant_presentAndAbsent() {
        UUID resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(gainRule(resto, "0.10")));
        assertThat(service.findGainRuleByRestaurant(resto)).isNotNull();
        UUID resto2 = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto2)).thenReturn(Optional.empty());
        assertThat(service.findGainRuleByRestaurant(resto2)).isNull();
    }

    @Test
    void createGainRule_alreadyExists_throwsBadRequest() {
        UUID resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(gainRule(resto, "0.10")));
        assertThatThrownBy(() -> service.createGainRule(new GainRuleCreateDto(
            resto, new BigDecimal("0.15"), null, null, null))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void createGainRule_success_withOptionalFields() {
        UUID resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        when(gainRuleRepository.save(any(GainRule.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createGainRule(new GainRuleCreateDto(
            resto, new BigDecimal("0.15"), 50, 500, new BigDecimal("20.00")))).isNotNull();
        verify(entityManager).refresh(any());
    }

    @Test
    void patchGainRule_notFound_throwsNotFound() {
        when(gainRuleRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patchGainRule(UUID.randomUUID(),
            new GainRulePatchDto(null, null, null, null, null, null, null))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void patchGainRule_success_updatesFields() {
        GainRule rule = gainRule(UUID.randomUUID(), "0.10");
        when(gainRuleRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(rule));
        when(gainRuleRepository.save(any(GainRule.class))).thenAnswer(i -> i.getArgument(0));
        service.patchGainRule(rule.getId(), new GainRulePatchDto(
            new BigDecimal("0.20"), 10, 100, new BigDecimal("5.00"), false, null, null));
        assertThat(rule.getConversionRate()).isEqualByComparingTo("0.20");
    }

    @Test
    void patchGainRule_welcomeMaxBelowDefault_throwsBadRequest() {
        GainRule rule = gainRule(UUID.randomUUID(), "0.10");
        when(gainRuleRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.of(rule));
        assertThatThrownBy(() -> service.patchGainRule(rule.getId(),
            new GainRulePatchDto(null, null, null, null, null, 400, 100)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void deleteGainRule_notFoundAndSuccess() {
        when(gainRuleRepository.findByIdAndDeletedAtIsNull(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteGainRule(UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        GainRule rule = gainRule(UUID.randomUUID(), "0.10");
        when(gainRuleRepository.findByIdAndDeletedAtIsNull(rule.getId())).thenReturn(Optional.of(rule));
        service.deleteGainRule(rule.getId());
        assertThat(rule.getDeletedAt()).isNotNull();
    }

    // ─── client helpers (static) / tiers ───────────────────────────────────────

    @Test
    void findTransactionsByClient_maps() {
        UUID client = UUID.randomUUID();
        when(transactionRepository.findAllByClientId(eq(client), any(Pageable.class)))
            .thenReturn(List.of(new LoyaltyTransaction(UUID.randomUUID(), UUID.randomUUID(), "earn", 10, "x")));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            assertThat(service.findTransactionsByClient(client, 50)).hasSize(1);
        }
    }

    @Test
    void findExpiredPointsByClient_sumsAbsoluteValues() {
        UUID client = UUID.randomUUID();
        when(transactionRepository.findExpiredByClientId(client)).thenReturn(List.of(
            new LoyaltyTransaction(UUID.randomUUID(), UUID.randomUUID(), "expire", -30, "x"),
            new LoyaltyTransaction(UUID.randomUUID(), UUID.randomUUID(), "expire", -20, "y")));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            assertThat(service.findExpiredPointsByClient(client).totalExpired()).isEqualTo(50);
        }
    }

    @Test
    void listTiers_andByTenant_map() {
        when(tierRepository.findAll()).thenReturn(List.of());
        when(tierRepository.findAllByTenantId(any())).thenReturn(List.of());
        assertThat(service.listTiers()).isEmpty();
        assertThat(service.listTiersByTenant(UUID.randomUUID())).isEmpty();
    }

    @Test
    void findTransactionsByRestaurant_delegates() {
        UUID resto = UUID.randomUUID();
        when(transactionRepository.findAllByRestaurantIdEnriched(eq(resto), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.findTransactionsByRestaurant(resto, 50)).isEmpty();
    }
}
