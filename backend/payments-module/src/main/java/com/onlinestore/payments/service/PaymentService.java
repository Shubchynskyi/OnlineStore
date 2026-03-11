package com.onlinestore.payments.service;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.common.util.Money;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentWebhookEvent;
import com.onlinestore.payments.entity.PaymentStatus;
import com.onlinestore.payments.mapper.PaymentMapper;
import com.onlinestore.payments.provider.PaymentRequest;
import com.onlinestore.payments.provider.PaymentResult;
import com.onlinestore.payments.registry.PaymentProviderRegistry;
import com.onlinestore.payments.repository.PaymentRepository;
import com.onlinestore.payments.repository.PaymentWebhookEventRepository;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final List<PaymentStatus> ACTIVE_PAYMENT_STATUSES = List.of(
        PaymentStatus.PENDING,
        PaymentStatus.REQUIRES_ACTION,
        PaymentStatus.AUTHORIZED,
        PaymentStatus.PAID
    );
    private static final List<String> INITIATE_PAYMENT_CONCURRENCY_CONSTRAINT_MARKERS = List.of(
        "payments_idempotency_key_key",
        "ux_payments_active_attempt_per_order_provider"
    );
    private static final String METADATA_INITIATED_BY_USER_ID = "initiatedByUserId";
    private static final String METADATA_NEXT_ACTION_URL = "nextActionUrl";
    private static final String WEBHOOK_EVENT_UNIQUE_CONSTRAINT = "ux_payment_webhook_events_provider_event";
    private static final long MAX_WEBHOOK_CLOCK_SKEW_SECONDS = 30;

    private final PaymentRepository paymentRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderAccessGateway orderAccessGateway;
    private final PaymentProviderRegistry providerRegistry;
    private final RabbitTemplate rabbitTemplate;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;
    @Value("${onlinestore.payments.webhook.max-age-seconds:300}")
    private long webhookMaxAgeSeconds = 300;

    @Transactional
    public PaymentDTO initiatePayment(Long userId, InitiatePaymentRequest request) {
        String providerCode = normalizeProviderCode(request.providerCode());
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        var order = orderAccessGateway.findByIdAndUserId(request.orderId(), userId)
            .orElseThrow(() -> new BusinessException(
                "ACCESS_DENIED",
                "Order is not available for current user"
            ));
        validateRequestedAmount(request, order);

        var existingByKey = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByKey.isPresent()) {
            var existingPayment = existingByKey.get();
            validateIdempotencyOwnership(existingPayment, order.id(), providerCode);
            log.info("Idempotent payment replay by key: paymentId={}, orderId={}, provider={}, userId={}",
                existingPayment.getId(),
                order.id(),
                providerCode,
                userId);
            return paymentMapper.toDto(existingPayment, readNextActionUrl(existingPayment));
        }

        var existingActivePayment = findActivePayment(order.id(), providerCode);
        if (existingActivePayment.isPresent()) {
            var payment = existingActivePayment.get();
            log.info("Idempotent payment replay by active payment: paymentId={}, orderId={}, provider={}, userId={}",
                payment.getId(),
                order.id(),
                providerCode,
                userId);
            return paymentMapper.toDto(payment, readNextActionUrl(payment));
        }

        var provider = providerRegistry.getProvider(providerCode);

        var payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setProviderCode(providerCode);
        payment.setAmount(order.totalAmount());
        payment.setCurrency(order.totalCurrency());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>(Map.of(METADATA_INITIATED_BY_USER_ID, userId)));

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            return handleConcurrentPaymentInitiation(order.id(), providerCode, idempotencyKey, userId, ex);
        }

        var result = provider.createPayment(new PaymentRequest(
            request.orderId().toString(),
            Money.of(order.totalAmount(), order.totalCurrency()),
            request.returnUrl(),
            idempotencyKey,
            Map.of("paymentId", saved.getId().toString())
        ));

        saved.setProviderPaymentId(result.providerPaymentId());
        saved.setStatus(mapStatus(result.status()));
        saved.setFailureReason(result.failureReason());
        storeNextActionUrl(saved, result.nextActionUrl());
        paymentRepository.save(saved);
        log.info("Payment initiated: paymentId={}, orderId={}, userId={}, provider={}, status={}",
            saved.getId(),
            saved.getOrderId(),
            userId,
            saved.getProviderCode(),
            saved.getStatus());

        return paymentMapper.toDto(saved, result.nextActionUrl());
    }

    @Transactional
    public void handleWebhook(String providerCode, String payload, String signature, String timestampHeader) {
        providerCode = normalizeProviderCode(providerCode);
        var provider = providerRegistry.getProviderForWebhook(providerCode);
        if (!provider.verifyWebhook(payload, signature, timestampHeader)) {
            throw new BusinessException("INVALID_WEBHOOK", "Webhook signature verification failed");
        }
        validateWebhookFreshness(timestampHeader);

        var webhookPayload = parseWebhookPayload(payload);
        var payment = paymentRepository.findByProviderCodeAndProviderPaymentId(providerCode, webhookPayload.providerPaymentId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Payment",
                "providerPaymentId",
                webhookPayload.providerPaymentId()
            ));
        var newStatus = parsePaymentStatus(webhookPayload.status());

        if (!reserveWebhookEvent(providerCode, webhookPayload.eventId(), payment.getId())) {
            log.warn("Replay webhook ignored: paymentId={}, provider={}, eventId={}",
                payment.getId(),
                providerCode,
                webhookPayload.eventId());
            return;
        }

        updatePaymentStatus(payment, newStatus);
        log.info("Webhook processed: paymentId={}, provider={}, eventId={}, status={}",
            payment.getId(),
            providerCode,
            webhookPayload.eventId(),
            newStatus);
    }

    @Transactional
    public void updatePaymentStatus(Long paymentId, PaymentStatus newStatus) {
        var payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        updatePaymentStatus(payment, newStatus);
    }

    private void updatePaymentStatus(Payment payment, PaymentStatus newStatus) {
        var previousStatus = payment.getStatus();
        if (previousStatus == newStatus) {
            paymentRepository.save(payment);
            return;
        }
        previousStatus.validateTransition(newStatus);

        payment.setStatus(newStatus);
        var saved = paymentRepository.save(payment);
        log.info("Payment status updated: paymentId={}, from={}, to={}",
            saved.getId(),
            previousStatus,
            newStatus);

        if (newStatus == PaymentStatus.PAID && previousStatus != PaymentStatus.PAID) {
            rabbitTemplate.convertAndSend(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                "payment.completed",
                paymentMapper.toEvent(saved)
            );
        }
    }

    private PaymentStatus mapStatus(PaymentResult.PaymentResultStatus resultStatus) {
        return switch (resultStatus) {
            case PENDING -> PaymentStatus.PENDING;
            case REQUIRES_ACTION -> PaymentStatus.REQUIRES_ACTION;
            case AUTHORIZED -> PaymentStatus.AUTHORIZED;
            case PAID -> PaymentStatus.PAID;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    private void validateRequestedAmount(InitiatePaymentRequest request, OrderAccessView order) {
        if (request.amount().compareTo(order.totalAmount()) != 0
            || !Objects.equals(
            request.currency().toUpperCase(Locale.ROOT),
            order.totalCurrency().toUpperCase(Locale.ROOT)
        )) {
            throw new BusinessException("AMOUNT_MISMATCH", "Payment amount does not match order total");
        }
    }

    private Optional<Payment> findActivePayment(Long orderId, String providerCode) {
        return paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            orderId,
            providerCode,
            ACTIVE_PAYMENT_STATUSES
        );
    }

    private Optional<Payment> resolveConcurrentPayment(Long orderId, String providerCode, String idempotencyKey) {
        var existingByIdempotency = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingByIdempotency.isPresent()) {
            return existingByIdempotency;
        }
        return findActivePayment(orderId, providerCode);
    }

    private void validateIdempotencyOwnership(Payment payment, Long orderId, String providerCode) {
        if (!Objects.equals(payment.getOrderId(), orderId) || !Objects.equals(payment.getProviderCode(), providerCode)) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSE",
                "Idempotency key is already used for another order or provider"
            );
        }
    }

    private String normalizeProviderCode(String rawProviderCode) {
        if (rawProviderCode == null) {
            throw new BusinessException("INVALID_PROVIDER", "Provider code is required");
        }
        String normalized = rawProviderCode.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new BusinessException("INVALID_PROVIDER", "Provider code is required");
        }
        return normalized;
    }

    private String normalizeIdempotencyKey(String rawIdempotencyKey) {
        if (rawIdempotencyKey == null) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY", "Idempotency key is required");
        }
        String normalized = rawIdempotencyKey.trim();
        if (normalized.isBlank()) {
            throw new BusinessException("INVALID_IDEMPOTENCY_KEY", "Idempotency key is required");
        }
        return normalized;
    }

    private void storeNextActionUrl(Payment payment, String nextActionUrl) {
        if (nextActionUrl == null || nextActionUrl.isBlank()) {
            return;
        }
        var metadata = payment.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            payment.setMetadata(metadata);
        }
        metadata.put(METADATA_NEXT_ACTION_URL, nextActionUrl);
    }

    private String readNextActionUrl(Payment payment) {
        var metadata = payment.getMetadata();
        if (metadata == null) {
            return null;
        }
        Object value = metadata.get(METADATA_NEXT_ACTION_URL);
        if (value instanceof String nextActionUrl && !nextActionUrl.isBlank()) {
            return nextActionUrl;
        }
        return null;
    }

    private void validateWebhookFreshness(String timestampHeader) {
        Instant webhookTimestamp = parseWebhookTimestamp(timestampHeader);
        Instant now = Instant.now();
        if (webhookTimestamp.isAfter(now.plusSeconds(MAX_WEBHOOK_CLOCK_SKEW_SECONDS))
            || webhookTimestamp.isBefore(now.minusSeconds(webhookMaxAgeSeconds))) {
            throw new BusinessException("STALE_WEBHOOK", "Webhook timestamp is outside allowed replay window");
        }
    }

    private Instant parseWebhookTimestamp(String timestampHeader) {
        if (timestampHeader == null || timestampHeader.isBlank()) {
            throw new BusinessException("INVALID_WEBHOOK_TIMESTAMP", "Webhook timestamp header is required");
        }
        try {
            long rawTimestamp = Long.parseLong(timestampHeader.trim());
            if (rawTimestamp > 9_999_999_999L) {
                return Instant.ofEpochMilli(rawTimestamp);
            }
            return Instant.ofEpochSecond(rawTimestamp);
        } catch (RuntimeException ex) {
            throw new BusinessException("INVALID_WEBHOOK_TIMESTAMP", "Webhook timestamp header has invalid format");
        }
    }

    private WebhookPayload parseWebhookPayload(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String providerPaymentId = readRequiredText(root, "providerPaymentId");
            String status = readRequiredText(root, "status");
            String eventId = readOptionalText(root, "eventId", "event_id", "id");
            if (eventId == null || eventId.isBlank()) {
                throw new BusinessException("INVALID_WEBHOOK_PAYLOAD", "Missing event id");
            }
            return new WebhookPayload(providerPaymentId, status, eventId);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("INVALID_WEBHOOK_PAYLOAD", "Webhook payload is not valid JSON");
        }
    }

    private String readRequiredText(JsonNode root, String fieldName) {
        String value = readOptionalText(root, fieldName);
        if (value == null || value.isBlank()) {
            throw new BusinessException("INVALID_WEBHOOK_PAYLOAD", "Missing " + fieldName);
        }
        return value;
    }

    private String readOptionalText(JsonNode root, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode valueNode = root.get(fieldName);
            if (valueNode != null && valueNode.isTextual()) {
                return valueNode.asText();
            }
        }
        return null;
    }

    private PaymentStatus parsePaymentStatus(String rawStatus) {
        String normalized = rawStatus.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return PaymentStatus.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("UNSUPPORTED_PAYMENT_STATUS", "Unsupported payment status: " + rawStatus);
        }
    }

    private boolean reserveWebhookEvent(String providerCode, String eventId, Long paymentId) {
        var event = new PaymentWebhookEvent();
        event.setProviderCode(providerCode);
        event.setEventId(eventId);
        event.setPaymentId(paymentId);
        try {
            webhookEventRepository.saveAndFlush(event);
            return true;
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateWebhookEventViolation(ex)) {
                return false;
            }
            throw ex;
        }
    }

    private boolean isDuplicateWebhookEventViolation(DataIntegrityViolationException ex) {
        return hasConstraintMarker(ex, List.of(WEBHOOK_EVENT_UNIQUE_CONSTRAINT));
    }

    private PaymentDTO handleConcurrentPaymentInitiation(
        Long orderId,
        String providerCode,
        String idempotencyKey,
        Long userId,
        DataIntegrityViolationException ex
    ) {
        if (!hasConstraintMarker(ex, INITIATE_PAYMENT_CONCURRENCY_CONSTRAINT_MARKERS)) {
            throw ex;
        }
        var concurrentPayment = resolveConcurrentPayment(orderId, providerCode, idempotencyKey)
            .orElseThrow(() -> ex);
        validateIdempotencyOwnership(concurrentPayment, orderId, providerCode);
        log.info("Idempotent payment replay after concurrent insert: paymentId={}, orderId={}, provider={}, userId={}",
            concurrentPayment.getId(),
            orderId,
            providerCode,
            userId);
        return paymentMapper.toDto(concurrentPayment, readNextActionUrl(concurrentPayment));
    }

    private boolean hasConstraintMarker(DataIntegrityViolationException ex, List<String> markers) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolationException
                && matchesConstraintMarker(constraintViolationException.getConstraintName(), markers)) {
                return true;
            }
            if (current instanceof SQLException sqlException
                && matchesConstraintMarker(sqlException.getMessage(), markers)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean matchesConstraintMarker(String rawValue, List<String> markers) {
        if (rawValue == null) {
            return false;
        }
        String normalized = rawValue.toLowerCase(Locale.ROOT);
        return markers.stream().anyMatch(normalized::contains);
    }

    private record WebhookPayload(String providerPaymentId, String status, String eventId) {
    }
}
