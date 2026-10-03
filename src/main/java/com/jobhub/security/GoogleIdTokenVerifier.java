package com.jobhub.security;

import com.jobhub.exception.UnauthorizedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Set;

@Service
public class GoogleIdTokenVerifier {

    private static final Set<String> ALLOWED_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com"
    );

    private final JwtDecoder decoder;

    public GoogleIdTokenVerifier(
            @Value("${app.security.google.client-id}") String clientId,
            @Value("${app.security.google.jwk-set-uri}") String jwkSetUri
    ) {
        if (!StringUtils.hasText(clientId)) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID must be configured");
        }

        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> issuerValidator = jwt ->
                ALLOWED_ISSUERS.contains(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                        ? OAuth2TokenValidatorResult.success()
                        : failure("The Google token issuer is invalid");
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(clientId)
                        ? OAuth2TokenValidatorResult.success()
                        : failure("The Google token audience is invalid");

        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                issuerValidator,
                audienceValidator
        ));
        this.decoder = nimbusDecoder;
    }

    public GoogleIdentity verify(String idToken) {
        try {
            Jwt jwt = decoder.decode(idToken);
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            Boolean emailVerified = jwt.getClaim("email_verified");

            if (!StringUtils.hasText(subject)
                    || !StringUtils.hasText(email)
                    || !Boolean.TRUE.equals(emailVerified)) {
                throw new UnauthorizedException("Google account email is not verified");
            }

            return new GoogleIdentity(subject, email, true);
        } catch (JwtException exception) {
            throw new UnauthorizedException("Invalid Google ID token");
        }
    }

    private OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", description, null)
        );
    }
}
