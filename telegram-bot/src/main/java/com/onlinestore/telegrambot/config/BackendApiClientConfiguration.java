package com.onlinestore.telegrambot.config;

import java.net.http.HttpClient;
import java.time.Clock;
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
        return RestClient.builder()
            .baseUrl(botProperties.getBackendApi().getBaseUrl())
            .requestFactory(requestFactory(botProperties))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public RestClient backendAuthRestClient(BotProperties botProperties) {
        return RestClient.builder()
            .requestFactory(requestFactory(botProperties))
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    private ClientHttpRequestFactory requestFactory(BotProperties botProperties) {
        BotProperties.BackendApi backendApi = botProperties.getBackendApi();
        HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(backendApi.getConnectTimeout())
            .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(backendApi.getReadTimeout());
        return requestFactory;
    }
}
