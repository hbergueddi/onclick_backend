package com.onesley.oneclick.modules.loyalty.internal;

import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.loyalty.api.TierUpdateDto;
import com.onesley.oneclick.modules.loyalty.api.ClientNameDto;
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
import static org.mockito.Mockito.mock;
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
    @Mock UserRepository userRepository;
    @InjectMocks LoyaltyService service;

    @BeforeEach
    void injectEm() { ReflectionTestUtils.setField(service, "entityManager", entityManager); }

    @Test
    void scannedTicketStats_mapsProjectionToDto() {
        var p = mock(LoyaltyTransactionRepository.ScannedTicketStats.class);
        when(p.getTicketCount()).thenReturn(7L);
        when(p.getPointsEmitted()).thenReturn(420L);
        when(p.getTotalAmount()).thenReturn(new BigDecimal("1234.50"));
        when(transactionRepository.aggregateScannedTickets()).thenReturn(p);

        var dto = service.scannedTicketStats();
        assertThat(dto.ticketCount()).isEqualTo(7L);
        assertThat(dto.pointsEmitted()).isEqualTo(420L);
        assertThat(dto.totalAmount()).isEqualByComparingTo("1234.50");
    }

    @Test
    void scannedTicketStats_nullAmount_defaultsToZero() {
        var p = mock(LoyaltyTransactionRepository.ScannedTicketStats.class);
        when(p.getTicketCount()).thenReturn(0L);
        when(p.getPointsEmitted()).thenReturn(0L);
        when(p.getTotalAmount()).thenReturn(null);
        when(transactionRepository.aggregateScannedTickets()).thenReturn(p);

        assertThat(service.scannedTicketStats().totalAmount()).isEqualByComparingTo("0");
    }

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

    // ─── resolveClientNames (PulsePro — scoped, alternative à /by-ids VIEW:USERS) ──

    @Test
    void resolveClientNames_scopesToClientsWithAccountAtRestaurant() {
        UUID r1 = UUID.randomUUID();
        UUID c1 = UUID.randomUUID(); // a un compte fidélité → résolu
        UUID c2 = UUID.randomUUID(); // PAS de compte → exclu (anti-énumération)
        when(accountRepository.findAllByRestaurantId(r1)).thenReturn(List.of(account(c1, r1, 100)));
        when(userRepository.findAllByIds(any()))
            .thenReturn(List.of(new User(c1, null, "c1@x.ma", "h", "Sara", "B")));

        List<ClientNameDto> res = service.resolveClientNames(List.of(r1), List.of(c1, c2));

        assertThat(res).hasSize(1);
        assertThat(res.get(0).id()).isEqualTo(c1);
        assertThat(res.get(0).firstName()).isEqualTo("Sara");
    }

    @Test
    void resolveClientNames_emptyInputs_returnsEmpty() {
        assertThat(service.resolveClientNames(List.of(), List.of(UUID.randomUUID()))).isEmpty();
        assertThat(service.resolveClientNames(List.of(UUID.randomUUID()), List.of())).isEmpty();
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
        // findByClient utilise désormais la projection enrichie (JOIN restaurants) → nom resto.
        LoyaltyAccountWithRestaurantView v = org.mockito.Mockito.mock(LoyaltyAccountWithRestaurantView.class);
        when(v.getRestaurantName()).thenReturn("Chez Test");
        when(accountRepository.findAllByClientIdWithRestaurant(client)).thenReturn(List.of(v));
        when(accountRepository.findAllByRestaurantId(resto)).thenReturn(List.of(account(client, resto, 5)));
        var accounts = service.findByClient(client);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).restaurantName()).isEqualTo("Chez Test");
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
            UUID.randomUUID(), resto, new BigDecimal("100"), "TICKET-1", null, null)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void snap2earn_belowMinAmount_creditsZero() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        GainRule rule = gainRule(resto, "0.10");
        rule.setMinAmount(new BigDecimal("1000"));
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.of(rule));
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(account(client, resto, 7)));

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("50"), null, null, null));

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

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("1000"), null, null, null));

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

        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("100"), null, null, null));

        assertThat(r.pointsEarned()).isEqualTo(10); // floor(100*0.10)
        assertThat(r.gainRuleApplied()).isEqualTo("default");
    }

    @Test
    void snap2earn_withRedeemPoints_debitsAfterEarn() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        LoyaltyAccount acc = account(client, resto, 100);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // amount 100, fallback 0.10 → earn 10 ; redeem 30 ⇒ balance 100 + 10 - 30 = 80
        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("100"), null, null, 30));

        assertThat(r.pointsEarned()).isEqualTo(10);
        assertThat(r.pointsRedeemed()).isEqualTo(30);
        assertThat(r.accountBalance()).isEqualTo(80);
        verify(eventPublisher).publishEvent(any(LoyaltyRedeemedEvent.class));
    }

    @Test
    void snap2earn_redeemExceedsBalance_throwsBadRequest() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        LoyaltyAccount acc = account(client, resto, 5);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // amount 100 → earn 10 → solde 15 ; redeem 50 > 15 ⇒ BadRequest (refus conversion)
        assertThatThrownBy(() -> service.snap2earn(
            new Snap2EarnDto(client, resto, new BigDecimal("100"), null, null, 50)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void snap2earn_zeroRedeem_noDebit() {
        UUID client = UUID.randomUUID(), resto = UUID.randomUUID();
        when(gainRuleRepository.findByRestaurantIdAndDeletedAtIsNull(resto)).thenReturn(Optional.empty());
        LoyaltyAccount acc = account(client, resto, 0);
        when(accountRepository.findAllByClientId(client)).thenReturn(List.of(acc));
        when(transactionRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(accountRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // redeem=0 ⇒ pas de conversion, pointsRedeemed null, solde = 10 (earn only)
        Snap2EarnResultDto r = service.snap2earn(new Snap2EarnDto(client, resto, new BigDecimal("100"), null, null, 0));

        assertThat(r.pointsEarned()).isEqualTo(10);
        assertThat(r.pointsRedeemed()).isNull();
        assertThat(r.accountBalance()).isEqualTo(10);
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
    void updateTier_appliesPresentFields() {
        UUID id = UUID.randomUUID();
        Tier tier = new Tier(id, UUID.randomUUID(), "Ruby", 0, new BigDecimal("0.00"));
        when(tierRepository.findById(id)).thenReturn(Optional.of(tier));
        when(tierRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var dto = service.updateTier(id, new TierUpdateDto("Diamond", 1500, new BigDecimal("7.50"), 2));
        assertThat(dto.name()).isEqualTo("Diamond");
        assertThat(dto.minPoints()).isEqualTo(1500);
        assertThat(dto.bonusPercent()).isEqualByComparingTo("7.50");
        assertThat(dto.sortOrder()).isEqualTo(2);
    }

    @Test
    void updateTier_notFound_throws() {
        when(tierRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateTier(UUID.randomUUID(), new TierUpdateDto(null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findTransactionsByRestaurant_delegates() {
        UUID resto = UUID.randomUUID();
        when(transactionRepository.findAllByRestaurantIdEnriched(eq(resto), any(Pageable.class))).thenReturn(List.of());
        assertThat(service.findTransactionsByRestaurant(resto, 50)).isEmpty();
    }

    @Test
    void searchClients_mapsUsersToClientNameDto() {
        User u = org.mockito.Mockito.mock(User.class);
        UUID id = UUID.randomUUID();
        when(u.getId()).thenReturn(id);
        when(u.getFirstName()).thenReturn("Karim");
        when(u.getLastName()).thenReturn("B");
        when(u.getPhone()).thenReturn("+212600000001");
        when(userRepository.searchClients(anyString(), any(Pageable.class))).thenReturn(List.of(u));

        List<ClientNameDto> dtos = service.searchClients("kar", 8);
        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).id()).isEqualTo(id);
        assertThat(dtos.get(0).firstName()).isEqualTo("Karim");
        assertThat(dtos.get(0).phone()).isEqualTo("+212600000001");
    }

    // ─── resolveClientByCode (Code OneClick — referral_code, QR / Carte Wallet) ──

    @Test
    void resolveClientByCode_clientFound_returnsDto() {
        User u = mock(User.class);
        Role role = mock(Role.class);
        UUID id = UUID.randomUUID();
        when(u.getDeletedAt()).thenReturn(null);
        when(u.getRole()).thenReturn(role);
        when(role.getCode()).thenReturn("CLIENT");
        when(u.getId()).thenReturn(id);
        when(u.getFirstName()).thenReturn("Imane");
        when(u.getLastName()).thenReturn("Z");
        when(u.getPhone()).thenReturn("+212611111111");
        when(userRepository.findByReferralCode("ABC123XY")).thenReturn(Optional.of(u));

        ClientNameDto dto = service.resolveClientByCode("ABC123XY");
        assertThat(dto.id()).isEqualTo(id);
        assertThat(dto.firstName()).isEqualTo("Imane");
        assertThat(dto.phone()).isEqualTo("+212611111111");
    }

    @Test
    void resolveClientByCode_notClientRole_throwsNotFound() {
        User u = mock(User.class);
        Role role = mock(Role.class);
        when(u.getDeletedAt()).thenReturn(null);
        when(u.getRole()).thenReturn(role);
        when(role.getCode()).thenReturn("RESTAURATEUR"); // code d'un owner → ne doit pas résoudre
        when(userRepository.findByReferralCode("OWNRCODE")).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> service.resolveClientByCode("OWNRCODE"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resolveClientByCode_unknown_throwsNotFound() {
        when(userRepository.findByReferralCode("NOPECODE")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resolveClientByCode("NOPECODE"))
            .isInstanceOf(NotFoundException.class);
    }

    // ─── P1 (anti-N+1 shim) — batch by-restaurants ──────────────────────────

    @Test
    void findAccountsByRestaurants_nullOrEmpty_returnsEmpty_noRepoCall() {
        assertThat(service.findAccountsByRestaurants(null)).isEmpty();
        assertThat(service.findAccountsByRestaurants(List.of())).isEmpty();
        verify(accountRepository, org.mockito.Mockito.never()).findAllByRestaurantIdIn(any());
    }

    @Test
    void findAccountsByRestaurants_mapsAllToDto() {
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        when(accountRepository.findAllByRestaurantIdIn(List.of(r1, r2)))
            .thenReturn(List.of(account(UUID.randomUUID(), r1, 100), account(UUID.randomUUID(), r2, 50)));
        assertThat(service.findAccountsByRestaurants(List.of(r1, r2))).hasSize(2);
    }

    @Test
    void findTransactionsByRestaurants_nullOrEmpty_returnsEmpty_noRepoCall() {
        assertThat(service.findTransactionsByRestaurants(null, 2000)).isEmpty();
        assertThat(service.findTransactionsByRestaurants(List.of(), 2000)).isEmpty();
        verify(transactionRepository, org.mockito.Mockito.never()).findAllByRestaurantIdInEnriched(any(), any());
    }

    @Test
    void findTransactionsByRestaurants_delegatesToEnrichedRepo() {
        UUID r1 = UUID.randomUUID();
        when(transactionRepository.findAllByRestaurantIdInEnriched(eq(List.of(r1)), any())).thenReturn(List.of());
        service.findTransactionsByRestaurants(List.of(r1), 2000);
        verify(transactionRepository).findAllByRestaurantIdInEnriched(eq(List.of(r1)), any());
    }
}
