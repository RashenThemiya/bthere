package com.jobhub.repository.job;

import com.jobhub.entity.job.BookingRequest;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<BookingRequest> {
    List<BookingRequest> findAllByJobIdIn(Collection<Long> jobIds);
    @Query("select r.status, count(r) from BookingRequest r group by r.status")
    List<Object[]> monitorStatusCounts();
    @Query("select r.paymentStatus, count(r) from BookingRequest r group by r.paymentStatus")
    List<Object[]> monitorPaymentCounts();
    @Query("select r.settlementStatus, count(r) from BookingRequest r group by r.settlementStatus")
    List<Object[]> monitorSettlementCounts();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookingRequest r where r.requestId = :id")
    Optional<BookingRequest> lockById(@Param("id") Long id);
    Optional<BookingRequest> findByUserIdAndIdempotencyKey(Long userId, String key);
    boolean existsByUserIdAndOptionIdAndStatusIn(Long userId,Long optionId,Collection<String> statuses);
    Optional<BookingRequest> findByPaymentOrderId(String orderId);
    Optional<BookingRequest> findByJobId(Long jobId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from BookingRequest r where r.jobId = :jobId")
    Optional<BookingRequest> lockByJobId(@Param("jobId") Long jobId);
    @Query("""
        select r from BookingRequest r where
        (r.status='MATCHING' and (r.expiresAt <= :now or exists
          (select o.offerId from BookingOffer o where o.requestId=r.requestId and o.status='PENDING' and o.expiresAt <= :now)))
        or (r.status='AWAITING_CUSTOMER_CONFIRMATION' and r.confirmationExpiresAt <= :now)
        or (r.status='PAYMENT_PENDING' and r.paymentStartedAt <= :paymentThreshold)
        order by r.requestId asc
        """)
    List<BookingRequest> findDue(@Param("now") java.time.Instant now,
            @Param("paymentThreshold") java.time.Instant paymentThreshold, org.springframework.data.domain.Pageable pageable);
}
