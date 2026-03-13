package com.onlinestore.catalog.dto;

public record MediaUploadResponse(
    String objectKey,
    String uploadUrl,
    String assetUrl,
    String httpMethod,
    long expiresInSeconds
) {
}
