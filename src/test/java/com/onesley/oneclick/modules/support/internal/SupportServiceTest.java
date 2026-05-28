package com.onesley.oneclick.modules.support.internal;

import com.onesley.oneclick.core.identity.api.User;
import com.onesley.oneclick.core.identity.api.UserRepository;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.modules.support.api.SupportDtos.AttachmentCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.MessageCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketCreateDto;
import com.onesley.oneclick.modules.support.api.SupportDtos.TicketUpdateDto;
import com.onesley.oneclick.security.SecurityHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class SupportServiceTest {

    @Mock SupportTicketRepository ticketRepo;
    @Mock TicketMessageRepository messageRepo;
    @Mock TicketAttachmentRepository attachmentRepo;
    @Mock EntityManager em;
    @Mock UserRepository userRepository;
    @InjectMocks SupportService service;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "entityManager", em);
        lenient().when(em.getReference(eq(User.class), any())).thenReturn(new User(UUID.randomUUID(), null, "u@x.ma", "h", "U", "U"));
        lenient().when(em.getReference(eq(SupportTicket.class), any())).thenReturn(ticket());
        lenient().when(ticketRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(messageRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        lenient().when(attachmentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private SupportTicket ticket() {
        return new SupportTicket(UUID.randomUUID(), null, "billing", "Problème facture");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAll_delegates() {
        when(ticketRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(UUID.randomUUID(), UUID.randomUUID(), "open", 0, 20).getContent()).isEmpty();
    }

    @Test
    void findById_notFoundAndFound() {
        when(ticketRepo.findById(any())).thenReturn(Optional.empty());
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThatThrownBy(() -> service.findById(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
        }
        SupportTicket t = ticket();
        when(ticketRepo.findById(t.getId())).thenReturn(Optional.of(t));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.findById(t.getId())).isNotNull();
        }
    }

    @Test
    void create_full_andMinimal() {
        assertThat(service.create(new TicketCreateDto(UUID.randomUUID(), "billing", "Sujet", "high", "corps", UUID.randomUUID(), "open"))).isNotNull();
        assertThat(service.create(new TicketCreateDto(UUID.randomUUID(), "billing", "Sujet", null, null, null, null))).isNotNull();
    }

    @Test
    void update_statusBranches_resolvedClosedOther() {
        SupportTicket t = ticket();
        when(ticketRepo.findById(any())).thenReturn(Optional.of(t));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            service.update(t.getId(), new TicketUpdateDto("resolved", "high", UUID.randomUUID(), "réponse", true));
            assertThat(t.getStatus()).isEqualTo("resolved");
            assertThat(t.isEscalatedToAdmin()).isTrue(); // escalade écrite via PATCH
            service.update(t.getId(), new TicketUpdateDto("closed", null, null, null, null));
            assertThat(t.getStatus()).isEqualTo("closed");
            assertThat(t.isEscalatedToAdmin()).isTrue(); // null → inchangé
            service.update(t.getId(), new TicketUpdateDto("in_progress", null, null, null, false));
            assertThat(t.getStatus()).isEqualTo("in_progress");
            assertThat(t.isEscalatedToAdmin()).isFalse(); // dé-escalade
        }
    }

    @Test
    void update_notFound_throwsNotFound() {
        when(ticketRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), new TicketUpdateDto("open", null, null, null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void messages_postAndFind() {
        SupportTicket t = ticket();
        when(ticketRepo.findById(any())).thenReturn(Optional.of(t));
        when(messageRepo.findAllByTicketId(any())).thenReturn(List.of(new TicketMessage(UUID.randomUUID(), t, null, "msg")));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.postMessage(new MessageCreateDto(t.getId(), UUID.randomUUID(), "bonjour"))).isNotNull();
            assertThat(service.findMessages(t.getId())).hasSize(1);
        }
    }

    @Test
    void attachments_attachAndFind() {
        SupportTicket t = ticket();
        when(ticketRepo.findById(any())).thenReturn(Optional.of(t));
        when(attachmentRepo.findAllByTicketId(any())).thenReturn(List.of(new TicketAttachment(UUID.randomUUID(), t, "https://x/y.pdf")));
        try (MockedStatic<SecurityHelper> sec = mockStatic(SecurityHelper.class)) {
            assertThat(service.attach(new AttachmentCreateDto(t.getId(), "https://x/z.pdf", "doc.pdf", "application/pdf"))).isNotNull();
            assertThat(service.findAttachments(t.getId())).hasSize(1);
        }
    }

    @Test
    void postMessage_ticketNotFound_throwsNotFound() {
        when(ticketRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.postMessage(new MessageCreateDto(UUID.randomUUID(), UUID.randomUUID(), "x")))
            .isInstanceOf(NotFoundException.class);
    }
}
