package com.onlinestore.payments.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.event.OutboxService;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.payments.dto.ConfirmPaymentRequest;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.RefundPaymentRequest;
import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentMutation;
import com.onlinestore.payments.entity.PaymentMutationStatus;
import com.onlinestore.payments.entity.PaymentMutationType;
import com.onlinestore.payments.entity.PaymentStatus;
import com.onlinestore.payments.entity.PaymentWebhookEvent;
import com.onlinestore.payments.mapper.PaymentMapper;
import com.onlinestore.payments.provider.PaymentProvider;
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
import java.util.Objects;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentMutationRepository paymentMutationRepository;
    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;
    @Mock
    private OrderAccessGateway orderAccessGateway;
    @Mock
    private PaymentProviderRegistry providerRegistry;
    @Mock
    private OutboxService outboxService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentService paymentService;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            var callback = (TransactionCallback<?>) invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        paymentService = new PaymentService(
            paymentRepository,
            paymentMutationRepository,
            webhookEventRepository,
            orderAccessGateway,
            providerRegistry,
            outboxService,
            new PaymentMapper(),
            new ObjectMapper(),
            transactionTemplate
        );
    }

    @Test
    void initiatePaymentShouldRejectAmountMismatch() {
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("9.99"),
            "EUR",
            "https://example.com/return",
            "idem-100"
        );
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));

        var ex = assertThrows(BusinessException.class, () -> paymentService.initiatePayment(77L, request));

        assertEquals("AMOUNT_MISMATCH", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider(any());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void initiatePaymentShouldReturnExistingPaymentForSameIdempotencyKey() {
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-200"
        );
        var existingPayment = payment(42L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        existingPayment.setProviderPaymentId("paypal-payment-42");
        existingPayment.getMetadata().put("nextActionUrl", "https://example.com/pay/42");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentRepository.findByIdempotencyKey("idem-200")).thenReturn(Optional.of(existingPayment));

        var dto = paymentService.initiatePayment(77L, request);

        assertEquals(42L, dto.id());
        assertEquals("https://example.com/pay/42", dto.nextActionUrl());
        verify(providerRegistry, never()).getProvider("paypal");
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleWebhookShouldRejectInvalidSignature() {
        var provider = mock(PaymentProvider.class);
        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(false);

        assertThrows(
            BusinessException.class,
            () -> paymentService.handleWebhook(
                "paypal",
                "{\"providerPaymentId\":\"p-1\",\"status\":\"PAID\",\"eventId\":\"evt-1\"}",
                "bad",
                currentTimestamp()
            )
        );

        verify(paymentRepository, never()).findByProviderCodeAndProviderPaymentId(any(), any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void handleWebhookShouldPublishOnlyOnceForReplayEvent() {
        var provider = mock(PaymentProvider.class);
        var payment = payment(11L, 99L, "paypal", PaymentStatus.PENDING, "15.00", "EUR");
        payment.setProviderPaymentId("paypal-payment-11");

        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);
        when(paymentRepository.findByProviderCodeAndProviderPaymentId("paypal", "paypal-payment-11"))
            .thenReturn(Optional.of(payment));
        when(webhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
            .thenAnswer(invocation -> invocation.getArgument(0))
            .thenThrow(new DataIntegrityViolationException(
                "duplicate webhook event",
                new ConstraintViolationException(
                    "duplicate webhook event",
                    new SQLException("duplicate webhook event", "23505"),
                    "ux_payment_webhook_events_provider_event"
                )
            ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = "{\"providerPaymentId\":\"paypal-payment-11\",\"status\":\"PAID\",\"eventId\":\"evt-1\"}";
        paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp());
        paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp());

        verify(outboxService, times(1)).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payments.completed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
        verify(outboxService, times(1)).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payment.completed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void handleWebhookShouldUseWebhookProviderLookup() {
        var provider = mock(PaymentProvider.class);
        var payment = payment(21L, 88L, "paypal", PaymentStatus.PENDING, "25.00", "EUR");
        payment.setProviderPaymentId("paypal-payment-21");

        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);
        when(paymentRepository.findByProviderCodeAndProviderPaymentId("paypal", "paypal-payment-21"))
            .thenReturn(Optional.of(payment));
        when(webhookEventRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String payload = "{\"providerPaymentId\":\"paypal-payment-21\",\"status\":\"PAID\",\"eventId\":\"evt-99\"}";
        paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp());

        verify(providerRegistry, only()).getProviderForWebhook("paypal");
    }

    @Test
    void handleWebhookShouldRejectStaleTimestamp() {
        var provider = mock(PaymentProvider.class);
        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);

        String oldTimestamp = String.valueOf(Instant.now().minusSeconds(3600).getEpochSecond());
        String payload = "{\"providerPaymentId\":\"paypal-payment-22\",\"status\":\"PAID\",\"eventId\":\"evt-22\"}";

        assertThrows(BusinessException.class, () -> paymentService.handleWebhook("paypal", payload, "signature", oldTimestamp));
        verify(paymentRepository, never()).findByProviderCodeAndProviderPaymentId(any(), any());
    }

    @Test
    void initiatePaymentShouldMarkPaymentFailedWhenProviderThrows() {
        var provider = mock(PaymentProvider.class);
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-400"
        );
        var savedPayment = payment(400L, 10L, "paypal", PaymentStatus.PENDING, "19.99", "EUR");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentRepository.findByIdempotencyKey("idem-400")).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            eq(10L), eq("paypal"), any()
        )).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment source = invocation.getArgument(0);
            if (source.getId() == null) {
                source.setId(400L);
            }
            return source;
        });
        when(paymentRepository.findById(400L)).thenReturn(Optional.of(savedPayment));
        when(provider.createPayment(any())).thenThrow(new RuntimeException("PSP unavailable"));

        assertThrows(RuntimeException.class, () -> paymentService.initiatePayment(77L, request));

        assertEquals(PaymentStatus.FAILED, savedPayment.getStatus());
        assertEquals("PSP unavailable", savedPayment.getFailureReason());
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payments.failed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
    }

    @Test
    void initiatePaymentShouldCallProviderAfterPendingRecordCommit() {
        var provider = mock(PaymentProvider.class);
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-401"
        );
        var providerResult = new PaymentResult(
            "psp-payment-401",
            PaymentResult.PaymentResultStatus.REQUIRES_ACTION,
            "https://pay.example.com/401",
            null,
            null
        );

        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentRepository.findByIdempotencyKey("idem-401")).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            eq(10L), eq("paypal"), any()
        )).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment source = invocation.getArgument(0);
            Payment savedCopy = copyPayment(source);
            if (savedCopy.getId() == null) {
                savedCopy.setId(401L);
            }
            return savedCopy;
        });
        when(provider.createPayment(any())).thenReturn(providerResult);

        var dto = paymentService.initiatePayment(77L, request);

        assertEquals(PaymentStatus.REQUIRES_ACTION, dto.status());
        assertEquals("https://pay.example.com/401", dto.nextActionUrl());

        var inOrder = inOrder(paymentRepository, provider);
        inOrder.verify(paymentRepository).save(argThat(p -> p.getId() == null && p.getStatus() == PaymentStatus.PENDING));
        inOrder.verify(provider).createPayment(any());
        inOrder.verify(paymentRepository).save(argThat(p -> Objects.equals(p.getId(), 401L)));
    }

    @Test
    void updatePaymentStatusShouldRejectPaidToFailed() {
        var payment = payment(500L, 10L, "paypal", PaymentStatus.PAID, "20.00", "EUR");
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(payment));

        var ex = assertThrows(BusinessException.class,
            () -> paymentService.updatePaymentStatus(500L, PaymentStatus.FAILED));

        assertEquals("INVALID_PAYMENT_TRANSITION", ex.getErrorCode());
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    @Test
    void updatePaymentStatusShouldAllowPaidToRefundedAndPublishEvent() {
        var payment = payment(502L, 10L, "paypal", PaymentStatus.PAID, "20.00", "EUR");
        when(paymentRepository.findById(502L)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        paymentService.updatePaymentStatus(502L, PaymentStatus.REFUNDED);

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payments.refunded"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
    }

    @Test
    void confirmPaymentShouldRejectForeignOrderOwnership() {
        var payment = payment(610L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-610");

        when(paymentRepository.findById(610L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.empty());

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.confirmPayment(77L, 610L, new ConfirmPaymentRequest("confirm-610"))
        );

        assertEquals("ACCESS_DENIED", ex.getErrorCode());
        verify(paymentMutationRepository, never()).findByIdempotencyKey(any());
        verify(providerRegistry, never()).getProvider(any());
    }

    @Test
    void confirmPaymentShouldRejectWhenOrderTotalDiffers() {
        var payment = payment(610L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-610");

        when(paymentRepository.findById(610L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "29.99", "EUR")));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.confirmPayment(77L, 610L, new ConfirmPaymentRequest("confirm-610"))
        );

        assertEquals("AMOUNT_MISMATCH", ex.getErrorCode());
        verify(paymentMutationRepository, never()).findByIdempotencyKey(any());
    }

    @Test
    void confirmPaymentShouldReturnExistingPaymentForCompletedMutation() {
        var payment = payment(611L, 10L, "paypal", PaymentStatus.PAID, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-611");
        var mutation = paymentMutation(6111L, 611L, PaymentMutationType.CONFIRM, "confirm-611", PaymentMutationStatus.COMPLETED);

        when(paymentRepository.findById(611L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("confirm-611")).thenReturn(Optional.of(mutation));

        var dto = paymentService.confirmPayment(77L, 611L, new ConfirmPaymentRequest("confirm-611"));

        assertEquals(611L, dto.id());
        assertEquals(PaymentStatus.PAID, dto.status());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void confirmPaymentShouldRejectIdempotencyReuseAcrossPayments() {
        var payment = payment(612L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-612");
        var mutation = paymentMutation(6121L, 999L, PaymentMutationType.CONFIRM, "confirm-612", PaymentMutationStatus.PENDING);

        when(paymentRepository.findById(612L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("confirm-612")).thenReturn(Optional.of(mutation));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.confirmPayment(77L, 612L, new ConfirmPaymentRequest("confirm-612"))
        );

        assertEquals("IDEMPOTENCY_KEY_REUSE", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void confirmPaymentShouldRejectAnotherPendingMutationWithDifferentKey() {
        var payment = payment(612L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-612");
        var pendingMutation = paymentMutation(
            6122L,
            612L,
            PaymentMutationType.CONFIRM,
            "confirm-612-original",
            PaymentMutationStatus.PENDING
        );

        when(paymentRepository.findById(612L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("confirm-612-new")).thenReturn(Optional.empty());
        when(paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(612L, PaymentMutationStatus.PENDING))
            .thenReturn(Optional.of(pendingMutation));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.confirmPayment(77L, 612L, new ConfirmPaymentRequest("confirm-612-new"))
        );

        assertEquals("PAYMENT_MUTATION_IN_PROGRESS", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void confirmPaymentShouldConfirmPaymentAndPublishLifecycleEvents() {
        var provider = mock(PaymentProvider.class);
        var payment = payment(613L, 10L, "paypal", PaymentStatus.REQUIRES_ACTION, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-613");
        PaymentMutation[] mutationRef = new PaymentMutation[1];

        when(paymentRepository.findById(613L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findByIdAndUserId(10L, 77L))
            .thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("confirm-613")).thenReturn(Optional.empty());
        when(paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(613L, PaymentMutationStatus.PENDING))
            .thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentMutationRepository.save(any(PaymentMutation.class))).thenAnswer(invocation -> {
            var mutation = invocation.getArgument(0, PaymentMutation.class);
            if (mutation.getId() == null) {
                mutation.setId(6131L);
            }
            mutationRef[0] = mutation;
            return mutation;
        });
        when(paymentMutationRepository.findById(6131L)).thenAnswer(invocation -> Optional.ofNullable(mutationRef[0]));
        when(provider.confirmPayment("paypal-payment-613", "confirm-613")).thenReturn(new PaymentResult(
            "paypal-payment-613",
            PaymentResult.PaymentResultStatus.PAID,
            null,
            null,
            null
        ));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = paymentService.confirmPayment(77L, 613L, new ConfirmPaymentRequest("confirm-613"));

        assertEquals(PaymentStatus.PAID, dto.status());
        assertNull(dto.nextActionUrl());
        assertNotNull(mutationRef[0]);
        assertEquals(PaymentMutationStatus.COMPLETED, mutationRef[0].getStatus());
        assertEquals("paypal-payment-613", mutationRef[0].getProviderReference());
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payments.completed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payment.completed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
    }

    @Test
    void refundPaymentShouldExposeServiceLevelAuthorizationAnnotation() throws NoSuchMethodException {
        var method = PaymentService.class.getMethod("refundPayment", Long.class, RefundPaymentRequest.class);
        var annotation = method.getAnnotation(PreAuthorize.class);

        assertNotNull(annotation);
        assertEquals("hasAnyRole('ADMIN', 'MANAGER')", annotation.value());
    }

    @Test
    void refundPaymentShouldRejectAmountMismatch() {
        var payment = payment(620L, 10L, "paypal", PaymentStatus.PAID, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-620");

        when(paymentRepository.findById(620L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findById(10L)).thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.refundPayment(
                620L,
                new RefundPaymentRequest(new BigDecimal("9.99"), "EUR", "refund-620")
            )
        );

        assertEquals("AMOUNT_MISMATCH", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void refundPaymentShouldReturnExistingPaymentForCompletedMutation() {
        var payment = payment(621L, 10L, "paypal", PaymentStatus.REFUNDED, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-621");
        var mutation = paymentMutation(6211L, 621L, PaymentMutationType.REFUND, "refund-621", PaymentMutationStatus.COMPLETED);

        when(paymentRepository.findById(621L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findById(10L)).thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("refund-621")).thenReturn(Optional.of(mutation));

        var dto = paymentService.refundPayment(
            621L,
            new RefundPaymentRequest(new BigDecimal("19.99"), "EUR", "refund-621")
        );

        assertEquals(PaymentStatus.REFUNDED, dto.status());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void refundPaymentShouldRejectAnotherPendingMutationWithDifferentKey() {
        var payment = payment(622L, 10L, "paypal", PaymentStatus.PAID, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-622");
        var pendingMutation = paymentMutation(
            6222L,
            622L,
            PaymentMutationType.REFUND,
            "refund-622-original",
            PaymentMutationStatus.PENDING
        );

        when(paymentRepository.findById(622L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findById(10L)).thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("refund-622-new")).thenReturn(Optional.empty());
        when(paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(622L, PaymentMutationStatus.PENDING))
            .thenReturn(Optional.of(pendingMutation));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.refundPayment(
                622L,
                new RefundPaymentRequest(new BigDecimal("19.99"), "EUR", "refund-622-new")
            )
        );

        assertEquals("PAYMENT_MUTATION_IN_PROGRESS", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void refundPaymentShouldRejectIdempotencyReuseAcrossPayments() {
        var payment = payment(622L, 10L, "paypal", PaymentStatus.PAID, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-622");
        var mutation = paymentMutation(6221L, 999L, PaymentMutationType.REFUND, "refund-622", PaymentMutationStatus.PENDING);

        when(paymentRepository.findById(622L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findById(10L)).thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("refund-622")).thenReturn(Optional.of(mutation));

        var ex = assertThrows(
            BusinessException.class,
            () -> paymentService.refundPayment(
                622L,
                new RefundPaymentRequest(new BigDecimal("19.99"), "EUR", "refund-622")
            )
        );

        assertEquals("IDEMPOTENCY_KEY_REUSE", ex.getErrorCode());
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void refundPaymentShouldRefundPaymentAndPublishRefundEvent() {
        var provider = mock(PaymentProvider.class);
        var payment = payment(623L, 10L, "paypal", PaymentStatus.PAID, "19.99", "EUR");
        payment.setProviderPaymentId("paypal-payment-623");
        PaymentMutation[] mutationRef = new PaymentMutation[1];

        when(paymentRepository.findById(623L)).thenReturn(Optional.of(payment));
        when(orderAccessGateway.findById(10L)).thenReturn(Optional.of(order(10L, 77L, "19.99", "EUR")));
        when(paymentMutationRepository.findByIdempotencyKey("refund-623")).thenReturn(Optional.empty());
        when(paymentMutationRepository.findFirstByPaymentIdAndStatusOrderByCreatedAtDesc(623L, PaymentMutationStatus.PENDING))
            .thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentMutationRepository.save(any(PaymentMutation.class))).thenAnswer(invocation -> {
            var mutation = invocation.getArgument(0, PaymentMutation.class);
            if (mutation.getId() == null) {
                mutation.setId(6231L);
            }
            mutationRef[0] = mutation;
            return mutation;
        });
        when(paymentMutationRepository.findById(6231L)).thenAnswer(invocation -> Optional.ofNullable(mutationRef[0]));
        when(provider.refund(eq("paypal-payment-623"), any(), eq("refund-623")))
            .thenReturn(new RefundResult("refund-psp-623", true, null));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = paymentService.refundPayment(
            623L,
            new RefundPaymentRequest(new BigDecimal("19.99"), "EUR", "refund-623")
        );

        assertEquals(PaymentStatus.REFUNDED, dto.status());
        assertNotNull(mutationRef[0]);
        assertEquals(PaymentMutationStatus.COMPLETED, mutationRef[0].getStatus());
        assertEquals("refund-psp-623", mutationRef[0].getProviderReference());
        verify(outboxService).queueEvent(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payments.refunded"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
    }

    private String currentTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }

    private OrderAccessView order(Long orderId, Long userId, String totalAmount, String currency) {
        return new OrderAccessView(orderId, userId, new BigDecimal(totalAmount), currency);
    }

    private Payment payment(
        Long paymentId,
        Long orderId,
        String providerCode,
        PaymentStatus status,
        String amount,
        String currency
    ) {
        var payment = new Payment();
        payment.setId(paymentId);
        payment.setOrderId(orderId);
        payment.setProviderCode(providerCode);
        payment.setStatus(status);
        payment.setAmount(new BigDecimal(amount));
        payment.setCurrency(currency);
        payment.setIdempotencyKey("payment-" + paymentId);
        payment.setMetadata(new HashMap<>());
        return payment;
    }

    private PaymentMutation paymentMutation(
        Long mutationId,
        Long paymentId,
        PaymentMutationType mutationType,
        String idempotencyKey,
        PaymentMutationStatus status
    ) {
        var mutation = new PaymentMutation();
        mutation.setId(mutationId);
        mutation.setPaymentId(paymentId);
        mutation.setMutationType(mutationType);
        mutation.setIdempotencyKey(idempotencyKey);
        mutation.setStatus(status);
        mutation.setAmount(new BigDecimal("19.99"));
        mutation.setCurrency("EUR");
        return mutation;
    }

    private Payment copyPayment(Payment source) {
        var copy = new Payment();
        copy.setId(source.getId());
        copy.setOrderId(source.getOrderId());
        copy.setProviderCode(source.getProviderCode());
        copy.setProviderPaymentId(source.getProviderPaymentId());
        copy.setStatus(source.getStatus());
        copy.setAmount(source.getAmount());
        copy.setCurrency(source.getCurrency());
        copy.setIdempotencyKey(source.getIdempotencyKey());
        copy.setFailureReason(source.getFailureReason());
        copy.setMetadata(source.getMetadata() == null ? null : new HashMap<>(source.getMetadata()));
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setVersion(source.getVersion());
        return copy;
    }
}
