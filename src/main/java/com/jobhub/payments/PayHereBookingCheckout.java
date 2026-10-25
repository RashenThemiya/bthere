package com.jobhub.payments;

import com.jobhub.dto.job.BookingRequestsApi.*;
import com.jobhub.entity.job.*;
import com.jobhub.exception.PaymentUnavailableException;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Hosted checkout only. Raw card details and client-reported success never enter this service. */
@Component
public class PayHereBookingCheckout {
    private final PaymentGatewaySettingsRepository settings;
    private final boolean enabled, sandbox;
    private final String merchantId, secret, notifyUrl, returnUrl, cancelUrl;
    public PayHereBookingCheckout(PaymentGatewaySettingsRepository settings,
            @Value("${booking.payhere.enabled:false}") boolean enabled,
            @Value("${booking.payhere.sandbox:true}") boolean sandbox,
            @Value("${booking.payhere.merchant-id:}") String merchantId,
            @Value("${booking.payhere.merchant-secret:}") String secret,
            @Value("${booking.payhere.notify-url:}") String notifyUrl,
            @Value("${booking.payhere.return-url:}") String returnUrl,
            @Value("${booking.payhere.cancel-url:}") String cancelUrl) {
        this.settings=settings; this.enabled=enabled; this.sandbox=sandbox;
        this.merchantId=merchantId; this.secret=secret; this.notifyUrl=notifyUrl;
        this.returnUrl=returnUrl; this.cancelUrl=cancelUrl;
    }
    public void requireReady() {
        if (!enabled || merchantId.isBlank() || secret.isBlank()
                || !notifyUrl.startsWith("https://") || !returnUrl.startsWith("https://")
                || !cancelUrl.startsWith("https://")
                || settings.findById(1L).filter(s -> "PAYHERE".equals(s.getProvider())).isEmpty())
            throw new PaymentUnavailableException("PayHere booking checkout is not configured and selected");
    }
    /** Frozen on the order before checkout; do not infer past funding from today's configuration. */
    public String fundingMode(){return sandbox?"PAYHERE_SANDBOX":"PAYHERE";}
    public Checkout checkout(BookingRequest request, Job job, Confirm contact) {
        requireReady();
        if (!"LKR".equals(job.getCurrencyCode()) || job.getExpectedAmount()==null
                || job.getExpectedAmount().signum()<=0) throw new IllegalArgumentException("A positive LKR card price is required");
        var fields=new LinkedHashMap<String,String>();
        fields.put("merchant_id",merchantId); fields.put("order_id",request.getPaymentOrderId());
        fields.put("amount",job.getExpectedAmount().setScale(2,RoundingMode.UNNECESSARY).toPlainString());
        fields.put("currency","LKR"); fields.put("items","Booking " + request.getRequestId());
        fields.put("notify_url",notifyUrl); fields.put("return_url",returnUrl); fields.put("cancel_url",cancelUrl);
        fields.put("first_name",contact.firstName()); fields.put("last_name",contact.lastName());
        fields.put("email",contact.email()); fields.put("phone",contact.phone());
        fields.put("address",contact.address()); fields.put("city",contact.city()); fields.put("country",contact.country());
        fields.put("payment_method",contact.cardMethod());
        fields.put("hash",md5(merchantId + request.getPaymentOrderId() + fields.get("amount") + "LKR" + md5(secret)));
        return new Checkout(sandbox ? "https://sandbox.payhere.lk/pay/checkout" : "https://www.payhere.lk/pay/checkout",fields,"PAYMENT_PENDING");
    }
    public void verify(Map<String,String> params) {
        // Continue validating outstanding callbacks even if new checkout initiation is disabled.
        if (merchantId.isBlank() || secret.isBlank()) throw new PaymentUnavailableException("PayHere callback verification is not configured");
        for(var key:List.of("merchant_id","order_id","payment_id","payhere_amount","payhere_currency","status_code","md5sig"))
            if(params.get(key)==null || params.get(key).isBlank()) throw new IllegalArgumentException("Missing payment notification field");
        if(!merchantId.equals(params.get("merchant_id"))) throw new IllegalArgumentException("Invalid payment merchant");
        String expected=md5(merchantId + params.get("order_id") + params.get("payhere_amount")
                + params.get("payhere_currency") + params.get("status_code") + md5(secret));
        if(!MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                params.get("md5sig").toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException("Invalid payment notification signature");
    }
    static String md5(String value) {
        try { return HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
