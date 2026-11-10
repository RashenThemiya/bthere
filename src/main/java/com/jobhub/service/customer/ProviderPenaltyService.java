package com.jobhub.service.customer;

import com.jobhub.dto.job.CancellationApi.Penalty;
import com.jobhub.dto.job.CancellationApi.PenaltySummary;
import com.jobhub.exception.ConflictException;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.JobCancellationRepository;
import com.jobhub.repository.job.ProviderPenaltyRepository;
import com.jobhub.repository.provider.ServiceProviderRepository;
import com.jobhub.service.audit.AuditService;
import com.jobhub.service.finance.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Provider cancellation penalties: the provider's own view, and the Super Admin's list, waiver and reactivation. */
@Service
@RequiredArgsConstructor
public class ProviderPenaltyService {
    private final ProviderPenaltyRepository penalties;
    private final JobCancellationRepository cancellations;
    private final ServiceProviderRepository providers;
    private final WalletService wallets;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PenaltySummary mine(Long userId) {
        var provider = providers.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        var items = penalties.findAllByProviderUserIdOrderByPenaltyIdDesc(userId).stream()
                .map(BookingCancellationService::penaltyView).toList();
        return new PenaltySummary("LKR", wallets.outstandingPenalties(userId, "LKR"), provider.getStatus(), items);
    }

    @Transactional(readOnly = true)
    public Page<Penalty> list(Long serviceProviderId, String status, Pageable pageable) {
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of("OUTSTANDING", "COLLECTED", "WAIVED").contains(normalized))
            throw new IllegalArgumentException("Status must be OUTSTANDING, COLLECTED or WAIVED");
        return penalties.search(serviceProviderId, normalized, pageable).map(BookingCancellationService::penaltyView);
    }

    @Transactional(readOnly = true)
    public Penalty get(Long penaltyId) {
        return penalties.findById(penaltyId).map(BookingCancellationService::penaltyView)
                .orElseThrow(() -> new ResourceNotFoundException("Penalty not found"));
    }

    /**
     * Waives a penalty (for example a documented emergency): money already collected goes back to the provider,
     * and the cancellation stops counting towards suspension. Does not reactivate a suspended provider.
     */
    @Transactional
    public Penalty waive(Long actorId, Long penaltyId, String reason) {
        var penalty = wallets.waiveProviderPenalty(penaltyId, actorId, reason);
        cancellations.findByJobId(penalty.getJobId()).ifPresent(c -> {
            c.setPenaltyWaived(true);
            cancellations.save(c);
        });
        return BookingCancellationService.penaltyView(penalty);
    }

    /** Lifts a cancellation suspension; the provider receives offers again. */
    @Transactional
    public Map<String, Object> reactivate(Long actorId, Long serviceProviderId, String note) {
        var provider = providers.findById(serviceProviderId)
                .orElseThrow(() -> new ResourceNotFoundException("Provider not found"));
        if (!"SUSPENDED".equals(provider.getStatus()))
            throw new ConflictException("Only a suspended provider can be reactivated");
        provider.setStatus("ACTIVE");
        providers.save(provider);
        audit.record(actorId, "PROVIDER_REACTIVATED", "SERVICE_PROVIDER", serviceProviderId, Map.of("status", "SUSPENDED"),
                note == null || note.isBlank() ? Map.of("status", "ACTIVE") : Map.of("status", "ACTIVE", "note", note.trim()));
        return Map.of("serviceProviderId", serviceProviderId, "status", "ACTIVE");
    }
}
