package com.onlinestore.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.catalog.config.CatalogMediaProperties;
import com.onlinestore.catalog.dto.GenerateUploadUrlRequest;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.util.SlugGenerator;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    private MediaStorageService mediaStorageService;

    @BeforeEach
    void setUp() {
        var properties = new CatalogMediaProperties();
        properties.setEndpoint("http://localhost:9000");
        properties.setBucket("product-images");
        properties.setPublicBaseUrl("http://localhost:9000/product-images");
        properties.setAllowedContentTypes(List.of("image/jpeg", "image/png", "image/webp"));
        properties.setMaxUploadSizeBytes(1024 * 1024);
        properties.setPresignedUploadExpiryMinutes(15);

        mediaStorageService = new MediaStorageService(minioClient, properties, new SlugGenerator());
    }

    @Test
    void generateUploadUrlShouldRejectUnsupportedContentType() {
        var request = new GenerateUploadUrlRequest("phone.svg", "image/svg+xml", 200);

        var exception = assertThrows(BusinessException.class, () -> mediaStorageService.generateUploadUrl(request));

        assertEquals("UNSUPPORTED_MEDIA_TYPE", exception.getErrorCode());
        verifyNoInteractions(minioClient);
    }

    @Test
    void generateUploadUrlShouldReturnPresignedPutUrlWithPublicAssetUrl() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
            .thenReturn("http://localhost:9000/upload-url");

        var response = mediaStorageService.generateUploadUrl(
            new GenerateUploadUrlRequest("Phone Image.png", "image/png", 512)
        );

        assertEquals("PUT", response.httpMethod());
        assertEquals("http://localhost:9000/upload-url", response.uploadUrl());
        assertEquals("http://localhost:9000/product-images/" + response.objectKey(), response.assetUrl());
        assertTrue(response.objectKey().startsWith("products/"));
        assertTrue(response.objectKey().endsWith(".png"));
        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    void assertObjectIsAttachableShouldRejectUnsupportedStoredContentType() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        var statResponse = org.mockito.Mockito.mock(StatObjectResponse.class);
        when(statResponse.contentType()).thenReturn("application/pdf");
        when(statResponse.size()).thenReturn(128L);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(statResponse);

        var exception = assertThrows(
            BusinessException.class,
            () -> mediaStorageService.assertObjectIsAttachable("products/2026/03/manual-upload.pdf")
        );

        assertEquals("UNSUPPORTED_MEDIA_TYPE", exception.getErrorCode());
    }
}
