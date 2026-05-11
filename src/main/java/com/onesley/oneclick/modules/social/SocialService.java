package com.onesley.oneclick.modules.social;

import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static com.onesley.oneclick.modules.social.SocialDtos.*;

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
         .map(FriendshipDto::from)
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
        return FriendshipDto.from(friendshipRepo.save(f));
    }

    @Transactional
    public FriendshipDto accept(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        f.markAccepted();
        return FriendshipDto.from(friendshipRepo.save(f));
    }

    @Transactional
    public FriendshipDto decline(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        f.setStatus("declined");
        return FriendshipDto.from(friendshipRepo.save(f));
    }

    // ─── Referrals ───────────────────────────────────────────────────────────

    public List<ReferralDto> findByReferrer(UUID referrerId) {
        return referralRepo.findAllByReferrerId(referrerId).stream().map(ReferralDto::from).toList();
    }

    @Transactional
    public ReferralDto create(ReferralCreateDto dto) {
        User referrer = entityManager.getReference(User.class, dto.referrerId());
        Referral r = new Referral(UUID.randomUUID(), referrer, dto.referralCode());
        return ReferralDto.from(referralRepo.save(r));
    }

    @Transactional
    public ReferralDto activate(UUID referralId, UUID referredUserId) {
        Referral r = referralRepo.findById(referralId)
            .orElseThrow(() -> new NotFoundException("Referral", referralId));
        r.setReferredUser(entityManager.getReference(User.class, referredUserId));
        r.markActivated();
        return ReferralDto.from(referralRepo.save(r));
    }
}
