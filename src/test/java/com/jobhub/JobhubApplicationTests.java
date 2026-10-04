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
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.s3.S3Client;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
        "app.bootstrap.super-admin.enabled=false",
        "app.bootstrap.default-market.enabled=false",
        "app.security.jwt.secret=test-only-jwt-secret-with-at-least-32-characters",
        "app.security.google.client-id=test-google-client-id.apps.googleusercontent.com",
        "app.security.otp.hash-secret=test-only-otp-secret-with-at-least-32-characters",
        "app.security.email.from=no-reply@test.example"
})
class JobhubApplicationTests {

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

    @Test
    void contextLoads() {
    }
}
