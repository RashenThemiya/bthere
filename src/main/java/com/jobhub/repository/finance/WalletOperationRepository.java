package com.jobhub.repository.finance;
import com.jobhub.entity.finance.WalletOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WalletOperationRepository extends JpaRepository<WalletOperation, Long> {
    Optional<WalletOperation> findByOperationKey(String key);
}
