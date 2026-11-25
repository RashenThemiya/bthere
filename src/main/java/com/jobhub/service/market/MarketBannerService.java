package com.jobhub.service.market;

import com.jobhub.dto.market.BannerApi.AdminBanner;
import com.jobhub.dto.market.BannerApi.Banner;
import com.jobhub.dto.market.BannerApi.Input;
import com.jobhub.dto.market.BannerApi.MarketBanners;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.MarketBanner;
import com.jobhub.exception.InvalidInputException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketBannerRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.S3ImageUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Market banners. Images are admin uploads under the public service-icons/ prefix, so the stored key becomes a
 * URL the app loads without a token. The app list holds only banners that are ACTIVE, inside their schedule, for
 * the audience, and whose linked service/option is still offered in the market.
 */
@Service
@RequiredArgsConstructor
public class MarketBannerService {
    public static final int MAX_PER_MARKET = 20;
    private static final Pattern IMAGE_KEY = Pattern.compile("service-icons/[A-Za-z0-9_-]{1,100}\\.(?:jpg|png|webp)");

    private final MarketBannerRepository banners;
    private final MarketRepository markets;
    private final MarketServiceOfferingRepository offerings;
    private final ServiceProviderTypeRepository services;
    private final ServiceOptionRepository options;
    private final S3ImageUploadService images;
    private final AuditService audit;
    private Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public List<AdminBanner> list(Long marketId) {
        market(marketId);
        return banners.findByMarketIdOrderBySortOrderAscBannerIdAsc(marketId).stream().map(this::admin).toList();
    }

    @Transactional(readOnly = true)
    public AdminBanner get(Long bannerId) {
        return admin(banner(bannerId));
    }

    @Transactional
    public AdminBanner create(Long adminId, Long marketId, Input in) {
        market(marketId);
        if (banners.countByMarketId(marketId) >= MAX_PER_MARKET) {
            throw new InvalidInputException("A market can have at most " + MAX_PER_MARKET + " banners",
                    "BANNER_LIMIT_REACHED");
        }
        MarketBanner b = new MarketBanner();
        b.setMarketId(marketId);
        b.setCreatedBy(adminId);
        List<MarketBanner> existing = banners.findByMarketIdOrderBySortOrderAscBannerIdAsc(marketId);
        b.setSortOrder(existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSortOrder() + 1);
        apply(b, in, adminId);
        MarketBanner saved = banners.save(b);
        audit.record(adminId, "MARKET_BANNER_CREATED", "MARKET_BANNER", saved.getBannerId(), null, admin(saved));
        return admin(saved);
    }

    @Transactional
    public AdminBanner update(Long adminId, Long bannerId, Input in) {
        MarketBanner b = banner(bannerId);
        AdminBanner before = admin(b);
        apply(b, in, adminId);
        audit.record(adminId, "MARKET_BANNER_UPDATED", "MARKET_BANNER", bannerId, before, admin(b));
        return admin(banners.save(b));
    }

    @Transactional
    public AdminBanner updateStatus(Long adminId, Long bannerId, String status) {
        MarketBanner b = banner(bannerId);
        String old = b.getStatus();
        b.setStatus(status);
        b.setUpdatedBy(adminId);
        audit.record(adminId, "MARKET_BANNER_STATUS", "MARKET_BANNER", bannerId, Map.of("status", old),
                Map.of("status", status));
        return admin(banners.save(b));
    }

    @Transactional
    public List<AdminBanner> reorder(Long adminId, Long marketId, List<Long> bannerIds) {
        market(marketId);
        List<MarketBanner> all = banners.findByMarketIdOrderBySortOrderAscBannerIdAsc(marketId);
        Set<Long> current = new HashSet<>(all.stream().map(MarketBanner::getBannerId).toList());
        if (bannerIds.size() != current.size() || !current.equals(new HashSet<>(bannerIds))) {
            throw new InvalidInputException("bannerIds must list every banner of this market exactly once",
                    "BANNER_ORDER_MISMATCH");
        }
        for (MarketBanner b : all) {
            b.setSortOrder(bannerIds.indexOf(b.getBannerId()));
            b.setUpdatedBy(adminId);
        }
        banners.saveAll(all);
        audit.record(adminId, "MARKET_BANNER_REORDERED", "MARKET", marketId, null, Map.of("bannerIds", bannerIds));
        return list(marketId);
    }

