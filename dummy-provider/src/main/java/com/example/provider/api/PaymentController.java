package com.example.provider.api;

import com.example.provider.api.ApiModels.CreatePaymentRequest;
import com.example.provider.payment.PaymentService;
import com.example.provider.payment.PaymentService.CreateResult;
import com.example.provider.payment.PaymentView;
import jakarta.validation.Valid;
import java.net.URI;
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
@RequestMapping("/payments")
public class PaymentController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentView> create(@RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey,
                                              @Valid @RequestBody CreatePaymentRequest request) {
        CreateResult result = paymentService.create(idempotencyKey, request.checkoutId(), request.amountMinor(),
                request.currency(), request.callbackUrl());
        if (!result.created()) {
            return ResponseEntity.ok(result.payment());
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/payments/" + result.payment().paymentId()))
                .body(result.payment());
    }

    @GetMapping("/{paymentId}")
    public PaymentView get(@PathVariable String paymentId) {
        return paymentService.get(paymentId);
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentView cancel(@PathVariable String paymentId) {
        return paymentService.cancel(paymentId);
    }

    @PostMapping("/{paymentId}/refund")
    public PaymentView refund(@PathVariable String paymentId,
                              @RequestHeader(IDEMPOTENCY_KEY) String idempotencyKey) {
        return paymentService.refund(paymentId, idempotencyKey);
    }
}
