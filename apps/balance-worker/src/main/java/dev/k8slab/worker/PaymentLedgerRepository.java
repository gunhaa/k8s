package dev.k8slab.worker;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, Long> {

    Optional<PaymentLedger> findByPaymentId(String paymentId);
}
