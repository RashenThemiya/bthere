package com.jobhub.repository.job;
import com.jobhub.entity.job.BookingDispatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface BookingDispatchEventRepository extends JpaRepository<BookingDispatchEvent,Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<BookingDispatchEvent> {
    List<BookingDispatchEvent> findTop100ByUserIdAndEventIdGreaterThanOrderByEventIdAsc(Long userId,Long after);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from BookingDispatchEvent e where e.eventId=:id")
    java.util.Optional<BookingDispatchEvent> lockById(@org.springframework.data.repository.query.Param("id") Long id);
    List<BookingDispatchEvent> findTop10ByDeliveryStatusAndNextDeliveryAtBeforeOrderByEventIdAsc(String status,java.time.Instant now);
}