    @Transactional
    public void delete(Long adminId, Long bannerId) {
        MarketBanner b = banner(bannerId);
        audit.record(adminId, "MARKET_BANNER_DELETED", "MARKET_BANNER", bannerId, admin(b), null);
        banners.delete(b);
    }

    /** The app carousel: audience CUSTOMER or PROVIDER also gets banners marked ALL. */
    @Transactional(readOnly = true)
    public MarketBanners live(Long marketId, String audience) {
        if (!"CUSTOMER".equals(audience) && !"PROVIDER".equals(audience)) {
            throw new InvalidInputException("audience must be CUSTOMER or PROVIDER", "BANNER_AUDIENCE_INVALID");
        }
        Market m = market(marketId);
        if (!"ACTIVE".equals(m.getStatus())) throw new ResourceNotFoundException("Market not found");
        List<Banner> list = banners.findByMarketIdAndStatusOrderBySortOrderAscBannerIdAsc(marketId, "ACTIVE").stream()
                .filter(b -> b.getAudience().equals(audience) || "ALL".equals(b.getAudience()))
                .filter(b -> "LIVE".equals(visibility(b)))
                .map(b -> new Banner(b.getBannerId(), b.getTitle(), b.getSubtitle(), images.publicUrl(b.getImageKey()),
                        b.getLinkType(), b.getServiceTypeId(), b.getOptionId(), b.getLinkUrl(), b.getSortOrder(),
                        b.getEndsAt()))
                .toList();
        return new MarketBanners(marketId, audience, list);
    }

    private void apply(MarketBanner b, Input in, Long adminId) {
        String key = imageKey(in);
        String audience = in.audience() == null ? "CUSTOMER" : in.audience();
        String linkType = in.linkType() == null ? "NONE" : in.linkType();
        if (in.startsAt() != null && in.endsAt() != null && !in.endsAt().isAfter(in.startsAt())) {
            throw new InvalidInputException("endsAt must be after startsAt", "BANNER_SCHEDULE_INVALID");
        }
        Long serviceTypeId = null, optionId = null;
        String url = null;
        switch (linkType) {
            case "SERVICE", "OPTION" -> {
                serviceTypeId = in.serviceTypeId();
                optionId = "OPTION".equals(linkType) ? in.optionId() : null;
                if (serviceTypeId == null || "OPTION".equals(linkType) && optionId == null) {
                    throw new InvalidInputException("OPTION".equals(linkType)
                            ? "serviceTypeId and optionId are required for an OPTION link"
                            : "serviceTypeId is required for a SERVICE link", "BANNER_LINK_INVALID");
                }
                if (!targetAvailable(b.getMarketId(), serviceTypeId, optionId)) {
                    throw new InvalidInputException("The linked service or option is not active in this market",
                            "BANNER_LINK_UNAVAILABLE");
                }
            }
            case "URL" -> url = httpsUrl(in.linkUrl());
            default -> { }
        }
        b.setTitle(in.title().trim());
        b.setSubtitle(in.subtitle() == null || in.subtitle().isBlank() ? null : in.subtitle().trim());
        b.setImageKey(key);
        b.setAudience(audience);
        b.setLinkType(linkType);
        b.setServiceTypeId(serviceTypeId);
        b.setOptionId(optionId);
        b.setLinkUrl(url);
        b.setStatus(in.status() == null ? (b.getStatus() == null ? "ACTIVE" : b.getStatus()) : in.status());
        b.setStartsAt(in.startsAt());
        b.setEndsAt(in.endsAt());
        if (in.sortOrder() != null) b.setSortOrder(Math.max(0, in.sortOrder()));
        b.setUpdatedBy(adminId);
    }

