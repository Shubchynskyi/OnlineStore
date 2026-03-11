package com.onlinestore.catalog.dto;

import java.io.Serial;
import java.io.Serializable;

public record ImageDTO(
    Long id,
    String url,
    String altText,
    int sortOrder,
    boolean isMain
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
