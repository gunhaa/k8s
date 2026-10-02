package dev.k8slab.worker;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 이벤트 하나를 하나의 DB 트랜잭션으로 처리한다.
 * payment_ledger.payment_id(UNIQUE)로 중복 처리를 막는다 (Kafka at-least-once 전달 대비).
 */
@Component
public class BalanceProcessor {

    private final AccountRepository accounts;
    private final PaymentLedgerRepository ledger;

    public BalanceProcessor(AccountRepository accounts, PaymentLedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
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

        // 1) 이미 원장에 있으면 재전송된 메시지이므로 저장된 결과를 그대로 돌려준다
        Optional<PaymentLedger> existing = ledger.findByPaymentId(event.paymentId());
        if (existing.isPresent()) {
            return toResult(event, existing.get());
        }

        // 2) 조건부 UPDATE 한 문장으로 잔고 부족을 원자적으로 판정
        Instant now = Instant.now();
        int updated = accounts.applyDelta(event.userId(), delta, now);

        String status;
        String reason = null;
        Long balanceAfter = null;
        if (updated == 1) {
            status = "COMPLETED";
            balanceAfter = accounts.findByUserId(event.userId()).map(Account::getBalance).orElseThrow();
        } else {
            status = "REJECTED";
            reason = accounts.existsByUserId(event.userId()) ? "insufficient balance" : "account not found";
        }

        // 3) 원장 기록. 두 consumer가 동시에 같은 결제를 처리하면(rebalance 경합) UNIQUE 위반으로
        //    트랜잭션 전체(잔고 반영 포함)가 롤백되고, 재시도 시 1)에서 기존 결과를 돌려준다.
        PaymentLedger saved = ledger.saveAndFlush(new PaymentLedger(event.paymentId(), event.userId(), event.type(),
                event.amount(), delta, status, balanceAfter, reason, event.requestedAt(), now));

        return toResult(event, saved);
    }

    private PaymentEvents.Result toResult(PaymentEvents.Requested event, PaymentLedger entry) {
        return new PaymentEvents.Result(event.paymentId(), event.userId(), event.type(), event.amount(),
                entry.getStatus(), entry.getBalanceAfter(), entry.getReason(), event.requestedAt(),
                entry.getProcessedAt() != null ? entry.getProcessedAt() : Instant.now());
    }
}
