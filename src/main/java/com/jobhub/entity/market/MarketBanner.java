package com.jobhub.entity.market;

import com.jobhub.entity.AuditedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A promotional image shown in the mobile apps of one market. imageKey is an admin upload under the public
 * service-icons/ prefix (POST /api/v1/uploads/images). audience: CUSTOMER, PROVIDER or ALL. linkType: NONE,
 * SERVICE, OPTION or URL. startsAt/endsAt (UTC, both optional) limit when the banner is shown.
 */
@Entity
@Table(name = "market_banner",
        indexes = @Index(name = "idx_market_banner_market", columnList = "market_id, status, sort_order"))
@Getter
@Setter
@NoArgsConstructor
public class MarketBanner extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bannerId;

    @Column(name = "market_id", nullable = false)
    private Long marketId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 250)
    private String subtitle;

    @Column(name = "image_key", nullable = false, length = 200)
    private String imageKey;

    @Column(nullable = false, length = 20)
    private String audience;

    @Column(name = "link_type", nullable = false, length = 20)
    private String linkType;

    private Long serviceTypeId;
    private Long optionId;

    @Column(length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 20)
    private String status;

    private Instant startsAt;
    private Instant endsAt;

    private Long createdBy;
    private Long updatedBy;
}
