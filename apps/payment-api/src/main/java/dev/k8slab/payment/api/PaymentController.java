package dev.k8slab.payment.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.k8slab.payment.account.Account;
import dev.k8slab.payment.account.AccountRepository;
import dev.k8slab.payment.domain.Payment;
import dev.k8slab.payment.domain.PaymentStore;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class PaymentController {

    /** 어느 Pod가 응답했는지 FE에서 볼 수 있게 헤더로 내려준다 (Service 로드밸런싱 관찰용) */
    private static final String SERVED_BY = "X-Served-By";

    private final PaymentService paymentService;
    private final PaymentStore store;
    private final AccountRepository accounts;
    private final String podName;

    public PaymentController(PaymentService paymentService, PaymentStore store, AccountRepository accounts,
            @Value("${app.pod-name}") String podName) {
        this.paymentService = paymentService;
        this.store = store;
        this.accounts = accounts;
        this.podName = podName;
    }

    @PostMapping("/payments")
    public ResponseEntity<Payment> create(@Valid @RequestBody PaymentRequest request) {
        Payment payment = paymentService.request(request);
        return ResponseEntity.accepted()
                .location(URI.create("/api/payments/" + payment.id()))
                .header(SERVED_BY, podName)
                .body(payment);
    }

    /** 인메모리 조회. 다른 replica가 접수한 결제는 결과 이벤트가 오기 전까지 404일 수 있다. */
    @GetMapping("/payments/{id}")
    public ResponseEntity<Payment> get(@PathVariable String id) {
        return store.find(id)
                .map(p -> ResponseEntity.ok().header(SERVED_BY, podName).body(p))
                .orElseGet(() -> ResponseEntity.notFound().header(SERVED_BY, podName).build());
    }

    @GetMapping("/payments")
    public ResponseEntity<List<Payment>> recent() {
        return ResponseEntity.ok().header(SERVED_BY, podName).body(store.recent(20));
    }

    /** 잔고는 실제 원장(MySQL)에서 read-only 계정으로 조회한다 */
    @GetMapping("/balances/{userId}")
    public ResponseEntity<Map<String, Object>> balance(@PathVariable String userId) {
        return accounts.findByUserId(userId)
                .map(Account::getBalance)
                .map(balance -> ResponseEntity.ok().header(SERVED_BY, podName)
                        .body(Map.<String, Object>of("userId", userId, "balance", balance)))
                .orElseGet(() -> ResponseEntity.notFound().header(SERVED_BY, podName).build());
    }

    @GetMapping("/whoami")
    public Map<String, String> whoami() {
        return Map.of("pod", podName);
    }
}
