package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.exception.NotFoundException;
import com.onesley.oneclick.security.SecurityHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static com.onesley.oneclick.core.media.api.MediaDtos.*;
import com.onesley.oneclick.core.media.api.MediaDtos;
import com.onesley.oneclick.core.media.api.MediaDtos.FileCreateDto;
import com.onesley.oneclick.core.media.api.MediaDtos.FileDto;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaCreateDto;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;

@Service
@Transactional(readOnly = true)
public class MediaService {

    private final MediaRepository mediaRepo;
    private final FileAttachmentRepository fileRepo;

    public MediaService(MediaRepository mediaRepo, FileAttachmentRepository fileRepo) {
        this.mediaRepo = mediaRepo;
        this.fileRepo = fileRepo;
    }

    // ─── Media polymorphique ─────────────────────────────────────────────────

    public Page<MediaDto> findAllMedia(String entityType, UUID entityId, String mediaType, int page, int size) {
        Specification<Media> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (entityType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        if (entityId != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId));
        if (mediaType != null)  spec = spec.and((root, q, cb) -> cb.equal(root.get("mediaType"), mediaType));
        return mediaRepo.findAll(spec, PageRequest.of(page, size, Sort.by("sortOrder", "createdAt")))
            .map(Media::toDto);
    }

    @Transactional
    public MediaDto createMedia(MediaCreateDto dto) {
        Media m = new Media(UUID.randomUUID(), dto.entityType(), dto.entityId(), dto.url(), dto.mediaType());
        if (dto.mimeType() != null)  m.setMimeType(dto.mimeType());
        if (dto.sizeBytes() != null) m.setSizeBytes(dto.sizeBytes());
        if (dto.sortOrder() != null) m.setSortOrder(dto.sortOrder());
        return mediaRepo.save(m).toDto();
    }

    @Transactional
    public void softDeleteMedia(UUID id) {
        Media m = mediaRepo.findById(id)
            .filter(x -> x.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("Media", id));
        SecurityHelper.requireOwnerOrAdmin(m.getCreatedBy());
        m.markDeleted();
        mediaRepo.save(m);
    }

    // ─── File attachments (PDF / docs) ───────────────────────────────────────

    public Page<FileDto> findAllFiles(String entityType, UUID entityId, int page, int size) {
        Specification<FileAttachment> spec = (root, q, cb) -> cb.isNull(root.get("deletedAt"));
        if (entityType != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType));
        if (entityId != null)   spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId));
        return fileRepo.findAll(spec, PageRequest.of(page, size, Sort.by("createdAt").descending()))
            .map(FileAttachment::toDto);
    }

    @Transactional
    public FileDto createFile(FileCreateDto dto) {
        FileAttachment f = new FileAttachment(UUID.randomUUID(), dto.entityType(), dto.entityId(),
            dto.path(), dto.mimeType());
        if (dto.sizeBytes() != null)    f.setSizeBytes(dto.sizeBytes());
        if (dto.originalName() != null) f.setOriginalName(dto.originalName());
        return fileRepo.save(f).toDto();
    }

    @Transactional
    public void softDeleteFile(UUID id) {
        FileAttachment f = fileRepo.findById(id)
            .filter(x -> !x.isDeleted())
            .orElseThrow(() -> new NotFoundException("FileAttachment", id));
        SecurityHelper.requireOwnerOrAdmin(f.getCreatedById());
        f.markDeleted();
        fileRepo.save(f);
    }
}
