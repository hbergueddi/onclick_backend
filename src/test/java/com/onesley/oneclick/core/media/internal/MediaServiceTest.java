package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.core.media.api.MediaDtos.FileCreateDto;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaCreateDto;
import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link MediaService} (L3 — core.media).
 * CRUD media polymorphique + file attachments (soft delete + RBAC owner).
 */
@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    @Mock MediaRepository mediaRepo;
    @Mock FileAttachmentRepository fileRepo;
    @InjectMocks MediaService service;

    @Test
    @SuppressWarnings("unchecked")
    void findAllMedia_andFiles_delegate() {
        when(mediaRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        when(fileRepo.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAllMedia("restaurant", UUID.randomUUID(), "image", 0, 20).getContent()).isEmpty();
        assertThat(service.findAllFiles("ticket", UUID.randomUUID(), 0, 20).getContent()).isEmpty();
    }

    @Test
    void createMedia_withOptionalFields_andMinimal() {
        when(mediaRepo.save(any(Media.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createMedia(new MediaCreateDto(
            "restaurant", UUID.randomUUID(), "https://x/y.png", "image", "image/png", 2048L, 1))).isNotNull();
        assertThat(service.createMedia(new MediaCreateDto(
            "user", UUID.randomUUID(), "https://x/z.png", "image", null, null, null))).isNotNull();
    }

    @Test
    void softDeleteMedia_notFound_throwsNotFound() {
        when(mediaRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeleteMedia(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDeleteMedia_success_marksDeleted() {
        Media m = new Media(UUID.randomUUID(), "restaurant", UUID.randomUUID(), "https://x/y.png", "image");
        when(mediaRepo.findById(m.getId())).thenReturn(Optional.of(m));
        when(mediaRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.softDeleteMedia(m.getId());
        }
        assertThat(m.getDeletedAt()).isNotNull();
    }

    @Test
    void createFile_withOptionalFields_andMinimal() {
        when(fileRepo.save(any(FileAttachment.class))).thenAnswer(i -> i.getArgument(0));
        assertThat(service.createFile(new FileCreateDto(
            "ticket", UUID.randomUUID(), "/path/a.pdf", "application/pdf", 4096L, "facture.pdf"))).isNotNull();
        assertThat(service.createFile(new FileCreateDto(
            "ticket", UUID.randomUUID(), "/path/b.pdf", "application/pdf", null, null))).isNotNull();
    }

    @Test
    void softDeleteFile_notFound_throwsNotFound() {
        when(fileRepo.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.softDeleteFile(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void softDeleteFile_success_marksDeleted() {
        FileAttachment f = new FileAttachment(UUID.randomUUID(), "ticket", UUID.randomUUID(), "/p.pdf", "application/pdf");
        when(fileRepo.findById(f.getId())).thenReturn(Optional.of(f));
        when(fileRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        try (MockedStatic<SecurityHelper> ignored = mockStatic(SecurityHelper.class)) {
            service.softDeleteFile(f.getId());
        }
        verify(fileRepo).save(f);
    }
}
