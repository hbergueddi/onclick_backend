package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
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

    private final SupportTicketRepository ticketRepo;
    private final TicketMessageRepository messageRepo;
    private final TicketAttachmentRepository attachmentRepo;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Tickets ─────────────────────────────────────────────────────────────

    public Page<TicketDto> findAll(UUID openedById, UUID assignedToId, String status, int page, int size) {
        Specification<SupportTicket> spec = (root, q, cb) -> cb.conjunction();
        if (openedById != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("openedById"), openedById));
        if (assignedToId != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedToId"), assignedToId));
        if (status != null)       spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        return ticketRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(SupportTicket::toDto);
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
