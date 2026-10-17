package com.jobhub.payments;
import com.jobhub.dto.customer.WalletApi.Capabilities;
/** PAYHERE is a selectable, fail-closed integration slot, not a live implementation. */
public record UnconfiguredPaymentGateway(String name) implements PaymentGateway {
    public Capabilities capabilities() { return new Capabilities(true,true,false,false,false,false,false); }
}
