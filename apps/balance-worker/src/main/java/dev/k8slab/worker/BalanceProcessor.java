package dev.k8slab.worker;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 이벤트 하나를 하나의 DB 트랜잭션으로 처리한다.
 * payment_ledger.payment_id(PK)로 중복 처리를 막는다 (Kafka at-least-once 전달 대비).
 */
@Component
public class BalanceProcessor {

    private final JdbcTemplate jdbc;

    public BalanceProcessor(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public PaymentEvents.Result process(PaymentEvents.Requested event) {
        long delta = switch (event.type()) {
            case "CHARGE" -> event.amount();
            case "PAYMENT" -> -event.amount();
            default -> throw new IllegalArgumentException("unknown payment type: " + event.type());
        };
        if (event.amount() <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + event.amount());
        }

        // 1) 원장에 먼저 기록 → 이미 있으면 재전송된 메시지이므로 저장된 결과를 그대로 돌려준다
        try {
            jdbc.update("""
                    INSERT INTO payment_ledger (payment_id, user_id, type, amount, delta, status, requested_at)
                    VALUES (?, ?, ?, ?, ?, 'PROCESSING', ?)
                    """, event.paymentId(), event.userId(), event.type(), event.amount(), delta,
                    Timestamp.from(event.requestedAt()));
        } catch (DuplicateKeyException alreadyProcessed) {
            return loadExisting(event);
        }

        // 2) 조건부 UPDATE 한 문장으로 잔고 부족을 원자적으로 판정
        int updated = jdbc.update("""
                UPDATE accounts SET balance = balance + ?, updated_at = NOW(6)
                WHERE user_id = ? AND balance + ? >= 0
                """, delta, event.userId(), delta);

        String status;
        String reason = null;
        Long balanceAfter = null;
        if (updated == 1) {
            status = "COMPLETED";
            balanceAfter = jdbc.queryForObject("SELECT balance FROM accounts WHERE user_id = ?", Long.class,
                    event.userId());
        } else {
            status = "REJECTED";
            Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM accounts WHERE user_id = ?", Integer.class,
                    event.userId());
            reason = (exists != null && exists > 0) ? "insufficient balance" : "account not found";
        }

        jdbc.update("""
                UPDATE payment_ledger SET status = ?, balance_after = ?, reason = ?, processed_at = NOW(6)
                WHERE payment_id = ?
                """, status, balanceAfter, reason, event.paymentId());

        return new PaymentEvents.Result(event.paymentId(), event.userId(), event.type(), event.amount(), status,
                balanceAfter, reason, event.requestedAt(), Instant.now());
    }

    private PaymentEvents.Result loadExisting(PaymentEvents.Requested event) {
        return jdbc.queryForObject("""
                SELECT status, balance_after, reason, processed_at FROM payment_ledger WHERE payment_id = ?
                """, (rs, i) -> {
            Timestamp processedAt = rs.getTimestamp("processed_at");
            long balanceAfter = rs.getLong("balance_after");
            return new PaymentEvents.Result(event.paymentId(), event.userId(), event.type(), event.amount(),
                    rs.getString("status"), rs.wasNull() ? null : balanceAfter, rs.getString("reason"),
                    event.requestedAt(), processedAt != null ? processedAt.toInstant() : Instant.now());
        }, event.paymentId());
    }
}
