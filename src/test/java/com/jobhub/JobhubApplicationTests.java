package com.jobhub;

import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.UserSessionRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
import com.jobhub.repository.auth.SmsGatewaySettingsRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.customer.CustomerRepository;
import com.jobhub.repository.provider.DocumentTypeRepository;
import com.jobhub.repository.provider.ServiceProviderDocumentRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderMarketRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
import com.jobhub.repository.provider.ServiceTypeDocumentRequirementRepository;
import com.jobhub.repository.provider.ServiceTypeSkillRepository;
import com.jobhub.repository.provider.ProviderServiceSkillRepository;
import com.jobhub.repository.provider.ProviderProfessionalCertificateRepository;
import com.jobhub.repository.provider.ProviderEducationQualificationRepository;
import com.jobhub.repository.provider.ServiceProviderAvailabilityRepository;
import com.jobhub.repository.provider.ServiceProviderServiceAreaRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import com.jobhub.repository.provider.ProviderServiceOptionRepository;
import com.jobhub.repository.provider.ProviderServiceLanguageRepository;
import com.jobhub.repository.provider.ServiceCustomFieldRepository;
import com.jobhub.repository.provider.ProviderCustomFieldSubmissionRepository;
import com.jobhub.repository.audit.AuditLogRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.market.RefundPolicyRepository;
import com.jobhub.repository.market.RefundPolicyTierRepository;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.JobCustomFieldAnswerRepository;
import com.jobhub.repository.job.JobProviderAssignmentRepository;
import com.jobhub.repository.finance.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "app.bootstrap.super-admin.enabled=false",
        "app.bootstrap.default-market.enabled=false",
        "app.security.jwt.secret=test-only-jwt-secret-with-at-least-32-characters",
        "app.security.google.client-id=test-google-client-id.apps.googleusercontent.com",
        "app.security.otp.hash-secret=test-only-otp-secret-with-at-least-32-characters",
        "app.security.email.from=no-reply@test.example"
})
@AutoConfigureMockMvc
class JobhubApplicationTests {
    @MockitoBean private com.jobhub.repository.job.BookingRequestRepository bookingRequests;
    @MockitoBean private com.jobhub.repository.job.BookingOfferRepository bookingOffers;
    @MockitoBean private com.jobhub.repository.job.BookingRequestPolicyRepository bookingRequestPolicies;
    @MockitoBean private com.jobhub.repository.job.BookingDispatchEventRepository bookingEvents;
    @MockitoBean private com.jobhub.repository.job.BookingPaymentNoticeRepository bookingPaymentNotices;
    @MockitoBean private com.jobhub.repository.auth.UserDeviceRepository bookingDevices;
    @Autowired private com.jobhub.controller.provider.ProviderBookingOfferController bookingOfferController;
    @Autowired private com.jobhub.controller.admin.AdminBookingRequestPolicyController bookingPolicyController;
    @Autowired private com.jobhub.controller.customer.CustomerBookingRequestController customerBookingRequestsController;
    @Autowired private com.jobhub.controller.admin.AdminPlatformFeeController platformFeeController;

