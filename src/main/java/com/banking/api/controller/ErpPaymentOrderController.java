package com.banking.api.controller;

import com.banking.api.dto.request.CreatePaymentOrderRequest;
import com.banking.api.dto.response.PaymentOrderResponse;
import com.banking.api.service.ErpPaymentOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/erp/payment-orders")
@RequiredArgsConstructor
public class ErpPaymentOrderController {

    private final ErpPaymentOrderService erpPaymentOrderService;

    @PostMapping
    public ResponseEntity<PaymentOrderResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(erpPaymentOrderService.create(request, idempotencyKey));
    }

    @GetMapping("/{paymentOrderId}")
    public ResponseEntity<PaymentOrderResponse> get(@PathVariable Long paymentOrderId) {
        return ResponseEntity.ok(erpPaymentOrderService.get(paymentOrderId));
    }
}
