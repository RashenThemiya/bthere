package com.jobhub.service.provider;

import com.jobhub.dto.provider.ServiceMediaPayload;
import com.jobhub.dto.provider.ServiceResponsibilityPayload;
import com.jobhub.entity.provider.ServiceMediaValue;
import com.jobhub.entity.provider.ServiceResponsibilityValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ServiceContentSupport {
    private static final Set<String> PARTIES = Set.of("PROVIDER", "CUSTOMER", "PLATFORM");

    private ServiceContentSupport() {}

    static void replaceMedia(List<ServiceMediaValue> target,
                             ServiceMediaPayload icon,
                             ServiceMediaPayload cover,
                             List<ServiceMediaPayload> gallery) {
        replaceMedia(target, icon, cover, gallery, null);
    }

    static void replaceMedia(List<ServiceMediaValue> target,
                             ServiceMediaPayload icon,
                             ServiceMediaPayload cover,
                             List<ServiceMediaPayload> gallery,
                             ServiceMediaPayload mapMarkerIcon) {
        List<ServiceMediaPayload> safeGallery = gallery == null ? List.of() : gallery;
        Set<String> keys = new HashSet<>();
        Set<Integer> orders = new HashSet<>();
        List<ServiceMediaValue> replacement = new ArrayList<>();
        if (icon != null) replacement.add(media("ICON", icon, 0, keys));
        if (cover != null) replacement.add(media("COVER", cover, 0, keys));
        // Marker assets are independent and may intentionally reuse a card icon's file.
        if (mapMarkerIcon != null) replacement.add(media("MAP_MARKER", mapMarkerIcon, 0, new HashSet<>()));
        for (int index = 0; index < safeGallery.size(); index++) {
            ServiceMediaPayload item = safeGallery.get(index);
            int order = item.displayOrder() == null ? index : item.displayOrder();
            if (order < 0) throw new IllegalArgumentException("Media display order cannot be negative");
            if (!orders.add(order)) {
                throw new IllegalArgumentException("Gallery display orders must be unique");
            }
            replacement.add(media("GALLERY", item, order, keys));
        }
        target.clear();
        target.addAll(replacement);
    }

    static void replaceResponsibilities(List<ServiceResponsibilityValue> target,
                                        List<ServiceResponsibilityPayload> requests) {
        List<ServiceResponsibilityPayload> safe = requests == null ? List.of() : requests;
        Set<Integer> orders = new HashSet<>();
        List<ServiceResponsibilityValue> replacement = new ArrayList<>();
        for (int index = 0; index < safe.size(); index++) {
            ServiceResponsibilityPayload request = safe.get(index);
            String party = request.responsibleParty().trim().toUpperCase(Locale.ROOT);
            if (!PARTIES.contains(party)) {
                throw new IllegalArgumentException(
                        "Responsible party must be PROVIDER, CUSTOMER or PLATFORM");
            }
            int order = request.displayOrder() == null ? index : request.displayOrder();
            if (!orders.add(order)) {
                throw new IllegalArgumentException("Responsibility display orders must be unique");
            }
            ServiceResponsibilityValue item = new ServiceResponsibilityValue();
            item.setResponsibleParty(party);
            item.setTitle(request.title().trim());
            item.setDescription(optional(request.description()));
            item.setRequired(request.required());
            item.setDisplayOrder(order);
            replacement.add(item);
        }
        target.clear();
        target.addAll(replacement);
    }

    static ServiceMediaPayload icon(List<ServiceMediaValue> media) {
        return first(media, "ICON");
    }

    public static ServiceMediaPayload mapMarkerIcon(List<ServiceMediaValue> media) {
        return first(media, "MAP_MARKER");
    }

    static ServiceMediaPayload cover(List<ServiceMediaValue> media) {
        return first(media, "COVER");
    }

    static List<ServiceMediaPayload> gallery(List<ServiceMediaValue> media) {
        if (media == null) return List.of();
        return media.stream().filter(item -> "GALLERY".equals(item.getMediaType()))
                .map(ServiceContentSupport::payload).toList();
    }

    static List<ServiceResponsibilityPayload> responsibilities(
            List<ServiceResponsibilityValue> responsibilities) {
        if (responsibilities == null) return List.of();
        return responsibilities.stream().map(item -> new ServiceResponsibilityPayload(
                item.getResponsibleParty(), item.getTitle(), item.getDescription(),
                item.isRequired(), item.getDisplayOrder())).toList();
    }

    private static ServiceMediaValue media(String type, ServiceMediaPayload request,
                                           int order, Set<String> keys) {
        String key = request.key().trim();
        if (!keys.add(key)) throw new IllegalArgumentException("Media keys must be unique");
        ServiceMediaValue item = new ServiceMediaValue();
        item.setMediaType(type);
        item.setObjectKey(key);
        item.setUrl(request.url().trim());
        item.setAltText(optional(request.altText()));
        item.setDisplayOrder(order);
        return item;
    }

    private static ServiceMediaPayload first(List<ServiceMediaValue> media, String type) {
        if (media == null) return null;
        return media.stream().filter(item -> type.equals(item.getMediaType()))
                .findFirst().map(ServiceContentSupport::payload).orElse(null);
    }

    private static ServiceMediaPayload payload(ServiceMediaValue item) {
        return new ServiceMediaPayload(item.getObjectKey(), item.getUrl(),
                item.getAltText(), item.getDisplayOrder());
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
