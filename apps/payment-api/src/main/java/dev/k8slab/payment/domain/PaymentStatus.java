package dev.k8slab.payment.domain;

public enum PaymentStatus {
    /** 접수됨(인메모리 저장 + Kafka 비동기 전송), worker 처리 결과 대기 */
    ACCEPTED,
    /** worker가 DB 잔고에 반영 완료 */
    COMPLETED,
    /** 잔고 부족, 계좌 없음 등으로 거절 */
    REJECTED,
    /** Kafka 전송 자체가 실패 */
    FAILED
}
