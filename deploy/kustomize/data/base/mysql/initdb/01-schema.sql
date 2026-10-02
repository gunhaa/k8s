-- MySQL 공식 이미지는 최초 기동(빈 데이터 디렉터리) 시 /docker-entrypoint-initdb.d 의 파일을 알파벳 순서로 실행한다.
-- K8s에서는 이 파일이 ConfigMap으로 만들어져 볼륨 마운트된다.
-- MYSQL_DATABASE(payments) 컨텍스트에서 실행된다.

CREATE TABLE IF NOT EXISTS accounts (
    user_id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    balance         BIGINT       NOT NULL,
    initial_balance BIGINT       NOT NULL,
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
);

-- 결제 원장. payment_id PK가 멱등성(중복 처리 방지) 키 역할을 한다.
CREATE TABLE IF NOT EXISTS payment_ledger (
    payment_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    type          VARCHAR(16)  NOT NULL,
    amount        BIGINT       NOT NULL,
    delta         BIGINT       NOT NULL,
    status        VARCHAR(16)  NOT NULL,
    balance_after BIGINT       NULL,
    reason        VARCHAR(255) NULL,
    requested_at  DATETIME(6)  NOT NULL,
    processed_at  DATETIME(6)  NULL,
    INDEX idx_ledger_user_status (user_id, status)
);

INSERT INTO accounts (user_id, balance, initial_balance) VALUES
    ('user-1', 100000, 100000),
    ('user-2', 50000, 50000),
    ('user-3', 10000, 10000);
