package com.example.provider;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Responsibility: shared setup and helpers for tests that run the real app over HTTP.
 *
 * <p>Starts the app on a random port with the test signing secret and shorter
 * PROCESSING-to-final and retry gaps so tests stay fast, gives each test a fresh
 * {@link FakeBackend}, resets chaos, and offers HTTP and waiting helpers. The contract timings
 * themselves are checked in {@link ContractTimingsTest}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "provider.signing-secret=" + ProviderTestBase.TEST_SECRET,
        "provider.final-after=300ms",
        "provider.retry-interval=200ms"
})
public abstract class ProviderTestBase {

    public static final String TEST_SECRET = "test-signing-secret";

    protected static final ObjectMapper JSON = JsonMapper.builder().build();
    protected static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    protected int port;

    protected FakeBackend backend;

    @BeforeEach
    void startBackend() throws Exception {
        backend = new FakeBackend(TEST_SECRET);
        setChaos("{\"modes\":[],\"delaySeconds\":0}");
    }

    @AfterEach
    void stopBackend() throws Exception {
        setChaos("{\"modes\":[],\"delaySeconds\":0}");
        backend.close();
    }

    protected record Response(int status, String body) {
        JsonNode json() {
            return JSON.readTree(body);
        }

        String paymentStatus() {
            return json().get("status").asString();
        }
    }

    protected Response send(String method, String path, String body, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            b.header("Content-Type", "application/json");
        }
        if (headers.length > 0) {
            b.headers(headers);
        }
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(r.statusCode(), r.body());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    protected void setChaos(String json) {
        Response r = send("PUT", "/admin/chaos", json);
        if (r.status() != 204) {
            throw new AssertionError("PUT /admin/chaos returned " + r.status() + ": " + r.body());
        }
    }

    protected String createBody(UUID checkoutId) {
        return """
                {"checkoutId":"%s","amountMinor":10050,"currency":"EUR","callbackUrl":"%s"}
                """.formatted(checkoutId, backend.callbackUrl());
    }

    protected Response createPayment(String idempotencyKey, UUID checkoutId) {
        return send("POST", "/payments", createBody(checkoutId), "Idempotency-Key", idempotencyKey);
    }

    /** Creates a payment with a fresh checkout id as the key, and returns its paymentId. */
    protected String newPayment() {
        UUID checkoutId = UUID.randomUUID();
        Response r = createPayment(checkoutId.toString(), checkoutId);
        if (r.status() != 201) {
            throw new AssertionError("POST /payments returned " + r.status() + ": " + r.body());
        }
        return r.json().get("paymentId").asString();
    }

    protected Response getPayment(String paymentId) {
        return send("GET", "/payments/" + paymentId, null);
    }

    protected void awaitStatus(String paymentId, String status) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(25))
                .until(() -> status.equals(getPayment(paymentId).paymentStatus()));
    }

    protected List<FakeBackend.Received> awaitCallbacks(String paymentId, int count) {
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(25))
                .until(() -> backend.receivedFor(paymentId).size() >= count);
        return backend.receivedFor(paymentId);
    }

    /** Waits long enough for any further (unexpected) callbacks or changes to show up. */
    protected static void quietPeriod() throws InterruptedException {
        Thread.sleep(1200);
    }
}
