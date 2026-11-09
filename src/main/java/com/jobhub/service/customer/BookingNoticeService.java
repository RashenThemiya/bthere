package com.jobhub.service.customer;

import com.jobhub.dto.job.BookingNoticeApi.Settings;
import com.jobhub.entity.provider.ServiceOption;
import com.jobhub.exception.ResourceNotFoundException;
import com.jobhub.repository.job.BookingRequestPolicyRepository;
import com.jobhub.repository.provider.ServiceOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;

/**
 * How far ahead a customer must book each service option. 0 allows "now" bookings (rides);
 * the default is 120 minutes.
 */
@Service
@RequiredArgsConstructor
public class BookingNoticeService {
    public static final int DEFAULT_MINUTES = 120;
    /** A "now" booking may carry a start a few minutes old: the app chose it before the request arrived. */
    static final Duration INSTANT_GRACE = Duration.ofMinutes(5);

    private final ServiceOptionRepository options;
    private final BookingRequestPolicyRepository policies;

    @Transactional(readOnly = true)
    public Settings settings(Long optionId) {
        return view(options.findById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found")));
    }

    @Transactional
    public Settings update(Long optionId, Settings input) {
        var option = options.findLockedById(optionId).orElseThrow(() -> new ResourceNotFoundException("Option not found"));
        option.setMinimumNoticeMinutes(input.minimumNoticeMinutes());
        // Keep the booking-request policy in step so the two settings never disagree.
        policies.findById(optionId).ifPresent(policy -> {
            policy.setMinimumNoticeMinutes(input.minimumNoticeMinutes());
            policies.save(policy);
        });
        return view(option);
    }

    static Settings view(ServiceOption option) {
        int minutes = minutes(option);
        return new Settings(minutes, minutes == 0);
    }

    public static int minutes(ServiceOption option) {
        return option == null || option.getMinimumNoticeMinutes() == null
                ? DEFAULT_MINUTES : option.getMinimumNoticeMinutes();
    }

    /** Rejects a start earlier than the option allows. */
    public static void require(Instant start, int minutes) {
        Instant now = Instant.now();
        Instant earliest = minutes == 0 ? now.minus(INSTANT_GRACE) : now.plus(Duration.ofMinutes(minutes));
        if (start.isBefore(earliest)) throw new IllegalArgumentException(message(minutes));
    }

    public static void require(ZoneId zone, LocalDateTime start, int minutes) {
        require(start.atZone(zone).toInstant(), minutes);
    }

    public static String message(int minutes) {
        return minutes == 0 ? "Start time cannot be in the past"
                : "Bookings for this service must start at least " + minutes + " minutes from now";
    }
}
