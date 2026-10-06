package com.jobhub.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.dto.admin.AdminRole;
import com.jobhub.dto.admin.CreateAdminRequest;
import com.jobhub.dto.auth.LoginRequest;
import com.jobhub.dto.auth.GoogleLoginRequest;
import com.jobhub.dto.auth.OtpRequest;
import com.jobhub.dto.auth.OtpVerifyRequest;
import com.jobhub.dto.auth.RegisterRequest;
import com.jobhub.dto.auth.RegistrationType;
import com.jobhub.dto.market.CreateMarketRequest;
import com.jobhub.dto.provider.ProviderDocumentRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void readsLatestAuthenticationContract() throws Exception {
        RegisterRequest registration = objectMapper.readValue("""
                {"email":"provider@example.com","password":"StrongPassword123!",
                 "accountType":"SERVICE_PROVIDER"}
                """, RegisterRequest.class);
        LoginRequest login = objectMapper.readValue("""
                {"identifier":"provider@example.com","password":"StrongPassword123!"}
                """, LoginRequest.class);
        OtpRequest otpRequest = objectMapper.readValue("""
                {"phoneNumber":"+94771234567","accountType":"SERVICE_PROVIDER"}
                """, OtpRequest.class);
        OtpVerifyRequest otpVerify = objectMapper.readValue("""
                {"phoneNumber":"+94771234567","otp":"123456",
                 "accountType":"SERVICE_PROVIDER"}
                """, OtpVerifyRequest.class);
        GoogleLoginRequest google = objectMapper.readValue("""
                {"idToken":"google-token","accountType":"CUSTOMER"}
                """, GoogleLoginRequest.class);

        assertThat(registration.accountType()).isEqualTo(RegistrationType.SERVICE_PROVIDER);
        assertThat(login.identifier()).isEqualTo("provider@example.com");
        assertThat(otpRequest.accountType()).isEqualTo(RegistrationType.SERVICE_PROVIDER);
        assertThat(otpVerify.accountType()).isEqualTo(RegistrationType.SERVICE_PROVIDER);
        assertThat(google.accountType()).isEqualTo(RegistrationType.CUSTOMER);
    }

    @Test
    void readsLatestDocumentMarketAndAdminContract() throws Exception {
        ProviderDocumentRequest document = objectMapper.readValue("""
                {"documentTypeId":1,"documentName":"NIC front",
                 "documentKey":"providers/42/documents/nic.pdf"}
                """, ProviderDocumentRequest.class);
        CreateMarketRequest market = objectMapper.readValue("""
                {"name":"Sri Lanka","countryCode":"LK","currencyCode":"LKR",
                 "timezone":"Asia/Colombo","locale":"en-LK","phoneCode":"+94"}
                """, CreateMarketRequest.class);
        CreateAdminRequest admin = objectMapper.readValue("""
                {"username":"admin01","email":"admin@example.com",
                 "password":"StrongAdminPassword!","accountType":"ADMIN"}
                """, CreateAdminRequest.class);

        assertThat(document.documentKey()).isEqualTo("providers/42/documents/nic.pdf");
        assertThat(market.currencyCode()).isEqualTo("LKR");
        assertThat(market.phoneCode()).isEqualTo("+94");
        assertThat(admin.accountType()).isEqualTo(AdminRole.ADMIN);
    }
}
