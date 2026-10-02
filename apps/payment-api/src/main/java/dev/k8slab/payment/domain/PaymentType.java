package dev.k8slab.payment.domain;

public enum PaymentType {
    /** 잔고 충전 (+) */
    CHARGE,
    /** 결제 (-) */
    PAYMENT
}
