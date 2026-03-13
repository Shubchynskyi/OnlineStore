package com.onlinestore.catalog.api;

import com.onlinestore.catalog.dto.AttachImageRequest;
import com.onlinestore.catalog.dto.GenerateUploadUrlRequest;
import com.onlinestore.catalog.dto.ImageDTO;
import com.onlinestore.catalog.dto.MediaUploadResponse;
import com.onlinestore.catalog.service.MediaStorageService;
import com.onlinestore.catalog.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminMediaController {

    private final MediaStorageService mediaStorageService;
    private final ProductService productService;

    @PostMapping("/media/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public MediaUploadResponse createUploadUrl(@Valid @RequestBody GenerateUploadUrlRequest request) {
        return mediaStorageService.generateUploadUrl(request);
    }

    @PostMapping("/products/{id}/images")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public ImageDTO attachImage(@PathVariable Long id, @Valid @RequestBody AttachImageRequest request) {
        return productService.attachImage(id, request);
    }
}