    private String imageKey(Input in) {
        String key = in.imageKey() == null || in.imageKey().isBlank() ? null : in.imageKey().trim();
        if (key == null && in.imageUrl() != null && !in.imageUrl().isBlank()) {
            String url = in.imageUrl().trim();
            int at = url.lastIndexOf("service-icons/");
            key = at < 0 ? null : url.substring(at);
            if (key != null && !url.equals(images.publicUrl(key))) key = null;
            if (key == null) {
                throw new InvalidInputException("imageUrl must be an image uploaded with POST /api/v1/uploads/images",
                        "BANNER_IMAGE_INVALID");
            }
        }
        if (key == null) throw new InvalidInputException("imageKey or imageUrl is required", "BANNER_IMAGE_REQUIRED");
        if (!IMAGE_KEY.matcher(key).matches()) {
            throw new InvalidInputException(
                    "imageKey must be a JPG, PNG or WebP uploaded by an admin with POST /api/v1/uploads/images",
                    "BANNER_IMAGE_INVALID");
        }
        if (!images.exists(key)) throw new InvalidInputException("Uploaded image does not exist", "BANNER_IMAGE_MISSING");
        return key;
    }

    private static String httpsUrl(String value) {
        try {
            URI uri = value == null ? null : URI.create(value.trim());
            if (uri != null && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null) return uri.toString();
        } catch (IllegalArgumentException ignored) {
            // falls through to the error below
        }
        throw new InvalidInputException("linkUrl must be a valid https:// URL", "BANNER_LINK_INVALID");
    }

    private boolean targetAvailable(Long marketId, Long serviceTypeId, Long optionId) {
        if (services.findById(serviceTypeId).filter(s -> "ACTIVE".equals(s.getStatus())).isEmpty()) return false;
        if (offerings.findByMarketIdAndServiceProviderTypeId(marketId, serviceTypeId)
                .filter(o -> "ACTIVE".equals(o.getStatus())).isEmpty()) return false;
        return optionId == null || options.findByOptionIdAndServiceProviderTypeId(optionId, serviceTypeId)
                .filter(o -> o.activeForService(serviceTypeId)).isPresent();
    }

    private String visibility(MarketBanner b) {
        if (!"ACTIVE".equals(b.getStatus())) return "INACTIVE";
        Instant now = clock.instant();
        if (b.getEndsAt() != null && !now.isBefore(b.getEndsAt())) return "ENDED";
        if (b.getStartsAt() != null && now.isBefore(b.getStartsAt())) return "SCHEDULED";
        if (b.getServiceTypeId() != null && !targetAvailable(b.getMarketId(), b.getServiceTypeId(), b.getOptionId())) {
            return "TARGET_UNAVAILABLE";
        }
        return "LIVE";
    }

    private AdminBanner admin(MarketBanner b) {
        return new AdminBanner(b.getBannerId(), b.getMarketId(), b.getTitle(), b.getSubtitle(), b.getImageKey(),
                images.publicUrl(b.getImageKey()), b.getAudience(), b.getLinkType(), b.getServiceTypeId(),
                b.getOptionId(), b.getLinkUrl(), b.getSortOrder(), b.getStatus(), b.getStartsAt(), b.getEndsAt(),
                visibility(b), b.getCreatedAt(), b.getUpdatedAt());
    }

    private Market market(Long marketId) {
        return markets.findById(marketId).orElseThrow(() -> new ResourceNotFoundException("Market not found"));
    }

    private MarketBanner banner(Long bannerId) {
        return banners.findById(bannerId).orElseThrow(() -> new ResourceNotFoundException("Banner not found"));
    }
}
