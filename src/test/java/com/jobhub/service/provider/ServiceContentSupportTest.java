package com.jobhub.service.provider;

import com.jobhub.dto.provider.ServiceMediaPayload;
import com.jobhub.dto.provider.ServiceResponsibilityPayload;
import com.jobhub.entity.provider.ServiceMediaValue;
import com.jobhub.entity.provider.ServiceResponsibilityValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServiceContentSupportTest {

    @Test
    void markerIsSeparateFromCardAndGalleryAndCanReuseTheSameAsset() {
        List<ServiceMediaValue> target = new ArrayList<>();
        var shared = media("icons/service.png", 0);
        ServiceContentSupport.replaceMedia(target, shared, null, List.of(), shared);
        assertEquals(shared.key(), ServiceContentSupport.icon(target).key());
        assertEquals(shared.key(), ServiceContentSupport.mapMarkerIcon(target).key());
        assertTrue(ServiceContentSupport.gallery(target).isEmpty());
        ServiceContentSupport.replaceMedia(target, shared, null, List.of(), null);
        assertNotNull(ServiceContentSupport.icon(target));
        assertNull(ServiceContentSupport.mapMarkerIcon(target));
    }

    @Test
    void replacesAndClassifiesStructuredMedia() {
        List<ServiceMediaValue> target = new ArrayList<>();
        ServiceContentSupport.replaceMedia(target,
                media("icons/service.png", 9),
                media("covers/service.jpg", 8),
                List.of(media("gallery/one.jpg", 2), media("gallery/two.jpg", 1)));

        assertEquals(4, target.size());
        assertEquals("icons/service.png", ServiceContentSupport.icon(target).key());
        assertEquals("covers/service.jpg", ServiceContentSupport.cover(target).key());
        assertEquals(2, ServiceContentSupport.gallery(target).size());
    }

    @Test
    void rejectsDuplicateMediaKeys() {
        List<ServiceMediaValue> target = new ArrayList<>();
        assertThrows(IllegalArgumentException.class, () ->
                ServiceContentSupport.replaceMedia(target,
                        media("same.png", 0), null,
                        List.of(media("same.png", 1))));
    }

    @Test
    void normalizesResponsibilityParty() {
        List<ServiceResponsibilityValue> target = new ArrayList<>();
        ServiceContentSupport.replaceResponsibilities(target, List.of(
                new ServiceResponsibilityPayload("provider", "Arrive on time",
                        "Be punctual", true, 1)));

        assertEquals("PROVIDER", target.get(0).getResponsibleParty());
        assertTrue(target.get(0).isRequired());
    }

    private ServiceMediaPayload media(String key, int order) {
        return new ServiceMediaPayload(key, "https://cdn.example.com/" + key,
                "Image", order);
    }
}
