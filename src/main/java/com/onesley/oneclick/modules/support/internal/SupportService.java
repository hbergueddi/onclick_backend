package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.ConflictException;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.onesley.oneclick.modules.support.api.SupportDtos.*;
import com.onesley.oneclick.modules.support.api.SupportDtos;
import com.onesley.oneclick.modules.support.api.SupportDtos.AttachmentCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.AttachmentDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.MessageCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.MessageDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketUpdateDto;
import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SupportService {

    /** Catégorie dédiée au ticket « plafond d'amis atteint » (dédup 1/24 h par user). */
    public static final String CATEGORY_FRIENDS_CAP = "friends_cap";

    private final SupportTicketRepository ticketRepo;
    private final TicketMessageRepository messageRepo;
    private final TicketAttachmentRepository attachmentRepo;
    private final UserRepository userRepository; // domaine identity (API publique) — enrichissement profil auteur
    private final Clock clock; // horloge injectable (UTC) — fenêtre de dédup testable (cf NoShowDisputeService)

    /** Fenêtre de déduplication d'un ticket auto (ex. friends_cap), en heures (défaut 24 h). */
    @Value("${app.support.dedup-window-hours:24}")
    private long dedupWindowHours;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Tickets ─────────────────────────────────────────────────────────────

    public Page<TicketDto> findAll(UUID openedById, UUID assignedToId, String status, int page, int size) {
        Specification<SupportTicket> spec = (root, q, cb) -> cb.conjunction();
        if (openedById != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("openedById"), openedById));
        if (assignedToId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedToId"), assignedToId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        Page<TicketDto> result = ticketRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(SupportTicket::toDto);

        // Enrichissement serveur-side : profil de l'auteur (openedBy) via l'API publique
        // du domaine identity (évite /api/users/by-ids = VIEW:USERS). Batch anti-N+1.
        Set<UUID> openerIds = result.getContent().stream()
            .map(TicketDto::openedById).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, User> users = openerIds.isEmpty() ? Map.of()
            : userRepository.findAllByIds(openerIds).stream().collect(Collectors.toMap(User::getId, u -> u));

        return result.map(d -> {
            User u = d.openedById() == null ? null : users.get(d.openedById());
            return new TicketDto(d.id(), d.openedById(), d.category(), d.priority(), d.status(), d.subject(),
                d.resolvedAt(), d.closedAt(), d.assignedToId(), d.restaurantId(), d.photos(), d.internal(),
                d.escalatedToAdmin(), d.lastReply(), d.aiHandled(), d.aiSummary(), d.message(),
                d.createdAt(), d.updatedAt(),
                u != null ? u.getFirstName() : null,
                u != null ? u.getLastName() : null,
                u != null ? u.getPhone() : null);
        });
    }

    public TicketDto findById(UUID id) {
        SupportTicket t = ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("SupportTicket", id));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        return t.toDto();
    }

    @Transactional
    public TicketDto create(TicketCreateDto dto) {
        User openerRef = entityManager.getReference(User.class, dto.openedById());
        SupportTicket t = new SupportTicket(UUID.randomUUID(), openerRef, dto.category(), dto.subject());
        if (dto.priority() != null) t.setPriority(dto.priority());
        if (dto.message() != null) t.setMessage(dto.message());
        if (dto.restaurantId() != null) t.setRestaurantId(dto.restaurantId());
        if (dto.status() != null) t.setStatus(dto.status());
        return ticketRepo.save(t).toDto();
    }

    /**
     * Ouvre un ticket « plafond d'amis atteint » ({@link #CATEGORY_FRIENDS_CAP}) pour le
     * user — <b>dédupliqué 1 / fenêtre {@link #dedupWindowHours} (défaut 24 h)</b> : si un
     * ticket friends_cap a déjà été ouvert par ce user dans la fenêtre, lève
     * {@link ConflictException} (409) plutôt que d'empiler des tickets identiques.
     *
     * <p>ABAC self/admin : un user ouvre SON propre ticket (jamais pour un tiers). Réutilise
     * la création de ticket standard (catégorie + sujet imposés) — l'autorité RBAC est
     * {@code CREATE:SUPPORT}, déjà détenue par le CLIENT (V11). La dédup est co-localisée
     * ici car {@code support} possède le domaine ticket (modules CLOSED, pas d'appel
     * cross-module depuis {@code social}).
     */
    @Transactional
    public TicketDto createFriendsCapTicket(UUID userId) {
        SecurityHelper.requireOwnerOrAdmin(userId);
        Instant since = Instant.now(clock).minus(Duration.ofHours(dedupWindowHours));
        if (ticketRepo.existsRecentByOpenerAndCategory(userId, CATEGORY_FRIENDS_CAP, since)) {
            throw new ConflictException(
                "Un ticket « plafond d'amis » a déjà été ouvert récemment — un seul par 24 h");
        }
        User openerRef = entityManager.getReference(User.class, userId);
        SupportTicket t = new SupportTicket(
            UUID.randomUUID(), openerRef, CATEGORY_FRIENDS_CAP, "Plafond d'amis atteint");
        t.setPriority("normal");
        t.setMessage("Demande d'augmentation du plafond d'amis (limite atteinte).");
        return ticketRepo.save(t).toDto();
    }

    @Transactional
    public TicketDto update(UUID id, TicketUpdateDto dto) {
        SupportTicket t = ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("SupportTicket", id));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        if (dto.status() != null) {
            if ("resolved".equals(dto.status())) t.markResolved();
            else if ("closed".equals(dto.status())) t.markClosed();
            else t.setStatus(dto.status());
        }
        if (dto.priority() != null)   t.setPriority(dto.priority());
        if (dto.assignedToId() != null) {
            t.setAssignedTo(entityManager.getReference(User.class, dto.assignedToId()));
        }
        if (dto.lastReply() != null)  t.setLastReply(dto.lastReply());
        if (dto.escalatedToAdmin() != null) t.setEscalatedToAdmin(dto.escalatedToAdmin());
        return ticketRepo.save(t).toDto();
    }

    // ─── Messages ────────────────────────────────────────────────────────────

    public List<MessageDto> findMessages(UUID ticketId) {
        SupportTicket t = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("SupportTicket", ticketId));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        return messageRepo.findAllByTicketId(ticketId).stream().map(TicketMessage::toDto).toList();
    }

    @Transactional
    public MessageDto postMessage(MessageCreateDto dto) {
        SupportTicket t = ticketRepo.findById(dto.ticketId())
            .orElseThrow(() -> new NotFoundException("SupportTicket", dto.ticketId()));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        SupportTicket ticketRef = entityManager.getReference(SupportTicket.class, dto.ticketId());
        User authorRef = entityManager.getReference(User.class, dto.authorId());
        TicketMessage m = new TicketMessage(UUID.randomUUID(), ticketRef, authorRef, dto.message());
        return messageRepo.save(m).toDto();
    }

    // ─── Attachments ─────────────────────────────────────────────────────────

    public List<AttachmentDto> findAttachments(UUID ticketId) {
        SupportTicket t = ticketRepo.findById(ticketId)
            .orElseThrow(() -> new NotFoundException("SupportTicket", ticketId));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        return attachmentRepo.findAllByTicketId(ticketId).stream().map(TicketAttachment::toDto).toList();
    }

    @Transactional
    public AttachmentDto attach(AttachmentCreateDto dto) {
        SupportTicket t = ticketRepo.findById(dto.ticketId())
            .orElseThrow(() -> new NotFoundException("SupportTicket", dto.ticketId()));
        SecurityHelper.requireOwnerOrAdmin(t.getOpenedById());
        SupportTicket ticketRef = entityManager.getReference(SupportTicket.class, dto.ticketId());
        TicketAttachment a = new TicketAttachment(UUID.randomUUID(), ticketRef, dto.url());
        if (dto.fileName() != null) a.setFileName(dto.fileName());
        if (dto.mimeType() != null) a.setMimeType(dto.mimeType());
        return attachmentRepo.save(a).toDto();
    }
}
