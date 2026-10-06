package com.jobhub.repository.finance;

import com.jobhub.entity.finance.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.math.BigDecimal;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    long countByPaymentStatus(String status);
    @Query("select coalesce(sum(p.totalAmount), 0) from Payment p where p.paymentStatus = 'PAID'")
    BigDecimal totalPaidAmount();
}