    @Autowired private com.jobhub.controller.admin.AdminBookingMonitorController bookingMonitor;
    @Test void superAdminCanReadBookingMonitoring() {
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test","unused",
            java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {org.assertj.core.api.Assertions.assertThat(bookingMonitor.summary().requestsByStatus()).isEmpty();}
        finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void bookingMonitoringIsSuperAdminOnly() {
        for(String role:java.util.List.of("CUSTOMER","SERVICE_PROVIDER","ADMIN","FINANCE_ADMIN")) {
            var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test","unused",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingMonitor.summary())
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingMonitor.request(1L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingMonitor.transaction(1L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        }
    }

    @Test void customersAndProvidersCannotConfigurePlatformCommission() {
        for(String role:java.util.List.of("CUSTOMER","SERVICE_PROVIDER","FINANCE_ADMIN")) {
            var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test","unused",
                    java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> platformFeeController.updateService(null,1L,null))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> platformFeeController.updateOption(null,12L,null))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        }
    }

    @Test void providerCannotConfirmCompletionOnCustomersBehalf() {
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("provider","unused",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_SERVICE_PROVIDER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerBookingRequestsController.confirmCompletion(null,70L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }

    @Test void customerCannotAcceptProviderOffersOrChangeDispatchPolicy() {
        var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("customer","unused",
                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingOfferController.accept(null,1L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> bookingPolicyController.update(12L,null))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @MockitoBean
    private com.jobhub.repository.provider.ServiceOptionMapMarkerRuleRepository mapMarkerRuleRepository;
    @Autowired private com.jobhub.controller.admin.AdminMapMarkerRuleController mapMarkerRuleController;

    @Test void combinationMapMarkerRulesRequireSuperAdmin() {
        for (String role : java.util.List.of("CUSTOMER", "SERVICE_PROVIDER", "VERIFICATION_ADMIN")) {
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "test", "unused", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + role)));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapMarkerRuleController.list(1L,12L))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> mapMarkerRuleController.delete(1L,12L,7L))
                        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        }
    }
    @Autowired private com.jobhub.controller.customer.WalletController walletController;
    @Autowired private com.jobhub.controller.admin.AdminProviderApprovalController approvalController;

