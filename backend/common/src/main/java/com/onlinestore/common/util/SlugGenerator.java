package com.onlinestore.common.util;

import java.text.Normalizer;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SlugGenerator {

    public String generate(String source) {
        String normalized = Normalizer.normalize(source, Normalizer.Form.NFKD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
        return normalized.isBlank() ? "item" : normalized;
    }
}
