package com.onesley.oneclick.modules.social.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.exception.TooManyRequestsException;
import com.onesley.oneclick.exception.UnprocessableException;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.FriendshipRequestedEvent;
import com.onesley.oneclick.shared.events.FriendshipRespondedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.onesley.oneclick.modules.social.api.SocialDtos.ContactImportRequestDto;
import com.onesley.oneclick.modules.social.api.SocialDtos.ContactImportResultDto;
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
    private final ContactImportRepository contactImportRepo; // V64 — quota import contacts
    private final UserRepository userRepository; // domaine identity (API publique) — enrichissement profils amis
    private final ApplicationEventPublisher eventPublisher; // notif server-side (FriendshipRequestedEvent)
    private final Clock clock; // horloge injectable (UTC) — fenêtre quota testable (cf NoShowDisputeService)

    /** Quota d'imports de contacts par fenêtre glissante de 24 h, par user (défaut 10). Configurable. */
    @Value("${app.social.contact-import.daily-limit:10}")
    private int contactImportDailyLimit;

    /** Plafond d'amitiés acceptées par user (défaut 50). Configurable. */
    @Value("${app.social.friends.cap:50}")
    private int friendsCap;

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
     * Demandes d'amitié REÇUES par un user (Pocket → « Demandes reçues ») : status
     * {@code pending} où l'user est partie mais N'EST PAS l'auteur ({@code requested_by}).
     * Enrichit avec le profil du DEMANDEUR (l'autre partie) — même pattern anti-N+1 que
     * {@link #findFriendsOf}. Le {@code friendId} du DTO porte le demandeur (à afficher).
     *
     * <p>Rows historiques sans {@code requested_by} (NULL) : direction inconnue → on les
     * considère « reçues » (par défaut sûr ; en pratique aucune demande pending pré-V39).
     */
    public List<FriendshipDto> findPendingReceivedBy(UUID userId) {
        List<FriendshipDto> base = Stream.concat(
            friendshipRepo.findAllByUser1Id(userId).stream(),
            friendshipRepo.findAllByUser2Id(userId).stream()
        ).filter(f -> "pending".equals(f.getStatus()))
         .filter(f -> !userId.equals(f.getRequestedBy())) // reçue = je ne suis pas l'auteur
         .map(Friendship::toDto)
         .toList();

        Set<UUID> requesterIds = base.stream()
            .map(d -> userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id())
            .collect(Collectors.toSet());
        Map<UUID, User> users = requesterIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(requesterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return base.stream().map(d -> {
            UUID requesterId = userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id();
            User u = users.get(requesterId);
            return new FriendshipDto(d.id(), d.user1Id(), d.user2Id(), d.status(), d.acceptedAt(), d.createdAt(),
                requesterId,
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName() : null,
                u != null ? u.getAvatarUrl() : null);
        }).toList();
    }

    /**
     * Demandes d'amitié ENVOYÉES par un user (Pocket → « Invitations envoyées · Amitié ») :
     * friendships dont l'user est l'auteur ({@code requested_by}) et encore non acceptées
     * (status pending ou declined). Enrichit avec le profil du DESTINATAIRE (l'autre partie)
     * — même pattern anti-N+1 que {@link #findFriendsOf}. Le {@code friendId} du DTO porte
     * le destinataire (à afficher). Symétrique de {@link #findPendingReceivedBy}.
     */
    public List<FriendshipDto> findSentBy(UUID userId) {
        List<FriendshipDto> base = Stream.concat(
            friendshipRepo.findAllByUser1Id(userId).stream(),
            friendshipRepo.findAllByUser2Id(userId).stream()
        ).filter(f -> userId.equals(f.getRequestedBy())) // envoyée = je suis l'auteur
         .filter(f -> "pending".equals(f.getStatus()) || "declined".equals(f.getStatus()))
         .map(Friendship::toDto)
         .toList();

        Set<UUID> addresseeIds = base.stream()
            .map(d -> userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id())
            .collect(Collectors.toSet());
        Map<UUID, User> users = addresseeIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(addresseeIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return base.stream().map(d -> {
            UUID addresseeId = userId.equals(d.user1Id()) ? d.user2Id() : d.user1Id();
            User u = users.get(addresseeId);
            return new FriendshipDto(d.id(), d.user1Id(), d.user2Id(), d.status(), d.acceptedAt(), d.createdAt(),
                addresseeId,
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
     * Découverte sociale par EMAIL — pendant exact de {@link #findUserByPhone(String)}
     * (toggle « Téléphone / Email » du Pocket « Ajouter un ami »). Insensible à la casse
     * ({@code findByEmailIgnoreCase}, comme le matching de l'import contacts), trimé, filtre
     * les comptes supprimés, 404 si introuvable. Ne renvoie qu'un {@link SocialDtos.PublicProfileDto}
     * (profil d'affichage minimal — anti-énumération RGPD, jamais le UserDto complet admin).
     */
    public PublicProfileDto findUserByEmail(String email) {
        User u = userRepository.findByEmailIgnoreCase(email.trim())
            .filter(x -> !x.isDeleted())
            .orElseThrow(() -> new NotFoundException("User", email));
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
        // Plafond d'amis (défaut 50) : le DEMANDEUR ne peut plus envoyer de demande au-delà
        // de friendsCap amitiés ACCEPTÉES. Le demandeur est l'appelant pour un user normal ;
        // pour un admin qui crée pour un tiers, on contrôle la partie qui INITIE (user1 si non
        // ré-ordonné). On borne l'initiateur réel : current s'il est partie, sinon user1Id.
        UUID initiator = (current.equals(dto.user1Id()) || current.equals(dto.user2Id()))
            ? current : dto.user1Id();
        long initiatorFriends = friendshipRepo.countAcceptedFriendshipsOf(initiator);
        if (initiatorFriends >= friendsCap) {
            throw new UnprocessableException(
                "Plafond d'amis atteint (" + friendsCap + ") — impossible d'ajouter un nouvel ami");
        }
        UUID a = dto.user1Id();
        UUID b = dto.user2Id();
        if (a.toString().compareTo(b.toString()) > 0) { UUID tmp = a; a = b; b = tmp; }
        User u1 = entityManager.getReference(User.class, a);
        User u2 = entityManager.getReference(User.class, b);
        Friendship f = new Friendship(UUID.randomUUID(), u1, u2);
        f.setRequestedBy(current); // V39 : direction (auteur) — distingue « reçue » vs « envoyée »
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
        FriendshipDto dto = friendshipRepo.save(f).toDto();
        publishResponded(f, true);
        return dto;
    }

    /** Notifie l'AUTRE partie (le demandeur) server-side — le répondant CLIENT n'a pas CREATE:NOTIFICATIONS. */
    private void publishResponded(Friendship f, boolean accepted) {
        UUID current = SecurityHelper.currentUserId();
        UUID recipient = (current != null && current.equals(f.getUser1Id())) ? f.getUser2Id() : f.getUser1Id();
        eventPublisher.publishEvent(new FriendshipRespondedEvent(f.getId(), recipient, accepted));
    }

    @Transactional
    public FriendshipDto decline(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        requireFriendshipPartyOrAdmin(f);
        f.setStatus("declined");
        FriendshipDto dto = friendshipRepo.save(f).toDto();
        publishResponded(f, false);
        return dto;
    }

    /**
     * Retrait DÉFINITIF d'une amitié (Pocket → « Retirer cet ami »). Contrairement à
     * {@link #decline} (qui pose le statut {@code declined} pour une demande en cours),
     * supprime physiquement la row — les deux ex-amis pourront se redemander en ami.
     * Réservé à une partie de l'amitié (user1/user2) ou admin (ABAC). 404 si absent.
     */
    @Transactional
    public void deleteFriendship(UUID friendshipId) {
        Friendship f = friendshipRepo.findById(friendshipId)
            .orElseThrow(() -> new NotFoundException("Friendship", friendshipId));
        requireFriendshipPartyOrAdmin(f);
        friendshipRepo.delete(f);
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

    /**
     * Nombre d'amis ACCEPTÉS d'un user — expose le compteur qui pilote le plafond
     * ({@link #friendsCap}). ABAC self/admin (un user ne lit pas le compteur d'un tiers).
     * Réutilisé par le front pour afficher « X/50 amis » et désactiver le bouton « Ajouter »
     * sans deviner l'état serveur.
     */
    public long acceptedFriendCount(UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        return friendshipRepo.countAcceptedFriendshipsOf(userId);
    }

    // ─── Import de contacts (carnet d'adresses → matching OneClick) — V64 ──────

    /**
     * Importe une liste de téléphones/emails (carnet d'adresses) et renvoie les
     * utilisateurs OneClick correspondants (profil public minimal).
     *
     * <p><b>Quota</b> : {@link #contactImportDailyLimit} imports / fenêtre glissante
     * de 24 h, par user. Au-delà → {@link TooManyRequestsException} (429), AVANT tout
     * matching (on ne consomme pas de ressource quand le quota est dépassé).
     *
     * <p><b>ABAC</b> : self-scope strict — un user n'importe QUE pour lui-même
     * ({@code requireOwnerOrAdmin(userId)}) ; jamais un {@code userId} arbitraire.
     * Le gate RBAC {@code CREATE:COMMUNITY} (détenu par CLIENT depuis V38) ne porte
     * pas sur l'identité → contrôle ABAC ici, comme {@link #request}.
     *
     * <p>Le matching réutilise les lookups identity existants ({@code findByPhone} /
     * {@code findByEmailIgnoreCase}, soft-deletes exclus). Self-match exclu (on ne se
     * propose pas soi-même comme contact). Doublons dédupliqués par id.
     */
    @Transactional
    public ContactImportResultDto importContacts(ContactImportRequestDto dto) {
        SecurityHelper.requireOwnerOrAdmin(dto.userId());

        // 1) Quota AVANT matching — fenêtre glissante 24 h (Clock injectable = testable).
        Instant since = Instant.now(clock).minus(Duration.ofHours(24));
        long usedToday = contactImportRepo.countByUserIdAndCreatedAtAfter(dto.userId(), since);
        if (usedToday >= contactImportDailyLimit) {
            throw new TooManyRequestsException(
                "Quota d'import de contacts atteint (" + contactImportDailyLimit
                + "/jour) — réessayez plus tard");
        }

        List<String> phones = dto.phones() == null ? List.of() : dto.phones();
        List<String> emails = dto.emails() == null ? List.of() : dto.emails();
        int submitted = phones.size() + emails.size();

        // 2) Matching — réutilise les lookups identity (anti-doublon par id, self exclu).
        Map<UUID, User> matched = new LinkedHashMap<>();
        for (String phone : phones) {
            if (phone == null || phone.isBlank()) continue;
            userRepository.findByPhone(phone.trim())
                .filter(u -> !u.isDeleted())
                .filter(u -> !u.getId().equals(dto.userId()))
                .ifPresent(u -> matched.putIfAbsent(u.getId(), u));
        }
        for (String email : emails) {
            if (email == null || email.isBlank()) continue;
            userRepository.findByEmailIgnoreCase(email.trim())
                .filter(u -> !u.isDeleted())
                .filter(u -> !u.getId().equals(dto.userId()))
                .ifPresent(u -> matched.putIfAbsent(u.getId(), u));
        }

        // 3) Enregistre l'import (consomme 1 unité de quota) — APRÈS le matching pour
        //    ne tracer que les imports effectivement traités.
        User userRef = entityManager.getReference(User.class, dto.userId());
        contactImportRepo.save(new ContactImport(UUID.randomUUID(), userRef, submitted));

        List<PublicProfileDto> matches = new ArrayList<>(matched.values().stream()
            .map(u -> new PublicProfileDto(u.getId(), u.getFirstName(), u.getLastName(),
                u.getAvatarUrl(), u.getPhone()))
            .toList());
        // usedToday + 1 = compteur APRÈS cet import (ce que le front affiche).
        return new ContactImportResultDto(matches, submitted, usedToday + 1, contactImportDailyLimit);
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

    /**
     * Active un parrainage par <b>code parrain</b> (Gap #8 — port legacy
     * {@code activate_referral_by_code}). Le filleul = caller courant. Résout le parrain
     * par son code de parrainage personnel, crée le parrainage actif, et établit l'amitié
     * filleul↔parrain (le parrainage vaut relation amicale).
     *
     * <p>Validations (1:1 legacy) : code introuvable → 400 « Code parrain invalide » ;
     * auto-parrainage → 400 ; parrainage déjà enregistré entre ces 2 users → 409.
     * <b>Pas de crédit de points</b> ici (la RPC legacy n'en créditait pas non plus).
     */
    @Transactional
    public ReferralDto activateByCode(String code) {
        UUID filleul = SecurityHelper.currentUserId();
        if (filleul == null) throw new ForbiddenException("Authentification requise");
        String trimmed = code == null ? "" : code.trim();
        if (trimmed.isEmpty()) throw new BadRequestException("Code parrain requis");

        User referrer = userRepository.findByReferralCodeIgnoreCase(trimmed)
            .orElseThrow(() -> new BadRequestException("Code parrain invalide"));
        if (referrer.getId().equals(filleul)) {
            throw new BadRequestException("Vous ne pouvez pas vous parrainer vous-même");
        }
        if (referralRepo.existsByReferrerIdAndReferredUserId(referrer.getId(), filleul)) {
            throw new ConflictException("Parrainage déjà enregistré");
        }

        Referral r = new Referral(UUID.randomUUID(), referrer, trimmed);
        r.setReferredUser(entityManager.getReference(User.class, filleul));
        r.markActivated();
        ReferralDto saved = referralRepo.save(r).toDto();

        // Auto-amitié filleul↔parrain (idempotent + flip d'une éventuelle demande pending).
        upsertAcceptedFriendship(filleul, referrer.getId());
        return saved;
    }

    /**
     * Crée (ou réactive) une amitié ACCEPTÉE entre deux users (Gap #8 — auto-amitié parrainage).
     * Convention canonique {@code user1 < user2}. Idempotent : si l'amitié existe en pending,
     * elle passe accepted ; si elle existe déjà accepted, no-op ; sinon création accepted.
     */
    private void upsertAcceptedFriendship(UUID initiator, UUID other) {
        UUID a = initiator, b = other;
        if (a.toString().compareTo(b.toString()) > 0) { UUID t = a; a = b; b = t; }
        var existing = friendshipRepo.findByUser1IdAndUser2Id(a, b);
        if (existing.isPresent()) {
            Friendship f = existing.get();
            if (!"accepted".equals(f.getStatus())) {
                f.markAccepted();
                friendshipRepo.save(f);
            }
            return;
        }
        Friendship f = new Friendship(UUID.randomUUID(),
            entityManager.getReference(User.class, a), entityManager.getReference(User.class, b));
        f.setRequestedBy(initiator); // le filleul a l'initiative (cohérent avec request())
        f.markAccepted();
        friendshipRepo.save(f);
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
