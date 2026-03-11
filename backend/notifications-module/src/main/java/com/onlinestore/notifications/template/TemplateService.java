package com.onlinestore.notifications.template;

import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class TemplateService {

    public String render(String template, Map<String, Object> model) {
        String rendered = template;
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return rendered;
    }
}
