package com.onesley.oneclick.core.media.internal;

import com.onesley.oneclick.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires Mockito de {@link MediaStorageService} (L3 — core.media).
 * Upload S3/MinIO (validation taille/MIME/bucket) + delete (extract key). S3Client mocké.
 */
@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock S3Client s3;

    private MediaStorageService svc(int maxMb) {
        return new MediaStorageService(s3, "oneclick-media", "http://localhost:9000/", maxMb);
    }
    private MockMultipartFile png(int bytes) {
        return new MockMultipartFile("file", "photo.png", "image/png", new byte[bytes]);
    }

    @Test
    void upload_nullOrEmpty_throwsBadRequest() {
        assertThatThrownBy(() -> svc(10).upload("restaurant", UUID.randomUUID(), null))
            .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> svc(10).upload("restaurant", UUID.randomUUID(),
            new MockMultipartFile("file", new byte[0]))).isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_tooLarge_throwsBadRequest() {
        // max 0 MB -> tout fichier non vide dépasse
        assertThatThrownBy(() -> svc(0).upload("restaurant", UUID.randomUUID(), png(5)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_disallowedMime_throwsBadRequest() {
        var txt = new MockMultipartFile("file", "x.txt", "text/plain", new byte[]{1, 2, 3});
        assertThatThrownBy(() -> svc(10).upload("restaurant", UUID.randomUUID(), txt))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_bucketMissing_throwsBadRequest() {
        when(s3.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().message("nope").build());
        assertThatThrownBy(() -> svc(10).upload("restaurant", UUID.randomUUID(), png(10)))
            .isInstanceOf(BadRequestException.class);
    }

    @Test
    void upload_success_returnsPublicUrl() {
        // headBucket + putObject non stubbés -> null (OK)
        String url = svc(10).upload("restaurant", UUID.randomUUID(), png(100));
        assertThat(url).startsWith("http://localhost:9000/oneclick-media/restaurant/");
        verify(s3).putObject(any(software.amazon.awssdk.services.s3.model.PutObjectRequest.class),
            any(software.amazon.awssdk.core.sync.RequestBody.class));
    }

    @Test
    void delete_validUrl_callsS3Delete() {
        svc(10).delete("http://localhost:9000/oneclick-media/restaurant/abc/photo.png");
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_nullUrl_noop() {
        svc(10).delete(null);
        verify(s3, org.mockito.Mockito.never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_s3Throws_isSwallowed() {
        when(s3.deleteObject(any(DeleteObjectRequest.class))).thenThrow(new RuntimeException("boom"));
        // ne doit pas propager
        svc(10).delete("oneclick-media/key/x.png"); // déjà une key
        verify(s3).deleteObject(any(DeleteObjectRequest.class));
    }
}
