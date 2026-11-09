package com.jobhub.repository.job;

import com.jobhub.entity.job.Rating;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Optional<Rating> findByJobIdAndServiceProviderId(Long jobId, Long serviceProviderId);

    List<Rating> findAllByJobIdOrderByRatingIdAsc(Long jobId);

    /** Rows of [serviceOptionId, rating, count] for a provider's visible ratings. */
    @Query("""
            select r.serviceOptionId, r.rating, count(r) from Rating r
            where r.serviceProviderId = :providerId and r.status = 'VISIBLE'
            group by r.serviceOptionId, r.rating
            """)
    List<Object[]> countByOptionAndScore(@Param("providerId") Long providerId);

    /** Rows of [serviceProviderId, average, count] for one option across several providers. */
    @Query("""
            select r.serviceProviderId, avg(r.rating), count(r) from Rating r
            where r.serviceOptionId = :optionId and r.serviceProviderId in :providerIds and r.status = 'VISIBLE'
            group by r.serviceProviderId
            """)
    List<Object[]> optionSummaries(@Param("optionId") Long optionId, @Param("providerIds") Collection<Long> providerIds);

    /** One row of [average, count] for all of a provider's visible ratings. */
    @Query("select avg(r.rating), count(r) from Rating r where r.serviceProviderId = :providerId and r.status = 'VISIBLE'")
    List<Object[]> overall(@Param("providerId") Long providerId);

    @Query("""
            select r from Rating r
            where r.serviceProviderId = :providerId and r.status = 'VISIBLE'
              and (:optionId is null or r.serviceOptionId = :optionId)
            """)
    Page<Rating> findVisible(@Param("providerId") Long providerId, @Param("optionId") Long optionId, Pageable pageable);

    @Query("""
            select r from Rating r
            where (:providerId is null or r.serviceProviderId = :providerId)
              and (:optionId is null or r.serviceOptionId = :optionId)
              and (:status is null or r.status = :status)
            """)
    Page<Rating> search(@Param("providerId") Long providerId, @Param("optionId") Long optionId,
                        @Param("status") String status, Pageable pageable);
}
