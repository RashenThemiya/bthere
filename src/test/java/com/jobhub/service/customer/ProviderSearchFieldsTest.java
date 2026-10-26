package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.customer.CustomerDiscoveryApi.ProviderFilter;
import com.jobhub.dto.provider.*;
import com.jobhub.entity.provider.ServiceProviderTypeAssignment;
import com.jobhub.service.provider.ServiceCustomFieldService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderSearchFieldsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ServiceCustomFieldService fields = mock(ServiceCustomFieldService.class);
    private final com.jobhub.repository.provider.ServiceProviderRepository providers = mock(com.jobhub.repository.provider.ServiceProviderRepository.class);
    private final com.jobhub.service.upload.S3PrivateDocumentService storage = mock(com.jobhub.service.upload.S3PrivateDocumentService.class);
    private final ProviderSearchFields service = new ProviderSearchFields(fields, providers, storage);

    @Test void approvedVisibleImagesHaveFreshUrlsAndNeverBecomeSearchFilters() throws Exception {
        var assignment = new ServiceProviderTypeAssignment(); assignment.setServiceProviderId(15L);
        var provider = new com.jobhub.entity.provider.ServiceProvider(); provider.setUserId(25L);
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var visible = field(22, "IMAGE_UPLOAD", true, false);
        when(fields.listForOption(2L, 12L, false)).thenReturn(List.of(visible,
                field(23, "IMAGE_UPLOAD", false, false), field(24, "DOCUMENT_UPLOAD", true, false)));
        var definitions = service.definitions(2L, 12L);
        assertThat(definitions).extracting(ServiceCustomFieldResponse::fieldId).containsExactly(22L, 24L);
        assertThat(service.schema(definitions)).isEmpty();
        assertThat(service.displaySchema(definitions)).extracting(d -> d.fieldId()).containsExactly(22L, 24L);
        String key = "providers/25/images/photo.jpg";
        when(storage.createPublishedProviderImageUrl(25L, key)).thenReturn(
                new com.jobhub.dto.upload.PresignedUrlResponse(key, "https://example.com/signed-image",
                        java.time.Instant.parse("2030-01-01T00:10:00Z")));
        when(storage.createPublishedProviderImageUrl(25L, "providers/99/images/private.jpg"))
                .thenThrow(new IllegalArgumentException("foreign"));
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(22, "{\"files\":[{\"key\":\"" + key + "\",\"url\":\"https://untrusted.invalid\"},\"providers/99/images/private.jpg\"]}", "APPROVED", true),
                answer(23, "[\"providers/25/images/private.jpg\"]", "APPROVED", false)));
        var images = service.attributes(assignment, definitions).get(22L);
        assertThat(images.size()).isEqualTo(1);
        assertThat(images.get(0).path("url").asText()).isEqualTo("https://example.com/signed-image");
        assertThat(images.get(0).path("expiresAt").asText()).isEqualTo("2030-01-01T00:10:00Z");
        assertThatIllegalArgumentException().isThrownBy(() -> service.validate(definitions, List.of(choice(22, "[\"A\"]"))));
        clearInvocations(storage);
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(answer(22, "[\"" + key + "\"]", "PENDING", false)));
        assertThat(service.attributes(assignment, definitions)).isEmpty();
        verifyNoInteractions(storage);
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(answer(22, "[\"" + key + "\"]", "APPROVED", false)));
        assertThat(service.attributes(assignment, List.of(field(22, "IMAGE_UPLOAD", false, false)))).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test void publicScalarFieldsPreserveValuesAndRemainDisplayOnly() throws Exception {
        var assignment = new ServiceProviderTypeAssignment();
        var types = List.of("TEXT", "TEXTAREA", "DATE", "TIME", "PHONE", "EMAIL");
        var values = List.of("Name", "Long description", "2026-10-26", "09:30", "+94771234567", "provider@example.com");
        var definitions = new ArrayList<ServiceCustomFieldResponse>();
        var answers = new ArrayList<ProviderCustomSubmissionResponse>();
        for (int i = 0; i < types.size(); i++) {
            definitions.add(field(i + 1, types.get(i), true, false));
            answers.add(answer(i + 1, mapper.writeValueAsString(values.get(i)), "APPROVED", false));
        }
        when(fields.listForOption(3L, 12L, false)).thenReturn(definitions);
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(answers);
        assertThat(service.definitions(3L, 12L)).hasSize(6);
        assertThat(service.schema(definitions)).isEmpty();
        assertThat(service.displaySchema(definitions)).hasSize(6);
        var attributes = service.attributes(assignment, definitions);
        for (int i = 0; i < values.size(); i++) assertThat(attributes.get((long)i + 1).asText()).isEqualTo(values.get(i));
        assertThatIllegalArgumentException().isThrownBy(() -> service.validate(
                List.of(field(1, "TEXT", true, true)), List.of(choice(1, "[\"A\"]"))));
    }

    @Test void publicDocumentsAndFilesUseOwnedSignedUrlsAndHideUnapprovedAndPrivateAnswers() throws Exception {
        var assignment = new ServiceProviderTypeAssignment(); assignment.setServiceProviderId(15L);
        var provider = new com.jobhub.entity.provider.ServiceProvider(); provider.setUserId(25L);
        when(providers.findById(15L)).thenReturn(Optional.of(provider));
        var definitions = List.of(field(31, "DOCUMENT_UPLOAD", true, false), field(32, "FILE_UPLOAD", true, false),
                field(33, "FILE_UPLOAD", false, false));
        String key = "providers/25/documents/certificate.pdf";
        when(storage.createPublishedProviderFileUrl(25L, key)).thenReturn(
                new com.jobhub.dto.upload.PresignedUrlResponse(key, "https://example.com/document",
                        java.time.Instant.parse("2030-01-01T00:10:00Z")));
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(31, "{\"files\":[{\"key\":\"" + key + "\"}]}", "APPROVED", true),
                answer(32, "[\"" + key + "\"]", "APPROVED", false),
                answer(33, "[\"" + key + "\"]", "APPROVED", false)));
        var attributes = service.attributes(assignment, definitions);
        assertThat(attributes).containsOnlyKeys(31L, 32L);
        assertThat(attributes.get(31L).get(0).path("url").asText()).isEqualTo("https://example.com/document");
        assertThat(attributes.get(32L).get(0).path("key").asText()).isEqualTo(key);
        clearInvocations(storage);
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(31, "[\"" + key + "\"]", "PENDING", false),
                answer(32, "[\"" + key + "\"]", "REJECTED", false)));
        assertThat(service.attributes(assignment, definitions)).isEmpty();
        verifyNoInteractions(storage);
    }

    @Test void pricingRequiresEverySelectedChoiceToMatchApprovedProviderAttributes() throws Exception {
        var assignment = new ServiceProviderTypeAssignment(); assignment.setServiceProviderTypeId(2L);
        when(fields.listForOption(2L, 12L, false)).thenReturn(List.of(
                field(45, "MULTI_SELECT", true, true), field(46, "SELECT", true, true)));
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(45, "[\"A\",\"B\"]", "APPROVED", false),
                answer(46, "\"A\"", "APPROVED", false)));
        var choices = List.of(new com.jobhub.dto.market.PricingConditionRequest(45L, "B"),
                new com.jobhub.dto.market.PricingConditionRequest(46L, "A"));
        service.requirePricingSelections(assignment, 12L, choices);
        assertThatIllegalArgumentException().isThrownBy(() -> service.requirePricingSelections(assignment, 12L,
                List.of(new com.jobhub.dto.market.PricingConditionRequest(45L, "B"),
                        new com.jobhub.dto.market.PricingConditionRequest(46L, "B"))));
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(45, "[\"B\"]", "APPROVED", false), answer(46, "\"A\"", "PENDING", false)));
        assertThatIllegalArgumentException().isThrownBy(() -> service.requirePricingSelections(assignment, 12L, choices));
    }

    @Test void selfDeclaredProfileValuesAreFilterableOnlyWhenProvidedAndAllowlisted() throws Exception {
        var assignment = new ServiceProviderTypeAssignment();
        var profileAnswer = new ProviderCustomSubmissionResponse(null, 14L, 25L, "GENDER", "Gender",
                mapper.readTree("\"A\""), "PROVIDED", null, null, null, "ASSIGNMENT", false, null,
                "PROVIDER_PROFILE", "gender", true);
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(profileAnswer));
        var attributes = service.attributes(assignment, List.of(field(25, "SELECT", true, true)));
        assertThat(attributes).containsKey(25L);
        assertThat(service.matches(attributes, List.of(choice(25, "[\"A\"]")))).isTrue();
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(new ProviderCustomSubmissionResponse(
                null, 14L, 25L, "GENDER", "Gender", mapper.readTree("\"A\""), "INVALID",
                null, null, null, "ASSIGNMENT", false, null, "PROVIDER_PROFILE", "gender", true)));
        assertThat(service.attributes(assignment, List.of(field(25, "SELECT", true, true)))).isEmpty();
    }

    ServiceCustomFieldResponse field(long id, String type, boolean visible, boolean searchable) throws Exception {
        return mapper.readValue("""
                {"fieldId":%d,"scope":"PROVIDER_REQUIREMENT","code":"FIELD_%d",
                 "label":"Field","fieldType":"%s","customerVisible":%s,"searchable":%s,
                 "options":["A","B"],"filterOperator":"IN","status":"ACTIVE"}
                """.formatted(id, id, type, visible, searchable), ServiceCustomFieldResponse.class);
    }

    ProviderFilter choice(long id, String values) throws Exception {
        return new ProviderFilter(id, mapper.readTree(values), null, null);
    }

    @Test void onlyOptedInSupportedProviderFieldsArePublished() throws Exception {
        when(fields.listForOption(3L, 12L, false)).thenReturn(List.of(
                field(1, "SELECT", true, true), field(2, "NUMBER", true, false),
                field(3, "SELECT", false, false), field(4, "IMAGE_UPLOAD", true, true)));
        var definitions = service.definitions(3L, 12L);
        assertThat(definitions).extracting(ServiceCustomFieldResponse::fieldId).containsExactly(1L, 2L, 4L);
        assertThat(service.schema(definitions)).extracting(f -> f.fieldId()).containsExactly(1L);
    }

    @Test void customerFilterSchemaCarriesOptionalFieldIcon() throws Exception {
        var definition = mapper.readValue("""
                {"fieldId":25,"scope":"PROVIDER_REQUIREMENT","fieldType":"SELECT",
                 "customerVisible":true,"searchable":true,"options":["A","B"],
                 "icon":{"key":"service-icons/gender.svg","url":"https://example.com/gender.svg"}}
                """, ServiceCustomFieldResponse.class);
        assertThat(service.schema(List.of(definition)).get(0).icon()).isEqualTo(definition.icon());
        assertThat(service.schema(List.of(field(1, "SELECT", true, true))).get(0).icon()).isNull();
    }

    @Test void richChoiceIconsReachFiltersAndMatchingUsesStableValues() throws Exception {
        var definition = mapper.readValue("""
                {"fieldId":25,"scope":"PROVIDER_REQUIREMENT","fieldType":"MULTI_SELECT",
                 "customerVisible":true,"searchable":true,"options":["BIKE",{
                 "value":"CAR","label":"Car","icon":{"key":"service-icons/car.png",
                 "url":"https://example.com/car.png"}}]}
                """, ServiceCustomFieldResponse.class);
        assertThat(service.schema(List.of(definition)).get(0).options()).isEqualTo(definition.options());
        var filters = List.of(choice(25, "[\"CAR\"]"));
        service.validate(List.of(definition), filters);
        var assignment = new ServiceProviderTypeAssignment();
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(answer(25, "[\"CAR\",\"BIKE\"]", "APPROVED", false)));
        var attributes = service.attributes(assignment, List.of(definition));
        assertThat(service.matches(attributes, filters)).isTrue();
        assertThatThrownBy(() -> service.validate(List.of(definition), List.of(choice(25, "[\"Car\"]"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(List.of(definition), List.of(choice(25, "[{\"value\":\"CAR\"}]"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void unknownPrivateAndDisplayOnlyFieldsCannotBeQueried() throws Exception {
        var definitions = List.of(field(1, "SELECT", true, false));
        assertThatThrownBy(() -> service.validate(definitions, List.of(choice(1, "[\"A\"]"))))
                .hasMessageContaining("not available");
        assertThatThrownBy(() -> service.validate(definitions, List.of(choice(99, "[\"A\"]"))))
                .hasMessageContaining("not available");
    }

    @Test void validatesChoicesDuplicatesTypesAndRangesBeforeSearching() throws Exception {
        var definitions = List.of(field(1, "SELECT", true, true), field(2, "NUMBER", true, true));
        assertThatThrownBy(() -> service.validate(definitions, List.of(choice(1, "[\"X\"]"))))
                .isInstanceOf(IllegalArgumentException.class);
        var filter = choice(1, "[\"A\"]");
        assertThatThrownBy(() -> service.validate(definitions, List.of(filter, filter)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(definitions, List.of(choice(1, "true"))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validate(definitions, List.of(new ProviderFilter(2L, null, BigDecimal.TEN, BigDecimal.ONE))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void filtersAreAndCombinedWithAnyChoiceAndInclusiveNumberRange() throws Exception {
        var filters = List.of(choice(1, "[\"A\",\"B\"]"),
                new ProviderFilter(2L, null, BigDecimal.ONE, BigDecimal.TEN), choice(3, "false"));
        var definitions = List.of(field(1, "MULTI_SELECT", true, true),
                field(2, "NUMBER", true, true), field(3, "BOOLEAN", true, true));
        service.validate(definitions, filters);
        assertThat(service.matches(Map.of(1L, mapper.readTree("[\"B\"]"),
                2L, mapper.readTree("10"), 3L, mapper.readTree("false")), filters)).isTrue();
        assertThat(service.matches(Map.of(1L, mapper.readTree("\"A\""),
                2L, mapper.readTree("11"), 3L, mapper.readTree("false")), filters)).isFalse();
        assertThat(service.matches(Map.of(), filters)).isFalse();
    }

    @Test void publicValuesRequireApprovedValidAnswersIncludingResolvedReuse() throws Exception {
        var assignment = new ServiceProviderTypeAssignment();
        when(fields.resolvedProviderAnswers(assignment)).thenReturn(List.of(
                answer(1, "\"A\"", "APPROVED", true),
                answer(2, "5", "PENDING", false),
                answer(3, "\"X\"", "APPROVED", false),
                answer(99, "\"private\"", "APPROVED", false)));
        var values = service.attributes(assignment, List.of(field(1, "SELECT", true, true),
                field(2, "NUMBER", true, true), field(3, "SELECT", true, true)));
        assertThat(values.keySet()).containsExactly(1L);
        assertThat(values.get(1L).asText()).isEqualTo("A");
    }

    ProviderCustomSubmissionResponse answer(long fieldId, String value, String status, boolean reused) throws Exception {
        return new ProviderCustomSubmissionResponse(1L, 14L, fieldId, "FIELD", "Field", mapper.readTree(value),
                status, null, null, null, "PROVIDER", reused, 15L);
    }
}
