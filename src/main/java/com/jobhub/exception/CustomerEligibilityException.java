package com.jobhub.exception;
import com.jobhub.dto.customer.CustomerRequirementsApi.Eligibility;
public class CustomerEligibilityException extends RuntimeException {
    private final Eligibility eligibility;
    public CustomerEligibilityException(Eligibility eligibility) {
        super("Customer requirements must be completed before booking");
        this.eligibility = eligibility;
    }
    public Eligibility getEligibility() { return eligibility; }
}
