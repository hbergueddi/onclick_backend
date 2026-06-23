package com.onesley.oneclick.modules.feedback.internal;

import com.onesley.oneclick.core.identity.api.UserDirectoryApi;
import com.onesley.oneclick.core.identity.api.UserDirectoryApi.UserName;
import com.onesley.oneclick.core.membership.api.MembershipDirectoryApi;
import com.onesley.oneclick.exception.BadRequestException;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.ForbiddenException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.CreateFeedbackDto;
import com.onesley.oneclick.modules.feedback.api.PccFeedbackDtos.FeedbackDto;
import com.onesley.oneclick.security.SecurityHelper;
import com.onesley.oneclick.shared.events.FeedbackCreatedEvent;
import com.onesley.oneclick.shared.events.FeedbackRepliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Logique « Avis » (PCC Lot 7) — port fidèle des 2 Edge Functions legacy
 * ({@code send-pcc-feedback} / {@code send-pcc-feedback-reply}) vers un service Spring avec ABAC.
 *
 * <h3>Mapping EF legacy → méthode</h3>
 * <ul>
 *   <li>{@code send-pcc-feedback} (insert feedback + notif owners/tenant-admins) → {@link #create}</li>
 *   <li>(nouveau, lecture membre) → {@link #listMine}</li>
 *   <li>(nouveau, lecture owner-scopée) → {@link #listForOwner}</li>
 *   <li>{@code send-pcc-feedback-reply} (update reply + notif membre) → {@link #reply}</li>
 *   <li>(legacy {@code reply_read_by_member} UPDATE membre) → {@link #markReplyRead}</li>
 * </ul>
 *
 * <h3>ABAC</h3>
 * <ul>
 *   <li>{@code create} : {@code member_id} = caller, {@code tenant_id} = tenant du caller
 *       (UserDirectoryApi) — pas de slug palmeraie en dur.</li>
 *   <li>{@code listMine} : self-scope (caller = member_id).</li>
 *   <li>{@code listForOwner} : staff/admin → inbox owner-scopée (read-view native : avis ciblant
 *       SES restos + généraux du tenant) ; un non-staff retombe sur {@code listMine}.</li>
 *   <li>{@code reply} : staff/admin uniquement (403 sinon) ; un owner « pur » (ni admin) ne peut
 *       répondre qu'à un avis ciblant SON resto OU général (403 sinon — owner-scope). 409 si déjà
 *       répondu.</li>
 *   <li>{@code markReplyRead} : le caller doit être le {@code member_id} (403 sinon).</li>
 * </ul>
 *
 * <h3>Cross-module (Modulith CLOSED)</h3>
 * <p>users → {@code UserDirectoryApi} (core.identity OPEN). owner-scope + nom resto →
 * read-views SQL natives du repo (tables {@code restaurant_staffs}/{@code restaurants}). Aucun
 * import de {@code modules.restaurant}/{@code modules.loyalty}. Notification → events Modulith
 * consommés par {@code core.notification}. Temps réel → {@link FeedbackPublisher} (STOMP).</p>
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class PccFeedbackService {

    private final PccFeedbackRepository repo;
    private final UserDirectoryApi userDirectory;
    private final MembershipDirectoryApi membershipDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final FeedbackPublisher feedbackPublisher;

    /**
     * Tenant « programme » du caller (membre) : sa 1ʳᵉ membership active (après le flip V95 un membre
     * a le home oneclick, mais conserve sa membership programme) sinon son tenant home (fallback
     * rétro-compatible : un non-membre n'a pas de membership → ancien comportement). {@code null} si
     * ni l'un ni l'autre.
     */
    private UUID callerProgramTenant(UUID caller) {
        return membershipDirectory.activeTenantIds(caller).stream().findFirst()
            .orElseGet(() -> userDirectory.tenantIdById(caller).orElse(null));
    }

    // ─── create (membre envoie un avis) ─────────────────────────────────────────

    /**
     * Crée un avis du caller. {@code member_id} = caller, {@code tenant_id} = tenant du caller.
     * Valide sentiment/category (en plus de Bean Validation, défense en profondeur). Publie
     * {@link FeedbackCreatedEvent} (→ notif owners/tenant-admins) + push STOMP création (dashboard
     * owner). Retourne le {@link FeedbackDto} enrichi.
     *
     * <ul>
     *   <li>caller sans tenant → 400 (la fonctionnalité avis suppose un tenant).</li>
     *   <li>sentiment hors {happy,unhappy} ou category vide → 400 (garde-fou service).</li>
     * </ul>
     */
    @Transactional
    public FeedbackDto create(CreateFeedbackDto dto) {
        UUID caller = requireCaller();
        // P3/V95 : le membre a le home oneclick après le flip → on scope au tenant de SA membership
        // programme (sinon home, fallback rétro-compatible). 400 si aucun tenant résoluble.
        UUID tenantId = callerProgramTenant(caller);
        if (tenantId == null) {
            throw new BadRequestException(
                "Aucun tenant associé à votre compte — l'envoi d'avis n'est pas disponible");
        }

        String sentiment = dto.sentiment() == null ? null : dto.sentiment().trim();
        if (!"happy".equals(sentiment) && !"unhappy".equals(sentiment)) {
            throw new BadRequestException("sentiment invalide (happy|unhappy)");
        }
        String category = trimToNull(dto.category());
        if (category == null || category.length() < 2) {
            throw new BadRequestException("category requise (min 2 caractères)");
        }
        String comment = trimToNull(dto.comment());

        // Garde-fou anti-spoof : un avis CIBLÉ ne peut viser qu'un resto du PROGRAMME du membre
        // (même tenant). Sans ça, un membre PCC pourrait cibler un resto d'un autre tenant (ex
        // OneClick standard) → l'owner non-PCC recevrait « Nouvel avis à traiter » (in-app + push) :
        // fuite de périmètre d'un module exclusivement PCC. La branche owners de
        // findFeedbackRecipientIds est elle aussi tenant-scopée (défense en profondeur).
        UUID target = dto.targetRestaurantId();
        if (target != null && !repo.restaurantBelongsToTenant(target, tenantId)) {
            throw new ForbiddenException(
                "Le restaurant ciblé n'appartient pas à votre programme.");
        }

        PccFeedback saved = repo.save(new PccFeedback(
            UUID.randomUUID(), caller, tenantId, sentiment, category, comment, dto.targetRestaurantId()));
        log.info("[feedback] create (id={}, member={}, sentiment={}, target={})",
            saved.getId(), caller, sentiment, dto.targetRestaurantId());

        FeedbackDto out = toDto(saved);

        // Notif server-side (le CLIENT n'a pas CREATE:NOTIFICATIONS) → owners du resto ciblé
        // (+ tenant admins) ou tenant admins seuls (avis général). On RÉSOUT ici les destinataires
        // (read-view native owners+tenant-admins) et on les porte sur l'event : le module
        // core.notification est CLOSED et ne peut résoudre ni les owners (modules.restaurant) ni
        // les tenant-admins (core.identity) — frontière Modulith. Le caller est exclu.
        List<UUID> recipients = repo.findFeedbackRecipientIds(tenantId, saved.getTargetRestaurantId(), caller);
        eventPublisher.publishEvent(new FeedbackCreatedEvent(
            saved.getId(), caller, tenantId, saved.getTargetRestaurantId(),
            recipients, sentiment, category, Instant.now()));
        // Temps réel : push immédiat sur le dashboard owner.
        feedbackPublisher.publishCreated(out);

        return out;
    }

    // ─── listMine (mes avis) ──────────────────────────────────────────────────────

    /** « Mes avis » du caller (self-scope), du plus récent au plus ancien. */
    public List<FeedbackDto> listMine() {
        UUID caller = requireCaller();
        return repo.findByMemberIdOrderByCreatedAtDesc(caller).stream()
            .map(this::toDto)
            .toList();
    }

    // ─── listForOwner (inbox owner-scopée) ──────────────────────────────────────

    /**
     * Inbox des avis pour un owner/admin : staff/admin → read-view owner-scopée (avis ciblant SES
     * restos + généraux, dans le tenant du caller) ; un non-staff retombe sur {@link #listMine}
     * (défense en profondeur — l'endpoint exige VIEW:FEEDBACK que le CLIENT possède aussi).
     */
    public List<FeedbackDto> listForOwner() {
        UUID caller = requireCaller();
        if (!SecurityHelper.isStaffOrAdmin()) {
            return listMine();
        }
        UUID tenantId = userDirectory.tenantIdById(caller).orElse(null);
        if (tenantId == null) {
            return List.of();
        }
        return repo.findVisibleForOwner(caller, tenantId).stream()
            .map(this::toDto)
            .toList();
    }

    // ─── reply (owner/admin répond) ─────────────────────────────────────────────

    /**
     * Réponse d'un owner/admin à un avis. Staff/admin only (403 sinon). Un owner « pur » (ni
     * GROUP_ADMIN/SUPERADMIN) ne peut répondre qu'à un avis ciblant un de SES restos OU un avis
     * général (target NULL) — owner-scope (403 sinon, legacy {@code send-pcc-feedback-reply}).
     * 409 si l'avis a déjà reçu une réponse. Publie {@link FeedbackRepliedEvent} (→ notif membre)
     * + push STOMP réponse (topic du membre).
     */
    @Transactional
    public FeedbackDto reply(UUID feedbackId, String replyText) {
        UUID caller = requireCaller();
        if (!SecurityHelper.isStaffOrAdmin()) {
            throw new ForbiddenException("Accès interdit : seul un owner ou admin peut répondre à un avis");
        }
        String text = trimToNull(replyText);
        if (text == null || text.length() < 2) {
            throw new BadRequestException("La réponse est trop courte (min 2 caractères)");
        }

        PccFeedback fb = repo.findById(feedbackId)
            .orElseThrow(() -> new NotFoundException("PccFeedback", feedbackId));

        // Owner-scope : un owner non-admin ne répond qu'aux avis de ses restos / généraux.
        if (!SecurityHelper.isAdmin()) {
            // Défense en profondeur tenant : un owner non-admin ne peut répondre qu'à un avis de SON
            // tenant (neutralise l'amplification cross-tenant — un avis qui aurait fuité vers le resto
            // d'un owner d'un autre programme ne pourrait pas être traité par cet owner).
            UUID callerTenant = userDirectory.tenantIdById(caller).orElse(null);
            if (callerTenant == null || !callerTenant.equals(fb.getTenantId())) {
                throw new ForbiddenException(
                    "Accès interdit : cet avis est hors de votre périmètre.");
            }
            UUID target = fb.getTargetRestaurantId();
            boolean isGeneral = target == null;
            boolean ownsTarget = target != null && repo.isActiveOwnerOf(caller, target);
            if (!isGeneral && !ownsTarget) {
                throw new ForbiddenException(
                    "Accès interdit : cet avis cible un autre module. Seul l'owner concerné ou un admin peut répondre.");
            }
        }

        if (fb.isReplied()) {
            throw new ConflictException("Cet avis a déjà reçu une réponse.");
        }

        fb.applyReply(text, caller, Instant.now());
        PccFeedback saved = repo.save(fb);
        log.info("[feedback] reply (id={}, by={}, member={})", saved.getId(), caller, saved.getMemberId());

        FeedbackDto out = toDto(saved);

        // Notif server-side au membre + push STOMP sur le topic du membre.
        eventPublisher.publishEvent(new FeedbackRepliedEvent(
            saved.getId(), saved.getMemberId(), saved.getTenantId(), caller, saved.getSentiment(), Instant.now()));
        feedbackPublisher.publishReply(saved.getMemberId(), out);

        return out;
    }

    // ─── markReplyRead (membre marque la réponse lue) ───────────────────────────

    /**
     * Le membre marque la réponse comme lue. Le caller doit être le {@code member_id} de l'avis
     * (403 sinon). Idempotent (re-marquer une réponse déjà lue ne change rien). 404 si l'avis
     * n'existe pas.
     */
    @Transactional
    public FeedbackDto markReplyRead(UUID feedbackId) {
        UUID caller = requireCaller();
        PccFeedback fb = repo.findById(feedbackId)
            .orElseThrow(() -> new NotFoundException("PccFeedback", feedbackId));
        if (!caller.equals(fb.getMemberId())) {
            throw new ForbiddenException(
                "Accès interdit : vous ne pouvez marquer comme lus que vos propres avis");
        }
        if (!fb.isReplyReadByMember()) {
            fb.markReplyRead();
            fb = repo.save(fb);
            log.info("[feedback] mark-read (id={}, member={})", fb.getId(), caller);
        }
        return toDto(fb);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────────

    private UUID requireCaller() {
        UUID caller = SecurityHelper.currentUserId();
        if (caller == null) {
            throw new ForbiddenException("Authentification requise");
        }
        return caller;
    }

    /** Mapping entité → DTO (enrichit nom/contact du membre via UserDirectoryApi). */
    private FeedbackDto toDto(PccFeedback fb) {
        UserName author = userDirectory.nameById(fb.getMemberId()).orElse(null);
        return new FeedbackDto(
            fb.getId(),
            fb.getMemberId(),
            author != null ? author.firstName() : null,
            author != null ? author.lastName() : null,
            author != null ? author.email() : null,
            fb.getTenantId(),
            fb.getSentiment(),
            fb.getCategory(),
            fb.getComment(),
            fb.getTargetRestaurantId(),
            null, // nom resto non résolu sur le mapping entité (cf. read-view owner pour l'inbox)
            fb.getReplyText(),
            fb.getReplyBy(),
            fb.getReplyAt(),
            fb.isReplyReadByMember(),
            fb.getCreatedAt());
    }

    /** Mapping read-view owner → DTO (le nom du resto ciblé est déjà joint dans la view). */
    private FeedbackDto toDto(PccFeedbackOwnerView v) {
        UserName author = userDirectory.nameById(v.getMemberId()).orElse(null);
        return new FeedbackDto(
            v.getId(),
            v.getMemberId(),
            author != null ? author.firstName() : null,
            author != null ? author.lastName() : null,
            author != null ? author.email() : null,
            v.getTenantId(),
            v.getSentiment(),
            v.getCategory(),
            v.getComment(),
            v.getTargetRestaurantId(),
            v.getTargetRestaurantName(),
            v.getReplyText(),
            v.getReplyBy(),
            v.getReplyAt(),
            v.getReplyReadByMember(),
            v.getCreatedAt());
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
