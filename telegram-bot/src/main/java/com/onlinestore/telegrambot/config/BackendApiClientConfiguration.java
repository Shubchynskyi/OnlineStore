package com.onlinestore.telegrambot.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class BackendApiClientConfiguration {

    @Bean
    public Clock backendApiClock() {
        return Clock.systemUTC();
    }

    @Bean
    public RestClient backendApiRestClient(BotProperties botProperties) {
        BotProperties.BackendApi backendApi = botProperties.getBackendApi();
        return RestClient.builder()
            .baseUrl(backendApi.getBaseUrl())
            .requestFactory(requestFactory(backendApi.getConnectTimeout(), backendApi.getReadTimeout()))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public RestClient backendAuthRestClient(BotProperties botProperties) {
        BotProperties.BackendApi backendApi = botProperties.getBackendApi();
        return RestClient.builder()
            .requestFactory(requestFactory(backendApi.getConnectTimeout(), backendApi.getReadTimeout()))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public RestClient openAiRestClient(BotProperties botProperties) {
        BotProperties.AiAssistant aiAssistant = botProperties.getAiAssistant();
        return RestClient.builder()
            .baseUrl(aiAssistant.getBaseUrl())
            .requestFactory(requestFactory(aiAssistant.getConnectTimeout(), aiAssistant.getReadTimeout()))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private ClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
