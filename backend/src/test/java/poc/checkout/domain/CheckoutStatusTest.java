package poc.checkout.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** states.md, "Statuses" and "Allowed status changes". */
class CheckoutStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "PENDING->PROCESSING", "PENDING->SUCCEEDED", "PENDING->FAILED", "PENDING->EXPIRED",
            "PROCESSING->SUCCEEDED", "PROCESSING->FAILED");

    @Test
    void onlyTheChangesInTheTableAreAllowed() {
        for (CheckoutStatus from : CheckoutStatus.values()) {
            for (CheckoutStatus to : CheckoutStatus.values()) {
                assertThat(from.canChangeTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(ALLOWED.contains(from + "->" + to));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = CheckoutStatus.class, names = {"SUCCEEDED", "FAILED", "EXPIRED"})
    void finalStatusesNeverChange(CheckoutStatus finalStatus) {
        assertThat(finalStatus.isFinal()).isTrue();
        for (CheckoutStatus to : CheckoutStatus.values()) {
            assertThat(finalStatus.canChangeTo(to)).as("%s -> %s", finalStatus, to).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(value = CheckoutStatus.class, names = {"PENDING", "PROCESSING"})
    void pendingAndProcessingAreNotFinal(CheckoutStatus status) {
        assertThat(status.isFinal()).isFalse();
    }
}
