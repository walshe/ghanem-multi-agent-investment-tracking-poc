package poc.checkout.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import poc.checkout.domain.Checkout;
import poc.checkout.domain.CheckoutStatus;

@Repository
public class CheckoutRepository {

    private static final String COLUMNS =
            "id, status, amount_minor, currency, payment_id, refund_requested, cancel_refused, "
                    + "created_at, updated_at, version";

    private final JdbcTemplate jdbc;

    public CheckoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Checkout c) {
        jdbc.update("INSERT INTO checkout (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                c.id(), c.status().name(), c.amountMinor(), c.currency(), c.paymentId(),
                c.refundRequested(), c.cancelRefused(), odt(c.createdAt()), odt(c.updatedAt()), c.version());
    }

    public Optional<Checkout> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM checkout WHERE id = ?", CheckoutRepository::map, id)
                .stream().findFirst();
    }

    /** Checkouts the scheduled job has to look at: not final, or expired with a refused cancel. */
    public List<UUID> findIdsForJob() {
        return jdbc.queryForList(
                "SELECT id FROM checkout WHERE status IN ('PENDING', 'PROCESSING') OR cancel_refused = TRUE "
                        + "ORDER BY created_at",
                UUID.class);
    }

    /**
     * Optimistic-locking update. Writes all mutable fields of {@code c} if the row still has
     * {@code c.version()}, and increments the version.
     *
     * @throws OptimisticLockingFailureException if someone else updated the checkout first
     */
    public Checkout update(Checkout c, Instant now) {
        int rows = jdbc.update(
                "UPDATE checkout SET status = ?, payment_id = ?, refund_requested = ?, cancel_refused = ?, "
                        + "updated_at = ?, version = version + 1 WHERE id = ? AND version = ?",
                c.status().name(), c.paymentId(), c.refundRequested(), c.cancelRefused(), odt(now),
                c.id(), c.version());
        if (rows != 1) {
            throw new OptimisticLockingFailureException(
                    "Checkout " + c.id() + " was changed concurrently (expected version " + c.version() + ")");
        }
        return new Checkout(c.id(), c.status(), c.amountMinor(), c.currency(), c.paymentId(), c.refundRequested(),
                c.cancelRefused(), c.createdAt(), now, c.version() + 1);
    }

    /** Stores the provider's paymentId unless the checkout already has one. */
    public void setPaymentIdIfMissing(UUID id, String paymentId, Instant now) {
        jdbc.update("UPDATE checkout SET payment_id = ?, updated_at = ?, version = version + 1 "
                + "WHERE id = ? AND payment_id IS NULL", paymentId, odt(now), id);
    }

    private static Checkout map(ResultSet rs, int rowNum) throws SQLException {
        return new Checkout(
                rs.getObject("id", UUID.class),
                CheckoutStatus.valueOf(rs.getString("status")),
                rs.getLong("amount_minor"),
                rs.getString("currency"),
                rs.getString("payment_id"),
                rs.getBoolean("refund_requested"),
                rs.getBoolean("cancel_refused"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant(),
                rs.getLong("version"));
    }

    static OffsetDateTime odt(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
