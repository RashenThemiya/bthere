package com.jobhub.payments;

import com.jobhub.dto.customer.WalletApi.Capabilities;
import com.jobhub.exception.PaymentUnavailableException;

/** Implement this port for an official gateway; mock outcomes are never webhooks. */
public interface PaymentGateway {
    String name();
    Capabilities capabilities();
    default void requireEnabled() {
        throw new PaymentUnavailableException(name() + " integration is not configured");
    }
    default boolean isMock() { return false; }
    record Request(Long intentId,Long userId,String purpose,String currencyCode,java.math.BigDecimal amount,Long jobId,Long methodId) {}
    default String start(Request request) { requireEnabled(); throw new PaymentUnavailableException("Payment initiation unavailable"); }
    default String simulate(String outcome) { throw new PaymentUnavailableException("Simulation is not available for this provider"); }
}
