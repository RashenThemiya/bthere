package com.jobhub.service.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.provider.ServiceCustomFieldRequest;
import com.jobhub.entity.provider.*;
import com.jobhub.repository.provider.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderProfileFieldsTest {
    private final ServiceProviderRepository providers = mock(ServiceProviderRepository.class);
    private final ServiceProviderMarketRepository markets = mock(ServiceProviderMarketRepository.class);
    private final ProviderProfileFields service = new ProviderProfileFields(providers, markets);
    private final ObjectMapper mapper = new ObjectMapper();

    private ServiceCustomField field(String name, String type) {
        var field = new ServiceCustomField(); field.setValueSource("PROVIDER_PROFILE");
        field.setProfileField(name); field.setFieldType(type);
        field.setOptionsJson("[\"MALE\",{\"value\":\"FEMALE\",\"label\":\"Female\"}]");
        return field;
    }

    @Test void resolvesLiveProfileWithoutCopyingAndValidatesSelectChoices() {
        var provider = new ServiceProvider(); provider.setGender("FEMALE");
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var gender = field("gender", "SELECT");
        assertThat(service.resolve(15L, gender).value().asText()).isEqualTo("FEMALE");
        provider.setGender("MALE");
        assertThat(service.resolve(15L, gender).value().asText()).isEqualTo("MALE");
        provider.setGender("UNCONFIGURED");
        assertThat(service.resolve(15L, gender).status()).isEqualTo("INVALID");
        provider.setGender(" ");
        assertThat(service.resolve(15L, gender).status()).isEqualTo("MISSING");
        verifyNoInteractions(markets);
    }

    @Test void ageChangesOnBirthdayAndMissingOrFutureDobIsNotUsed() {
        var p = new ServiceProvider(); p.setDateOfBirth(LocalDate.of(2000, 10, 20));
        assertThat(ProviderProfileFields.read(p, "age", LocalDate.of(2026, 10, 19)).asInt()).isEqualTo(25);
        assertThat(ProviderProfileFields.read(p, "age", LocalDate.of(2026, 10, 20)).asInt()).isEqualTo(26);
        assertThat(ProviderProfileFields.read(p, "dateOfBirth", LocalDate.of(2026, 10, 20)).asText()).isEqualTo("2000-10-20");
        p.setDateOfBirth(LocalDate.of(2030, 1, 1));
        assertThat(ProviderProfileFields.read(p, "age", LocalDate.of(2026, 10, 20))).isNull();
        p.setDateOfBirth(null);
        assertThat(ProviderProfileFields.read(p, "dateOfBirth", LocalDate.of(2026, 10, 20))).isNull();
    }

    @Test void profileAndActiveMarketAreIsolatedByProviderId() {
        var p = new ServiceProvider(); p.setNicNumber("private-nic");
        when(providers.findById(15L)).thenReturn(Optional.of(p));
        var market = new ServiceProviderMarket(); market.setMarketId(3L);
        when(markets.findByServiceProviderIdAndStatus(15L, "ACTIVE")).thenReturn(Optional.of(market));
        assertThat(service.resolve(15L, field("marketId", "NUMBER")).value().asLong()).isEqualTo(3L);
        assertThat(service.resolve(15L, field("nicNumber", "TEXT")).value().asText()).isEqualTo("private-nic");
        assertThat(service.resolve(16L, field("nicNumber", "TEXT")).status()).isEqualTo("MISSING");
    }

    @Test void validatesAllowlistTypesScopeAndSensitivePublication() throws Exception {
        var gender = request("gender", "SELECT", "");
        assertThatCode(() -> ProviderProfileFields.validate(gender, "PROVIDER_REQUIREMENT", "SELECT")).doesNotThrowAnyException();
        assertThatThrownBy(() -> ProviderProfileFields.validate(gender, "BOOKING_FIELD", "SELECT")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderProfileFields.validate(gender, "PROVIDER_REQUIREMENT", "NUMBER")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderProfileFields.validate(request("password", "TEXT", ""), "PROVIDER_REQUIREMENT", "TEXT"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ProviderProfileFields.validate(request("gender", "SELECT", ",\"requiresApproval\":true"), "PROVIDER_REQUIREMENT", "SELECT"))
                .hasMessageContaining("reviewed evidence separately");
        assertThatThrownBy(() -> ProviderProfileFields.validate(request("nicNumber", "TEXT", ",\"customerVisible\":true"), "PROVIDER_REQUIREMENT", "TEXT"))
                .hasMessageContaining("Only profile gender and derived age");
    }

    private ServiceCustomFieldRequest request(String name, String type, String extra) throws Exception {
        return mapper.readValue("{\"scope\":\"PROVIDER_REQUIREMENT\",\"code\":\"FIELD\",\"label\":\"Field\","
                + "\"valueSource\":\"PROVIDER_PROFILE\",\"profileField\":\"" + name + "\",\"fieldType\":\"" + type + "\"" + extra + "}", ServiceCustomFieldRequest.class);
    }
}
