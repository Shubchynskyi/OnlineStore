package com.onlinestore.catalog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AttachImageRequest(
    @NotBlank @Size(max = 500) String objectKey,
    @Size(max = 255) String altText,
    Integer sortOrder,
    boolean isMain
) {
}
