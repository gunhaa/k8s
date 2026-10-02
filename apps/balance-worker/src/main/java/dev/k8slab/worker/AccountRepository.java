package dev.k8slab.worker;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByUserId(String userId);

    boolean existsByUserId(String userId);

    /**
     * 조건부 UPDATE 한 문장으로 잔고 부족을 원자적으로 판정한다 (read-modify-write 경쟁 없음).
     * 벌크 연산은 영속성 컨텍스트를 거치지 않으므로, 이후 조회가 캐시된 옛 값을 보지 않게 비운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Account a SET a.balance = a.balance + :delta, a.updatedAt = :now
            WHERE a.userId = :userId AND a.balance + :delta >= 0
            """)
    int applyDelta(@Param("userId") String userId, @Param("delta") long delta, @Param("now") Instant now);
}
