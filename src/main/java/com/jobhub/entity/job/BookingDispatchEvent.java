package com.jobhub.entity.job;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
/** Durable notification outbox. Consumers must stop ringing after the server offer expires. */
@Entity @Table(name="booking_dispatch_event",indexes={
        @Index(name="idx_booking_event_user",columnList="user_id,event_id"),
        @Index(name="idx_booking_event_delivery",columnList="delivery_status,next_delivery_at")}) @Getter @Setter @NoArgsConstructor
public class BookingDispatchEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long eventId;
    private Long userId;
    private Long requestId;
    private Long offerId;
    private String eventType;
    private Instant createdAt;
    private Instant expiresAt;
    private String deliveryStatus="PENDING";
    private int deliveryAttempts;
    private Instant nextDeliveryAt;
}
