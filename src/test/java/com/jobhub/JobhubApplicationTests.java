package com.jobhub;

import com.jobhub.repository.access.UserRoleRepository;
import com.jobhub.repository.access.RoleRepository;
import com.jobhub.repository.auth.UserAuthProviderRepository;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.auth.UserSessionRepository;
import com.jobhub.repository.auth.VerificationTokenRepository;
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
import software.amazon.awssdk.services.sns.SnsClient;
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
    private SnsClient snsClient;

    @MockitoBean
    private SesV2Client sesV2Client;

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
}
