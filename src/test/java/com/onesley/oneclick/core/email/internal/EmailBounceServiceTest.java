package com.onesley.oneclick.core.email.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests unitaires Mockito de {@link EmailBounceService} (Gap #4 — suppression list). */
@ExtendWith(MockitoExtension.class)
class EmailBounceServiceTest {

    @Mock EmailBounceRepository repository;
    @InjectMocks EmailBounceService service;

    @Test
    void recordBounce_newPermanent_suppresses() {
        when(repository.findByEmail("a@x.ma")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.recordBounce("A@X.MA", "permanent", "mailbox does not exist", "resend-webhook", "{}");

        ArgumentCaptor<EmailBounce> c = ArgumentCaptor.forClass(EmailBounce.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().getEmail()).isEqualTo("a@x.ma"); // normalisé
        assertThat(c.getValue().isSuppressed()).isTrue();
        assertThat(c.getValue().getBounceCount()).isEqualTo(1);
    }

    @Test
    void recordBounce_newTransient_notSuppressed() {
        when(repository.findByEmail("b@x.ma")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.recordBounce("b@x.ma", "transient", null, null, null);
        ArgumentCaptor<EmailBounce> c = ArgumentCaptor.forClass(EmailBounce.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().isSuppressed()).isFalse();
    }

    @Test
    void recordBounce_complaint_suppresses() {
        when(repository.findByEmail("c@x.ma")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.recordBounce("c@x.ma", "complaint", null, null, null);
        ArgumentCaptor<EmailBounce> c = ArgumentCaptor.forClass(EmailBounce.class);
        verify(repository).save(c.capture());
        assertThat(c.getValue().isSuppressed()).isTrue();
    }

    @Test
    void recordBounce_existing_incrementsAndKeepsSuppressed() {
        EmailBounce existing = new EmailBounce(UUID.randomUUID(), "d@x.ma", "permanent");
        existing.setSuppressed(true);
        existing.setBounceCount(2);
        when(repository.findByEmail("d@x.ma")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.recordBounce("d@x.ma", "transient", null, null, null); // rebounce transient

        assertThat(existing.isSuppressed()).isTrue(); // une suppression ne redescend jamais
        assertThat(existing.getBounceCount()).isEqualTo(3);
    }

    @Test
    void recordBounce_blankEmail_noop() {
        service.recordBounce("  ", "permanent", null, null, null);
        verify(repository, never()).save(any());
    }

    @Test
    void isSuppressed_normalizesAndDelegates() {
        when(repository.existsByEmailAndSuppressedTrue("e@x.ma")).thenReturn(true);
        assertThat(service.isSuppressed("  E@X.MA ")).isTrue();
        assertThat(service.isSuppressed("")).isFalse();
    }
}
