package com.jobhub.repository.finance;
import com.jobhub.entity.finance.WalletOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<WalletOperation> {
    Optional<WalletOperation> findByOperationKey(String key);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select o from WalletOperation o where o.operationKey=:key")
    Optional<WalletOperation> findLockedByOperationKey(@org.springframework.data.repository.query.Param("key") String key);
}
