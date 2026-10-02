package dev.k8slab.payment.account;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/** save/delete가 없는 Repository만 상속해 쓰기 메서드 자체를 노출하지 않는다 */
public interface AccountRepository extends Repository<Account, Long> {

    Optional<Account> findByUserId(String userId);
}
