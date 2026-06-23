package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.core.media.api.MediaAccessApi;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaCreateDto;
import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;
import com.onesley.oneclick.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Implémentation du port {@link MediaAccessApi} (package {@code internal} — non
 * visible hors module ; Spring l'injecte par l'interface publique).
 *
 * <p>Délègue le stockage à {@link MediaStorageService} et la persistance à
 * {@link MediaService} / {@link MediaRepository}. Aucune vérification d'accès ici :
 * la garde RBAC/ABAC est portée par le contrôleur appelant (cf. javadoc du port).</p>
 */
@Service
@RequiredArgsConstructor
class MediaAccessApiImpl implements MediaAccessApi {

    private final MediaStorageService storage;
    private final MediaService mediaService;
    private final MediaRepository mediaRepo;

    @Override
    @Transactional
    public MediaDto upload(String entityType, UUID entityId, MultipartFile file, Integer sortOrder) {
        String url = storage.upload(entityType, entityId, file);
        return mediaService.createMedia(new MediaCreateDto(
            entityType, entityId, url, "image", file.getContentType(), file.getSize(), sortOrder));
    }

    @Override
    public List<MediaDto> listForEntity(String entityType, UUID entityId) {
        return mediaRepo
            .findByEntityTypeAndEntityIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(entityType, entityId)
            .stream().map(Media::toDto).toList();
    }

    @Override
    public long countForEntity(String entityType, UUID entityId) {
        return mediaRepo.countByEntityTypeAndEntityIdAndDeletedAtIsNull(entityType, entityId);
    }

    @Override
    @Transactional
    public void delete(UUID mediaId) {
        Media m = mediaRepo.findByIdAndDeletedAtIsNull(mediaId)
            .orElseThrow(() -> new NotFoundException("Media", mediaId));
        m.markDeleted();
        mediaRepo.save(m);
    }

    @Override
    public MediaDto findById(UUID mediaId) {
        return mediaRepo.findByIdAndDeletedAtIsNull(mediaId)
            .map(Media::toDto)
            .orElseThrow(() -> new NotFoundException("Media", mediaId));
    }
}
