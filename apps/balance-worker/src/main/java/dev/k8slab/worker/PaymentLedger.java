package dev.k8slab.worker;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 결제 원장. PK는 AUTO_INCREMENT id이고, payment_id UNIQUE 제약이 멱등성(중복 처리 방지) 키 역할을 한다.
 */
@Entity
@Table(name = "payment_ledger",
        uniqueConstraints = @UniqueConstraint(name = "uk_ledger_payment_id", columnNames = "payment_id"),
        indexes = @Index(name = "idx_ledger_user_status", columnList = "user_id, status"))
public class PaymentLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false, length = 64)
    private String paymentId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long delta;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "balance_after")
    private Long balanceAfter;

    @Column(length = 255)
    private String reason;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected PaymentLedger() {
    }

    public PaymentLedger(String paymentId, String userId, String type, long amount, long delta, String status,
            Long balanceAfter, String reason, Instant requestedAt, Instant processedAt) {
        this.paymentId = paymentId;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.delta = delta;
        this.status = status;
        this.balanceAfter = balanceAfter;
        this.reason = reason;
        this.requestedAt = requestedAt;
        this.processedAt = processedAt;
    }

    public Long getId() {
        return id;
    }

    public String getPaymentId() {
        return paymentId;
    }

    public String getStatus() {
        return status;
    }

    public Long getBalanceAfter() {
        return balanceAfter;
    }

    public String getReason() {
        return reason;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
