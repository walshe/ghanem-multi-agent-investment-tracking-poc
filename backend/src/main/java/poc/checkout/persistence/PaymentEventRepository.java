package poc.checkout.persistence;

import static poc.checkout.persistence.CheckoutRepository.odt;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import poc.checkout.domain.EventOutcome;
import poc.checkout.domain.PaymentEvent;
import poc.checkout.domain.PaymentEventStatus;

@Repository
public class PaymentEventRepository {

    private final JdbcTemplate jdbc;

    public PaymentEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the event. The unique constraint on event_id is the idempotency check: if another
     * transaction is inserting the same eventId, this waits for it and then fails here.
     *
     * @return false if an event with this eventId already exists
     */
    public boolean insertIfNew(PaymentEvent e) {
        try {
            jdbc.update("INSERT INTO payment_event (event_id, checkout_id, payment_id, status, amount_minor, "
                            + "currency, occurred_at, outcome, received_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    e.eventId(), e.checkoutId(), e.paymentId(), e.status().name(), e.amountMinor(), e.currency(),
                    odt(e.occurredAt()), e.outcome().name(), odt(e.receivedAt()));
            return true;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    public void setOutcome(String eventId, EventOutcome outcome) {
        jdbc.update("UPDATE payment_event SET outcome = ? WHERE event_id = ?", outcome.name(), eventId);
    }

    /** Events for a checkout, oldest first. */
    public List<PaymentEvent> findByCheckoutId(String checkoutId) {
        return jdbc.query("SELECT event_id, checkout_id, payment_id, status, amount_minor, currency, occurred_at, "
                        + "outcome, received_at FROM payment_event WHERE checkout_id = ? ORDER BY received_at, id",
                PaymentEventRepository::map, checkoutId);
    }

    private static PaymentEvent map(ResultSet rs, int rowNum) throws SQLException {
        OffsetDateTime occurredAt = rs.getObject("occurred_at", OffsetDateTime.class);
        return new PaymentEvent(
                rs.getString("event_id"),
                rs.getString("checkout_id"),
                rs.getString("payment_id"),
                PaymentEventStatus.valueOf(rs.getString("status")),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                occurredAt == null ? null : occurredAt.toInstant(),
                EventOutcome.valueOf(rs.getString("outcome")),
                rs.getObject("received_at", OffsetDateTime.class).toInstant());
    }
}
