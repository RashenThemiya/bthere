package com.jobhub.service.customer;
import com.jobhub.repository.auth.UserDeviceRepository;
import com.jobhub.repository.job.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
@Service @RequiredArgsConstructor
public class BookingPushDelivery {
    private final BookingDispatchEventRepository events;
    private final BookingOfferRepository offers;
    private final UserDeviceRepository devices;
    private final BookingPushGateway push;
    @Transactional
    public void deliver(Long id) {
        var e=events.lockById(id).orElseThrow();
        if(!"PENDING".equals(e.getDeliveryStatus()) || !push.enabled())return;
        if((e.getExpiresAt()!=null && !Instant.now().isBefore(e.getExpiresAt()))
                || Instant.now().isAfter(e.getCreatedAt().plus(Duration.ofHours(1)))
                || "BOOKING_OFFER".equals(e.getEventType()) && offers.findById(e.getOfferId()).filter(o -> "PENDING".equals(o.getStatus())).isEmpty()) {
            e.setDeliveryStatus("EXPIRED");return;
        }
        boolean failed=false;
        int accepted=0;
        var targets=devices.findAllByUserIdAndStatusOrderByDeviceIdDesc(e.getUserId(),"ACTIVE");
        if(targets.isEmpty())failed=true;
        for(var device:targets.stream().limit(10).toList()) {
            try{if(!push.send(device.getDeviceToken(),e))device.setStatus("INACTIVE");else accepted++;}
            catch(Exception ex){if(ex instanceof InterruptedException)Thread.currentThread().interrupt();failed=true;}
        }
        if(accepted==0)failed=true;
        e.setDeliveryAttempts(e.getDeliveryAttempts()+1);
        if(!failed)e.setDeliveryStatus("DELIVERED");
        else if(e.getDeliveryAttempts()>=8)e.setDeliveryStatus("FAILED");
        else e.setNextDeliveryAt(Instant.now().plusSeconds(Math.min(60,1L<<e.getDeliveryAttempts())));
    }
}
