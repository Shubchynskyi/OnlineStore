package com.onlinestore.apigateway.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

@RestController
@RequestMapping(path = "/fallback", produces = MediaType.APPLICATION_JSON_VALUE)
public class FallbackController {

    @RequestMapping("/{routeId}")
    public ResponseEntity<Map<String, Object>> fallback(
        @PathVariable String routeId,
        ServerWebExchange exchange
    ) {
        Map<String, Object> body = Map.of(
            "timestamp", Instant.now(),
            "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
            "error", "Service Unavailable",
            "message", "Upstream service is temporarily unavailable.",
            "path", exchange.getRequest().getPath().value(),
            "routeId", routeId
        );

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
