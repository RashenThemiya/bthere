package com.jobhub.payments;
import com.jobhub.dto.customer.WalletApi.Capabilities;
public final class MockPaymentGateway implements PaymentGateway {
    public String name() { return "MOCK"; }
    public boolean isMock() { return true; }
    public void requireEnabled() {}
    public String start(Request request) { return "PENDING"; }
    public String simulate(String outcome) {
        if (!java.util.Set.of("SUCCESS","FAILURE","PENDING","REFUND").contains(outcome)) throw new IllegalArgumentException("Invalid mock outcome");
        return outcome;
    }
    public Capabilities capabilities() { return new Capabilities(true,true,true,true,true,true,true); }
}
