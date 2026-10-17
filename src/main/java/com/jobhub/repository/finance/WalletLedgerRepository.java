package com.jobhub.repository.finance;
import com.jobhub.entity.finance.WalletLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;
import java.util.*;
public interface WalletLedgerRepository extends JpaRepository<WalletLedgerEntry, Long> {
    Page<WalletLedgerEntry> findAllByWalletIdOrderByWalletLedgerEntryIdDesc(Long walletId, Pageable pageable);
    List<WalletLedgerEntry> findAllByWalletId(Long walletId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from WalletLedgerEntry e where e.walletId = :id")
    List<WalletLedgerEntry> lockCurrentEntries(@org.springframework.data.repository.query.Param("id") Long id);
}
