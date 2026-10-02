package dev.k8slab.worker;

import java.time.Instant;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 사용자 잔고. 스키마는 이 엔티티로부터 Hibernate가 생성한다 (spring.jpa.hibernate.ddl-auto).
 * id는 MySQL AUTO_INCREMENT(IDENTITY)로 생성한다.
 */
@Entity
@Table(name = "accounts",
        uniqueConstraints = @UniqueConstraint(name = "uk_accounts_user_id", columnNames = "user_id"),
        check = @CheckConstraint(name = "chk_balance_non_negative", constraint = "balance >= 0"))
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "initial_balance", nullable = false)
    private long initialBalance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public long getBalance() {
        return balance;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
