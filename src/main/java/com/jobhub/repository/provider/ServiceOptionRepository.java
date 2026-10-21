package com.jobhub.repository.provider;

import com.jobhub.entity.provider.ServiceOption;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceOptionRepository extends JpaRepository<ServiceOption, Long> {
    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ServiceOption o where o.optionId = :optionId")
    Optional<ServiceOption> findLockedById(@Param("optionId") Long optionId);
    @Query("select o from ServiceOption o join o.serviceTypes l where l.serviceProviderTypeId = :typeId order by l.displayOrder, o.name")
    List<ServiceOption> findAllByServiceProviderTypeIdOrderByDisplayOrderAscNameAsc(@Param("typeId") Long typeId);

    @Query("select o from ServiceOption o join o.serviceTypes l where l.serviceProviderTypeId = :typeId and l.status = :status and o.status = :status order by l.displayOrder, o.name")
    List<ServiceOption> findAllByServiceProviderTypeIdAndStatusOrderByDisplayOrderAscNameAsc(
            @Param("typeId") Long typeId, @Param("status") String status);

    @Query("select o from ServiceOption o join o.serviceTypes l where o.optionId in :ids and l.serviceProviderTypeId = :typeId and l.status = :status and o.status = :status")
    List<ServiceOption> findAllByOptionIdInAndServiceProviderTypeIdAndStatus(
            @Param("ids") Collection<Long> ids, @Param("typeId") Long typeId, @Param("status") String status);

    @Query("select (count(o) > 0) from ServiceOption o join o.serviceTypes l where l.serviceProviderTypeId = :typeId and lower(o.code) = lower(:code)")
    boolean existsByServiceProviderTypeIdAndCodeIgnoreCase(@Param("typeId") Long typeId, @Param("code") String code);

    @Query("select o from ServiceOption o join o.serviceTypes l where o.optionId = :optionId and l.serviceProviderTypeId = :typeId")
    Optional<ServiceOption> findByOptionIdAndServiceProviderTypeId(@Param("optionId") Long optionId, @Param("typeId") Long typeId);

    long countByStatus(String status);
    long countBySchedulingModel(String schedulingModel);
    long countByBookingMode(String bookingMode);
    long countByFulfillmentModel(String fulfillmentModel);
}
