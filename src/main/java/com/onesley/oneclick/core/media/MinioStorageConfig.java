package com.onesley.oneclick.core.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * Configuration MinIO / S3 — Phase 3.5 §X spec senior dev.
 *
 * <p>Bean {@link S3Client} configurable via {@code app.storage.s3.*} properties.
 * Compatible MinIO local (endpoint http://localhost:9000, path-style addressing)
 * et S3 AWS prod (endpoint null, virtual-hosted style).
 *
 * <p>Configuration locale par défaut :
 * <pre>
 * app.storage.s3.endpoint=http://localhost:9000
 * app.storage.s3.region=us-east-1
 * app.storage.s3.access-key=minioadmin
 * app.storage.s3.secret-key=minioadmin
 * app.storage.s3.path-style=true
 * app.storage.s3.bucket=oneclick-media
 * </pre>
 */
@Configuration
public class MinioStorageConfig {

    @Value("${app.storage.s3.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${app.storage.s3.region:us-east-1}")
    private String region;

    @Value("${app.storage.s3.access-key:minioadmin}")
    private String accessKey;

    @Value("${app.storage.s3.secret-key:minioadmin}")
    private String secretKey;

    @Value("${app.storage.s3.path-style:true}")
    private boolean pathStyleAccess;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey)
            ))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build())
            .build();
    }
}
