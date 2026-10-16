package poc.checkout;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.github.tomakehurst.wiremock.http.Fault;

import tools.jackson.databind.JsonNode;

/** states.md, "Late and missing callbacks (scheduled job)", with the default timings (10 / 30 minutes). */
@ExtendWith(OutputCaptureExtension.class)
class CheckoutJobTest extends IntegrationTestBase {

    // ---------- 1. Reconcile ----------

    @Test
    void reconcileAppliesTheProvidersStatusAndRecordsAnEvent() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "SUCCEEDED", 10050, "EUR");

        advance(Duration.ofMinutes(11));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
        JsonNode events = events(checkoutId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).get("eventId").stringValue()).isEqualTo("reconcile-" + checkoutId + "-SUCCEEDED");
        assertThat(events.get(0).get("status").stringValue()).isEqualTo("SUCCEEDED");
        assertThat(events.get(0).get("outcome").stringValue()).isEqualTo("APPLIED");
    }

    @Test
    void reconcileMovesProcessingToFinal() {
        String checkoutId = createCheckout(10050, "EUR");
        sendCallback(newEventId(), checkoutId, "PROCESSING");
        stubGetPayment(paymentIdFor(checkoutId), "FAILED", 10050, "EUR");

        advance(Duration.ofMinutes(11));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("FAILED");
        assertThat(events(checkoutId).get(1).get("eventId").stringValue())
                .isEqualTo("reconcile-" + checkoutId + "-FAILED");
    }

    @Test
    void reconcileDoesNotAskTheProviderBeforeReconcileAfter() {
        String checkoutId = createCheckout(10050, "EUR");
        stubGetPayment(paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        advance(Duration.ofMinutes(9));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("PENDING");
        provider.verify(0, getRequestedFor(urlPathMatching("/payments/.*")));
    }

    @Test
    void reconcileIgnoresAProviderStatusThatIsNotAnAllowedChange() {
        String checkoutId = createCheckout(10050, "EUR");
        sendCallback(newEventId(), checkoutId, "PROCESSING");
        stubGetPayment(paymentIdFor(checkoutId), "PROCESSING", 10050, "EUR");

        advance(Duration.ofMinutes(11));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("PROCESSING");
        assertThat(events(checkoutId)).hasSize(1);
    }

    @Test
    void reconcileDoesNotApplyAStatusWithADifferentAmount() {
        String checkoutId = createCheckout(10050, "EUR");
        stubGetPayment(paymentIdFor(checkoutId), "SUCCEEDED", 1, "EUR");

        advance(Duration.ofMinutes(11));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("PENDING");
        assertThat(events(checkoutId)).isEmpty();
    }

    @Test
    void finalCheckoutsAreNotReconciled() {
        String checkoutId = createCheckout(10050, "EUR");
        sendCallback(newEventId(), checkoutId, "SUCCEEDED");

        advance(Duration.ofMinutes(11));
        job.runOnce();

        provider.verify(0, getRequestedFor(urlPathMatching("/payments/.*")));
    }

    // ---------- 2. Expire ----------

    @Test
    void pendingCheckoutIsExpiredAndThePaymentCancelled() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "PENDING", 10050, "EUR");
        stubCancel(paymentId, 200);

        advance(Duration.ofMinutes(31));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        provider.verify(1, postRequestedFor(urlPathEqualTo("/payments/" + paymentId + "/cancel")));

        // Cancel accepted: nothing more to do on later runs.
        provider.resetRequests();
        job.runOnce();
        provider.verify(0, anyRequestedFor(urlPathMatching("/payments.*")));
    }

    @Test
    void pendingCheckoutIsNotExpiredBeforeExpireAfter() {
        String checkoutId = createCheckout(10050, "EUR");
        stubGetPayment(paymentIdFor(checkoutId), "PENDING", 10050, "EUR");

        advance(Duration.ofMinutes(29));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("PENDING");
        provider.verify(0, postRequestedFor(urlPathMatching("/payments/.*/cancel")));
    }

    @Test
    void reconcileRunsBeforeExpireSoAPaymentThatSucceededIsNotExpired() {
        String checkoutId = createCheckout(10050, "EUR");
        stubGetPayment(paymentIdFor(checkoutId), "SUCCEEDED", 10050, "EUR");

        advance(Duration.ofMinutes(31));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
        provider.verify(0, postRequestedFor(urlPathMatching("/payments/.*/cancel")));
    }

    @Test
    void checkoutWithoutPaymentIdIsExpiredWithoutCancel() {
        provider.stubFor(post(urlPathEqualTo("/payments")).willReturn(aResponse().withFault(Fault.EMPTY_RESPONSE)));
        String checkoutId = json.readTree(postJson("/checkouts", "{\"amountMinor\": 10050, \"currency\": \"EUR\"}")
                .body()).get("checkoutId").stringValue();

        advance(Duration.ofMinutes(31));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        provider.verify(0, postRequestedFor(urlPathMatching("/payments/.*/cancel")));
    }

    // ---------- 3. Refused cancel ----------

    @Test
    void refusedCancelIsCheckedOnEveryRunAndRefundedOnceIfItSucceeds() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "PENDING", 10050, "EUR");
        stubCancel(paymentId, 409);
        stubRefund(paymentId, 200);

        advance(Duration.ofMinutes(31));
        job.runOnce();
        assertThat(status(checkoutId)).isEqualTo("EXPIRED");

        // Still moving: keep checking, no refund yet.
        stubGetPayment(paymentId, "PROCESSING", 10050, "EUR");
        provider.resetRequests();
        job.runOnce();
        provider.verify(1, getRequestedFor(urlPathEqualTo("/payments/" + paymentId)));
        provider.verify(0, postRequestedFor(urlPathMatching("/payments/.*/refund")));

        // It completed after all: refund, status stays EXPIRED.
        stubGetPayment(paymentId, "SUCCEEDED", 10050, "EUR");
        job.runOnce();
        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isTrue();
        provider.verify(1, postRequestedFor(urlPathEqualTo("/payments/" + paymentId + "/refund"))
                .withHeader("Idempotency-Key", equalTo("refund-" + checkoutId)));

        // Final status reached: no more checking, no second refund.
        provider.resetRequests();
        job.runOnce();
        job.runOnce();
        provider.verify(0, anyRequestedFor(urlPathMatching("/payments.*")));
    }

    @Test
    void refusedCancelThatEndsFailedIsNotRefundedAndStopsBeingChecked() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "PENDING", 10050, "EUR");
        stubCancel(paymentId, 409);

        advance(Duration.ofMinutes(31));
        job.runOnce();
        stubGetPayment(paymentId, "FAILED", 10050, "EUR");
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("EXPIRED");
        assertThat(checkout(checkoutId).get("refundRequested").booleanValue()).isFalse();
        provider.resetRequests();
        job.runOnce();
        provider.verify(0, anyRequestedFor(urlPathMatching("/payments.*")));
    }

    @Test
    void lateSuccessCallbackAfterRefusedCancelAndTheJobRequestOnlyOneRefund() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        stubGetPayment(paymentId, "PENDING", 10050, "EUR");
        stubCancel(paymentId, 409);
        stubRefund(paymentId, 200);
        advance(Duration.ofMinutes(31));
        job.runOnce();

        sendCallback(newEventId(), checkoutId, "SUCCEEDED");
        stubGetPayment(paymentId, "REFUNDED", 10050, "EUR");
        job.runOnce();
        job.runOnce();

        provider.verify(1, postRequestedFor(urlPathMatching("/payments/.*/refund")));
    }

    // ---------- 4. Stuck in PROCESSING ----------

    @Test
    void processingAfterExpireAfterIsNeverExpiredButLogsAWarning(CapturedOutput output) {
        String checkoutId = createCheckout(10050, "EUR");
        sendCallback(newEventId(), checkoutId, "PROCESSING");
        stubGetPayment(paymentIdFor(checkoutId), "PROCESSING", 10050, "EUR");

        advance(Duration.ofMinutes(31));
        job.runOnce();

        assertThat(status(checkoutId)).isEqualTo("PROCESSING");
        provider.verify(0, postRequestedFor(urlPathMatching("/payments/.*/cancel")));
        assertThat(output.getAll()).contains("Checkout " + checkoutId).contains("still PROCESSING")
                .contains("A person must check it");
    }

    // ---------- robustness ----------

    @Test
    void providerDownDuringTheJobChangesNothingAndIsRetriedNextRun() {
        String checkoutId = createCheckout(10050, "EUR");
        String paymentId = paymentIdFor(checkoutId);
        provider.resetAll(); // every provider call now gets a 404 from WireMock

        advance(Duration.ofMinutes(11));
        job.runOnce();
        assertThat(status(checkoutId)).isEqualTo("PENDING");

        stubGetPayment(paymentId, "SUCCEEDED", 10050, "EUR");
        job.runOnce();
        assertThat(status(checkoutId)).isEqualTo("SUCCEEDED");
    }
}
