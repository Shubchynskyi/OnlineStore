package com.onlinestore.apigateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class KeyResolverConfig {

    @Bean("principalOrIpKeyResolver")
    public KeyResolver principalOrIpKeyResolver() {
        return exchange -> exchange.getPrincipal()
            .map(Principal::getName)
            .filter(name -> !name.isBlank())
            .switchIfEmpty(resolveRemoteIp(exchange.getRequest().getRemoteAddress()))
            .defaultIfEmpty("anonymous");
    }

    private Mono<String> resolveRemoteIp(InetSocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.getAddress() == null) {
            return Mono.empty();
        }
        return Mono.just(remoteAddress.getAddress().getHostAddress());
    }
}
