package com.jobhub.dto.market;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateMarketRequest(
        @NotBlank(message = "Market name is required")
        @Size(max = 150, message = "Market name must not exceed 150 characters")
        String name,

        @NotBlank(message = "Country code is required")
        @Pattern(regexp = "^[A-Z]{2}$", message = "Country code must use ISO alpha-2 format")
        String countryCode,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must use ISO format")
        String defaultCurrency,

        @NotBlank(message = "Timezone is required")
        String timezone,

        @NotBlank(message = "Locale is required")
        String locale,

        @NotBlank(message = "Phone country code is required")
        @Pattern(regexp = "^\\+[1-9][0-9]{0,3}$", message = "Phone country code must start with +")
        String phoneCountryCode
) {
}
