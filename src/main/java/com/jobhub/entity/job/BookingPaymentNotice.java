package com.jobhub.entity.job;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
/** Verified callback audit; no raw card/contact data or merchant secrets. */
@Entity @Table(name="booking_payment_notice",uniqueConstraints=@UniqueConstraint(
        name="uk_booking_payment_notice",columnNames={"payment_id","status_code"}))
@Getter @Setter @NoArgsConstructor
public class BookingPaymentNotice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long noticeId;
    private Long requestId;
    @Column(length=100) private String paymentId;
    private String statusCode;
    private Instant receivedAt;
}
