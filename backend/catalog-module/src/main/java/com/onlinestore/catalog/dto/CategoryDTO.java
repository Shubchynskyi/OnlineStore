package com.onlinestore.catalog.dto;

import java.io.Serial;
import java.io.Serializable;

public record CategoryDTO(
    Long id,
    String name,
    String slug,
    String description
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
}
