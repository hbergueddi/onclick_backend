package com.onesley.oneclick.modules.support;

import com.onesley.oneclick.core.identity.User;
import com.onesley.oneclick.exception.NotFoundException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.onesley.oneclick.modules.support.SupportDtos.*;

@Service
@Transactional(readOnly = true)
public class SupportService {

    private final SupportTicketRepository ticketRepo;
    private final TicketMessageRepository messageRepo;
    private final TicketAttachmentRepository attachmentRepo;

    @PersistenceContext
    private EntityManager entityManager;

    public SupportService(SupportTicketRepository ticketRepo,
                          TicketMessageRepository messageRepo,
                          TicketAttachmentRepository attachmentRepo) {
        this.ticketRepo = ticketRepo;
        this.messageRepo = messageRepo;
        this.attachmentRepo = attachmentRepo;
    }

    // ─── Tickets ─────────────────────────────────────────────────────────────

    public Page<TicketDto> findAll(UUID openedById, UUID assignedToId, String status, int page, int size) {
        Specification<SupportTicket> spec = (root, q, cb) -> cb.conjunction();
        if (openedById != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("openedById"), openedById));
        if (assignedToId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedToId"), assignedToId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return ticketRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(TicketDto::from);
    }

    public TicketDto findById(UUID id) {
        return TicketDto.from(ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("SupportTicket", id)));
    }

    @Transactional
    public TicketDto create(TicketCreateDto dto) {
        User openerRef = entityManager.getReference(User.class, dto.openedById());
        SupportTicket t = new SupportTicket(UUID.randomUUID(), openerRef, dto.category(), dto.subject());
        if (dto.priority() != null) t.setPriority(dto.priority());
        return TicketDto.from(ticketRepo.save(t));
    }

    @Transactional
    public TicketDto update(UUID id, TicketUpdateDto dto) {
        SupportTicket t = ticketRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("SupportTicket", id));
        if (dto.status() != null) {
            if ("resolved".equals(dto.status())) t.markResolved();
            else if ("closed".equals(dto.status())) t.markClosed();
            else t.setStatus(dto.status());
        }
        if (dto.priority() != null)   t.setPriority(dto.priority());
        if (dto.assignedToId() != null) {
            t.setAssignedTo(entityManager.getReference(User.class, dto.assignedToId()));
        }
        return TicketDto.from(ticketRepo.save(t));
    }

    // ─── Messages ────────────────────────────────────────────────────────────

    public List<MessageDto> findMessages(UUID ticketId) {
        return messageRepo.findAllByTicketId(ticketId).stream().map(MessageDto::from).toList();
    }

    @Transactional
    public MessageDto postMessage(MessageCreateDto dto) {
        SupportTicket ticketRef = entityManager.getReference(SupportTicket.class, dto.ticketId());
        User authorRef = entityManager.getReference(User.class, dto.authorId());
        TicketMessage m = new TicketMessage(UUID.randomUUID(), ticketRef, authorRef, dto.message());
        return MessageDto.from(messageRepo.save(m));
    }

    // ─── Attachments ─────────────────────────────────────────────────────────

    public List<AttachmentDto> findAttachments(UUID ticketId) {
        return attachmentRepo.findAllByTicketId(ticketId).stream().map(AttachmentDto::from).toList();
    }

    @Transactional
    public AttachmentDto attach(AttachmentCreateDto dto) {
        SupportTicket ticketRef = entityManager.getReference(SupportTicket.class, dto.ticketId());
        TicketAttachment a = new TicketAttachment(UUID.randomUUID(), ticketRef, dto.url());
        if (dto.fileName() != null) a.setFileName(dto.fileName());
        if (dto.mimeType() != null) a.setMimeType(dto.mimeType());
        return AttachmentDto.from(attachmentRepo.save(a));
    }
}
