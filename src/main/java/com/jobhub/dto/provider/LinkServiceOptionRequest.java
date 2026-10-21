package com.jobhub.dto.provider;
import jakarta.validation.constraints.Min;
public record LinkServiceOptionRequest(@Min(0) Integer displayOrder,
        @jakarta.validation.constraints.Positive Long mapMarkerFieldId) {
    public LinkServiceOptionRequest(Integer displayOrder) { this(displayOrder, null); }
}
