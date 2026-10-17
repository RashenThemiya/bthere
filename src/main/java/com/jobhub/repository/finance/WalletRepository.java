package com.jobhub.repository.finance;
import com.jobhub.entity.finance.Wallet;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface WalletRepository extends JpaRepository<Wallet, Long> {
    List<Wallet> findAllByUserIdOrderByCurrencyCodeAsc(Long userId);
    Optional<Wallet> findByUserIdAndCurrencyCode(Long userId, String currencyCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId and w.currencyCode = :currency")
    Optional<Wallet> lockOwned(@Param("userId") Long userId, @Param("currency") String currency);
}
