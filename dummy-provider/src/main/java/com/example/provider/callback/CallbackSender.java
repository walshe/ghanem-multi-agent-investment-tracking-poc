package com.example.provider.callback;

import com.example.provider.ProviderProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Responsibility: deliver callbacks to the backend: prepare them, POST them, and retry the
 * ones that don't get a 2xx.
 *
 * <ul>
 *   <li>{@link #prepare}: serialise a {@link PaymentEvent} once and sign exactly those bytes
 *       (signing itself is delegated to {@link Signer}).</li>
 *   <li>{@link #sendInOrder}: send in the background, in order, with retries
 *       ({@code maxRetries} times, {@code retryInterval} apart, byte-identical each time).</li>
 *   <li>{@link #sendOnce}: one POST, returning the backend's HTTP status.</li>
 * </ul>
 *
 * <p>Does not decide <em>which</em> callbacks to send or apply chaos rules; callers do that.
 */
@Component
public class CallbackSender {

    public static final String SIGNATURE_HEADER = "X-Signature";
    /** Reported as backendStatus when the backend could not be reached at all. */
    public static final int NO_RESPONSE = 0;

    private static final Logger log = LoggerFactory.getLogger(CallbackSender.class);

    private final ObjectMapper objectMapper;
    private final Signer signer;
    private final ProviderProperties properties;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService callbackExecutor;
    private final HttpClient httpClient;

    public CallbackSender(ObjectMapper objectMapper, Signer signer, ProviderProperties properties,
                          ScheduledExecutorService providerScheduler, ExecutorService callbackExecutor) {
        this.objectMapper = objectMapper;
        this.signer = signer;
        this.properties = properties;
        this.scheduler = providerScheduler;
        this.callbackExecutor = callbackExecutor;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.callbackTimeout())
                .executor(callbackExecutor)
                .build();
    }

    /** Serialise once, then sign exactly those bytes. */
    public SignedCallback prepare(String callbackUrl, PaymentEvent event, boolean validSignature) {
        byte[] body = objectMapper.writeValueAsBytes(event);
        String signature = validSignature ? signer.sign(body) : signer.signWithWrongSecret(body);
        return new SignedCallback(URI.create(callbackUrl), event.eventId(), body, signature);
    }

    /**
     * Sends the callbacks in the given order, in the background. Each one that doesn't get a
     * 2xx is retried on its own, with the same body and signature. {@code stillWanted} is
     * checked before every attempt; when it returns false nothing more is sent.
     */
    public void sendInOrder(List<SignedCallback> callbacks, BooleanSupplier stillWanted) {
        if (callbacks.isEmpty()) {
            return;
        }
        callbackExecutor.execute(() -> {
            for (SignedCallback callback : callbacks) {
                attempt(callback, 0, stillWanted);
            }
        });
    }

    private void attempt(SignedCallback callback, int retriesDone, BooleanSupplier stillWanted) {
        if (!stillWanted.getAsBoolean()) {
            log.info("Not sending callback {}: payment no longer wants callbacks", callback.eventId());
            return;
        }
        int status = sendOnce(callback);
        if (is2xx(status)) {
            return;
        }
        if (retriesDone >= properties.maxRetries()) {
            log.warn("Giving up on callback {} after {} retries (last status {})",
                    callback.eventId(), retriesDone, status);
            return;
        }
        log.info("Callback {} got {}, retry {} of {} in {}", callback.eventId(), status,
                retriesDone + 1, properties.maxRetries(), properties.retryInterval());
        scheduler.schedule(
                () -> callbackExecutor.execute(() -> attempt(callback, retriesDone + 1, stillWanted)),
                properties.retryInterval().toMillis(), TimeUnit.MILLISECONDS);
    }

    /** One POST. Returns the backend's HTTP status, or {@link #NO_RESPONSE} if there was none. */
    public int sendOnce(SignedCallback callback) {
        HttpRequest request = HttpRequest.newBuilder(callback.url())
                .timeout(properties.callbackTimeout())
                .header("Content-Type", "application/json")
                .header(SIGNATURE_HEADER, callback.signature())
                .POST(HttpRequest.BodyPublishers.ofByteArray(callback.body()))
                .build();
        try {
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("Callback {} -> {}", callback.eventId(), response.statusCode());
            return response.statusCode();
        } catch (IOException e) {
            log.info("Callback {} to {} failed: {}", callback.eventId(), callback.url(), e.toString());
            return NO_RESPONSE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NO_RESPONSE;
        }
    }

    private static boolean is2xx(int status) {
        return status >= 200 && status < 300;
    }
}
