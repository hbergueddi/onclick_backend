package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.FriendshipRequestedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final UserRepository userRepository; // domaine identity (API publique) — enrichissement profils amis
    private final ApplicationEventPublisher eventPublisher; // notif server-side (FriendshipRequestedEvent)

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Friendships ─────────────────────────────────────────────────────────

    /**
     * Tous les amis (status=accepted) d'un user — recherche dans user1_id OU user2_id
     * vu la convention canonique (user1 < user2).
     */
    public List<FriendshipDto> findFriendsOf(UUID userId) {
        List<FriendshipDto> base = Stream.concat(
            friendshipRepo.findAllByUser1Id(userId).stream(),
            friendshipRepo.findAllByUser2Id(userId).stream()
        ).filter(f -> "accepted".equals(f.getStatus()))
         .map(Friendship::toDto)
         .toList();

        // Enrichissement serveur-side : profil de l'AMI (l'autre user) via l'API
        // publique du domaine identity (évite que le front appelle /api/users/by-ids
        // qui est admin-only). Batch anti-N+1.
        Set<UUID> friendIds = base.stream()
            .map(d -> userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id())
            .collect(Collectors.toSet());
        Map<UUID, User> users = friendIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(friendIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return base.stream().map(d -> {
            UUID friendId = userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id();
            User u = users.get(friendId);
            return new FriendshipDto(d.id(), d.user1Id(), d.user2Id(), d.status(), d.acceptedAt(), d.createdAt(),
                friendId,
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName() : null,
                u != null ? u.getAvatarUrl() : null);
        }).toList();
    }

    /**
     * Découverte sociale : résout le profil public MINIMAL d'un user par téléphone
     * (recherche pour invitation / ajout d'ami). 404 si aucun user actif. Ne renvoie
     * PAS le UserDto complet (admin VIEW:USERS) — cf {@link SocialDtos.PublicProfileDto}.
     */
    public PublicProfileDto findUserByPhone(String phone) {
        User u = userRepository.findByPhone(phone)
            .filter(x -> !x.isDeleted())
            .orElseThrow(() -> new NotFoundException("User", phone));
        return new PublicProfileDto(u.getId(), u.getFirstName(), u.getLastName(), u.getAvatarUrl(), u.getPhone());
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
        // Le demandeur doit être l'une des 2 parties (ou admin) — empêche de forger une
        // amitié entre deux tiers. Le gate CREATE:COMMUNITY (détenu par le CLIENT depuis V38)
        // ne porte pas sur l'identité des parties → contrôle ABAC ici.
        UUID current = SecurityHelper.currentUserId();
        if (current == null) throw new ForbiddenException("Authentification requise");
        if (!SecurityHelper.isAdmin()
            && !current.equals(dto.user1Id()) && !current.equals(dto.user2Id())) {
            throw new ForbiddenException("Accès interdit : vous devez être l'une des parties de l'amitié");
        }
        UUID a = dto.user1Id();
        UUID b = dto.user2Id();
        if (a.toString().compareTo(b.toString()) > 0) { UUID tmp = a; a = b; b = tmp; }
        User u1 = entityManager.getReference(User.class, a);
        User u2 = entityManager.getReference(User.class, b);
        Friendship f = new Friendship(UUID.randomUUID(), u1, u2);
        Friendship saved = friendshipRepo.save(f);
        // Notif server-side au destinataire (l'autre partie) — le CLIENT n'a pas CREATE:NOTIFICATIONS.
        UUID requester = current.equals(dto.user2Id()) ? dto.user2Id() : dto.user1Id();
        UUID addressee = requester.equals(dto.user1Id()) ? dto.user2Id() : dto.user1Id();
        eventPublisher.publishEvent(new FriendshipRequestedEvent(saved.getId(), requester, addressee));
        return saved.toDto();
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
        // Seul le parrain lui-même (ou admin) crée son code de parrainage.
        SecurityHelper.requireOwnerOrAdmin(dto.referrerId());
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
        List<FriendGroupMemberDto> base = groupMemberRepo.findAllByFriendGroupId(groupId).stream()
            .map(FriendGroupMember::toDto)
            .toList();

        // Enrichissement serveur-side : profil de chaque membre via l'API publique du
        // domaine identity (évite que le front appelle /api/users/by-ids admin-only). Batch anti-N+1.
        Set<UUID> friendIds = base.stream().map(FriendGroupMemberDto::friendId).collect(Collectors.toSet());
        Map<UUID, User> users = friendIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(friendIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return base.stream().map(d -> {
            User u = users.get(d.friendId());
            return new FriendGroupMemberDto(d.id(), d.friendGroupId(), d.friendId(), d.role(), d.joinedAt(),
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName() : null,
                u != null ? u.getAvatarUrl() : null);
        }).toList();
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
