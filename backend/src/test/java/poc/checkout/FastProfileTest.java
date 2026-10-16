package poc.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.github.tomakehurst.wiremock.WireMockServer;

import poc.checkout.config.CheckoutProperties;
import poc.checkout.service.CheckoutService;

/** The "fast" profile has the test timings from states.md, and the job really runs on its schedule. */
@SpringBootTest(properties = {
        "checkout.job-enabled=true",
        // Own database, so this context's scheduler never touches other tests' checkouts.
        "spring.datasource.url=jdbc:h2:mem:fast-profile-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
@ActiveProfiles({"test", "fast"})
@DirtiesContext // stop the scheduler when this class is done
class FastProfileTest {

    private static final WireMockServer provider = new WireMockServer(options().dynamicPort().globalTemplating(true));

    static {
        provider.start();
    }

    @DynamicPropertySource
    static void providerProperties(DynamicPropertyRegistry registry) {
        registry.add("provider.base-url", provider::baseUrl);
    }

    @AfterAll
    static void stopProvider() {
        provider.stop();
    }

    @Autowired
    CheckoutProperties properties;
    @Autowired
    CheckoutService checkouts;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void fastTimingsMatchTheTable() {
        assertThat(properties.reconcileAfter()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.expireAfter()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.jobInterval()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void scheduledJobExpiresAnUnpaidCheckoutOnItsOwn() throws Exception {
        provider.stubFor(post("/payments").willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"paymentId": "pay-fast", "checkoutId": "{{jsonPath request.body '$.checkoutId'}}",
                         "status": "PENDING", "amountMinor": 10050, "currency": "EUR", "refundCount": 0}""")));
        provider.stubFor(get("/payments/pay-fast").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"paymentId": "pay-fast", "checkoutId": "x", "status": "PENDING",
                         "amountMinor": 10050, "currency": "EUR", "refundCount": 0}""")));
        provider.stubFor(post("/payments/pay-fast/cancel").willReturn(aResponse().withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                        {"paymentId": "pay-fast", "checkoutId": "x", "status": "CANCELLED",
                         "amountMinor": 10050, "currency": "EUR", "refundCount": 0}""")));

        UUID checkoutId = checkouts.create(10050, "EUR").id();

        long deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos();
        String status = "PENDING";
        while (System.nanoTime() < deadline && !status.equals("EXPIRED")) {
            Thread.sleep(250);
            status = jdbc.queryForObject("SELECT status FROM checkout WHERE id = ?", String.class, checkoutId);
        }
        assertThat(status).isEqualTo("EXPIRED");
        provider.verify(1, postRequestedFor(urlPathMatching("/payments/pay-fast/cancel")));
    }
}