    @Test void providerApprovalEndpointsRestrictRoles() {
        for (String role : java.util.List.of("CUSTOMER","SERVICE_PROVIDER","FINANCE_ADMIN")) {
            var auth=new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test","unused",java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_"+role)));
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
            try {
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalController.detail(12L)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> approvalController.bulk(null,null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
        }
    }

    @Autowired private com.jobhub.controller.admin.AdminWalletController adminWalletController;
    @Autowired private com.jobhub.controller.admin.AdminPaymentGatewayController adminPaymentGatewayController;

    @Test
    void customerCannotInvokeFinanceSettlementAndExternalFundingFailsClosed() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "customer", "unused", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminPaymentGatewayController.get())
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.assertj.core.api.Assertions.assertThat(walletController.capabilities().topups()).isFalse();
            org.assertj.core.api.Assertions.assertThat(walletController.capabilities().withdrawals()).isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminWalletController.approve(null, 1L))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> walletController.setup(new com.jobhub.security.AuthenticatedUser(25L,"test",null,"ACTIVE",java.util.List.of("CUSTOMER")),"CARD"))
                    .isInstanceOf(com.jobhub.exception.PaymentUnavailableException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    @MockitoBean
    private jakarta.persistence.EntityManager approvalEntityManager;

    @MockitoBean
    private com.jobhub.repository.finance.PaymentGatewaySettingsRepository paymentGatewaySettingsRepository;

    @MockitoBean private com.jobhub.repository.finance.WalletRepository walletRepository;
    @MockitoBean private com.jobhub.repository.finance.GatewayIntentRepository gatewayIntentRepository;
    @MockitoBean private com.jobhub.repository.finance.PaymentInstrumentRepository paymentInstrumentRepository;
    @MockitoBean private com.jobhub.repository.finance.WalletOperationRepository walletOperationRepository;
    @MockitoBean private com.jobhub.repository.finance.WalletLedgerRepository walletLedgerRepository;

    @MockitoBean
    private com.jobhub.repository.customer.CustomerRequirementRepository customerRequirementRepository;

    @MockitoBean
    private com.jobhub.repository.customer.CustomerRequirementSubmissionRepository customerRequirementSubmissionRepository;

    @MockitoBean
    private com.jobhub.repository.customer.CustomerEligibilityRuleRepository customerEligibilityRuleRepository;

    @Autowired
    private com.jobhub.controller.admin.AdminCustomerRequirementsController customerRequirementsAdmin;

    @Test
    void customerCannotConfigureRequirementsOrReadOtherCustomerReviews() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "customer", "unused", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerRequirementsAdmin.list())
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerRequirementsAdmin.queue("PENDING", org.springframework.data.domain.Pageable.unpaged()))
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test
    void verificationAdminCanReviewButCannotChangeDefinitions() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "reviewer", "unused", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_VERIFICATION_ADMIN")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
        var page = org.springframework.data.domain.Pageable.unpaged();
        org.mockito.Mockito.when(customerRequirementSubmissionRepository.findAllByStatus("PENDING", page))
                .thenReturn(org.springframework.data.domain.Page.empty());
        try {
            org.assertj.core.api.Assertions.assertThat(customerRequirementsAdmin.queue("PENDING", page)).isEmpty();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> customerRequirementsAdmin.list())
                    .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserRoleRepository userRoleRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private UserAuthProviderRepository userAuthProviderRepository;

    @MockitoBean
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private VerificationTokenRepository verificationTokenRepository;

    @MockitoBean
    private SesV2Client sesV2Client;

    @MockitoBean
    private SmsGatewaySettingsRepository smsGatewaySettingsRepository;

    @MockitoBean
    private S3Client s3Client;

    @MockitoBean
    private S3Presigner s3Presigner;

    @MockitoBean
    private MarketRepository marketRepository;

    @MockitoBean
    private CustomerRepository customerRepository;

    @MockitoBean
    private ServiceProviderRepository serviceProviderRepository;

    @MockitoBean
    private ServiceProviderMarketRepository providerMarketRepository;

    @MockitoBean
    private ServiceProviderTypeRepository serviceProviderTypeRepository;

    @MockitoBean
    private ServiceProviderTypeAssignmentRepository assignmentRepository;

    @MockitoBean
    private DocumentTypeRepository documentTypeRepository;

    @MockitoBean
    private ServiceProviderDocumentRepository documentRepository;

    @MockitoBean
    private ServiceTypeDocumentRequirementRepository requirementRepository;

    @MockitoBean
    private ServiceTypeSkillRepository skillRepository;

    @MockitoBean
    private ProviderServiceSkillRepository providerSkillRepository;

    @MockitoBean
    private ProviderProfessionalCertificateRepository certificateRepository;

    @MockitoBean
    private ProviderEducationQualificationRepository educationRepository;

    @MockitoBean
    private ServiceProviderAvailabilityRepository availabilityRepository;

    @MockitoBean
    private ServiceProviderServiceAreaRepository serviceAreaRepository;

    @MockitoBean
    private ServiceOptionRepository serviceOptionRepository;

    @MockitoBean
    private ProviderServiceOptionRepository providerServiceOptionRepository;

    @MockitoBean
    private ProviderServiceLanguageRepository providerServiceLanguageRepository;

    @MockitoBean
    private ServiceCustomFieldRepository serviceCustomFieldRepository;

    @MockitoBean
    private ProviderCustomFieldSubmissionRepository providerCustomFieldSubmissionRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private MarketServiceOfferingRepository marketServiceOfferingRepository;

    @MockitoBean
    private RefundPolicyRepository refundPolicyRepository;

    @MockitoBean
    private RefundPolicyTierRepository refundPolicyTierRepository;

    @MockitoBean
    private JobRateRepository jobRateRepository;

    @MockitoBean
    private JobRepository jobRepository;

    @MockitoBean
    private JobCustomFieldAnswerRepository jobCustomFieldAnswerRepository;

    @MockitoBean
    private JobProviderAssignmentRepository jobProviderAssignmentRepository;

    @MockitoBean
    private PaymentRepository paymentRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void adminLoginPreflightAllowsVercelOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://bthere-admin-web.vercel.app")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "https://bthere-admin-web.vercel.app"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Authorization")));
    }

    @Test
    void adminLoginPreflightAllowsLocalDevelopmentOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Authorization")));
    }

    @Test
    void adminLoginPreflightAllowsAdditionalLocalDevelopmentOrigin() throws Exception {
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "http://localhost:54421")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type, Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin",
                        "http://localhost:54421"))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Content-Type")))
                .andExpect(header().string("Access-Control-Allow-Headers",
                        org.hamcrest.Matchers.containsStringIgnoringCase("Authorization")));
    }
}
