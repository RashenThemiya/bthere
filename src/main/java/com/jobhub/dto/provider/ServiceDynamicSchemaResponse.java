package com.jobhub.dto.provider;

import java.util.List;

public record ServiceDynamicSchemaResponse(
        Long serviceTypeId,
        String serviceName,
        ServiceSetupResponse serviceSetup,
        List<ServiceCustomFieldResponse> commonFields,
        List<OptionSchema> options
) {
    public record OptionSchema(
            Long optionId, String code, String name,
            List<ServiceCustomFieldResponse> fields
    ) {}
}
