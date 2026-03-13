package com.onlinestore.catalog.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "onlinestore.catalog.media")
public class CatalogMediaProperties {

    @NotBlank
    private String endpoint = "http://localhost:9000";

    @NotBlank
    private String accessKey = "minioadmin";

    @NotBlank
    private String secretKey = "minioadmin";

    @NotBlank
    private String bucket = "product-images";

    @NotBlank
    private String publicBaseUrl = "http://localhost:9000/product-images";

    @Positive
    private int presignedUploadExpiryMinutes = 15;

    @Positive
    private long maxUploadSizeBytes = 10L * 1024L * 1024L;

    private List<String> allowedContentTypes = List.of(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/gif"
    );
}
