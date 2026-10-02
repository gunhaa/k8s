-- 초기 계좌. Hibernate가 스키마를 만든 뒤 매 기동마다 실행된다 (application.yml: spring.sql.init).
-- ddl-auto=update(테이블 유지)에서도 다시 실행되므로 user_id UNIQUE + INSERT IGNORE로 멱등하게 작성한다.
-- id는 AUTO_INCREMENT가 채운다.
INSERT IGNORE INTO accounts (user_id, balance, initial_balance, updated_at) VALUES
    ('user-1', 100000, 100000, UTC_TIMESTAMP(6)),
    ('user-2', 50000, 50000, UTC_TIMESTAMP(6)),
    ('user-3', 10000, 10000, UTC_TIMESTAMP(6));
