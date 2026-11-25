package com.jobhub.service.market;

import com.jobhub.dto.market.BannerApi.AdminBanner;
import com.jobhub.dto.market.BannerApi.Input;
import com.jobhub.entity.market.Market;
import com.jobhub.entity.market.MarketBanner;
import com.jobhub.entity.market.MarketServiceOffering;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.entity.provider.ServiceProviderType;
import com.jobhub.exception.InvalidInputException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.market.MarketBannerRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.upload.S3ImageUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MarketBannerServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-25T06:00:00Z");
    private static final String KEY = "service-icons/3f6c1c0e-9a51-4c1b-8d55-0f1c2a9b7e10.jpg";
    private static final String URL = "https://bucket.s3.eu-north-1.amazonaws.com/" + KEY;

    @Mock MarketBannerRepository banners;
    @Mock MarketRepository markets;
    @Mock MarketServiceOfferingRepository offerings;
    @Mock ServiceProviderTypeRepository services;
    @Mock ServiceOptionRepository options;
    @Mock S3ImageUploadService images;
    @Mock AuditService audit;
    @InjectMocks MarketBannerService service;

    private final List<MarketBanner> stored = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "clock", Clock.fixed(NOW, ZoneOffset.UTC));
        Market market = new Market();
        market.setMarketId(1L);
        market.setStatus("ACTIVE");
        when(markets.findById(1L)).thenReturn(Optional.of(market));
        when(images.publicUrl(anyString())).thenAnswer(i -> "https://bucket.s3.eu-north-1.amazonaws.com/" + i.getArgument(0));
        when(images.exists(anyString())).thenReturn(true);
        when(banners.save(any())).thenAnswer(i -> {
            MarketBanner b = i.getArgument(0);
            if (b.getBannerId() == null) {
                b.setBannerId((long) stored.size() + 1);
                stored.add(b);
            }
            return b;
        });
        when(banners.countByMarketId(1L)).thenAnswer(i -> (long) stored.size());
        when(banners.findByMarketIdOrderBySortOrderAscBannerIdAsc(1L)).thenAnswer(i -> sorted());
        when(banners.findByMarketIdAndStatusOrderBySortOrderAscBannerIdAsc(eq(1L), eq("ACTIVE")))
                .thenAnswer(i -> sorted().stream().filter(b -> "ACTIVE".equals(b.getStatus())).toList());
        when(banners.findById(anyLong())).thenAnswer(i -> stored.stream()
                .filter(b -> b.getBannerId().equals(i.getArgument(0))).findFirst());

        ServiceProviderType caregiver = new ServiceProviderType();
        caregiver.setStatus("ACTIVE");
        when(services.findById(3L)).thenReturn(Optional.of(caregiver));
        MarketServiceOffering offered = new MarketServiceOffering();
        offered.setStatus("ACTIVE");
        when(offerings.findByMarketIdAndServiceProviderTypeId(1L, 3L)).thenReturn(Optional.of(offered));
        ServiceOption homeCare = mock(ServiceOption.class);
        when(homeCare.activeForService(3L)).thenReturn(true);
        when(options.findByOptionIdAndServiceProviderTypeId(12L, 3L)).thenReturn(Optional.of(homeCare));
    }

    private List<MarketBanner> sorted() {
        return stored.stream().sorted((a, b) -> a.getSortOrder() != b.getSortOrder()
                ? Integer.compare(a.getSortOrder(), b.getSortOrder()) : a.getBannerId().compareTo(b.getBannerId())).toList();
    }

    private static Input input(String title, String audience, String linkType, Long serviceTypeId, Long optionId,
                               String linkUrl, Instant startsAt, Instant endsAt) {
        return new Input(title, null, KEY, null, audience, linkType, serviceTypeId, optionId, linkUrl, null, null,
                startsAt, endsAt);
    }

    @Test
    void appGetsOnlyLiveBannersForItsAudienceInAdminOrder() {
        service.create(9L, 1L, input("Home care offer", "CUSTOMER", "OPTION", 3L, 12L, null, null, null));
        service.create(9L, 1L, input("For everyone", "ALL", "URL", null, null, "https://example.com/offer", null, null));
        service.create(9L, 1L, input("Providers only", "PROVIDER", null, null, null, null, null, null));
        service.create(9L, 1L, input("Next week", "CUSTOMER", null, null, null, null, NOW.plusSeconds(86_400 * 7), null));
        service.create(9L, 1L, input("Expired", "CUSTOMER", null, null, null, null, null, NOW.minusSeconds(60)));
        AdminBanner off = service.create(9L, 1L, input("Off", "CUSTOMER", null, null, null, null, null, null));
        service.updateStatus(9L, off.bannerId(), "INACTIVE");
        service.reorder(9L, 1L, List.of(2L, 1L, 3L, 4L, 5L, 6L));

        var customer = service.live(1L, "CUSTOMER").banners();
        assertThat(customer).extracting(b -> b.title()).containsExactly("For everyone", "Home care offer");
        assertThat(customer.get(1).imageUrl()).isEqualTo(URL);
        assertThat(customer.get(1).linkType()).isEqualTo("OPTION");
        assertThat(customer.get(1).optionId()).isEqualTo(12L);
        assertThat(service.live(1L, "PROVIDER").banners()).extracting(b -> b.title())
                .containsExactly("For everyone", "Providers only");
        assertThat(service.list(1L)).extracting(AdminBanner::visibility)
                .containsExactly("LIVE", "LIVE", "LIVE", "SCHEDULED", "ENDED", "INACTIVE");
    }

    @Test
    void bannerLinkedToAServiceThatStopsBeingOfferedLeavesTheAppButStaysVisibleToTheAdmin() {
        service.create(9L, 1L, input("Caregivers", "CUSTOMER", "SERVICE", 3L, null, null, null, null));
        MarketServiceOffering paused = new MarketServiceOffering();
        paused.setStatus("INACTIVE");
        when(offerings.findByMarketIdAndServiceProviderTypeId(1L, 3L)).thenReturn(Optional.of(paused));

        assertThat(service.live(1L, "CUSTOMER").banners()).isEmpty();
        assertThat(service.list(1L).get(0).visibility()).isEqualTo("TARGET_UNAVAILABLE");
    }

    @Test
    void imageUrlFromTheUploadResponseIsAcceptedAndOtherImagesAreRefused() {
        Input byUrl = new Input("By url", null, null, URL, null, null, null, null, null, null, null, null, null);
        assertThat(service.create(9L, 1L, byUrl).imageKey()).isEqualTo(KEY);

        Input privateImage = new Input("Private", null, "providers/40/images/a.jpg", null, null, null, null, null,
                null, null, null, null, null);
        assertThatThrownBy(() -> service.create(9L, 1L, privateImage))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("imageKey must be");
        Input otherSite = new Input("Other", null, null, "https://evil.example/service-icons/a.jpg", null, null,
                null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(9L, 1L, otherSite))
                .isInstanceOf(InvalidInputException.class).hasMessageContaining("POST /api/v1/uploads/images");
        when(images.exists(KEY)).thenReturn(false);
        assertThatThrownBy(() -> service.create(9L, 1L, input("Gone", null, null, null, null, null, null, null)))
                .hasMessage("Uploaded image does not exist");
    }

    @Test
    void invalidLinksSchedulesOrderAndAudienceAreRejectedWithCodes() {
        assertThatThrownBy(() -> service.create(9L, 1L, input("x", null, "OPTION", 3L, null, null, null, null)))
                .isInstanceOf(InvalidInputException.class).extracting("code").isEqualTo("BANNER_LINK_INVALID");
        assertThatThrownBy(() -> service.create(9L, 1L, input("x", null, "OPTION", 3L, 99L, null, null, null)))
                .extracting("code").isEqualTo("BANNER_LINK_UNAVAILABLE");
        assertThatThrownBy(() -> service.create(9L, 1L, input("x", null, "URL", null, null, "http://a.com", null, null)))
                .extracting("code").isEqualTo("BANNER_LINK_INVALID");
        assertThatThrownBy(() -> service.create(9L, 1L, input("x", null, null, null, null, null, NOW, NOW)))
                .extracting("code").isEqualTo("BANNER_SCHEDULE_INVALID");

        service.create(9L, 1L, input("a", null, null, null, null, null, null, null));
        assertThatThrownBy(() -> service.reorder(9L, 1L, List.of(1L, 1L)))
                .extracting("code").isEqualTo("BANNER_ORDER_MISMATCH");
        assertThatThrownBy(() -> service.live(1L, "ALL"))
                .extracting("code").isEqualTo("BANNER_AUDIENCE_INVALID");
        assertThatThrownBy(() -> service.live(2L, "CUSTOMER")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void marketIsLimitedToTwentyBannersAndUpdateKeepsPositionAndStatus() {
        for (int i = 0; i < MarketBannerService.MAX_PER_MARKET; i++) {
            service.create(9L, 1L, input("b" + i, null, null, null, null, null, null, null));
        }
        assertThatThrownBy(() -> service.create(9L, 1L, input("one more", null, null, null, null, null, null, null)))
                .extracting("code").isEqualTo("BANNER_LIMIT_REACHED");

        service.updateStatus(9L, 5L, "INACTIVE");
        AdminBanner edited = service.update(9L, 5L, input("renamed", null, null, null, null, null, null, null));
        assertThat(edited.title()).isEqualTo("renamed");
        assertThat(edited.sortOrder()).isEqualTo(4);
        assertThat(edited.status()).isEqualTo("INACTIVE");
        verify(audit).record(eq(9L), eq("MARKET_BANNER_UPDATED"), eq("MARKET_BANNER"), eq(5L), any(), any());
    }
}
