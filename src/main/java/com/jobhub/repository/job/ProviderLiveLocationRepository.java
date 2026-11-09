package com.jobhub.repository.job;

import com.jobhub.entity.job.ProviderLiveLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface ProviderLiveLocationRepository extends JpaRepository<ProviderLiveLocation, Long> {
    @Modifying
    @Query("delete from ProviderLiveLocation l where l.receivedAt < :before")
    int deleteReceivedBefore(@Param("before") Instant before);
}
