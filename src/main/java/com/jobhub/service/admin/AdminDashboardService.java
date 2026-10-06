package com.jobhub.service.admin;

import com.jobhub.dto.admin.AdminDashboardResponse;
import com.jobhub.repository.auth.UserRepository;
import com.jobhub.repository.finance.PaymentRepository;
import com.jobhub.repository.job.JobRepository;
import com.jobhub.repository.market.MarketRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.repository.provider.ServiceProviderTypeAssignmentRepository;
import com.jobhub.repository.provider.ServiceProviderTypeRepository;
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

    @Transactional(readOnly = true)
    public AdminDashboardResponse get() {
        return new AdminDashboardResponse(users.count(), users.countByStatus("ACTIVE"),
                users.countByStatus("INACTIVE") + users.countByStatus("SUSPENDED"),
                providers.count(), providers.countByVerificationStatus("VERIFIED"),
                assignments.countByVerificationStatus("PENDING"), markets.countByStatus("ACTIVE"),
                serviceTypes.countByStatus("ACTIVE"), jobs.count(), jobs.countByJobStatus("PENDING"),
                jobs.countByJobStatus("COMPLETED"), payments.countByPaymentStatus("PAID"),
                payments.totalPaidAmount());
    }
}
