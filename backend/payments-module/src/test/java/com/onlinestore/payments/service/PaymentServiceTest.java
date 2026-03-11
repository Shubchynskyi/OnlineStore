package com.onlinestore.payments.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.onlinestore.common.config.RabbitMQConfig;
import com.onlinestore.common.exception.BusinessException;
import com.onlinestore.common.port.orders.OrderAccessGateway;
import com.onlinestore.common.port.orders.OrderAccessView;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.entity.Payment;
import com.onlinestore.payments.entity.PaymentWebhookEvent;
import com.onlinestore.payments.entity.PaymentStatus;
import com.onlinestore.payments.mapper.PaymentMapper;
import com.onlinestore.payments.provider.PaymentProvider;
import com.onlinestore.payments.registry.PaymentProviderRegistry;
import com.onlinestore.payments.repository.PaymentRepository;
import com.onlinestore.payments.repository.PaymentWebhookEventRepository;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private PaymentWebhookEventRepository webhookEventRepository;
    @Mock
    private OrderAccessGateway orderAccessGateway;
    @Mock
    private PaymentProviderRegistry providerRegistry;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(
            paymentRepository,
            webhookEventRepository,
            orderAccessGateway,
            providerRegistry,
            rabbitTemplate,
            new PaymentMapper(),
            new ObjectMapper()
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
        var order = new OrderAccessView(10L, 77L, new BigDecimal("19.99"), "EUR");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.of(order));

        assertThrows(BusinessException.class, () -> paymentService.initiatePayment(77L, request));

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
        var order = new OrderAccessView(10L, 77L, new BigDecimal("19.99"), "EUR");

        var existingPayment = new Payment();
        existingPayment.setId(42L);
        existingPayment.setOrderId(10L);
        existingPayment.setProviderCode("paypal");
        existingPayment.setProviderPaymentId("paypal-payment-42");
        existingPayment.setStatus(PaymentStatus.REQUIRES_ACTION);
        existingPayment.setAmount(new BigDecimal("19.99"));
        existingPayment.setCurrency("EUR");
        existingPayment.setMetadata(new HashMap<>());
        existingPayment.getMetadata().put("nextActionUrl", "https://example.com/pay/42");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.of(order));
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
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void handleWebhookShouldRejectUnknownStatus() {
        var provider = mock(PaymentProvider.class);
        var payment = new Payment();
        payment.setId(55L);
        payment.setProviderPaymentId("paypal-payment-55");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>());

        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);
        when(paymentRepository.findByProviderCodeAndProviderPaymentId("paypal", "paypal-payment-55"))
            .thenReturn(Optional.of(payment));

        String payload = "{\"providerPaymentId\":\"paypal-payment-55\",\"status\":\"UNKNOWN\",\"eventId\":\"evt-1\"}";
        assertThrows(BusinessException.class, () -> paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp()));

        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void handleWebhookShouldPublishOnlyOnceForReplayEvent() {
        var provider = mock(PaymentProvider.class);
        var payment = new Payment();
        payment.setId(11L);
        payment.setOrderId(99L);
        payment.setAmount(new BigDecimal("15.00"));
        payment.setCurrency("EUR");
        payment.setProviderPaymentId("paypal-payment-11");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>());

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

        verify(rabbitTemplate, times(1)).convertAndSend(
            eq(RabbitMQConfig.PAYMENT_EXCHANGE),
            eq("payment.completed"),
            org.mockito.ArgumentMatchers.<Object>any()
        );
        verify(providerRegistry, never()).getProvider("paypal");
    }

    @Test
    void handleWebhookShouldUseWebhookProviderLookup() {
        var provider = mock(PaymentProvider.class);
        var payment = new Payment();
        payment.setId(21L);
        payment.setOrderId(88L);
        payment.setAmount(new BigDecimal("25.00"));
        payment.setCurrency("EUR");
        payment.setProviderPaymentId("paypal-payment-21");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>());

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
    void initiatePaymentShouldReturnConcurrentPaymentAfterUniqueConstraintRace() {
        var provider = mock(PaymentProvider.class);
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-300"
        );
        var order = new OrderAccessView(10L, 77L, new BigDecimal("19.99"), "EUR");

        var concurrentPayment = new Payment();
        concurrentPayment.setId(300L);
        concurrentPayment.setOrderId(10L);
        concurrentPayment.setProviderCode("paypal");
        concurrentPayment.setStatus(PaymentStatus.PENDING);
        concurrentPayment.setAmount(new BigDecimal("19.99"));
        concurrentPayment.setCurrency("EUR");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("idem-300"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(concurrentPayment));
        when(paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            eq(10L),
            eq("paypal"),
            any()
        )).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException(
            "duplicate idempotency key",
            new ConstraintViolationException(
                "duplicate idempotency key",
                new SQLException("duplicate key value violates unique constraint payments_idempotency_key_key", "23505"),
                "payments_idempotency_key_key"
            )
        ));

        var dto = paymentService.initiatePayment(77L, request);

        assertEquals(300L, dto.id());
        verify(provider, never()).createPayment(any());
    }

    @Test
    void initiatePaymentShouldRejectConcurrentPaymentWhenOwnershipMismatch() {
        var provider = mock(PaymentProvider.class);
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-301"
        );
        var order = new OrderAccessView(10L, 77L, new BigDecimal("19.99"), "EUR");

        var foreignPayment = new Payment();
        foreignPayment.setId(301L);
        foreignPayment.setOrderId(999L);
        foreignPayment.setProviderCode("paypal");
        foreignPayment.setStatus(PaymentStatus.PENDING);
        foreignPayment.setAmount(new BigDecimal("19.99"));
        foreignPayment.setCurrency("EUR");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("idem-301"))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(foreignPayment));
        when(paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            eq(10L),
            eq("paypal"),
            any()
        )).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException(
            "duplicate idempotency key",
            new ConstraintViolationException(
                "duplicate idempotency key",
                new SQLException("duplicate key value violates unique constraint payments_idempotency_key_key", "23505"),
                "payments_idempotency_key_key"
            )
        ));

        assertThrows(BusinessException.class, () -> paymentService.initiatePayment(77L, request));
        verify(provider, never()).createPayment(any());
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
    void handleWebhookShouldPropagateNonDuplicateIntegrityViolation() {
        var provider = mock(PaymentProvider.class);
        var payment = new Payment();
        payment.setId(77L);
        payment.setOrderId(100L);
        payment.setAmount(new BigDecimal("30.00"));
        payment.setCurrency("EUR");
        payment.setProviderPaymentId("paypal-payment-77");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>());

        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);
        when(paymentRepository.findByProviderCodeAndProviderPaymentId("paypal", "paypal-payment-77"))
            .thenReturn(Optional.of(payment));
        when(webhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
            .thenThrow(new DataIntegrityViolationException("value too long", new SQLException("value too long", "22001")));

        String payload = "{\"providerPaymentId\":\"paypal-payment-77\",\"status\":\"PAID\",\"eventId\":\"evt-77\"}";

        assertThrows(
            DataIntegrityViolationException.class,
            () -> paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp())
        );
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void handleWebhookShouldPropagateUnexpectedUniqueConstraintViolation() {
        var provider = mock(PaymentProvider.class);
        var payment = new Payment();
        payment.setId(78L);
        payment.setOrderId(101L);
        payment.setAmount(new BigDecimal("35.00"));
        payment.setCurrency("EUR");
        payment.setProviderPaymentId("paypal-payment-78");
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMetadata(new HashMap<>());

        when(providerRegistry.getProviderForWebhook("paypal")).thenReturn(provider);
        when(provider.verifyWebhook(any(), any(), any())).thenReturn(true);
        when(paymentRepository.findByProviderCodeAndProviderPaymentId("paypal", "paypal-payment-78"))
            .thenReturn(Optional.of(payment));
        when(webhookEventRepository.saveAndFlush(any(PaymentWebhookEvent.class)))
            .thenThrow(new DataIntegrityViolationException(
                "unexpected unique violation",
                new ConstraintViolationException(
                    "unexpected unique violation",
                    new SQLException("duplicate key value violates unique constraint orders_pkey", "23505"),
                    "orders_pkey"
                )
            ));

        String payload = "{\"providerPaymentId\":\"paypal-payment-78\",\"status\":\"PAID\",\"eventId\":\"evt-78\"}";

        assertThrows(
            DataIntegrityViolationException.class,
            () -> paymentService.handleWebhook("paypal", payload, "signature", currentTimestamp())
        );
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void initiatePaymentShouldPropagateUnexpectedIntegrityViolationOnInitialSave() {
        var provider = mock(PaymentProvider.class);
        var request = new InitiatePaymentRequest(
            10L,
            "paypal",
            new BigDecimal("19.99"),
            "EUR",
            "https://example.com/return",
            "idem-302"
        );
        var order = new OrderAccessView(10L, 77L, new BigDecimal("19.99"), "EUR");

        when(orderAccessGateway.findByIdAndUserId(10L, 77L)).thenReturn(Optional.of(order));
        when(paymentRepository.findByIdempotencyKey("idem-302")).thenReturn(Optional.empty());
        when(paymentRepository.findFirstByOrderIdAndProviderCodeAndStatusInOrderByCreatedAtDesc(
            eq(10L),
            eq("paypal"),
            any()
        )).thenReturn(Optional.empty());
        when(providerRegistry.getProvider("paypal")).thenReturn(provider);
        when(paymentRepository.save(any(Payment.class))).thenThrow(new DataIntegrityViolationException(
            "foreign key violation",
            new ConstraintViolationException(
                "foreign key violation",
                new SQLException("insert or update on table payments violates foreign key constraint payments_order_id_fkey", "23503"),
                "payments_order_id_fkey"
            )
        ));

        assertThrows(DataIntegrityViolationException.class, () -> paymentService.initiatePayment(77L, request));
        verify(provider, never()).createPayment(any());
    }

    private String currentTimestamp() {
        return String.valueOf(Instant.now().getEpochSecond());
    }
}
