package com.onesley.oneclick.core.media.api;

import com.onesley.oneclick.core.media.api.MediaDtos.MediaDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Port public du module {@code core/media} — permet aux modules métier (ex.
 * {@code modules.restaurant}) d'uploader / lister / supprimer des médias liés à
 * une entité SANS importer les classes {@code internal} (Modulith CLOSED).
 *
 * <p>L'autorisation (RBAC + ABAC) reste portée par le contrôleur appelant : ce
 * port n'effectue AUCUN contrôle d'accès (contrairement à {@code MediaController}
 * gardé par {@code hasAuthority('…:MEDIA')}). Le module restaurant garde donc ses
 * photos via {@code hasAuthority('UPDATE:RESTAURANTS')} + garde propriétaire de
 * restaurant, sans dépendre des autorités média génériques.</p>
 */
public interface MediaAccessApi {

    /** Upload binaire (MinIO/S3) + création de la row {@code media} (type {@code image}). */
    MediaDto upload(String entityType, UUID entityId, MultipartFile file, Integer sortOrder);

    /** Médias actifs d'une entité, triés {@code sort_order} puis {@code created_at} (croissants). */
    List<MediaDto> listForEntity(String entityType, UUID entityId);

    /** Nombre de médias actifs (non supprimés) d'une entité — pour l'application de quotas. */
    long countForEntity(String entityType, UUID entityId);

    /** Soft-delete d'un média par id (sans contrôle propriétaire — l'appelant porte l'ABAC). */
    void delete(UUID mediaId);

    /** Média actif par id, ou 404. */
    MediaDto findById(UUID mediaId);
}
