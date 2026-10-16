package com.example.provider.callback;

import com.example.provider.payment.Payment;
import com.example.provider.payment.PaymentService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * POST /admin/send-callback. Ignores chaos settings and payment status. Sends once per copy,
 * with no retries, and reports what the backend answered.
 */
@Service
public class ManualCallbackService {

    public record CopyResult(String eventId, int backendStatus) {
    }

    private final PaymentService paymentService;
    private final CallbackSender callbackSender;
    private final ExecutorService callbackExecutor;

    public ManualCallbackService(PaymentService paymentService, CallbackSender callbackSender,
                                 ExecutorService callbackExecutor) {
        this.paymentService = paymentService;
        this.callbackSender = callbackSender;
        this.callbackExecutor = callbackExecutor;
    }

    public List<CopyResult> send(String paymentId, CallbackStatus status, String eventId, Long amountMinor,
                                 boolean validSignature, int copies) {
        Payment payment = paymentService.find(paymentId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No payment with id " + paymentId));

        PaymentEvent event = new PaymentEvent(
                eventId != null ? eventId : UUID.randomUUID().toString(),
                payment.checkoutId(),
                payment.id(),
                status,
                amountMinor != null ? amountMinor : payment.amountMinor(),
                payment.currency(),
                PaymentService.now());
        // One body, one signature: every copy is byte-identical.
        SignedCallback callback = callbackSender.prepare(payment.callbackUrl(), event, validSignature);

        // Each copy gets its own thread, and all wait on one gate so they go out together.
        CountDownLatch gate = new CountDownLatch(1);
        List<CompletableFuture<Integer>> futures = new ArrayList<>(copies);
        for (int i = 0; i < copies; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    gate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return CallbackSender.NO_RESPONSE;
                }
                return callbackSender.sendOnce(callback);
            }, callbackExecutor));
        }
        gate.countDown();

        return futures.stream()
                .map(f -> new CopyResult(callback.eventId(), f.join()))
                .toList();
    }
}
