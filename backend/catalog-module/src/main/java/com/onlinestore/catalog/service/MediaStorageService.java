package com.onlinestore.catalog.service;

import com.onlinestore.catalog.config.CatalogMediaProperties;
import com.onlinestore.catalog.dto.GenerateUploadUrlRequest;
import com.onlinestore.catalog.dto.MediaUploadResponse;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.util.SlugGenerator;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MediaStorageService {

    private final MinioClient minioClient;
    private final CatalogMediaProperties properties;
    private final SlugGenerator slugGenerator;

    public static final int MAX_PRODUCT_IMAGE_URL_LENGTH = 1024;

    private final AtomicBoolean bucketPrepared = new AtomicBoolean(false);
    private final Object bucketPreparationMonitor = new Object();

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MediaUploadResponse generateUploadUrl(GenerateUploadUrlRequest request) {
        validateUploadRequest(request);
        ensureBucketPrepared();

        String objectKey = buildObjectKey(request.fileName(), request.contentType());
        try {
            String uploadUrl = minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .expiry(properties.getPresignedUploadExpiryMinutes(), TimeUnit.MINUTES)
                    .build()
            );
            return new MediaUploadResponse(
                objectKey,
                uploadUrl,
                resolveObjectUrl(objectKey),
                Method.PUT.name(),
                TimeUnit.MINUTES.toSeconds(properties.getPresignedUploadExpiryMinutes())
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate a presigned upload URL for media object: " + objectKey, ex);
        }
    }

    public void assertObjectIsAttachable(String objectKey) {
        StatObjectResponse objectResponse = statObject(objectKey);
        validateStoredObject(objectKey, objectResponse.contentType(), objectResponse.size());
    }

    private StatObjectResponse statObject(String objectKey) {
        ensureBucketPrepared();
        try {
            return minioClient.statObject(
                StatObjectArgs.builder()
                    .bucket(properties.getBucket())
                    .object(objectKey)
                    .build()
            );
        } catch (ErrorResponseException ex) {
            String errorCode = ex.errorResponse() == null ? "" : ex.errorResponse().code();
            if ("NoSuchKey".equalsIgnoreCase(errorCode)
                || "NoSuchObject".equalsIgnoreCase(errorCode)
                || "NoSuchBucket".equalsIgnoreCase(errorCode)) {
                throw new BusinessException("MEDIA_OBJECT_NOT_FOUND", "Uploaded media object does not exist: " + objectKey);
            }
            throw new IllegalStateException("Failed to verify media object existence: " + objectKey, ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to verify media object existence: " + objectKey, ex);
        }
    }

    private void validateStoredObject(String objectKey, String contentType, long size) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
        Set<String> allowedContentTypes = allowedContentTypes();
        if (!allowedContentTypes.contains(normalizedContentType)) {
            throw new BusinessException("UNSUPPORTED_MEDIA_TYPE", "Stored media has unsupported content type: " + objectKey);
        }
        if (size > properties.getMaxUploadSizeBytes()) {
            throw new BusinessException(
                "MEDIA_FILE_TOO_LARGE",
                "Stored media exceeds the configured maximum size of %d bytes".formatted(properties.getMaxUploadSizeBytes())
            );
        }
    }

    public String resolveObjectUrl(String objectKey) {
        String baseUrl = properties.getPublicBaseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = trimTrailingSlash(properties.getEndpoint()) + "/" + properties.getBucket();
        }
        return trimTrailingSlash(baseUrl) + "/" + objectKey;
    }

    private void validateUploadRequest(GenerateUploadUrlRequest request) {
        Set<String> allowedContentTypes = allowedContentTypes();
        String normalizedContentType = request.contentType().toLowerCase(Locale.ROOT).trim();
        if (!allowedContentTypes.contains(normalizedContentType)) {
            throw new BusinessException("UNSUPPORTED_MEDIA_TYPE", "Unsupported media content type: " + request.contentType());
        }
        if (request.fileSizeBytes() > properties.getMaxUploadSizeBytes()) {
            throw new BusinessException(
                "MEDIA_FILE_TOO_LARGE",
                "Media file exceeds the configured maximum size of %d bytes".formatted(properties.getMaxUploadSizeBytes())
            );
        }
    }

    private String buildObjectKey(String fileName, String contentType) {
        String normalizedFileName = StringUtils.getFilename(fileName);
        String baseName = StringUtils.stripFilenameExtension(normalizedFileName);
        String extension = normalizeExtension(StringUtils.getFilenameExtension(normalizedFileName), contentType);
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        return "products/%d/%02d/%s-%s.%s".formatted(
            now.getYear(),
            now.getMonthValue(),
            slugGenerator.generate(baseName == null ? fileName : baseName),
            UUID.randomUUID(),
            extension
        );
    }

    private String normalizeExtension(String extension, String contentType) {
        if (extension != null && !extension.isBlank()) {
            return extension.toLowerCase(Locale.ROOT);
        }

        return switch (contentType.toLowerCase(Locale.ROOT).trim()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            default -> "bin";
        };
    }

    private void ensureBucketPrepared() {
        if (bucketPrepared.get()) {
            return;
        }

        synchronized (bucketPreparationMonitor) {
            if (bucketPrepared.get()) {
                return;
            }

            try {
                var bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                        .bucket(properties.getBucket())
                        .build()
                );
                if (!bucketExists) {
                    minioClient.makeBucket(
                        MakeBucketArgs.builder()
                            .bucket(properties.getBucket())
                            .build()
                    );
                    minioClient.setBucketPolicy(
                        SetBucketPolicyArgs.builder()
                            .bucket(properties.getBucket())
                            .config(publicReadPolicy(properties.getBucket()))
                            .build()
                    );
                }
                bucketPrepared.set(true);
            } catch (Exception ex) {
                throw new IllegalStateException("Failed to prepare media bucket: " + properties.getBucket(), ex);
            }
        }
    }

    private Set<String> allowedContentTypes() {
        return properties.getAllowedContentTypes().stream()
            .map(value -> value.toLowerCase(Locale.ROOT).trim())
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    }

    private String publicReadPolicy(String bucket) {
        return """
            {
              "Version": "2012-10-17",
              "Statement": [
                {
                  "Effect": "Allow",
                  "Principal": {
                    "AWS": ["*"]
                  },
                  "Action": ["s3:GetObject"],
                  "Resource": ["arn:aws:s3:::%s/*"]
                }
              ]
            }
            """.formatted(bucket);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
