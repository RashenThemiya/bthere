package com.jobhub.service.customer;
import com.jobhub.entity.auth.UserDevice;
import com.jobhub.entity.job.*;
import com.jobhub.repository.auth.UserDeviceRepository;
import com.jobhub.repository.job.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class BookingPushDeliveryTest {
    BookingDispatchEventRepository events=mock(BookingDispatchEventRepository.class);
    BookingOfferRepository offers=mock(BookingOfferRepository.class);
    UserDeviceRepository devices=mock(UserDeviceRepository.class);
    BookingPushGateway push=mock(BookingPushGateway.class);
    BookingPushDelivery service=new BookingPushDelivery(events,offers,devices,push);
    BookingDispatchEvent event;
    @BeforeEach void setup(){
        event=new BookingDispatchEvent();event.setEventId(1L);event.setUserId(9L);event.setRequestId(2L);
        event.setEventType("BOOKING_CONFIRMED");event.setCreatedAt(Instant.now());
        when(events.lockById(1L)).thenReturn(Optional.of(event));when(push.enabled()).thenReturn(true);
    }
    @Test void expiredOfferNeverRings(){
        event.setEventType("BOOKING_OFFER");event.setExpiresAt(Instant.now().minusSeconds(1));service.deliver(1L);
        assertThat(event.getDeliveryStatus()).isEqualTo("EXPIRED");verifyNoInteractions(devices);
    }
    @Test void acceptedOfferIsNotSentAgainEvenBeforeOriginalDeadline(){
        event.setEventType("BOOKING_OFFER");event.setOfferId(3L);event.setExpiresAt(Instant.now().plusSeconds(60));
        var offer=new BookingOffer();offer.setStatus("ACCEPTED");when(offers.findById(3L)).thenReturn(Optional.of(offer));
        service.deliver(1L);assertThat(event.getDeliveryStatus()).isEqualTo("EXPIRED");verifyNoInteractions(devices);
    }
    @Test void onlyOwnersDevicesReceiveEventAndDuplicateWorkerDeliveryIsIgnored() throws Exception{
        var device=new UserDevice();device.setDeviceToken("device-token");
        when(devices.findAllByUserIdAndStatusOrderByDeviceIdDesc(9L,"ACTIVE")).thenReturn(List.of(device));
        when(push.send("device-token",event)).thenReturn(true);service.deliver(1L);service.deliver(1L);
        assertThat(event.getDeliveryStatus()).isEqualTo("DELIVERED");verify(push,times(1)).send("device-token",event);
    }
    @Test void failedDeliveryBacksOffAndHasBoundedRetries() throws Exception{
        when(devices.findAllByUserIdAndStatusOrderByDeviceIdDesc(9L,"ACTIVE")).thenReturn(List.of());
        service.deliver(1L);assertThat(event.getNextDeliveryAt()).isAfter(Instant.now());
        for(int i=1;i<8;i++)service.deliver(1L);assertThat(event.getDeliveryStatus()).isEqualTo("FAILED");
    }
}
