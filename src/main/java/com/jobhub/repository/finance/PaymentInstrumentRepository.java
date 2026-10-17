package com.jobhub.repository.finance;
import com.jobhub.entity.finance.PaymentInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PaymentInstrumentRepository extends JpaRepository<PaymentInstrument,Long> {
    Optional<PaymentInstrument> findByUserIdAndProviderAndType(Long userId,String provider,String type);
    List<PaymentInstrument> findAllByUserIdAndProvider(Long userId,String provider);
}
