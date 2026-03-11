package com.onlinestore.payments.webhook;

import com.onlinestore.payments.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/payments")
@RequiredArgsConstructor
public class PaymentWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handleWebhook(
        @PathVariable String provider,
        @RequestBody String payload,
        @RequestHeader("X-Webhook-Signature") String signature,
        @RequestHeader("X-Webhook-Timestamp") String timestamp
    ) {
        paymentService.handleWebhook(provider, payload, signature, timestamp);
        return ResponseEntity.ok().build();
    }
}
