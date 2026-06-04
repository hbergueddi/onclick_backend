package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupMemberAddDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupUpdateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendshipCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.ReferralCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.UserFavoriteCreateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link SocialService} (L3 — modules.social).
 * Friendships (request/accept/decline + RBAC partie), referrals, favoris (conflit/
 * owner), friend groups (CRUD + membres + RBAC owner/admin/membre). SecurityHelper
 * statique via mockStatic ; champs *_id (insertable=false) posés par réflexion.
 */
@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class SocialServiceTest {

    @Mock FriendshipRepository friendshipRepo;
    @Mock ReferralRepository referralRepo;
    @Mock UserFavoriteRepository favoriteRepo;
    @Mock FriendGroupRepository groupRepo;
    @Mock FriendGroupMemberRepository groupMemberRepo;
    @Mock ContactImportRepository contactImportRepo; // V64 — quota import contacts
    @Mock EntityManager entityManager;
    @Mock com.onesley.oneclick.core.identity.api.UserRepository userRepository; // enrichissement profils amis (findFriendsOf)
    @Mock org.springframework.context.ApplicationEventPublisher eventPublisher; // FriendshipRequestedEvent
    @InjectMocks SocialService service;

    private final UUID me = UUID.randomUUID();
    /** Horloge fixe — fenêtre glissante du quota import déterministe dans les tests. */
    private final java.time.Clock fixedClock =
        java.time.Clock.fixed(java.time.Instant.parse("2026-06-04T12:00:00Z"), java.time.ZoneOffset.UTC);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        // @Value + @Bean(Clock) ne sont pas injectés par @InjectMocks → posés par réflexion.
        ReflectionTestUtils.setField(service, "clock", fixedClock);
        ReflectionTestUtils.setField(service, "contactImportDailyLimit", 10);
        ReflectionTestUtils.setField(service, "friendsCap", 50);
        lenient().when(entityManager.getReference(eq(User.class), any()))
            .thenReturn(new User(UUID.randomUUID(), null, "x@x.ma", "h", "X", "Y"));
        lenient().when(friendshipRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(referralRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(favoriteRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(groupRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(groupMemberRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(groupMemberRepo.countByFriendGroupId(any())).thenReturn(1L);
        lenient().when(contactImportRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // Par défaut le demandeur est sous le plafond (les tests cap le surchargent).
        lenient().when(friendshipRepo.countAcceptedFriendshipsOf(any())).thenReturn(0L);
    }

    private Friendship friendship(String status, UUID u1, UUID u2) {
        Friendship f = new Friendship(UUID.randomUUID(),
            new User(UUID.randomUUID(), null, "a@x.ma", "h", "A", "A"),
            new User(UUID.randomUUID(), null, "b@x.ma", "h", "B", "B"));
        f.setStatus(status);
        if (u1 != null) ReflectionTestUtils.setField(f, "user1Id", u1);
        if (u2 != null) ReflectionTestUtils.setField(f, "user2Id", u2);
        return f;
    }

    private FriendGroup group(UUID ownerId) {
        FriendGroup g = new FriendGroup(UUID.randomUUID(),
            new User(UUID.randomUUID(), null, "o@x.ma", "h", "O", "W"), "Squad");
        if (ownerId != null) ReflectionTestUtils.setField(g, "ownerId", ownerId);
        return g;
    }

    // ─── friendships ─────────────────────────────────────────────────────────

    @Test
    void findFriendsOf_filtersAccepted() {
        when(friendshipRepo.findAllByUser1Id(me)).thenReturn(List.of(friendship("accepted", null, null), friendship("pending", null, null)));
        when(friendshipRepo.findAllByUser2Id(me)).thenReturn(List.of(friendship("accepted", null, null)));
        assertThat(service.findFriendsOf(me)).hasSize(2);
    }

    @Test
    void request_savesFriendship_bothOrderings() {
        UUID lo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hi = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(lo); // partie de l'amitié
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThat(service.request(new FriendshipCreateDto(hi, lo))).isNotNull(); // swap
            assertThat(service.request(new FriendshipCreateDto(lo, hi))).isNotNull(); // déjà ordonné
        }
        verify(friendshipRepo, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void request_setsRequestedBy_toCurrentUser() {
        UUID lo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hi = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(lo);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            service.request(new FriendshipCreateDto(lo, hi));
        }
        org.mockito.ArgumentCaptor<Friendship> cap = org.mockito.ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepo).save(cap.capture());
        assertThat(cap.getValue().getRequestedBy()).isEqualTo(lo); // direction V39 = l'auteur
    }

    @Test
    void findPendingReceivedBy_returnsOnlyReceivedPending() {
        UUID requester = UUID.randomUUID();
        Friendship received = friendship("pending", me, requester);
        received.setRequestedBy(requester);                 // l'autre a demandé → REÇUE
        Friendship sentByMe = friendship("pending", me, UUID.randomUUID());
        sentByMe.setRequestedBy(me);                        // moi auteur → envoyée, exclue
        Friendship accepted = friendship("accepted", me, UUID.randomUUID()); // pas pending, exclue
        when(friendshipRepo.findAllByUser1Id(me)).thenReturn(List.of(received, sentByMe, accepted));
        when(friendshipRepo.findAllByUser2Id(me)).thenReturn(List.of());
        when(userRepository.findAllByIds(any())).thenReturn(List.of());
        var result = service.findPendingReceivedBy(me);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(received.getId());
    }

    @Test
    void findSentBy_returnsOnlySentNonAccepted() {
        Friendship sentPending = friendship("pending", me, UUID.randomUUID());
        sentPending.setRequestedBy(me);                        // moi auteur, pending → ENVOYÉE
        Friendship sentDeclined = friendship("declined", me, UUID.randomUUID());
        sentDeclined.setRequestedBy(me);                       // moi auteur, declined → ENVOYÉE (refusée)
        Friendship sentAccepted = friendship("accepted", me, UUID.randomUUID());
        sentAccepted.setRequestedBy(me);                       // acceptée → exclue (c'est un ami)
        Friendship received = friendship("pending", me, UUID.randomUUID());
        received.setRequestedBy(received.getUser2Id());        // l'autre auteur → reçue, exclue
        when(friendshipRepo.findAllByUser1Id(me)).thenReturn(List.of(sentPending, sentDeclined, sentAccepted, received));
        when(friendshipRepo.findAllByUser2Id(me)).thenReturn(List.of());
        when(userRepository.findAllByIds(any())).thenReturn(List.of());
        var result = service.findSentBy(me);
        assertThat(result).extracting(d -> d.id())
            .containsExactlyInAnyOrder(sentPending.getId(), sentDeclined.getId());
    }

    @Test
    void request_notAPartyNorAdmin_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID()); // tiers
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.request(
                new FriendshipCreateDto(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void accept_notFound_throwsNotFound() {
        when(friendshipRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.accept(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
    }

    @Test
    void accept_asParty_marksAccepted() {
        Friendship f = friendship("pending", me, UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            service.accept(f.getId());
        }
        assertThat(f.getStatus()).isEqualTo("accepted");
    }

    @Test
    void decline_asAdmin_setsDeclined() {
        Friendship f = friendship("pending", UUID.randomUUID(), UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            service.decline(f.getId());
        }
        assertThat(f.getStatus()).isEqualTo("declined");
    }

    @Test
    void accept_notPartyNorAdmin_throwsForbidden() {
        Friendship f = friendship("pending", UUID.randomUUID(), UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(UUID.randomUUID());
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.accept(f.getId())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void accept_notAuthenticated_throwsForbidden() {
        Friendship f = friendship("pending", UUID.randomUUID(), UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.accept(f.getId())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void deleteFriendship_notFound_throwsNotFound() {
        when(friendshipRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.deleteFriendship(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteFriendship_asParty_deletes() {
        Friendship f = friendship("accepted", me, UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            service.deleteFriendship(f.getId());
        }
        verify(friendshipRepo).delete(f);
    }

    @Test
    void deleteFriendship_notPartyNorAdmin_throwsForbidden() {
        Friendship f = friendship("accepted", UUID.randomUUID(), UUID.randomUUID());
        when(friendshipRepo.findById(any())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me); // tiers
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.deleteFriendship(f.getId())).isInstanceOf(ForbiddenException.class);
        }
    }

    // ─── referrals ─────────────────────────────────────────────────────────────

    @Test
    void findByReferrer_maps() {
        when(referralRepo.findAllByReferrerId(me)).thenReturn(List.of(
            new Referral(UUID.randomUUID(), new User(me, null, "r@x.ma", "h", "R", "R"), "OC-ABC")));
        assertThat(service.findByReferrer(me)).hasSize(1);
    }

    @Test
    void createReferral_saves() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // requireOwnerOrAdmin(referrerId) → no-op sous mockStatic (le parrain crée son code)
            assertThat(service.create(new ReferralCreateDto(me, "OC-XYZ"))).isNotNull();
        }
    }

    @Test
    void activateReferral_notFoundAndSuccess() {
        when(referralRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.activate(UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(NotFoundException.class);

        Referral r = new Referral(UUID.randomUUID(), new User(me, null, "r@x.ma", "h", "R", "R"), "OC-XYZ");
        when(referralRepo.findById(r.getId())).thenReturn(Optional.of(r));
        service.activate(r.getId(), UUID.randomUUID());
        assertThat(r.getStatus()).isEqualTo("activated");
    }

    // ─── favoris ───────────────────────────────────────────────────────────────

    @Test
    void addFavorite_conflict_whenExists() {
        when(favoriteRepo.existsByUserIdAndRestaurantId(any(), any())).thenReturn(true);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.addFavorite(new UserFavoriteCreateDto(me, UUID.randomUUID())))
                .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void addFavorite_success() {
        when(favoriteRepo.existsByUserIdAndRestaurantId(any(), any())).thenReturn(false);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.addFavorite(new UserFavoriteCreateDto(me, UUID.randomUUID()))).isNotNull();
        }
    }

    @Test
    void removeFavorite_notFoundAndSuccess() {
        when(favoriteRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.removeFavorite(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        UserFavorite f = new UserFavorite(UUID.randomUUID(), new User(me, null, "u@x.ma", "h", "U", "U"), UUID.randomUUID());
        when(favoriteRepo.findById(f.getId())).thenReturn(Optional.of(f));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.removeFavorite(f.getId());
        }
        verify(favoriteRepo).delete(f);
    }

    // ─── friend groups ───────────────────────────────────────────────────────────

    @Test
    void createGroup_notAuthenticated_throwsForbidden() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.createGroup(new FriendGroupCreateDto("Squad", null, null)))
                .isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void createGroup_success_addsOwnerMember() {
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            assertThat(service.createGroup(new FriendGroupCreateDto("Squad", "desc", "http://a"))).isNotNull();
        }
        verify(groupMemberRepo).save(any(FriendGroupMember.class)); // owner ajouté comme membre
    }

    @Test
    void updateGroup_deletedGroup_throwsNotFound() {
        FriendGroup g = group(me);
        ReflectionTestUtils.setField(g, "deletedAt", java.time.Instant.now());
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        assertThatThrownBy(() -> service.updateGroup(g.getId(), new FriendGroupUpdateDto("New", null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateGroup_success() {
        FriendGroup g = group(me);
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.updateGroup(g.getId(), new FriendGroupUpdateDto("Nouveau", "desc2", "http://b"));
        }
        assertThat(g.getName()).isEqualTo("Nouveau");
    }

    @Test
    void deleteGroup_marksDeleted() {
        FriendGroup g = group(me);
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.deleteGroup(g.getId());
        }
        assertThat(g.isDeleted()).isTrue();
    }

    @Test
    void findGroupById_asAdmin_returnsDto() {
        FriendGroup g = group(UUID.randomUUID());
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            assertThat(service.findGroupById(g.getId())).isNotNull();
        }
    }

    @Test
    void addGroupMember_conflict_whenAlreadyMember() {
        FriendGroup g = group(me);
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.existsByFriendGroupIdAndFriendId(any(), any())).thenReturn(true);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            assertThatThrownBy(() -> service.addGroupMember(g.getId(), new FriendGroupMemberAddDto(UUID.randomUUID(), "member")))
                .isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void addGroupMember_success_defaultRole() {
        FriendGroup g = group(me);
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.existsByFriendGroupIdAndFriendId(any(), any())).thenReturn(false);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            assertThat(service.addGroupMember(g.getId(), new FriendGroupMemberAddDto(UUID.randomUUID(), null))).isNotNull();
        }
    }

    @Test
    void removeGroupMember_ownerCannotBeRemoved_throwsConflict() {
        UUID owner = UUID.randomUUID();
        FriendGroup g = group(owner);
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            assertThatThrownBy(() -> service.removeGroupMember(g.getId(), owner)).isInstanceOf(ConflictException.class);
        }
    }

    @Test
    void removeGroupMember_self_success() {
        FriendGroup g = group(UUID.randomUUID());
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.deleteByFriendGroupIdAndFriendId(any(), eq(me))).thenReturn(1);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            service.removeGroupMember(g.getId(), me);
        }
        verify(groupMemberRepo).deleteByFriendGroupIdAndFriendId(any(), eq(me));
    }

    @Test
    void findGroupsByOwner_byMember_andMembers_map() {
        FriendGroup g = group(me);
        when(groupRepo.findAllByOwnerIdAndDeletedAtIsNull(me)).thenReturn(List.of(g));
        when(groupRepo.findAllByMemberUserId(me)).thenReturn(List.of(g));
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.findAllByFriendGroupId(any())).thenReturn(List.of());
        assertThat(service.findGroupsByOwner(me)).hasSize(1);
        assertThat(service.findGroupsByMember(me)).hasSize(1);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(true);
            assertThat(service.findGroupMembers(g.getId())).isEmpty();
        }
    }

    // ─── RBAC friend-groups : lecture (requireGroupReadAccess) + admin junction (isGroupAdminOrOwner) ───

    @Test
    void findGroupMembers_asOwner_ok() {
        FriendGroup g = group(me); // current == owner
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.findAllByFriendGroupId(any())).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThat(service.findGroupMembers(g.getId())).isEmpty();
        }
    }

    @Test
    void findGroupMembers_asMember_ok() {
        FriendGroup g = group(UUID.randomUUID()); // owner ≠ current
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.existsByFriendGroupIdAndFriendId(any(), eq(me))).thenReturn(true);
        when(groupMemberRepo.findAllByFriendGroupId(any())).thenReturn(List.of());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThat(service.findGroupMembers(g.getId())).isEmpty();
        }
    }

    @Test
    void findGroupMembers_outsider_throwsForbidden() {
        FriendGroup g = group(UUID.randomUUID());
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.existsByFriendGroupIdAndFriendId(any(), any())).thenReturn(false);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.findGroupMembers(g.getId())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void findGroupMembers_notAuthenticated_throwsForbidden() {
        FriendGroup g = group(UUID.randomUUID());
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(null);
            assertThatThrownBy(() -> service.findGroupMembers(g.getId())).isInstanceOf(ForbiddenException.class);
        }
    }

    @Test
    void removeGroupMember_asJunctionAdmin_removesOther() {
        FriendGroup g = group(UUID.randomUUID()); // owner ≠ current
        UUID target = UUID.randomUUID();
        FriendGroupMember adminMember = new FriendGroupMember(
            UUID.randomUUID(), g, new User(me, null, "m@x.ma", "h", "M", "M"), "admin");
        ReflectionTestUtils.setField(adminMember, "friendId", me); // friendId = mirror insertable=false
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.findAllByFriendGroupId(any())).thenReturn(List.of(adminMember));
        when(groupMemberRepo.deleteByFriendGroupIdAndFriendId(any(), eq(target))).thenReturn(1);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me); // pas owner, pas admin global → via junction
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            service.removeGroupMember(g.getId(), target);
        }
        verify(groupMemberRepo).deleteByFriendGroupIdAndFriendId(any(), eq(target));
    }

    @Test
    void removeGroupMember_outsider_throwsForbidden() {
        FriendGroup g = group(UUID.randomUUID());
        UUID target = UUID.randomUUID();
        when(groupRepo.findById(any())).thenReturn(Optional.of(g));
        when(groupMemberRepo.findAllByFriendGroupId(any())).thenReturn(List.of()); // current absent de la junction
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(me); // ni owner, ni self (target ≠ me), ni admin junction
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.removeGroupMember(g.getId(), target)).isInstanceOf(ForbiddenException.class);
        }
    }

    // ─── ITEM 2 — Plafond d'amis (50) dans request() ───────────────────────────

    @Test
    void request_underCap_succeeds() {
        UUID lo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hi = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        when(friendshipRepo.countAcceptedFriendshipsOf(lo)).thenReturn(49L); // 49 < 50 → OK
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(lo);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThat(service.request(new FriendshipCreateDto(lo, hi))).isNotNull();
        }
        verify(friendshipRepo).save(any());
    }

    @Test
    void request_atCap_throwsUnprocessable_andDoesNotSave() {
        UUID lo = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hi = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        when(friendshipRepo.countAcceptedFriendshipsOf(lo)).thenReturn(50L); // 50 == cap → rejet
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(SecurityHelper::currentUserId).thenReturn(lo);
            sec.when(SecurityHelper::isAdmin).thenReturn(false);
            assertThatThrownBy(() -> service.request(new FriendshipCreateDto(lo, hi)))
                .isInstanceOf(com.onesley.oneclick.exception.UnprocessableException.class)
                .hasMessageContaining("Plafond d'amis atteint");
        }
        verify(friendshipRepo, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void acceptedFriendCount_returnsRepoCount() {
        when(friendshipRepo.countAcceptedFriendshipsOf(me)).thenReturn(7L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            // requireOwnerOrAdmin(me) → no-op sous mockStatic
            assertThat(service.acceptedFriendCount(me)).isEqualTo(7L);
        }
    }

    // ─── ITEM 2 — Quota d'import de contacts (10/jour/user) ────────────────────

    @Test
    void importContacts_underQuota_returnsMatches_andRecordsImport() {
        // 9 imports déjà consommés → le 10e passe (9 < 10).
        when(contactImportRepo.countByUserIdAndCreatedAtAfter(eq(me), any())).thenReturn(9L);
        User match = new User(UUID.randomUUID(), null, "friend@x.ma", "h", "Fri", "End");
        ReflectionTestUtils.setField(match, "phone", "+212600000001");
        when(userRepository.findByPhone("+212600000001")).thenReturn(Optional.of(match));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            var result = service.importContacts(new SocialDtos.ContactImportRequestDto(
                me, List.of("+212600000001"), List.of()));
            assertThat(result.matches()).hasSize(1);
            assertThat(result.matches().get(0).id()).isEqualTo(match.getId());
            assertThat(result.submittedCount()).isEqualTo(1);
            assertThat(result.dailyCount()).isEqualTo(10L);  // 9 + cet import
            assertThat(result.dailyLimit()).isEqualTo(10);
        }
        verify(contactImportRepo).save(any(ContactImport.class)); // import journalisé
    }

    @Test
    void importContacts_atQuota_throwsTooManyRequests_andDoesNotRecord() {
        // 10 imports déjà consommés → le 11e est rejeté (10 >= 10), avant tout matching.
        when(contactImportRepo.countByUserIdAndCreatedAtAfter(eq(me), any())).thenReturn(10L);
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.importContacts(
                new SocialDtos.ContactImportRequestDto(me, List.of("+212600000002"), List.of())))
                .isInstanceOf(com.onesley.oneclick.exception.TooManyRequestsException.class)
                .hasMessageContaining("Quota d'import de contacts atteint");
        }
        verify(contactImportRepo, org.mockito.Mockito.never()).save(any());
        org.mockito.Mockito.verifyNoInteractions(userRepository); // pas de matching quand quota dépassé
    }

    @Test
    void importContacts_quotaResets_whenWindowEmpty() {
        // 0 import dans la fenêtre 24 h (reset) → l'import passe et compte 1.
        when(contactImportRepo.countByUserIdAndCreatedAtAfter(eq(me), any())).thenReturn(0L);
        when(userRepository.findByEmailIgnoreCase("known@x.ma"))
            .thenReturn(Optional.of(new User(UUID.randomUUID(), null, "known@x.ma", "h", "K", "N")));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            var result = service.importContacts(new SocialDtos.ContactImportRequestDto(
                me, List.of(), List.of("known@x.ma")));
            assertThat(result.dailyCount()).isEqualTo(1L);
            assertThat(result.matches()).hasSize(1);
        }
        verify(contactImportRepo).save(any(ContactImport.class));
    }

    @Test
    void importContacts_excludesSelfMatch_andDeduplicates() {
        when(contactImportRepo.countByUserIdAndCreatedAtAfter(eq(me), any())).thenReturn(0L);
        // Le téléphone résout vers MOI → exclu (on ne se propose pas comme contact).
        User self = new User(me, null, "me@x.ma", "h", "Me", "Self");
        when(userRepository.findByPhone("+212600000003")).thenReturn(Optional.of(self));
        // Email résout vers le même ami que… un autre email (dédup par id).
        User friend = new User(UUID.randomUUID(), null, "dup@x.ma", "h", "Dup", "Lic");
        when(userRepository.findByEmailIgnoreCase("dup@x.ma")).thenReturn(Optional.of(friend));
        when(userRepository.findByEmailIgnoreCase("dup2@x.ma")).thenReturn(Optional.of(friend));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            var result = service.importContacts(new SocialDtos.ContactImportRequestDto(
                me, List.of("+212600000003"), List.of("dup@x.ma", "dup2@x.ma")));
            assertThat(result.matches()).hasSize(1);            // self exclu + doublon fusionné
            assertThat(result.matches().get(0).id()).isEqualTo(friend.getId());
            assertThat(result.submittedCount()).isEqualTo(3);   // 1 phone + 2 emails soumis
        }
    }

    @Test
    void importContacts_selfScope_enforcedBeforeQuota() {
        // ABAC : requireOwnerOrAdmin(userId) doit lever un Forbidden pour un userId arbitraire.
        UUID other = UUID.randomUUID();
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            sec.when(() -> SecurityHelper.requireOwnerOrAdmin(other))
                .thenThrow(new ForbiddenException("Accès interdit"));
            assertThatThrownBy(() -> service.importContacts(
                new SocialDtos.ContactImportRequestDto(other, List.of("+212600000004"), List.of())))
                .isInstanceOf(ForbiddenException.class);
        }
        verify(contactImportRepo, org.mockito.Mockito.never()).save(any());
    }
}
