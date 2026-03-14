package com.onlinestore.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SlugGeneratorTest {

    private final SlugGenerator slugGenerator = new SlugGenerator();

    @Test
    void generateShouldNormalizeDiacriticsAndWhitespace() {
        assertThat(slugGenerator.generate("  Café Déjà Vu  "))
            .isEqualTo("cafe-deja-vu");
    }

    @Test
    void generateShouldFallbackWhenSlugBecomesBlank() {
        assertThat(slugGenerator.generate("!!!"))
            .isEqualTo("item");
    }
}
