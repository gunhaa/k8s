package dev.k8slab.payment.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;

/**
 * accounts 테이블의 읽기 전용 뷰. 스키마의 주인은 balance-worker이고, 이 앱은 api_ro(SELECT 전용) 계정으로 조회만 한다.
 * 필요한 컬럼만 매핑하며, INSERT하지 않으므로 id 생성 전략(@GeneratedValue)도 두지 않는다.
 */
@Entity
@Immutable
@Table(name = "accounts")
public class Account {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private long balance;

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
}
