package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.onesley.oneclick.modules.social.api.SocialDtos.*;
import com.onesley.oneclick.modules.social.api.SocialDtos;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendshipCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendshipDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.ReferralCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.ReferralDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.UserFavoriteDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.UserFavoriteCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupCreateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupUpdateDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupMemberDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.FriendGroupMemberAddDto;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SocialService {

    private final FriendshipRepository friendshipRepo;
    private final ReferralRepository referralRepo;
    private final UserFavoriteRepository favoriteRepo;
    private final FriendGroupRepository groupRepo;
    private final FriendGroupMemberRepository groupMemberRepo;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Friendships ─────────────────────────────────────────────────────────

    /**
     * Tous les amis (status=accepted) d'un user — recherche dans user1_id OU user2_id
     * vu la convention canonique (user1 < user2).
     */
    public List<FriendshipDto> findFriendsOf(UUID userId) {
        return Stream.concat(
            friendshipRepo.findAllByUser1Id(userId).stream(),
            friendshipRepo.findAllByUser2Id(userId).stream()
        ).filter(f -> "accepted".equals(f.getStatus()))
         .map(Friendship::toDto)
         .toList();
    }

    /**
     * Crée une demande d'amitié. La contrainte DB {@code friendships_check} exige
     * {@code user1_id < user2_id} pour empêcher les doublons bidirectionnels.
     *
     * <p><b>NB</b> : Postgres compare les UUID de façon <b>non signée</b> (byte-wise),
     * alors que {@link UUID#compareTo} est <b>signée</b> sur les long internes. On
     * passe donc par {@link UUID#toString} (hex lexicographique) qui matche l'ordre
     * binaire Postgres pour les UUID.
     */
    @Transactional
    public FriendshipDto request(FriendshipCreateDto dto) {
        UUID a = dto.user1Id();
        UUID b = dto.user2Id();
        if (a.toString().compareTo(b.toString()) > 0) { UUID tmp = a; a = b; b = tmp; }
        User u1 = entityManager.getReference(User.class, a);
        User u2 = entityManager.getReference(User.class, b);
        Friendship f = new Friendship(UUID.randomUUID(), u1, u2);
        return friendshipRepo.save(f).toDto();
    }

    @Transactional
    public FriendshipDto accept(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        // Friendship n'a pas de requester/receiver distinct → autorise les 2 parties
        // (ou admin). Faute de getReceiverUserId, on accepte user1 OU user2.
        requireFriendshipPartyOrAdmin(f);
        f.markAccepted();
        return friendshipRepo.save(f).toDto();
    }

    @Transactional
    public FriendshipDto decline(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        requireFriendshipPartyOrAdmin(f);
        f.setStatus("declined");
        return friendshipRepo.save(f).toDto();
    }

    /**
     * Vérifie que le user courant fait partie de l'amitié (user1 ou user2) OU est admin.
     * Faute de champ requester/receiver distinct, on accepte les 2 parties.
     */
    private void requireFriendshipPartyOrAdmin(Friendship f) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        if (current.equals(f.getUser1Id()) || current.equals(f.getUser2Id())) return;
        if (SecurityHelper.isAdmin()) return;
        throw new ForbiddenException("Accès interdit : vous n'êtes pas partie de cette amitié");
    }

    // ─── Referrals ───────────────────────────────────────────────────────────

    public List<ReferralDto> findByReferrer(UUID referrerId) {
        return referralRepo.findAllByReferrerId(referrerId).stream().map(Referral::toDto).toList();
    }

    /** Liste paginée platform-wide — admin (TableauxPulse). */
    public org.springframework.data.domain.Page<ReferralDto> findAllReferrals(int page, int size) {
        return referralRepo.findAllOrdered(org.springframework.data.domain.PageRequest.of(page, size))
            .map(Referral::toDto);
    }

    @Transactional
    public ReferralDto create(ReferralCreateDto dto) {
        User referrer = entityManager.getReference(User.class, dto.referrerId());
        Referral r = new Referral(UUID.randomUUID(), referrer, dto.referralCode());
        return referralRepo.save(r).toDto();
    }

    @Transactional
    public ReferralDto activate(UUID referralId, UUID referredUserId) {
        Referral r = referralRepo.findById(referralId)
            .orElseThrow(() -> new NotFoundException("Referral", referralId));
        r.setReferredUser(entityManager.getReference(User.class, referredUserId));
        r.markActivated();
        return referralRepo.save(r).toDto();
    }

    // ─── Favoris (user_favorites) ────────────────────────────────────────────

    /** Liste des restaurants favoris d'un user (ordre d'ajout). */
    public List<UserFavoriteDto> findFavoritesOf(UUID userId) {
        return favoriteRepo.findAllByUserId(userId).stream()
            .map(UserFavorite::toDto)
            .toList();
    }

    /**
     * Ajoute un favori — 409 si déjà existant (UNIQUE constraint applicative
     * pour message clair).
     */
    @Transactional
    public UserFavoriteDto addFavorite(UserFavoriteCreateDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.userId());
        if (favoriteRepo.existsByUserIdAndRestaurantId(dto.userId(), dto.restaurantId())) {
            throw new ConflictException("Favori déjà existant pour ce restaurant");
        }
        User user = entityManager.getReference(User.class, dto.userId());
        UserFavorite f = new UserFavorite(UUID.randomUUID(), user, dto.restaurantId());
        return favoriteRepo.save(f).toDto();
    }

    /** Retire un favori — 404 si l'id n'existe pas, 403 si ce n'est pas le owner. */
    @Transactional
    public void removeFavorite(UUID favoriteId) {
        UserFavorite f = favoriteRepo.findById(favoriteId)
            .orElseThrow(() -> new NotFoundException("UserFavorite", favoriteId));
        SecurityHelper.requireOwnerOrAdmin(f.getUserId());
        favoriteRepo.delete(f);
    }

    // ─── Friend groups ───────────────────────────────────────────────────────

    /** Groupes possédés par un user (soft-deleted exclus). */
    public List<FriendGroupDto> findGroupsByOwner(UUID ownerId) {
        return groupRepo.findAllByOwnerIdAndDeletedAtIsNull(ownerId).stream()
            .map(this::toGroupDto)
            .toList();
    }

    /** Groupes dont un user est membre (via {@code friend_group_members}). */
    public List<FriendGroupDto> findGroupsByMember(UUID userId) {
        return groupRepo.findAllByMemberUserId(userId).stream()
            .map(this::toGroupDto)
            .toList();
    }

    /** Détail d'un groupe — 404 si introuvable ou soft-deleted. */
    public FriendGroupDto findGroupById(UUID groupId) {
        FriendGroup g = requireActiveGroup(groupId);
        requireGroupReadAccess(g);
        return toGroupDto(g);
    }

    /** Crée un groupe — owner = current user (récupéré via SecurityHelper). */
    @Transactional
    public FriendGroupDto createGroup(FriendGroupCreateDto dto) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) {
            throw new ForbiddenException("Authentification requise");
        }
        User owner = entityManager.getReference(User.class, current);
        FriendGroup g = new FriendGroup(UUID.randomUUID(), owner, dto.name());
        g.setDescription(dto.description());
        g.setAvatarUrl(dto.avatarUrl());
        FriendGroup saved = groupRepo.save(g);

        // Owner = membre automatique avec rôle "owner".
        FriendGroupMember m = new FriendGroupMember(UUID.randomUUID(), saved, owner, "owner");
        m.setInvitedBy(current);
        groupMemberRepo.save(m);

        return toGroupDto(saved);
    }

    /** Met à jour nom/description/avatar — 403 si pas owner/admin. */
    @Transactional
    public FriendGroupDto updateGroup(UUID groupId, FriendGroupUpdateDto dto) {
        FriendGroup g = requireActiveGroup(groupId);
        SecurityHelper.requireOwnerOrAdmin(g.getOwnerId());
        if (dto.name() != null && !dto.name().isBlank()) g.setName(dto.name());
        if (dto.description() != null) g.setDescription(dto.description());
        if (dto.avatarUrl() != null) g.setAvatarUrl(dto.avatarUrl());
        return toGroupDto(groupRepo.save(g));
    }

    /** Soft delete — 403 si pas owner/admin. */
    @Transactional
    public void deleteGroup(UUID groupId) {
        FriendGroup g = requireActiveGroup(groupId);
        SecurityHelper.requireOwnerOrAdmin(g.getOwnerId());
        g.markDeleted();
        groupRepo.save(g);
    }

    /** Liste les membres d'un groupe — 404 si introuvable. */
    public List<FriendGroupMemberDto> findGroupMembers(UUID groupId) {
        FriendGroup g = requireActiveGroup(groupId);
        requireGroupReadAccess(g);
        return groupMemberRepo.findAllByFriendGroupId(groupId).stream()
            .map(FriendGroupMember::toDto)
            .toList();
    }

    /**
     * Ajoute un membre — 403 si pas owner/admin du groupe.
     * Le owner du groupe + les admins du groupe peuvent ajouter.
     */
    @Transactional
    public FriendGroupMemberDto addGroupMember(UUID groupId, FriendGroupMemberAddDto dto) {
        FriendGroup g = requireActiveGroup(groupId);
        requireGroupAdminOrOwner(g);
        if (groupMemberRepo.existsByFriendGroupIdAndFriendId(groupId, dto.friendId())) {
            throw new ConflictException("Cet utilisateur est déjà membre du groupe");
        }
        User friend = entityManager.getReference(User.class, dto.friendId());
        String role = dto.role() == null ? "member" : dto.role();
        FriendGroupMember m = new FriendGroupMember(UUID.randomUUID(), g, friend, role);
        m.setInvitedBy(SecurityHelper.currentUserId());
        return groupMemberRepo.save(m).toDto();
    }

    /**
     * Retire un membre du groupe.
     * - Le owner/admin du groupe peut retirer n'importe qui (sauf le owner lui-même).
     * - Un membre peut se retirer lui-même.
     * - Le owner du groupe ne peut PAS être retiré (il doit soft-delete le groupe à la place).
     */
    @Transactional
    public void removeGroupMember(UUID groupId, UUID friendId) {
        FriendGroup g = requireActiveGroup(groupId);
        UUID current = SecurityHelper.currentUserId();
        if (current == null) throw new ForbiddenException("Authentification requise");

        if (friendId.equals(g.getOwnerId())) {
            throw new ConflictException("Le owner du groupe ne peut être retiré — supprimez le groupe");
        }
        boolean isSelf = friendId.equals(current);
        boolean isAdminOfGroup = isGroupAdminOrOwner(g);
        if (!isSelf && !isAdminOfGroup) {
            throw new ForbiddenException("Accès interdit : seul un admin du groupe ou le membre lui-même peut retirer");
        }
        int deleted = groupMemberRepo.deleteByFriendGroupIdAndFriendId(groupId, friendId);
        if (deleted == 0) {
            throw new NotFoundException("FriendGroupMember", groupId + "/" + friendId);
        }
    }

    // ─── Helpers friend_groups ───────────────────────────────────────────────

    private FriendGroup requireActiveGroup(UUID groupId) {
        FriendGroup g = groupRepo.findById(groupId)
            .orElseThrow(() -> new NotFoundException("FriendGroup", groupId));
        if (g.isDeleted()) {
            throw new NotFoundException("FriendGroup", groupId);
        }
        return g;
    }

    /** Lecture autorisée : owner du groupe, membre du groupe, ou admin global. */
    private void requireGroupReadAccess(FriendGroup g) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) throw new ForbiddenException("Authentification requise");
        if (current.equals(g.getOwnerId())) return;
        if (SecurityHelper.isAdmin()) return;
        if (groupMemberRepo.existsByFriendGroupIdAndFriendId(g.getId(), current)) return;
        throw new ForbiddenException("Accès interdit : vous n'êtes pas membre de ce groupe");
    }

    /** Mutation autorisée : owner du groupe, admin du groupe, ou admin global. */
    private void requireGroupAdminOrOwner(FriendGroup g) {
        if (isGroupAdminOrOwner(g)) return;
        throw new ForbiddenException("Accès interdit : seul un admin du groupe peut effectuer cette opération");
    }

    private boolean isGroupAdminOrOwner(FriendGroup g) {
        UUID current = SecurityHelper.currentUserId();
        if (current == null) return false;
        if (current.equals(g.getOwnerId())) return true;
        if (SecurityHelper.isAdmin()) return true;
        // Membre avec rôle owner/admin dans la junction.
        return groupMemberRepo.findAllByFriendGroupId(g.getId()).stream()
            .anyMatch(m -> m.getFriendId().equals(current)
                && ("owner".equals(m.getRole()) || "admin".equals(m.getRole())));
    }

    private FriendGroupDto toGroupDto(FriendGroup g) {
        long count = groupMemberRepo.countByFriendGroupId(g.getId());
        return g.toDto(count);
    }
}
