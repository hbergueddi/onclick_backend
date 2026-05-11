package com.onesley.oneclick.core.media;

import com.onesley.oneclick.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Service de stockage binaire — Phase 3.5 §X spec.
 *
 * <p>Upload des fichiers binaires (photos, PDFs, docs) vers MinIO / S3.
 * Retourne une URL publique (path-style: {@code http://localhost:9000/{bucket}/{key}}).
 *
 * <p>Pattern :
 * <ul>
 *   <li>Le binaire vit dans S3/MinIO (bucket {@code oneclick-media}).</li>
 *   <li>La table {@code media} / {@code file_attachments} stocke uniquement l'URL.</li>
 *   <li>Le hook d'écriture passe par {@link MediaService} qui crée la row DB.</li>
 * </ul>
 *
 * <p>Validation : taille max 10 MB, MIME whitelist (jpg, png, webp, pdf).
 * Override via {@code app.storage.media.max-size-mb} / {@code allowed-mime-types}.
 */
@Service
public class MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageService.class);

    private static final Set<String> DEFAULT_ALLOWED_MIME = Set.of(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif",
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final S3Client s3;
    private final String bucket;
    private final String publicBaseUrl;
    private final long maxSizeBytes;

    public MediaStorageService(S3Client s3,
                                @Value("${app.storage.s3.bucket:oneclick-media}") String bucket,
                                @Value("${app.storage.s3.public-base-url:http://localhost:9000}") String publicBaseUrl,
                                @Value("${app.storage.media.max-size-mb:10}") int maxSizeMb) {
        this.s3 = s3;
        this.bucket = bucket;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        this.maxSizeBytes = (long) maxSizeMb * 1024 * 1024;
    }

    /**
     * Upload binaire vers MinIO et retourne l'URL publique.
     *
     * @param entityType "restaurant", "user", "offer", etc. — utilisé pour le path
     * @param entityId   UUID parent (génère prefix dossier)
     * @param file       binaire uploadé via multipart
     * @return URL publique téléchargeable {@code http://endpoint/{bucket}/{key}}
     */
    public String upload(String entityType, UUID entityId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("file vide ou null");
        }
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException(
                "Fichier trop volumineux : " + (file.getSize() / 1024 / 1024) + " MB (max " + (maxSizeBytes / 1024 / 1024) + " MB)"
            );
        }
        String mime = file.getContentType() != null ? file.getContentType().toLowerCase() : "application/octet-stream";
        if (!DEFAULT_ALLOWED_MIME.contains(mime)) {
            throw new BadRequestException("Type MIME non autorisé : " + mime);
        }
        ensureBucketExists();

        String key = buildKey(entityType, entityId, file.getOriginalFilename());
        try {
            s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(mime)
                .contentLength(file.getSize())
                .build(),
                RequestBody.fromBytes(file.getBytes())
            );
        } catch (IOException e) {
            throw new BadRequestException("Erreur lecture fichier : " + e.getMessage());
        }
        String url = publicBaseUrl + "/" + bucket + "/" + key;
        log.info("MinIO upload : {} → {} ({} bytes, {})", file.getOriginalFilename(), url, file.getSize(), mime);
        return url;
    }

    /** Supprimer un objet du bucket (call cleanup quand on soft-delete une row Media). */
    public void delete(String urlOrKey) {
        String key = extractKey(urlOrKey);
        if (key == null) {
            log.warn("MinIO delete : URL non parsable {}", urlOrKey);
            return;
        }
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("MinIO delete : {}", key);
        } catch (Exception e) {
            log.warn("MinIO delete failed : {} — {}", key, e.getMessage());
        }
    }

    private void ensureBucketExists() {
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException e) {
            throw new BadRequestException("Bucket S3 introuvable : " + bucket);
        } catch (Exception e) {
            // Continue : on tente quand même le put (peut être un transient)
            log.warn("HeadBucket avertissement : {}", e.getMessage());
        }
    }

    private String buildKey(String entityType, UUID entityId, String filename) {
        String safeName = filename == null ? "file" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        String prefix = (entityType == null ? "misc" : entityType.toLowerCase());
        String id = (entityId == null ? "shared" : entityId.toString());
        return prefix + "/" + id + "/" + UUID.randomUUID() + "-" + safeName;
    }

    private String extractKey(String urlOrKey) {
        if (urlOrKey == null) return null;
        // URL full : http://host/bucket/key/path → key/path
        String marker = "/" + bucket + "/";
        int idx = urlOrKey.indexOf(marker);
        if (idx >= 0) return urlOrKey.substring(idx + marker.length());
        // Already a key (no marker)
        return urlOrKey.startsWith("/") ? urlOrKey.substring(1) : urlOrKey;
    }
}
