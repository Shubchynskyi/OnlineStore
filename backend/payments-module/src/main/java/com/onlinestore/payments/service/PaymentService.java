package com.onlinestore.payments.service;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.exception.ResourceNotFoundException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.common.util.Money;
import com.onlinestore.payments.dto.ConfirmPaymentRequest;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.dto.RefundPaymentRequest;
import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentMutation;
import com.onlinestore.payments.entity.PaymentMutationStatus;
import com.onlinestore.payments.entity.PaymentMutationType;
import com.onlinestore.payments.entity.PaymentStatus;
import com.onlinestore.payments.entity.PaymentWebhookEvent;
import com.onlinestore.payments.mapper.PaymentMapper;
import com.onlinestore.payments.provider.PaymentProvider;
import com.onlinestore.payments.provider.PaymentRequest;
import com.onlinestore.payments.provider.PaymentResult;
import com.onlinestore.payments.provider.RefundResult;
import com.onlinestore.payments.registry.PaymentProviderRegistry;
import com.onlinestore.payments.repository.PaymentMutationRepository;
import com.onlinestore.payments.repository.PaymentRepository;
import com.onlinestore.payments.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
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
    private static final List<String> PAYMENT_MUTATION_IDEMPOTENCY_CONSTRAINT_MARKERS = List.of(
        "payment_mutations_idempotency_key_key",
        "ux_payment_mutations_idempotency_key"
    );
    private static final List<String> PAYMENT_MUTATION_PENDING_CONSTRAINT_MARKERS = List.of(
        "ux_payment_mutations_one_pending_per_payment"
    );
    private static final String METADATA_INITIATED_BY_USER_ID = "initiatedByUserId";
    private static final String METADATA_NEXT_ACTION_URL = "nextActionUrl";
    private static final String LEGACY_PAYMENT_COMPLETED_EVENT = "payment.completed";
    private static final String PAYMENT_AUTHORIZED_EVENT = "payments.authorized";
    private static final String PAYMENT_COMPLETED_EVENT = "payments.completed";
    private static final String PAYMENT_FAILED_EVENT = "payments.failed";
    private static final String PAYMENT_REFUNDED_EVENT = "payments.refunded";
    private static final String WEBHOOK_EVENT_UNIQUE_CONSTRAINT = "ux_payment_webhook_events_provider_event";
    private static final long MAX_WEBHOOK_CLOCK_SKEW_SECONDS = 30;

    private final PaymentRepository paymentRepository;
    private final PaymentMutationRepository paymentMutationRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final OrderAccessGateway orderAccessGateway;
    private final PaymentProviderRegistry providerRegistry;
    private final OutboxService outboxService;
    private final PaymentMapper paymentMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Value("${onlinestore.payments.webhook.max-age-seconds:300}")
    private long webhookMaxAgeSeconds = 300;

    public PaymentService(
        PaymentRepository paymentRepository,
        PaymentMutationRepository paymentMutationRepository,
        PaymentWebhookEventRepository webhookEventRepository,
        OrderAccessGateway orderAccessGateway,
        PaymentProviderRegistry providerRegistry,
        OutboxService outboxService,
        PaymentMapper paymentMapper,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate
    ) {
        this.paymentRepository = paymentRepository;
        this.paymentMutationRepository = paymentMutationRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.orderAccessGateway = orderAccessGateway;
        this.providerRegistry = providerRegistry;
        this.outboxService = outboxService;
        this.paymentMapper = paymentMapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Initiates a payment using a three-phase durable flow to prevent holding a DB connection
     * during external PSP I/O and to eliminate the risk of a duplicate charge caused by a
     * DB rollback after a successful PSP response.
     *
     * Phase 1 (TX): Validate order, check idempotency, persist PENDING record — then commit.
     * Phase 2 (no TX): Call external PSP. On failure, mark the record FAILED (Phase 2b TX).
     * Phase 3 (TX): Apply PSP result to the committed record — then commit.
     */
    public PaymentDTO initiatePayment(Long userId, InitiatePaymentRequest request) {
        String providerCode = normalizeProviderCode(request.providerCode());
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        var initResult = transactionTemplate.execute(
            status -> preparePaymentRecord(userId, providerCode, idempotencyKey, request)
        );
        Objects.requireNonNull(initResult, "Payment initiation produced null result");
        if (initResult.isEarlyReturn()) {
            return initResult.dto();
        }

        Payment pending = initResult.payment();
        var provider = initResult.provider();

        PaymentResult providerResult;
        try {
            providerResult = provider.createPayment(new PaymentRequest(
                request.orderId().toString(),
                Money.of(pending.getAmount(), pending.getCurrency()),
                request.returnUrl(),
                idempotencyKey,
                Map.of("paymentId", pending.getId().toString())
            ));
        } catch (RuntimeException ex) {
            String failureReason = resolveFailureReason(ex.getMessage(), "Provider payment request failed");
            transactionTemplate.execute(status -> {
                markPaymentFailedById(pending.getId(), failureReason);
                return null;
            });
            throw ex;
        }

        Payment completed = transactionTemplate.execute(
            status -> applyProviderResult(pending, providerResult)
        );
        Objects.requireNonNull(completed, "Failed to apply provider result to payment");
        log.info("Payment initiated: paymentId={}, orderId={}, userId={}, provider={}, status={}",
            completed.getId(),
            completed.getOrderId(),
            userId,
            completed.getProviderCode(),
            completed.getStatus());

        return paymentMapper.toDto(completed, readNextActionUrl(completed));
    }

    public PaymentDTO confirmPayment(Long userId, Long paymentId, ConfirmPaymentRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        var mutationStart = transactionTemplate.execute(
            status -> prepareConfirmMutation(userId, paymentId, idempotencyKey)
        );
        Objects.requireNonNull(mutationStart, "Payment confirmation produced null result");
        if (mutationStart.isEarlyReturn()) {
            return mutationStart.dto();
        }

        Payment payment = mutationStart.payment();
        PaymentMutation mutation = mutationStart.mutation();
        PaymentProvider provider = mutationStart.provider();

        PaymentResult providerResult;
        try {
            providerResult = provider.confirmPayment(requireProviderPaymentId(payment), idempotencyKey);
        } catch (RuntimeException ex) {
            String failureReason = resolveFailureReason(ex.getMessage(), "Provider confirmation request failed");
            transactionTemplate.execute(status -> {
                markMutationFailedById(mutation.getId(), failureReason);
                return null;
            });
            throw ex;
        }

        Payment confirmed = transactionTemplate.execute(
            status -> applyConfirmMutation(mutation.getId(), providerResult)
        );
        Objects.requireNonNull(confirmed, "Failed to apply confirmation result to payment");
        log.info("Payment confirmed: paymentId={}, orderId={}, userId={}, provider={}, status={}",
            confirmed.getId(),
            confirmed.getOrderId(),
            userId,
            confirmed.getProviderCode(),
            confirmed.getStatus());

        return paymentMapper.toDto(confirmed, readNextActionUrl(confirmed));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    public PaymentDTO refundPayment(Long paymentId, RefundPaymentRequest request) {
        String idempotencyKey = normalizeIdempotencyKey(request.idempotencyKey());

        var mutationStart = transactionTemplate.execute(
            status -> prepareRefundMutation(paymentId, request, idempotencyKey)
        );
        Objects.requireNonNull(mutationStart, "Payment refund produced null result");
        if (mutationStart.isEarlyReturn()) {
            return mutationStart.dto();
        }

        Payment payment = mutationStart.payment();
        PaymentMutation mutation = mutationStart.mutation();
        PaymentProvider provider = mutationStart.provider();
        Money refundAmount = Money.of(mutation.getAmount(), mutation.getCurrency());

        RefundResult refundResult;
        try {
            refundResult = provider.refund(requireProviderPaymentId(payment), refundAmount, idempotencyKey);
        } catch (RuntimeException ex) {
            String failureReason = resolveFailureReason(ex.getMessage(), "Provider refund request failed");
            transactionTemplate.execute(status -> {
                markMutationFailedById(mutation.getId(), failureReason);
                return null;
            });
            throw ex;
        }

        RefundApplicationResult refundOutcome = transactionTemplate.execute(
            status -> applyRefundMutation(mutation.getId(), refundResult)
        );
        Objects.requireNonNull(refundOutcome, "Failed to apply refund result");
        if (!refundOutcome.successful()) {
            throw new BusinessException("REFUND_FAILED", refundOutcome.failureReason());
        }

        Payment refunded = refundOutcome.payment();
        log.info("Payment refunded: paymentId={}, orderId={}, provider={}, status={}",
            refunded.getId(),
            refunded.getOrderId(),
            refunded.getProviderCode(),
            refunded.getStatus());

        return paymentMapper.toDto(refunded, readNextActionUrl(refunded));
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
        var payment = findPayment(paymentId);
        updatePaymentStatus(payment, newStatus);
    }

    private InitiationResult preparePaymentRecord(
        Long userId,
        String providerCode,
        String idempotencyKey,
        InitiatePaymentRequest request
    ) {
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
            return InitiationResult.earlyReturn(paymentMapper.toDto(existingPayment, readNextActionUrl(existingPayment)));
        }

        var existingActivePayment = findActivePayment(order.id(), providerCode);
        if (existingActivePayment.isPresent()) {
            var payment = existingActivePayment.get();
            log.info("Idempotent payment replay by active payment: paymentId={}, orderId={}, provider={}, userId={}",
                payment.getId(),
                order.id(),
                providerCode,
                userId);
            return InitiationResult.earlyReturn(paymentMapper.toDto(payment, readNextActionUrl(payment)));
        }

        var provider = providerRegistry.getProvider(providerCode);

        var payment = new Payment();
        payment.setOrderId(request.orderId());
        payment.setProviderCode(providerCode);
        payment.setAmount(order.totalAmount());
        payment.setCurrency(normalizeCurrency(order.totalCurrency()));
        payment.setIdempotencyKey(idempotencyKey);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>(Map.of(METADATA_INITIATED_BY_USER_ID, userId)));

        Payment saved;
        try {
            saved = paymentRepository.save(payment);
        } catch (DataIntegrityViolationException ex) {
            PaymentDTO dto = handleConcurrentPaymentInitiation(order.id(), providerCode, idempotencyKey, userId, ex);
            return InitiationResult.earlyReturn(dto);
        }

        return InitiationResult.newPayment(saved, provider);
    }

    private MutationPreparationResult prepareConfirmMutation(Long userId, Long paymentId, String idempotencyKey) {
        var payment = findPayment(paymentId);
        var order = orderAccessGateway.findByIdAndUserId(payment.getOrderId(), userId)
            .orElseThrow(() -> new BusinessException(
                "ACCESS_DENIED",
                "Order is not available for current user"
            ));
        validatePaymentAgainstOrder(payment, order);

        MutationPreparationResult existingMutation = resolveExistingMutation(payment, PaymentMutationType.CONFIRM, idempotencyKey);
        if (existingMutation != null) {
            return existingMutation;
        }
        MutationPreparationResult pendingMutation = resolvePendingMutationConflict(
            payment,
            PaymentMutationType.CONFIRM,
            idempotencyKey
        );
        if (pendingMutation != null) {
            return pendingMutation;
        }

        validateConfirmable(payment);
        return persistMutationStart(
            payment,
            PaymentMutationType.CONFIRM,
            idempotencyKey,
            payment.getAmount(),
            payment.getCurrency()
        );
    }

    private MutationPreparationResult prepareRefundMutation(
        Long paymentId,
        RefundPaymentRequest request,
        String idempotencyKey
    ) {
        var payment = findPayment(paymentId);
        var order = orderAccessGateway.findById(payment.getOrderId())
            .orElseThrow(() -> new ResourceNotFoundException("Order", "id", payment.getOrderId()));
        validateFullRefundRequest(payment, order, request);

        MutationPreparationResult existingMutation = resolveExistingMutation(payment, PaymentMutationType.REFUND, idempotencyKey);
        if (existingMutation != null) {
            return existingMutation;
        }
        MutationPreparationResult pendingMutation = resolvePendingMutationConflict(
            payment,
            PaymentMutationType.REFUND,
            idempotencyKey
        );
        if (pendingMutation != null) {
            return pendingMutation;
        }

        validateRefundable(payment);
        return persistMutationStart(
            payment,
            PaymentMutationType.REFUND,
            idempotencyKey,
            request.amount(),
            normalizeCurrency(request.currency())
        );
    }

    private MutationPreparationResult resolveExistingMutation(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey
    ) {
        var existingMutation = paymentMutationRepository.findByIdempotencyKey(idempotencyKey);
        if (existingMutation.isEmpty()) {
            return null;
        }

        var mutation = existingMutation.get();
        validateMutationScope(mutation, payment.getId(), mutationType);

        if (mutation.getStatus() == PaymentMutationStatus.COMPLETED) {
            log.info("Idempotent {} replay: mutationId={}, paymentId={}",
                mutationType.name().toLowerCase(Locale.ROOT),
                mutation.getId(),
                payment.getId());
            return MutationPreparationResult.earlyReturn(paymentMapper.toDto(payment, readNextActionUrl(payment)));
        }
        if (mutation.getStatus() == PaymentMutationStatus.FAILED) {
            throw mutationFailedException(mutation);
        }

        log.info("Resuming pending {} mutation: mutationId={}, paymentId={}",
            mutationType.name().toLowerCase(Locale.ROOT),
            mutation.getId(),
            payment.getId());
        return MutationPreparationResult.start(payment, mutation, providerRegistry.getProvider(payment.getProviderCode()));
    }

    private MutationPreparationResult persistMutationStart(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey,
        BigDecimal amount,
        String currency
    ) {
        var provider = providerRegistry.getProvider(payment.getProviderCode());
        var mutation = newMutation(payment, mutationType, idempotencyKey, amount, currency);
        try {
            return MutationPreparationResult.start(payment, paymentMutationRepository.save(mutation), provider);
        } catch (DataIntegrityViolationException ex) {
            return handleConcurrentMutationStart(payment, mutationType, idempotencyKey, ex);
        }
    }

    private MutationPreparationResult resolvePendingMutationConflict(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey
    ) {
        var pendingMutation = paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
            payment.getId(),
            PaymentMutationStatus.PENDING
        );
        if (pendingMutation.isEmpty()) {
            return null;
        }

        return reuseOrRejectPendingMutation(payment, mutationType, idempotencyKey, pendingMutation.get(), "existing");
    }

    private MutationPreparationResult handleConcurrentMutationStart(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey,
        DataIntegrityViolationException ex
    ) {
        if (hasConstraintMarker(ex, PAYMENT_MUTATION_IDEMPOTENCY_CONSTRAINT_MARKERS)) {
            return resumeMutationByIdempotencyKey(payment, mutationType, idempotencyKey, ex);
        }
        if (hasConstraintMarker(ex, PAYMENT_MUTATION_PENDING_CONSTRAINT_MARKERS)) {
            var pendingMutation = paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(
                payment.getId(),
                PaymentMutationStatus.PENDING
            );
            if (pendingMutation.isPresent()) {
                return reuseOrRejectPendingMutation(
                    payment,
                    mutationType,
                    idempotencyKey,
                    pendingMutation.get(),
                    "concurrent insert"
                );
            }
        }
        throw ex;
    }

    private MutationPreparationResult resumeMutationByIdempotencyKey(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey,
        DataIntegrityViolationException ex
    ) {
        var concurrentMutation = paymentMutationRepository.findByIdempotencyKey(idempotencyKey)
            .orElseThrow(() -> ex);
        validateMutationScope(concurrentMutation, payment.getId(), mutationType);
        if (concurrentMutation.getStatus() == PaymentMutationStatus.FAILED) {
            throw mutationFailedException(concurrentMutation);
        }
        if (concurrentMutation.getStatus() == PaymentMutationStatus.COMPLETED) {
            return MutationPreparationResult.earlyReturn(paymentMapper.toDto(payment, readNextActionUrl(payment)));
        }

        log.info("Idempotent {} replay after concurrent insert: mutationId={}, paymentId={}",
            mutationType.name().toLowerCase(Locale.ROOT),
            concurrentMutation.getId(),
            payment.getId());
        return MutationPreparationResult.start(
            payment,
            concurrentMutation,
            providerRegistry.getProvider(payment.getProviderCode())
        );
    }

    private MutationPreparationResult reuseOrRejectPendingMutation(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey,
        PaymentMutation pendingMutation,
        String source
    ) {
        if (pendingMutation.getMutationType() != mutationType) {
            throw mutationInProgressException(pendingMutation);
        }
        if (!Objects.equals(pendingMutation.getIdempotencyKey(), idempotencyKey)) {
            throw mutationInProgressException(pendingMutation);
        }

        log.info("Resuming {} pending {} mutation: mutationId={}, paymentId={}",
            source,
            mutationType.name().toLowerCase(Locale.ROOT),
            pendingMutation.getId(),
            payment.getId());
        return MutationPreparationResult.start(
            payment,
            pendingMutation,
            providerRegistry.getProvider(payment.getProviderCode())
        );
    }

    private Payment applyProviderResult(Payment pending, PaymentResult result) {
        try {
            return persistPaymentState(
                pending,
                mapStatus(result.status()),
                result.providerPaymentId(),
                result.failureReason(),
                result.nextActionUrl()
            );
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.warn("Optimistic lock conflict applying provider result: paymentId={}, returning current state",
                pending.getId());
            return paymentRepository.findById(pending.getId())
                .orElseThrow(() -> ex);
        }
    }

    private Payment applyConfirmMutation(Long mutationId, PaymentResult result) {
        var mutation = paymentMutationRepository.findById(mutationId)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentMutation", "id", mutationId));
        var payment = findPayment(mutation.getPaymentId());

        Payment savedPayment;
        try {
            savedPayment = persistPaymentState(
                payment,
                mapStatus(result.status()),
                result.providerPaymentId(),
                result.failureReason(),
                result.nextActionUrl()
            );
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.warn("Optimistic lock conflict applying confirmation result: mutationId={}, paymentId={}",
                mutationId,
                payment.getId());
            savedPayment = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> ex);
        }

        mutation.setStatus(PaymentMutationStatus.COMPLETED);
        mutation.setProviderReference(firstNonBlank(result.providerPaymentId(), payment.getProviderPaymentId()));
        mutation.setFailureReason(result.failureReason());
        paymentMutationRepository.save(mutation);
        return savedPayment;
    }

    private RefundApplicationResult applyRefundMutation(Long mutationId, RefundResult result) {
        var mutation = paymentMutationRepository.findById(mutationId)
            .orElseThrow(() -> new ResourceNotFoundException("PaymentMutation", "id", mutationId));
        var payment = findPayment(mutation.getPaymentId());
        String failureReason = resolveFailureReason(result.failureReason(), "Provider refund request failed");

        if (!result.success()) {
            mutation.setStatus(PaymentMutationStatus.FAILED);
            mutation.setProviderReference(result.refundId());
            mutation.setFailureReason(failureReason);
            paymentMutationRepository.save(mutation);
            return new RefundApplicationResult(payment, false, failureReason);
        }

        Payment savedPayment;
        try {
            savedPayment = persistPaymentState(payment, PaymentStatus.REFUNDED, null, null, null);
        } catch (ObjectOptimisticLockingFailureException ex) {
            log.warn("Optimistic lock conflict applying refund result: mutationId={}, paymentId={}",
                mutationId,
                payment.getId());
            savedPayment = paymentRepository.findById(payment.getId())
                .orElseThrow(() -> ex);
        }

        mutation.setStatus(PaymentMutationStatus.COMPLETED);
        mutation.setProviderReference(result.refundId());
        mutation.setFailureReason(null);
        paymentMutationRepository.save(mutation);
        return new RefundApplicationResult(savedPayment, true, null);
    }

    private void markPaymentFailedById(Long paymentId, String failureReason) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.PENDING) {
                persistPaymentState(payment, PaymentStatus.FAILED, null, failureReason, null);
            }
        });
    }

    private void markMutationFailedById(Long mutationId, String failureReason) {
        paymentMutationRepository.findById(mutationId).ifPresent(mutation -> {
            if (mutation.getStatus() == PaymentMutationStatus.PENDING) {
                mutation.setStatus(PaymentMutationStatus.FAILED);
                mutation.setFailureReason(failureReason);
                paymentMutationRepository.save(mutation);
            }
        });
    }

    private void updatePaymentStatus(Payment payment, PaymentStatus newStatus) {
        String failureReason = newStatus == PaymentStatus.FAILED ? payment.getFailureReason() : null;
        persistPaymentState(payment, newStatus, null, failureReason, null);
    }

    private Payment persistPaymentState(
        Payment payment,
        PaymentStatus newStatus,
        String providerPaymentId,
        String failureReason,
        String nextActionUrl
    ) {
        PaymentStatus previousStatus = payment.getStatus();
        if (providerPaymentId != null && !providerPaymentId.isBlank()) {
            payment.setProviderPaymentId(providerPaymentId);
        }
        payment.setFailureReason(failureReason);
        updateNextActionUrl(payment, newStatus, nextActionUrl);

        if (previousStatus != newStatus) {
            previousStatus.validateTransition(newStatus);
            payment.setStatus(newStatus);
        }

        Payment saved = paymentRepository.save(payment);
        if (previousStatus != newStatus) {
            log.info("Payment status updated: paymentId={}, from={}, to={}",
                saved.getId(),
                previousStatus,
                newStatus);
            publishLifecycleEvents(saved, previousStatus, newStatus);
        }
        return saved;
    }

    private void publishLifecycleEvents(Payment payment, PaymentStatus previousStatus, PaymentStatus newStatus) {
        if (previousStatus == newStatus) {
            return;
        }

        String lifecycleRoutingKey = lifecycleRoutingKey(newStatus);
        if (lifecycleRoutingKey != null) {
            outboxService.queueEvent(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                lifecycleRoutingKey,
                paymentMapper.toStatusChangedEvent(payment, lifecycleRoutingKey)
            );
        }
        if (newStatus == PaymentStatus.PAID) {
            outboxService.queueEvent(
                RabbitMQConfig.PAYMENT_EXCHANGE,
                LEGACY_PAYMENT_COMPLETED_EVENT,
                paymentMapper.toCompletedEvent(payment)
            );
        }
    }

    private String lifecycleRoutingKey(PaymentStatus status) {
        return switch (status) {
            case AUTHORIZED -> PAYMENT_AUTHORIZED_EVENT;
            case PAID -> PAYMENT_COMPLETED_EVENT;
            case FAILED -> PAYMENT_FAILED_EVENT;
            case REFUNDED -> PAYMENT_REFUNDED_EVENT;
            default -> null;
        };
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
            || !sameCurrency(request.currency(), order.totalCurrency())) {
            throw new BusinessException("AMOUNT_MISMATCH", "Payment amount does not match order total");
        }
    }

    private void validatePaymentAgainstOrder(Payment payment, OrderAccessView order) {
        if (payment.getAmount().compareTo(order.totalAmount()) != 0
            || !sameCurrency(payment.getCurrency(), order.totalCurrency())) {
            throw new BusinessException("AMOUNT_MISMATCH", "Payment amount does not match order total");
        }
    }

    private void validateFullRefundRequest(Payment payment, OrderAccessView order, RefundPaymentRequest request) {
        String requestedCurrency = normalizeCurrency(request.currency());
        if (payment.getAmount().compareTo(request.amount()) != 0
            || order.totalAmount().compareTo(request.amount()) != 0
            || !sameCurrency(payment.getCurrency(), requestedCurrency)
            || !sameCurrency(order.totalCurrency(), requestedCurrency)) {
            throw new BusinessException("AMOUNT_MISMATCH", "Refund amount must match the original payment amount");
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

    private void validateMutationScope(PaymentMutation mutation, Long paymentId, PaymentMutationType mutationType) {
        if (!Objects.equals(mutation.getPaymentId(), paymentId) || mutation.getMutationType() != mutationType) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSE",
                "Idempotency key is already used for another payment operation"
            );
        }
    }

    private void validateConfirmable(Payment payment) {
        if (payment.getStatus() != PaymentStatus.REQUIRES_ACTION && payment.getStatus() != PaymentStatus.AUTHORIZED) {
            throw invalidMutationState(PaymentMutationType.CONFIRM, payment.getStatus());
        }
    }

    private void validateRefundable(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PAID) {
            throw invalidMutationState(PaymentMutationType.REFUND, payment.getStatus());
        }
    }

    private BusinessException invalidMutationState(PaymentMutationType mutationType, PaymentStatus status) {
        return new BusinessException(
            "INVALID_PAYMENT_STATE",
            "Payment status " + status + " does not allow " + mutationType.name().toLowerCase(Locale.ROOT)
        );
    }

    private BusinessException mutationInProgressException(PaymentMutation mutation) {
        return new BusinessException(
            "PAYMENT_MUTATION_IN_PROGRESS",
            "Payment already has a pending " + mutation.getMutationType().name().toLowerCase(Locale.ROOT) + " operation"
        );
    }

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
    }

    private String requireProviderPaymentId(Payment payment) {
        if (payment.getProviderPaymentId() == null || payment.getProviderPaymentId().isBlank()) {
            throw new BusinessException("PAYMENT_NOT_READY", "Payment has no provider reference yet");
        }
        return payment.getProviderPaymentId();
    }

    private PaymentMutation newMutation(
        Payment payment,
        PaymentMutationType mutationType,
        String idempotencyKey,
        BigDecimal amount,
        String currency
    ) {
        var mutation = new PaymentMutation();
        mutation.setPaymentId(payment.getId());
        mutation.setMutationType(mutationType);
        mutation.setStatus(PaymentMutationStatus.PENDING);
        mutation.setIdempotencyKey(idempotencyKey);
        mutation.setAmount(amount);
        mutation.setCurrency(normalizeCurrency(currency));
        return mutation;
    }

    private BusinessException mutationFailedException(PaymentMutation mutation) {
        String action = mutation.getMutationType().name().toLowerCase(Locale.ROOT);
        String failureReason = resolveFailureReason(mutation.getFailureReason(), "Previous mutation attempt failed");
        return new BusinessException("PAYMENT_MUTATION_FAILED", "Previous " + action + " request failed: " + failureReason);
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

    private String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null) {
            throw new BusinessException("INVALID_CURRENCY", "Currency is required");
        }
        String normalized = rawCurrency.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() != 3) {
            throw new BusinessException("INVALID_CURRENCY", "Currency must be a 3-letter code");
        }
        return normalized;
    }

    private boolean sameCurrency(String firstCurrency, String secondCurrency) {
        return Objects.equals(normalizeCurrency(firstCurrency), normalizeCurrency(secondCurrency));
    }

    private void updateNextActionUrl(Payment payment, PaymentStatus newStatus, String nextActionUrl) {
        var metadata = payment.getMetadata();
        if (metadata == null) {
            metadata = new HashMap<>();
            payment.setMetadata(metadata);
        }

        if (nextActionUrl != null && !nextActionUrl.isBlank()) {
            metadata.put(METADATA_NEXT_ACTION_URL, nextActionUrl);
            return;
        }
        if (newStatus != PaymentStatus.REQUIRES_ACTION) {
            metadata.remove(METADATA_NEXT_ACTION_URL);
        }
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

    private String firstNonBlank(String primaryValue, String fallbackValue) {
        if (primaryValue != null && !primaryValue.isBlank()) {
            return primaryValue;
        }
        return fallbackValue;
    }

    private String resolveFailureReason(String candidate, String fallback) {
        if (candidate == null || candidate.isBlank()) {
            return fallback;
        }
        return candidate;
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

    private record InitiationResult(PaymentDTO dto, Payment payment, PaymentProvider provider) {

        static InitiationResult earlyReturn(PaymentDTO dto) {
            return new InitiationResult(dto, null, null);
        }

        static InitiationResult newPayment(Payment payment, PaymentProvider provider) {
            return new InitiationResult(null, payment, provider);
        }

        boolean isEarlyReturn() {
            return dto != null;
        }
    }

    private record MutationPreparationResult(
        PaymentDTO dto,
        Payment payment,
        PaymentMutation mutation,
        PaymentProvider provider
    ) {

        static MutationPreparationResult earlyReturn(PaymentDTO dto) {
            return new MutationPreparationResult(dto, null, null, null);
        }

        static MutationPreparationResult start(Payment payment, PaymentMutation mutation, PaymentProvider provider) {
            return new MutationPreparationResult(null, payment, mutation, provider);
        }

        boolean isEarlyReturn() {
            return dto != null;
        }
    }

    private record RefundApplicationResult(Payment payment, boolean successful, String failureReason) {
    }

    private record WebhookPayload(String providerPaymentId, String status, String eventId) {
    }
}
