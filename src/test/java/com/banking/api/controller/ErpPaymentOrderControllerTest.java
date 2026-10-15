package com.banking.api.controller;

import com.banking.api.exception.GlobalExceptionHandler;
import com.banking.api.service.ErpPaymentOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;

class ErpPaymentOrderControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ErpPaymentOrderService service = mock(ErpPaymentOrderService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ErpPaymentOrderController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/erp/payment-orders")
                        .contentType("application/json")
                        .content("""
                                {
                                  "sourceAccountId": 42,
                                  "erpPaymentReference": "PAY-1001",
                                  "invoiceReference": "INV-1001",
                                  "beneficiaryName": "Northwind Supplies",
                                  "beneficiaryAccount": "SIM-001-9284",
                                  "amount": 1250.00,
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Required request header 'Idempotency-Key' is missing"));
    }

    @Test
    void malformedJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/erp/payment-orders")
                        .header("Idempotency-Key", "key-1001")
                        .contentType("application/json")
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void nonNumericPaymentOrderIdReturnsBadRequest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/erp/payment-orders/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/v1/erp/payment-orders")
                        .header("Idempotency-Key", "key-1001")
                        .contentType("text/plain")
                        .content("payment order"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
    }

    @Test
    void unsupportedHttpMethodReturnsMethodNotAllowed() throws Exception {
        mockMvc.perform(put("/api/v1/erp/payment-orders/81"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Allow", org.hamcrest.Matchers.containsString("GET")))
                .andExpect(jsonPath("$.status").value(405));
    }
}
