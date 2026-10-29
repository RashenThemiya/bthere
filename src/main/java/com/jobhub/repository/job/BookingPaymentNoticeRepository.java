package com.jobhub.repository.job;
import com.jobhub.entity.job.BookingPaymentNotice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface BookingPaymentNoticeRepository extends JpaRepository<BookingPaymentNotice,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<BookingPaymentNotice> {
    Optional<BookingPaymentNotice> findByPaymentIdAndStatusCode(String paymentId,String statusCode);
    Optional<BookingPaymentNotice> findFirstByPaymentOrderIdOrderByNoticeIdAsc(String paymentOrderId);
    boolean existsByRequestIdAndStatusCode(Long requestId,String statusCode);
}
