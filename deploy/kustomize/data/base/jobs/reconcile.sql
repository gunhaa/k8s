-- 정합성 점검: 현재 잔고 = 초기 잔고 + COMPLETED 원장 delta 합계 여야 한다.
-- 결과 행이 있으면 불일치(버그 또는 중복 처리)를 뜻한다.
SELECT a.user_id,
       a.balance,
       a.initial_balance + COALESCE(SUM(l.delta), 0) AS expected
FROM accounts a
LEFT JOIN payment_ledger l
       ON l.user_id = a.user_id AND l.status = 'COMPLETED'
GROUP BY a.user_id, a.balance, a.initial_balance
HAVING a.balance <> expected;
