package com.jobhub.config;
import com.jobhub.repository.job.BookingRequestRepository;
import com.jobhub.service.customer.BookingRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;
import java.util.Set;
@Configuration @EnableScheduling @RequiredArgsConstructor
public class BookingRequestExpiryWorker {
    private final BookingRequestRepository requests;
    private final BookingRequestService service;
    private final com.jobhub.repository.job.BookingDispatchEventRepository events;
    private final com.jobhub.service.customer.BookingPushDelivery delivery;
    private final com.jobhub.service.customer.BookingPushGateway push;
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(BookingRequestExpiryWorker.class);
    @org.springframework.context.annotation.Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler taskScheduler(){
        var scheduler=new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);scheduler.setThreadNamePrefix("booking-workers-");return scheduler;
    }
    @Scheduled(fixedDelayString="${booking.expiry-scan-ms:5000}")
    public void scan(){
        var now=java.time.Instant.now();
        for(var r:requests.findDue(now,now.minus(java.time.Duration.ofMinutes(15)),org.springframework.data.domain.PageRequest.of(0,100))) {
            try{service.expire(r.getRequestId());}catch(RuntimeException e){log.warn("Booking request {} expiry deferred: {}",r.getRequestId(),e.getClass().getSimpleName());}
        }
    }
    @Scheduled(fixedDelayString="${booking.push-scan-ms:1000}")
    public void push(){
        if(!push.enabled())return;
        for(var e:events.findTop10ByDeliveryStatusAndNextDeliveryAtBeforeOrderByEventIdAsc("PENDING",java.time.Instant.now())) {
            try{delivery.deliver(e.getEventId());}catch(RuntimeException ex){log.warn("Booking event {} delivery deferred",e.getEventId());}
        }
    }
}
