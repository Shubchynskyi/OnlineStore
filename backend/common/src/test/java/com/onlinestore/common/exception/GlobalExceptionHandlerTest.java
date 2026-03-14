package com.onlinestore.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlinestore.common.dto.ApiError;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFoundShouldReturn404ApiError() {
        var request = request("/api/v1/products/15");
        var exception = new ResourceNotFoundException("Product", "id", 15L);

        var response = handler.handleNotFound(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody())
            .extracting(ApiError::error, ApiError::message, ApiError::path)
            .containsExactly("Not Found", "Product not found with id: 15", "/api/v1/products/15");
    }

    @Test
    void handleBusinessShouldReturn422ApiError() {
        var request = request("/api/v1/orders");
        var exception = new BusinessException("PAYMENT_FAILED", "Payment was declined");

        var response = handler.handleBusiness(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody())
            .extracting(ApiError::error, ApiError::message, ApiError::path)
            .containsExactly("PAYMENT_FAILED", "Payment was declined", "/api/v1/orders");
    }

    @Test
    void handleValidationShouldReturnFieldErrors() throws Exception {
        var request = request("/api/v1/users/me");
        var bindingResult = new BeanPropertyBindingResult(new ValidationPayload(""), "payload");
        bindingResult.addError(new FieldError("payload", "name", "", false, null, null, "must not be blank"));
        var method = GlobalExceptionHandlerTest.class.getDeclaredMethod("validationEndpoint", ValidationPayload.class);
        var parameter = new MethodParameter(method, 0);
        var exception = new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody())
            .extracting(ApiError::error, ApiError::message, ApiError::path)
            .containsExactly("Validation Failed", "Invalid request body", "/api/v1/users/me");
        assertThat(response.getBody().fieldErrors())
            .containsExactly(new ApiError.FieldError("name", "must not be blank"));
    }

    @Test
    void businessExceptionShouldExposeErrorCode() {
        var exception = new BusinessException("INVALID_TOKEN", "Token is invalid");

        assertThat(exception.getErrorCode()).isEqualTo("INVALID_TOKEN");
        assertThat(exception).hasMessage("Token is invalid");
    }

    @Test
    void resourceNotFoundExceptionShouldFormatMessage() {
        assertThat(new ResourceNotFoundException("Order", "id", 11L))
            .hasMessage("Order not found with id: 11");
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest();
        request.setRequestURI(path);
        return request;
    }

    @SuppressWarnings("unused")
    private void validationEndpoint(ValidationPayload payload) {
    }

    private record ValidationPayload(@NotBlank String name) {
    }
}
