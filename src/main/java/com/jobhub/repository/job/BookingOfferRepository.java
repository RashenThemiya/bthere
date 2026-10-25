package com.jobhub.repository.job;

import com.jobhub.entity.job.BookingOffer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface BookingOfferRepository extends JpaRepository<BookingOffer, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<BookingOffer> {
    List<BookingOffer> findAllByRequestIdOrderByOfferIdAsc(Long requestId);
    List<BookingOffer> findAllByProviderIdAndStatusOrderByOfferIdAsc(Long providerId, String status);
}
