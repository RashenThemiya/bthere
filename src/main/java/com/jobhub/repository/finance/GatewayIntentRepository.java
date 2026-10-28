package com.jobhub.repository.finance;
import com.jobhub.entity.finance.GatewayIntent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface GatewayIntentRepository extends JpaRepository<GatewayIntent,Long> {
    Optional<GatewayIntent> findByUserIdAndProviderAndRequestKey(Long userId,String provider,String key);
    boolean existsByMethodIdAndStatus(Long methodId,String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from GatewayIntent i where i.intentId=:id and i.userId=:userId")
    Optional<GatewayIntent> lockOwned(@Param("id") Long id,@Param("userId") Long userId);
}
