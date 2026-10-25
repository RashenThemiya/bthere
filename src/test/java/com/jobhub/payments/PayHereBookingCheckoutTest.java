package com.jobhub.payments;

import com.jobhub.dto.job.BookingRequestsApi.Confirm;
import com.jobhub.entity.finance.PaymentGatewaySettings;
import com.jobhub.entity.job.*;
import com.jobhub.repository.finance.PaymentGatewaySettingsRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PayHereBookingCheckoutTest {
    PaymentGatewaySettingsRepository settings=mock(PaymentGatewaySettingsRepository.class);
    PayHereBookingCheckout gateway=new PayHereBookingCheckout(settings,true,true,"123","test-secret",
            "https://example.com/notify","https://example.com/return","https://example.com/cancel");
    Map<String,String> valid(){
        var p=new HashMap<String,String>();p.put("merchant_id","123");p.put("order_id","BR-1");p.put("payment_id","100");
        p.put("payhere_amount","1000.00");p.put("payhere_currency","LKR");p.put("status_code","2");
        p.put("md5sig",PayHereBookingCheckout.md5("123BR-11000.00LKR2"+PayHereBookingCheckout.md5("test-secret")));return p;
    }
    @Test void signedCallbackAcceptsValidDataAndRejectsChangedAmountOrMerchant(){
        gateway.verify(valid());var amount=valid();amount.put("payhere_amount","1.00");assertThatIllegalArgumentException().isThrownBy(() -> gateway.verify(amount));
        var merchant=valid();merchant.put("merchant_id","another");assertThatIllegalArgumentException().isThrownBy(() -> gateway.verify(merchant));
    }
    @Test void checkoutHasServerAmountSignatureAndCardMethodWithoutMerchantSecret(){
        var selected=new PaymentGatewaySettings();selected.setProvider("PAYHERE");when(settings.findById(1L)).thenReturn(Optional.of(selected));
        var r=new BookingRequest();r.setRequestId(1L);r.setPaymentOrderId("BR-1");
        var j=new Job();j.setExpectedAmount(new BigDecimal("1000"));j.setCurrencyCode("LKR");
        var result=gateway.checkout(r,j,new Confirm("MASTER","A","B","a@example.com","0771234567","Address","Colombo","Sri Lanka"));
        assertThat(result.actionUrl()).isEqualTo("https://sandbox.payhere.lk/pay/checkout");
        assertThat(result.fields()).containsEntry("amount","1000.00").containsEntry("payment_method","MASTER")
                .containsEntry("hash",PayHereBookingCheckout.md5("123BR-11000.00LKR"+PayHereBookingCheckout.md5("test-secret")));
        assertThat(result.fields().values()).doesNotContain("test-secret");
    }
    @Test void callbackStillVerifiesAfterNewCheckoutIsDisabled(){
        var disabled=new PayHereBookingCheckout(settings,false,true,"123","test-secret","","","");
        disabled.verify(valid());assertThatThrownBy(disabled::requireReady).isInstanceOf(com.jobhub.exception.PaymentUnavailableException.class);
    }
}
