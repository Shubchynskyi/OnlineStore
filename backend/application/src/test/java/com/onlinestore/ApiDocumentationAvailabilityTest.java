package com.onlinestore;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocumentationAvailabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void swaggerUiShouldBePubliclyAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));
    }

    @Test
    void apiDocsShouldBePubliclyAvailable() throws Exception {
        mockMvc.perform(get("/api-docs"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("\"title\":\"OnlineStore API\"")));
    }
}
