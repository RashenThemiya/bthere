package com.jobhub.service.admin;

import com.jobhub.dto.admin.AdminDashboardResponse;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.finance.PaymentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.job.JobRateRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.market.MarketServiceOfferingRepository;
import com.jobhub.repository.provider.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AdminDashboardService {
    private final UserRepository users;
    private final ServiceProviderRepository providers;
    private final ServiceProviderTypeAssignmentRepository assignments;
    private final MarketRepository markets;
    private final ServiceProviderTypeRepository serviceTypes;
    private final JobRepository jobs;
    private final PaymentRepository payments;
    private final ServiceOptionRepository options;
    private final ServiceCustomFieldRepository customFields;
    private final MarketServiceOfferingRepository marketOfferings;
    private final JobRateRepository rates;
    private final ProviderServiceOptionRepository providerOptions;
    private final ServiceProviderDocumentRepository documents;
    private final ProviderServiceSkillRepository skills;
    private final ProviderProfessionalCertificateRepository certificates;
    private final ProviderEducationQualificationRepository education;
    private final ProviderCustomFieldSubmissionRepository customSubmissions;

    @Transactional(readOnly = true)
    public AdminDashboardResponse get() {
        long pendingProviderServices = assignments.countByVerificationStatus("PENDING");
        long pendingOptions = providerOptions.countByVerificationStatus("PENDING");
        long pendingDocuments = documents.countByVerificationStatus("PENDING");
        long pendingSkills = skills.countByVerificationStatus("PENDING");
        long pendingCertificates = certificates.countByVerificationStatus("PENDING");
        long pendingEducation = education.countByVerificationStatus("PENDING");
        long pendingCustom = customSubmissions.countByVerificationStatus("PENDING");
        var configuration = new AdminDashboardResponse.ServiceConfigurationStatistics(
                serviceTypes.count(), serviceTypes.countByStatus("ACTIVE"),
                serviceTypes.countByStatus("INACTIVE"), options.count(),
                options.countByStatus("ACTIVE"), options.countByStatus("INACTIVE"),
                options.countBySchedulingModel("TIME_BASED"),
                options.countBySchedulingModel("ROUTE_BASED"),
                options.countByBookingMode("ONE_AT_A_TIME"),
                options.countByBookingMode("MANY_AT_A_TIME"),
                options.countByFulfillmentModel("ONE_TO_ONE"),
                options.countByFulfillmentModel("MANY_CUSTOMERS_ONE_PROVIDER"),
                options.countByFulfillmentModel("ONE_CUSTOMER_MANY_PROVIDERS"),
                customFields.countByScopeAndStatus("PROVIDER_REQUIREMENT", "ACTIVE"),
                customFields.countByScopeAndStatus("BOOKING_FIELD", "ACTIVE"),
                marketOfferings.count(), marketOfferings.countByStatus("ACTIVE"),
                rates.count(), rates.countByStatus("ACTIVE"));
        var approvalQueues = new AdminDashboardResponse.ApprovalQueueStatistics(
                pendingProviderServices, pendingOptions, pendingDocuments, pendingSkills,
                pendingCertificates, pendingEducation, pendingCustom,
                pendingProviderServices + pendingOptions + pendingDocuments + pendingSkills
                        + pendingCertificates + pendingEducation + pendingCustom);
        var bookingStatistics = new AdminDashboardResponse.BookingStatistics(
                jobs.countBySchedulingModel("TIME_BASED"),
                jobs.countBySchedulingModel("ROUTE_BASED"),
                jobs.countByFulfillmentModel("ONE_TO_ONE"),
                jobs.countByFulfillmentModel("MANY_CUSTOMERS_ONE_PROVIDER"),
                jobs.countByFulfillmentModel("ONE_CUSTOMER_MANY_PROVIDERS"));
        return new AdminDashboardResponse(users.count(), users.countByStatus("ACTIVE"),
                users.countByStatus("INACTIVE") + users.countByStatus("SUSPENDED"),
                providers.count(), providers.countByVerificationStatus("VERIFIED"),
                pendingProviderServices, markets.countByStatus("ACTIVE"),
                serviceTypes.countByStatus("ACTIVE"), jobs.count(), jobs.countByJobStatus("PENDING"),
                jobs.countByJobStatus("COMPLETED"), payments.countByPaymentStatus("PAID"),
                payments.totalPaidAmount(), configuration, approvalQueues, bookingStatistics);
    }
}
