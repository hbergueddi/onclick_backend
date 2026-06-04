package com.onesley.oneclick.modules.promotion.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.promotion.api.OfferReadDto;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Service du suivi lu/non-lu des offres par utilisateur (table {@code offer_reads},
 * V63 — module {@code modules/promotion}).
 *
 * <h3>RBAC / ABAC</h3>
 * <p>Calque du pattern « mark-read » de {@code core/notification} : on réutilise une
 * autorité que le client détient déjà ({@code VIEW:OFFERS} — accordée à CLIENT en V29
 * pour le catalogue public Compass/Spotlight) plutôt que d'introduire une nouvelle
 * ressource RBAC. Le verrou est l'ABAC ici présent : <b>toutes</b> les opérations
 * portent sur {@link SecurityHelper#currentUserId()} — jamais sur un {@code userId}
 * fourni par l'appelant. Un utilisateur ne peut donc lire/écrire QUE ses propres
 * états de lecture (pas d'IDOR possible).</p>
 *
 * <p>Différence avec {@code NotificationService.markRead} (qui prend un id de ressource
 * possédée + {@code requireOwnerOrAdmin}) : ici la ressource « read » est créée à la
 * volée pour le user courant, donc l'ownership est garanti par construction — on n'a
 * pas besoin de {@code requireOwnerOrAdmin}.</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OfferReadService {

    private final OfferReadRepository readRepository;
    private final OfferRepository offerRepository;

    /**
     * Marque une offre comme lue pour l'utilisateur courant (upsert idempotent).
     *
     * <p>Si l'état existe déjà, on met à jour {@code read_at} (re-lecture) sans créer
     * de doublon — la contrainte UNIQUE(user_id, offer_id) est ainsi respectée même
     * sous double-tap front. 404 si l'offre n'existe pas (ou soft-deleted) — même
     * sémantique que {@code OfferService.recordImpression}.</p>
     */
    @Transactional
    public OfferReadDto markRead(UUID offerId) {
        // L'offre doit exister et ne pas être supprimée (cohérent avec VIEW:OFFERS).
        offerRepository.findById(offerId)
            .filter(o -> o.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Offer", offerId));

        UUID userId = SecurityHelper.currentUserId();
        Instant now = Instant.now();

        OfferRead read = readRepository.findByUserIdAndOfferId(userId, offerId)
            .map(existing -> { existing.setReadAt(now); return existing; })
            .orElseGet(() -> new OfferRead(UUID.randomUUID(), userId, offerId, now, now));

        OfferRead saved = readRepository.save(read);
        return new OfferReadDto(saved.getOfferId(), saved.getUserId(), saved.getReadAt(), saved.getCreatedAt());
    }

    /**
     * Ids des offres lues par l'utilisateur courant (plus récemment lues d'abord).
     * Pilote la logique « épinglées non-lues » du front : {@code offresÉpinglées −
     * cesIds = épinglées non lues}.
     */
    public List<UUID> listReadOfferIds() {
        return readRepository.findOfferIdsByUserId(SecurityHelper.currentUserId());
    }
}
