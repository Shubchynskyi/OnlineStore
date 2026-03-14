package com.onlinestore.payments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlinestore.common.security.AuthenticatedUser;
import com.onlinestore.common.security.AuthenticatedUserResolver;
import com.onlinestore.payments.dto.ConfirmPaymentRequest;
import com.onlinestore.payments.dto.InitiatePaymentRequest;
import com.onlinestore.payments.dto.PaymentDTO;
import com.onlinestore.payments.dto.RefundPaymentRequest;
import com.onlinestore.payments.service.PaymentService;
import com.onlinestore.payments.webhook.PaymentWebhookController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class PaymentsApiControllersTest {

    @Mock
    private AuthenticatedUserResolver authenticatedUserResolver;

    @Mock
    private PaymentService paymentService;

    private Jwt jwt;
    private PaymentController paymentController;
    private AdminPaymentController adminPaymentController;
    private PaymentWebhookController paymentWebhookController;

    @BeforeEach
    void setUp() {
        jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .claim("sub", "kc-1")
            .build();
        paymentController = new PaymentController(authenticatedUserResolver, paymentService);
        adminPaymentController = new AdminPaymentController(paymentService);
        paymentWebhookController = new PaymentWebhookController(paymentService);
    }

    @Test
    void initiateShouldDelegateToPaymentService() {
        stubAuthenticatedUser();
        var request = mock(InitiatePaymentRequest.class);
        var response = mock(PaymentDTO.class);
        when(paymentService.initiatePayment(42L, request)).thenReturn(response);

        assertThat(paymentController.initiate(jwt, request)).isSameAs(response);

        verify(paymentService).initiatePayment(42L, request);
    }

    @Test
    void confirmShouldDelegateToPaymentService() {
        stubAuthenticatedUser();
        var request = mock(ConfirmPaymentRequest.class);
        var response = mock(PaymentDTO.class);
        when(paymentService.confirmPayment(42L, 15L, request)).thenReturn(response);

        assertThat(paymentController.confirm(jwt, 15L, request)).isSameAs(response);

        verify(paymentService).confirmPayment(42L, 15L, request);
    }

    @Test
    void refundShouldDelegateToPaymentService() {
        var request = mock(RefundPaymentRequest.class);
        var response = mock(PaymentDTO.class);
        when(paymentService.refundPayment(15L, request)).thenReturn(response);

        assertThat(adminPaymentController.refund(15L, request)).isSameAs(response);

        verify(paymentService).refundPayment(15L, request);
    }

    @Test
    void handleWebhookShouldDelegateToPaymentServiceAndReturnOk() {
        var response = paymentWebhookController.handleWebhook(
            "stripe",
            "{\"event\":\"payment.succeeded\"}",
            "signature",
            "1720000000"
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isNull();
        verify(paymentService).handleWebhook(
            "stripe",
            "{\"event\":\"payment.succeeded\"}",
            "signature",
            "1720000000"
        );
    }

    private void stubAuthenticatedUser() {
        when(authenticatedUserResolver.resolve(jwt)).thenReturn(new AuthenticatedUser(42L, "kc-1"));
    }
}
