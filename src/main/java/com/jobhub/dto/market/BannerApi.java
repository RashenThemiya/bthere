package com.jobhub.dto.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/** Market banners: admin management and the mobile carousel. */
public final class BannerApi {
    private BannerApi() {}

    /**
     * Create or replace a banner. Send imageKey (the "key" from POST /api/v1/uploads/images as SUPER_ADMIN) or
     * the upload's imageUrl. audience defaults to CUSTOMER, linkType to NONE, status to ACTIVE; sortOrder null
     * puts a new banner last (and keeps the current position on update).
     */
    public record Input(
            @NotBlank @Size(max = 120) String title,
            @Size(max = 250) String subtitle,
            @Size(max = 200) String imageKey,
            @Size(max = 600) String imageUrl,
            @Pattern(regexp = "CUSTOMER|PROVIDER|ALL") String audience,
            @Pattern(regexp = "NONE|SERVICE|OPTION|URL") String linkType,
            Long serviceTypeId,
            Long optionId,
            @Size(max = 500) String linkUrl,
            Integer sortOrder,
            @Pattern(regexp = "ACTIVE|INACTIVE") String status,
            Instant startsAt,
            Instant endsAt) {}

    public record StatusUpdate(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {}

    /** Every banner id of the market, in the new display order. */
    public record Order(@NotEmpty List<Long> bannerIds) {}

    /**
     * visibility: LIVE (shown now), SCHEDULED (startsAt in the future), ENDED (endsAt passed), INACTIVE
     * (switched off) or TARGET_UNAVAILABLE (linked service/option is inactive or not offered in this market).
     */
    public record AdminBanner(Long bannerId, Long marketId, String title, String subtitle, String imageKey,
                              String imageUrl, String audience, String linkType, Long serviceTypeId, Long optionId,
                              String linkUrl, int sortOrder, String status, Instant startsAt, Instant endsAt,
                              String visibility, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    /** What the app shows: in sortOrder, already filtered to live banners for the audience. */
    public record Banner(Long bannerId, String title, String subtitle, String imageUrl, String linkType,
                         Long serviceTypeId, Long optionId, String linkUrl, int sortOrder, Instant endsAt) {}

    public record MarketBanners(Long marketId, String audience, List<Banner> banners) {}
}
