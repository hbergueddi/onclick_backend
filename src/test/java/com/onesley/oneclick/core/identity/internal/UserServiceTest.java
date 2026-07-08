package com.onesley.oneclick.core.identity.internal;

import com.onesley.oneclick.core.identity.api.Action;
import com.onesley.oneclick.core.identity.api.Menu;
import com.onesley.oneclick.core.identity.api.Permission;
import com.onesley.oneclick.core.identity.api.Role;
import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserCreateDto;
import com.onesley.oneclick.core.identity.api.UserRegisterDto;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.core.identity.api.UserUpdateDto;
import com.onesley.oneclick.core.tenant.api.Tenant;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.OneClickUserDetailsService;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.AccountDeletedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link UserService} (L3 — core.identity).
 * Signup (conflits email/phone/role), lookups, patch, soft-delete + eviction,
 * changePassword (validations), rolesDistribution (native), findMe / findMeContext.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserRepository repository;
    @Mock RoleRepository roleRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock OneClickUserDetailsService userDetailsService;
    @Mock EntityManager em;
    @Mock Query query;
    @InjectMocks UserService service;

    private final Role role = new Role(UUID.randomUUID(), "CLIENT", "Client");

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(Tenant.class), any())).thenReturn(new Tenant(UUID.randomUUID(), "T", "t"));
        lenient().when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(passwordEncoder.encode(anyString())).thenReturn("hashed");
    }

    private User user() { return new User(UUID.randomUUID(), role, "u@x.ma", "h", "U", "U"); }

    // ─── lookups ────────────────────────────────────────────────────────────

    @Test
    void findById_notFoundAndFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        assertThat(service.findById(u.getId())).isNotNull();
    }

    @Test
    void findByEmail_phone_referral_notFoundAndFound() {
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByEmail("x@y.ma")).isInstanceOf(NotFoundException.class);
        when(repository.findByEmailIgnoreCase(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByEmail("x@y.ma")).isNotNull();

        when(repository.findByPhone(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByPhone("0600")).isInstanceOf(NotFoundException.class);
        when(repository.findByPhone(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByPhone("0600")).isNotNull();

        when(repository.findByReferralCode(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findByReferralCode("OC-1")).isInstanceOf(NotFoundException.class);
        when(repository.findByReferralCode(any())).thenReturn(Optional.of(user()));
        assertThat(service.findByReferralCode("OC-1")).isNotNull();
    }

    @Test
    void findAll_findByRole_delegate() {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(0, 20).getContent()).isEmpty();
        when(repository.findByRoleCode(any(), any(), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findByRole("CLIENT", UUID.randomUUID(), 0, 20).getContent()).isEmpty();
    }

    @Test
    void findAllByIds_emptyAndNonEmpty() {
        assertThat(service.findAllByIds(null)).isEmpty();
        assertThat(service.findAllByIds(List.of())).isEmpty();
        when(repository.findAllByIds(any())).thenReturn(List.of(user()));
        assertThat(service.findAllByIds(List.of(UUID.randomUUID()))).hasSize(1);
    }

    // ─── create ────────────────────────────────────────────────────────────

    @Test
    void create_emailConflict() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, role.getId(), "dup@x.ma", null, "password1", "F", "L", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_emailConflict_genericMessage_doesNotEchoEmail() {
        // Anti-énumération : le message d'erreur ne doit pas renvoyer l'email (info-leak).
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, role.getId(), "secret-leak@x.ma", null, "password1", "F", "L", null)))
            .isInstanceOf(ConflictException.class)
            .hasMessageNotContaining("secret-leak@x.ma");
    }

    @Test
    void create_phoneConflict() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repository.existsByPhone(any())).thenReturn(true);
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, role.getId(), "x@x.ma", "0600", "password1", "F", "L", null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void create_update_existingUser_byId_updatesAndDoesNotRepublishEvent() {
        // Accès suivant : dto.id() renseigné → mise à jour de l'utilisateur existant.
        User existing = user();
        UUID id = existing.getId();
        when(repository.findById(id)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));

        var result = service.create(new UserCreateDto(
            id, null, role.getId(), "New@x.ma", null, null, "New", "Name", "en"));

        assertThat(result).isNotNull();
        assertThat(existing.getEmail()).isEqualTo("new@x.ma");   // normalisé lowercase + écrasé
        assertThat(existing.getFirstName()).isEqualTo("New");
        // Pas de mot de passe fourni → hash inchangé.
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(anyString());
        // UserRegisteredEvent réservé à la création — jamais republié sur mise à jour.
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any());
        // Rôle/email/hash peuvent changer → éviction du cache userDetails.
        verify(userDetailsService).evictUser(id);
    }

    @Test
    void create_roleNotFound() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(new UserCreateDto(null, UUID.randomUUID(), "x@x.ma", null, "password1", "F", "L", null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void create_fullAndMinimal() {
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repository.existsByPhone(any())).thenReturn(false);
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));
        assertThat(service.create(new UserCreateDto(UUID.randomUUID(), role.getId(), "Full@x.ma", "0600", "password1", "F", "L", "fr"))).isNotNull();
        assertThat(service.create(new UserCreateDto(null, role.getId(), "Min@x.ma", null, "password1", "F", "L", null))).isNotNull();
        verify(eventPublisher, org.mockito.Mockito.atLeastOnce()).publishEvent(any(Object.class));
    }

    // ─── register : vérification email (P1 enrollment) ─────────────────────

    @Test
    void register_emailVerificationRequired_setsPendingStatus_reflectedInDto() {
        ReflectionTestUtils.setField(service, "emailVerificationRequired", true);
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        User created = new User(UUID.randomUUID(), role, "p@x.ma", "$2a$h", "P", "P"); // status défaut "active"
        when(repository.findById(any())).thenReturn(Optional.of(created));

        var dto = service.register(new UserRegisterDto(
            null, "p@x.ma", null, "password1234", "P", "P", "fr", false));

        assertThat(created.getStatus()).isEqualTo(User.STATUS_PENDING_EMAIL_VERIFICATION);
        assertThat(dto.status()).isEqualTo(User.STATUS_PENDING_EMAIL_VERIFICATION); // signal renvoyé au client
        verify(repository).save(created);
    }

    @Test
    void register_default_noFlag_staysActive_noRefetch() {
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);

        var dto = service.register(new UserRegisterDto(
            null, "q@x.ma", null, "password1234", "Q", "Q", "fr", false));

        assertThat(dto.status()).isEqualTo(User.STATUS_ACTIVE);            // défaut rétro-compat
        verify(repository, org.mockito.Mockito.never()).findById(any());   // ni CGU ni flag → pas de re-fetch
    }

    // ─── register : reprise pending ancrée EMAIL + garde-fous sécurité ──────

    @Test
    void register_reusesPendingByEmail_updatesProfile_keepsCredentials_noEvent() {
        // Re-soumission par le MÊME email (ancre) : compte pending réutilisé (pas de 409), on met à jour
        // les champs de profil (prénom, téléphone corrigé) MAIS on ne touche NI l'email NI le mot de passe,
        // et id + referralCode + statut pending sont conservés (QR déjà affiché toujours valable).
        UUID pendingId = UUID.randomUUID();
        User pending = new User(pendingId, role, "reuse@x.ma", "$2a$ORIGINAL", "Old", "Name");
        pending.setPhone("0600");
        pending.setStatus(User.STATUS_PENDING_EMAIL_VERIFICATION);
        pending.setReferralCode("REF12345");
        when(repository.findByEmailIgnoreCase("reuse@x.ma")).thenReturn(Optional.of(pending));
        when(repository.existsByPhone("0700")).thenReturn(false); // nouveau téléphone libre

        var dto = service.register(new UserRegisterDto(
            null, "reuse@x.ma", "0700", "newpassword1234", "Fixed", "Newl", "en", true));

        assertThat(dto.id()).isEqualTo(pendingId);                                    // même compte
        assertThat(pending.getReferralCode()).isEqualTo("REF12345");                  // QR préservé
        assertThat(pending.getStatus()).isEqualTo(User.STATUS_PENDING_EMAIL_VERIFICATION);
        assertThat(pending.getFirstName()).isEqualTo("Fixed");                        // profil corrigé
        assertThat(pending.getPhone()).isEqualTo("0700");                             // téléphone corrigé
        assertThat(pending.getEmail()).isEqualTo("reuse@x.ma");                       // email (ancre) NON réécrit
        assertThat(pending.getPasswordHash()).isEqualTo("$2a$ORIGINAL");             // credential NON écrasé (anti-takeover)
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(anyString());     // pas de ré-encodage sur reprise
        verify(repository).save(pending);
        verify(roleRepository, org.mockito.Mockito.never()).findByCode(anyString());  // pas passé par create()
        verify(eventPublisher, org.mockito.Mockito.never()).publishEvent(any(Object.class)); // pas de re-UserRegisteredEvent
    }

    @Test
    void register_phoneOnlyMatch_freeEmail_notReused_delegatesToCreate_409() {
        // SÉCURITÉ (non-régression takeover) : un attaquant anonyme envoyant {email LIBRE + téléphone d'une
        // victime} ne doit PAS reprendre le compte de la victime. L'ancre est l'email (ici libre) → pas de
        // reprise → create() lève 409 sur le téléphone ; RIEN n'est muté (ni save, ni ré-encodage).
        when(repository.findByEmailIgnoreCase("attacker@evil.com")).thenReturn(Optional.empty()); // email libre
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(repository.existsByPhone("0600")).thenReturn(true);   // téléphone de la victime déjà pris
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.register(new UserRegisterDto(
            null, "attacker@evil.com", "0600", "attackerpass1234", "M", "M", "fr", false)))
            .isInstanceOf(ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());             // compte de la victime intact
        verify(passwordEncoder, org.mockito.Mockito.never()).encode(anyString()); // aucun credential réécrit
    }

    @Test
    void register_reuseByEmail_correctedPhoneOfAnotherAccount_throwsConflict() {
        // Reprise par email OK, mais le téléphone corrigé appartient à un AUTRE compte → 409 (pas de vol
        // de téléphone), message générique, avant tout save.
        User pending = new User(UUID.randomUUID(), role, "reuse@x.ma", "$2a$h", "P", "P");
        pending.setPhone("0600");
        pending.setStatus(User.STATUS_PENDING_EMAIL_VERIFICATION);
        when(repository.findByEmailIgnoreCase("reuse@x.ma")).thenReturn(Optional.of(pending));
        when(repository.existsByPhone("0611")).thenReturn(true);   // téléphone appartenant à un autre compte

        assertThatThrownBy(() -> service.register(new UserRegisterDto(
            null, "reuse@x.ma", "0611", "password1234", "P", "P", "fr", false)))
            .isInstanceOf(ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void register_activeEmail_notReused_throwsConflict() {
        // Email d'un compte ACTIF (déjà confirmé) → jamais repris → create() lève 409 (pas de prise de contrôle).
        User active = new User(UUID.randomUUID(), role, "taken@x.ma", "$2a$h", "A", "A"); // statut "active"
        when(repository.findByEmailIgnoreCase("taken@x.ma")).thenReturn(Optional.of(active));
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(true);
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.register(new UserRegisterDto(
            null, "taken@x.ma", null, "password1234", "N", "N", "fr", false)))
            .isInstanceOf(ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void register_crossTenantPending_notReused_throwsConflict() {
        // Compte pending pour cet email sur un AUTRE tenant → pas de reprise cross-tenant → create() → 409.
        User pending = new User(UUID.randomUUID(), role, "reuse@x.ma", "$2a$h", "P", "P");
        pending.setStatus(User.STATUS_PENDING_EMAIL_VERIFICATION);
        pending.setTenant(new Tenant(UUID.randomUUID(), "T1", "t1"));    // tenant T1
        when(repository.findByEmailIgnoreCase("reuse@x.ma")).thenReturn(Optional.of(pending));
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(true);
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));

        assertThatThrownBy(() -> service.register(new UserRegisterDto(
            UUID.randomUUID(), "reuse@x.ma", null, "password1234", "P", "P", "fr", false))) // demande sur tenant T2
            .isInstanceOf(ConflictException.class);
        verify(repository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void register_softDeletedEmail_ignored_createsNormally() {
        // Un compte SUPPRIMÉ (soft-delete) au même email ne doit ni bloquer ni être repris :
        // reusablePendingSignup l'écarte (deletedAt != null) → create() crée un nouveau compte.
        User deleted = new User(UUID.randomUUID(), role, "gone@x.ma", "$2a$h", "G", "G");
        deleted.setStatus(User.STATUS_PENDING_EMAIL_VERIFICATION);
        deleted.markDeleted();
        when(repository.findByEmailIgnoreCase("gone@x.ma")).thenReturn(Optional.of(deleted));
        when(repository.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(roleRepository.findByCode("CLIENT")).thenReturn(Optional.of(role));
        when(roleRepository.findById(any())).thenReturn(Optional.of(role));

        var dto = service.register(new UserRegisterDto(
            null, "gone@x.ma", null, "password1234", "G2", "G2", "fr", false));

        assertThat(dto.status()).isEqualTo(User.STATUS_ACTIVE);                 // nouveau compte, pas la reprise du supprimé
        verify(repository, org.mockito.Mockito.never()).save(deleted);          // l'ancien compte supprimé n'est pas touché
    }

    // ─── patch ────────────────────────────────────────────────────────────

    @Test
    void patch_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.patch(UUID.randomUUID(), new UserUpdateDto("F", null, null, null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_allFields_success() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(false);
        service.patch(u.getId(), new UserUpdateDto("Nouveau", "Nom", "0700", "http://avatar", "Casablanca", "en",
            List.of("gluten", "lactose")));
        assertThat(u.getFirstName()).isEqualTo("Nouveau");
        assertThat(u.getPhone()).isEqualTo("0700");
        assertThat(u.getCity()).isEqualTo("Casablanca"); // ITEM 1 — city persisté via patch self-service
        // V65 — allergènes persistés via patch self-service : List<String> → String[].
        assertThat(u.getAllergens()).containsExactly("gluten", "lactose");
    }

    /**
     * V65 — patch allergens : la liste fournie écrase le tableau de l'entité.
     * Cas isolé (sans toucher aux autres champs) pour prouver l'indépendance du champ.
     */
    @Test
    void patch_allergens_setsArray() {
        User u = user();
        u.setAllergens(new String[]{"eggs"}); // valeur initiale
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.patch(u.getId(), new UserUpdateDto(null, null, null, null, null, null,
            List.of("peanuts", "soy", "fish")));
        assertThat(u.getAllergens()).containsExactly("peanuts", "soy", "fish");
    }

    /**
     * V65 — patch SANS allergens (null) : le tableau existant reste inchangé
     * (même garde {@code if (dto.allergens() != null)} que pour city/language).
     */
    @Test
    void patch_allergensNull_leavesArrayUnchanged() {
        User u = user();
        u.setAllergens(new String[]{"celery", "mustard"});
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(false);
        service.patch(u.getId(), new UserUpdateDto("OnlyName", null, null, null, null, null, null));
        assertThat(u.getFirstName()).isEqualTo("OnlyName");
        assertThat(u.getAllergens()).containsExactly("celery", "mustard"); // inchangé
    }

    // ─── H (V79) — updatePccMemberType ───────────────────────────────────────

    @Test
    void updatePccMemberType_resident_sets() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        var dto = service.updatePccMemberType(u.getId(), "resident");
        assertThat(u.getPccMemberType()).isEqualTo("resident");
        assertThat(dto.pccMemberType()).isEqualTo("resident");
    }

    @Test
    void updatePccMemberType_null_clears() {
        User u = user();
        u.setPccMemberType("non_resident");
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.updatePccMemberType(u.getId(), null);
        assertThat(u.getPccMemberType()).isNull();
    }

    @Test
    void updatePccMemberType_invalid_throwsBadRequest_noLookup() {
        assertThatThrownBy(() -> service.updatePccMemberType(UUID.randomUUID(), "vip"))
            .isInstanceOf(BadRequestException.class);
        verify(repository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void updatePccMemberType_notFound_throws() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updatePccMemberType(UUID.randomUUID(), "resident"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void patch_phoneConflict() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(repository.existsByPhone(any())).thenReturn(true);
        assertThatThrownBy(() -> service.patch(u.getId(), new UserUpdateDto(null, null, "0700", null, null, null, null)))
            .isInstanceOf(ConflictException.class);
    }

    // ─── softDelete ──────────────────────────────────────────────────────────

    @Test
    void softDelete_notFoundAndSuccess() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDelete(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        service.softDelete(u.getId());
        assertThat(u.getDeletedAt()).isNotNull();
        verify(userDetailsService).evictUser(u.getId());
    }

    // ─── deleteOwnAccount (self-service RGPD / App Store §5.1.1(v)) ───────────────
    @Test
    void deleteOwnAccount_notFound_throws() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteOwnAccount(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOwnAccount_anonymizes_softDeletes_evicts_publishesEvent() {
        User u = user();
        UUID id = u.getId();
        when(repository.findById(id)).thenReturn(Optional.of(u));

        service.deleteOwnAccount(id);

        // Soft-delete + anonymisation PII (email/téléphone libérés)
        assertThat(u.getDeletedAt()).isNotNull();
        assertThat(u.getEmail()).startsWith("deleted-").contains(id.toString());
        assertThat(u.getPhone()).isNull();
        assertThat(u.getFirstName()).isEqualTo("Compte");
        assertThat(u.getLastName()).isEqualTo("supprimé");
        assertThat(u.getStatus()).isEqualTo("deleted");
        // Cache évincé : un JWT déjà émis ne doit plus réauthentifier
        verify(userDetailsService).evictUser(id);
        // Event cross-module publié (frontière Modulith) avec le bon userId
        ArgumentCaptor<AccountDeletedEvent> cap = ArgumentCaptor.forClass(AccountDeletedEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(id);
    }

    // ─── changePassword ──────────────────────────────────────────────────────

    @Test
    void changePassword_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.changePassword(UUID.randomUUID(), "old", "new"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void changePassword_currentInvalid() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("wrong"), any())).thenReturn(false);
        assertThatThrownBy(() -> service.changePassword(u.getId(), "wrong", "newpass"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePassword_sameAsOld() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("current"), any())).thenReturn(true);
        when(passwordEncoder.matches(eq("current2"), any())).thenReturn(true);
        assertThatThrownBy(() -> service.changePassword(u.getId(), "current", "current2"))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void changePassword_success_evicts() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("current"), any())).thenReturn(true);
        when(passwordEncoder.matches(eq("brandnew"), any())).thenReturn(false);
        service.changePassword(u.getId(), "current", "brandnew");
        verify(userDetailsService).evictUser(u.getId());
    }

    /** BE-3 — un compte provisionné avec mdp temporaire (flag true) le perd dès qu'il change. */
    @Test
    void changePassword_success_clearsPasswordMustChange() {
        User u = user();
        u.setPasswordMustChange(true); // simulé : compte restaurateur provisionné (BE-2)
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("temp"), any())).thenReturn(true);
        when(passwordEncoder.matches(eq("brandnew"), any())).thenReturn(false);
        service.changePassword(u.getId(), "temp", "brandnew");
        assertThat(u.isPasswordMustChange()).isFalse();
    }

    // ─── resetPassword (Phase A — mot de passe oublié, sans mot de passe actuel) ──

    @Test
    void resetPassword_notFound() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.resetPassword(UUID.randomUUID(), "BrandNewPass1"))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resetPassword_sameAsOld_throws() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("SamePass1234"), any())).thenReturn(true); // réutilisation interdite
        assertThatThrownBy(() -> service.resetPassword(u.getId(), "SamePass1234"))
            .isInstanceOf(BadRequestException.class);
        verify(userDetailsService, org.mockito.Mockito.never()).evictUser(any());
    }

    @Test
    void resetPassword_success_setsHash_evicts() {
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("BrandNewPass1"), any())).thenReturn(false); // différent de l'ancien
        when(passwordEncoder.encode("BrandNewPass1")).thenReturn("$2a$encoded");

        service.resetPassword(u.getId(), "BrandNewPass1");

        assertThat(u.getPasswordHash()).isEqualTo("$2a$encoded");
        verify(repository).save(u);
        verify(userDetailsService).evictUser(u.getId());
    }

    /** BE-3 — cohérent avec changePassword : un reset effectif lève aussi le « changement forcé ». */
    @Test
    void resetPassword_success_clearsPasswordMustChange() {
        User u = user();
        u.setPasswordMustChange(true);
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(eq("BrandNewPass1"), any())).thenReturn(false);
        when(passwordEncoder.encode("BrandNewPass1")).thenReturn("$2a$encoded");
        service.resetPassword(u.getId(), "BrandNewPass1");
        assertThat(u.isPasswordMustChange()).isFalse();
    }

    // ─── rolesDistribution (native) ────────────────────────────────────────────

    @Test
    void rolesDistribution_mapsRows() {
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of(new Object[]{"CLIENT", 100L}, new Object[]{"STAFF", 12L}));
        assertThat(service.rolesDistribution()).hasSize(2);
    }

    // ─── findMe / findMeContext ────────────────────────────────────────────────

    @Test
    void findMe_notAuthenticated_andAuthenticated() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findMe()).isInstanceOf(BadRequestException.class);
        }
        User u = user();
        when(repository.findById(u.getId())).thenReturn(Optional.of(u));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(u.getId());
            assertThat(service.findMe()).isNotNull();
        }
    }

    @Test
    void findMeContext_notAuthenticated_userNotFound_success() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findMeContext()).isInstanceOf(BadRequestException.class);
        }
        UUID id = UUID.randomUUID();
        when(repository.findByIdWithRoleAndPermissions(id)).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(id);
            assertThatThrownBy(() -> service.findMeContext()).isInstanceOf(NotFoundException.class);
        }
        // succès : user avec rôle + permissions (menu/action) → menus + permissions construits
        Menu menu = new Menu(UUID.randomUUID(), "dashboard", "Dashboard");
        Action action = new Action(UUID.randomUUID(), "VIEW", "Voir", "core");
        role.getPermissions().add(new Permission(UUID.randomUUID(), role, menu, action));
        User u = new User(UUID.randomUUID(), role, "me@x.ma", "h", "F", "L");
        when(repository.findByIdWithRoleAndPermissions(u.getId())).thenReturn(Optional.of(u));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(u.getId());
            var ctx = service.findMeContext();
            assertThat(ctx.menus()).hasSize(1);
            assertThat(ctx.permissions()).contains("VIEW:dashboard");
        }
    }

    /** BE-3 — le drapeau « changement forcé » est exposé dans MeContextDto pour le gating client. */
    @Test
    void findMeContext_exposesPasswordMustChange() {
        User u = new User(UUID.randomUUID(), role, "tmp@x.ma", "h", "F", "L");
        u.setPasswordMustChange(true);
        when(repository.findByIdWithRoleAndPermissions(u.getId())).thenReturn(Optional.of(u));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(u.getId());
            assertThat(service.findMeContext().user().passwordMustChange()).isTrue();
        }
    }
}
