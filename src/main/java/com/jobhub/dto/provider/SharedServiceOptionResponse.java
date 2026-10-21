package com.jobhub.dto.provider;
import java.util.List;

public record SharedServiceOptionResponse(Long optionId, String code, String name,
        String description, List<ServiceLink> services) {
    public record ServiceLink(Long serviceTypeId, Integer displayOrder, String status, Long mapMarkerFieldId) {}
}
