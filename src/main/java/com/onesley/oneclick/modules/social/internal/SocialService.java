package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
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

@Service
@Transactional(readOnly = true)
public class SocialService {

    private final FriendshipRepository friendshipRepo;
    private final ReferralRepository referralRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public SocialService(FriendshipRepository friendshipRepo, ReferralRepository referralRepo) {
        this.friendshipRepo = friendshipRepo;
        this.referralRepo = referralRepo;
    }

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
}
