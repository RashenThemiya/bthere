package com.jobhub.controller.admin;

import com.jobhub.dto.admin.AdminProviderRegistrationApi.*;
import com.jobhub.dto.provider.*;
import com.jobhub.security.AuthenticatedUser;
import com.jobhub.service.provider.AdminProviderRegistrationService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockMultipartFile;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminProviderRegistrationControllerTest {
    AnnotationConfigApplicationContext context;
    AdminProviderRegistrationController controller;
    AdminProviderRegistrationService service;
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean AdminProviderRegistrationService service() { return mock(AdminProviderRegistrationService.class); }
        @Bean AdminProviderRegistrationController controller(AdminProviderRegistrationService service) {
            return new AdminProviderRegistrationController(service);
        }
    }
    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(Config.class);
        controller = context.getBean(AdminProviderRegistrationController.class);
        service = context.getBean(AdminProviderRegistrationService.class);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); context.close(); }
    AuthenticatedUser authenticate(String role) {
        var actor = new AuthenticatedUser(99L, "admin", "admin@example.com", "ACTIVE", List.of(role));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                actor, "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
        return actor;
    }
    @Test void bothAuthorizedAdminRolesCanAssistWithoutProviderToken() {
        for (String role : List.of("SUPER_ADMIN", "VERIFICATION_ADMIN")) {
            var actor = authenticate(role);
            controller.context(15L);
            var input = new Registration(mock(ProviderServiceRegistrationRequest.class), Source.WHATSAPP, null);
            controller.register(actor, 15L, input);
            verify(service).register(99L, 15L, input);
        }
        verify(service, times(2)).context(15L);
    }
    @Test void otherRolesCannotReadRegisterUploadOrSubmitEvidence() {
        for (String role : List.of("CUSTOMER", "SERVICE_PROVIDER", "ADMIN", "FINANCE_ADMIN")) {
            var actor = authenticate(role);
            var file = new MockMultipartFile("file", new byte[]{1});
            assertThatThrownBy(() -> controller.context(15L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.register(actor, 15L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.uploadDocument(actor, 15L, file, Source.WHATSAPP)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.uploadImage(actor, 15L, file, Source.WHATSAPP)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.document(actor, 15L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.requirements(actor, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.certificate(actor, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(() -> controller.education(actor, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
        authenticate("SERVICE_PROVIDER");
        assertThatThrownBy(() -> controller.options(null, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.languages(null, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.skills(null, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.schedule(null, 15L, 14L, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(() -> controller.areas(null, 15L, 14L, 12L, "ONLINE", List.of())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test void multipartRouteUsesAdminActorAndNestedRegistrationValidationReturns400() throws Exception {
        authenticate("VERIFICATION_ADMIN");
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver())
                .build();
        var uploaded = new com.jobhub.dto.upload.FileUploadResponse("providers/25/documents/a.pdf", "s3://bucket/key", "application/pdf", 1);
        when(service.upload(org.mockito.ArgumentMatchers.eq(99L), org.mockito.ArgumentMatchers.eq(15L),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(Source.WHATSAPP)))
                .thenReturn(uploaded);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(
                "/api/v1/admin/providers/15/uploads/documents")
                .file(new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1}))
                .param("source", "WHATSAPP"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.key")
                        .value("providers/25/documents/a.pdf"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/v1/admin/providers/15/service-registrations")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"source\":\"WHATSAPP\",\"registration\":{}}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        verify(service, never()).register(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test void registrationAndSourceAreRequiredAndNestedPayloadIsValidated() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new Registration(null, null, "x".repeat(1001)))).hasSize(3);
            var registration = new ProviderServiceRegistrationRequest(null, null, null, null, null,
                    null, null, null, null, null, null);
            assertThat(validator.validate(new Registration(registration, Source.WHATSAPP, null))).hasSize(10);
        }
    }
}
