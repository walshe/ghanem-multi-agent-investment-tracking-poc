package poc.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;

import poc.checkout.job.CheckoutJob;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * <b>Responsibility:</b> give integration tests a running application and the tools to drive it.
 *
 * <p>Starts the real application on a random port against a WireMock fake of the provider, and
 * resets the database, the fake provider and the clock before every test. Offers helpers to stub
 * the provider, call the API, send correctly (or wrongly) signed callbacks, and move time forward.
 * Time is controlled with {@link MutableClock}; the scheduled job is run by calling
 * {@link CheckoutJob#runOnce()}.
 *
 * <p><b>Not responsible for:</b> any assertions about the rules. Those live in the subclasses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class IntegrationTestBase {

    /** Must match provider.signing-secret in application-test.yml. */
    protected static final String SECRET = "test-signing-secret";

    protected static final WireMockServer provider =
            new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        provider.start();
    }

    private static final AtomicInteger eventCounter = new AtomicInteger();

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", provider::baseUrl);
    }

    @Autowired
    protected MutableClock clock;
    @Autowired
    protected CheckoutJob job;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    private Environment environment;

    protected final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @BeforeEach
    void resetState() {
        provider.resetAll();
        jdbc.update("DELETE FROM payment_event");
        jdbc.update("DELETE FROM checkout");
        clock.reset();
        stubCreatePaymentAccepted();
    }

    // ---------- provider stubs ----------

    /** The fake provider derives the paymentId from the checkoutId, see {@link #paymentIdFor(String)}. */
    protected void stubCreatePaymentAccepted() {
        provider.stubFor(post(urlPathEqualTo("/payments")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"paymentId": "pay-{{jsonPath request.body '$.checkoutId'}}",
                         "checkoutId": "{{jsonPath request.body '$.checkoutId'}}",
                         "status": "PENDING",
                         "amountMinor": {{jsonPath request.body '$.amountMinor'}},
                         "currency": "{{jsonPath request.body '$.currency'}}",
                         "refundCount": 0}""")));
    }

    protected static String paymentIdFor(String checkoutId) {
        return "pay-" + checkoutId;
    }

    protected void stubGetPayment(String paymentId, String status, long amountMinor, String currency) {
        provider.stubFor(get(urlPathEqualTo("/payments/" + paymentId)).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"paymentId": "%s", "checkoutId": "x", "status": "%s", "amountMinor": %d,
                         "currency": "%s", "refundCount": 0}""".formatted(paymentId, status, amountMinor, currency))));
    }

    protected void stubCancel(String paymentId, int httpStatus) {
        provider.stubFor(post(urlPathEqualTo("/payments/" + paymentId + "/cancel")).willReturn(aResponse()
                .withStatus(httpStatus)
                .withHeader("Content-Type", "application/json")
                .withBody(httpStatus == 200 ? """
                        {"paymentId": "%s", "checkoutId": "x", "status": "CANCELLED", "amountMinor": 0,
                         "currency": "EUR", "refundCount": 0}""".formatted(paymentId) : "")));
    }

    protected void stubRefund(String paymentId, int httpStatus) {
        provider.stubFor(post(urlPathEqualTo("/payments/" + paymentId + "/refund"))
                .withHeader("Idempotency-Key", equalTo("refund-" + paymentId.substring("pay-".length())))
                .willReturn(aResponse()
                        .withStatus(httpStatus)
                        .withHeader("Content-Type", "application/json")
                        .withBody(httpStatus == 200 ? """
                                {"paymentId": "%s", "checkoutId": "x", "status": "REFUNDED", "amountMinor": 0,
                                 "currency": "EUR", "refundCount": 1}""".formatted(paymentId) : "")));
    }

    // ---------- app calls ----------

    protected String baseUrl() {
        return "http://localhost:" + environment.getProperty("local.server.port");
    }

    protected HttpResponse<String> postJson(String path, String body) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    protected HttpResponse<String> getPath(String path) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl() + path)).GET());
    }

    protected HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Creates a checkout through the API. The fake provider accepts it. */
    protected String createCheckout(long amountMinor, String currency) {
        HttpResponse<String> response = postJson("/checkouts",
                "{\"amountMinor\": " + amountMinor + ", \"currency\": \"" + currency + "\"}");
        assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).get("checkoutId").stringValue();
    }

    protected JsonNode checkout(String checkoutId) {
        HttpResponse<String> response = getPath("/checkouts/" + checkoutId);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    protected String status(String checkoutId) {
        return checkout(checkoutId).get("status").stringValue();
    }

    protected JsonNode events(String checkoutId) {
        HttpResponse<String> response = getPath("/checkouts/" + checkoutId + "/events");
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body());
    }

    protected int eventRowCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment_event", Integer.class);
    }

    // ---------- callbacks ----------

    protected static String newEventId() {
        return "evt-" + eventCounter.incrementAndGet() + "-" + UUID.randomUUID();
    }

    protected static String callbackBody(String eventId, String checkoutId, String paymentId, String status,
                                         long amountMinor, String currency) {
        return """
                {"eventId":"%s","checkoutId":"%s","paymentId":"%s","status":"%s","amountMinor":%d,\
                "currency":"%s","occurredAt":"2026-10-16T10:00:00Z"}"""
                .formatted(eventId, checkoutId, paymentId, status, amountMinor, currency);
    }

    /** A correctly signed callback with the checkout's real amount, currency and paymentId. */
    protected HttpResponse<String> sendCallback(String eventId, String checkoutId, String status) {
        return postCallback(callbackBody(eventId, checkoutId, paymentIdFor(checkoutId), status, 10050, "EUR"));
    }

    protected HttpResponse<String> postCallback(String body) {
        return postCallback(body, sign(body));
    }

    protected HttpResponse<String> postCallback(String body, String signature) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl() + "/callbacks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (signature != null) {
            request.header("X-Signature", signature);
        }
        return send(request);
    }

    /** Independent HMAC implementation, so the tests don't trust the app's own signing code. */
    protected static String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- time and state helpers ----------

    protected void advance(Duration duration) {
        clock.advance(duration);
    }

    /** A checkout created through the API, then expired by the scheduled job (cancel accepted). */
    protected String expiredCheckout() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "PENDING", 10050, "EUR");
        stubCancel(paymentId, 200);
        advance(Duration.ofMinutes(31));
        job.runOnce();
        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        return checkoutId;
    }
}
