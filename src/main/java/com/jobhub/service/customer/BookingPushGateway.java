package com.jobhub.service.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobhub.entity.job.BookingDispatchEvent;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.*;
import java.util.*;

/** FCM HTTP v1 with short-lived OAuth service-account credentials. */
@Component
public class BookingPushGateway {
    /**
     * Event types shown as a visible notification (title, body); every other type is a silent data message the
     * app handles in the background (for example OFFER_CANCELLED revoking an offer).
     */
    static final Map<String,String[]> VISIBLE=Map.ofEntries(
            Map.entry("BOOKING_OFFER",new String[]{"New booking request","Open the app to accept before the offer expires."}),
            Map.entry("PROVIDER_ACCEPTED",new String[]{"Provider found","Confirm and pay to secure your booking."}),
            Map.entry("BOOKING_CONFIRMED",new String[]{"Booking confirmed","Your booking is confirmed. Open the app for the details."}),
            Map.entry("PROVIDER_EN_ROUTE",new String[]{"Provider on the way","Track your provider live in the app."}),
            Map.entry("PROVIDER_ARRIVED",new String[]{"Provider has arrived","Share your start code to begin the service."}),
            Map.entry("BOOKING_STARTED",new String[]{"Service started","Your booking is now in progress."}),
            Map.entry("COMPLETION_REQUESTED",new String[]{"Confirm completion","Your provider marked the service as complete. Please confirm."}),
            Map.entry("BOOKING_COMPLETED",new String[]{"Booking completed","Thank you. You can rate your provider in the app."}),
            Map.entry("PAYMENT_FAILED",new String[]{"Payment failed","Retry the payment or choose another method before the time runs out."}),
            Map.entry("PAYMENT_DEADLINE_EXTENDED",new String[]{"More time to pay","Your payment window was extended. Open the app to pay."}),
            Map.entry("BOOKING_CANCELLED",new String[]{"Booking cancelled","Your booking was cancelled. Open the app to see the details and any refund to your wallet."}),
            Map.entry("BOOKING_UNMATCHED",new String[]{"No provider available","We could not find a provider for your request. Try again or choose another time."}),
            Map.entry("BOOKING_EXPIRED",new String[]{"Booking expired","Your booking request expired. Open the app to book again."}),
            Map.entry("BOOKING_CANCELLED_BY_CUSTOMER",new String[]{"Booking cancelled by customer","The customer cancelled this booking. Your slot is free again."}));
    private final ObjectMapper mapper;
    private final String credentialsPath;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private String accessToken;
    private Instant tokenExpiry=Instant.EPOCH;
    public BookingPushGateway(ObjectMapper mapper,@Value("${booking.push.service-account-file:}") String path){this.mapper=mapper;this.credentialsPath=path;}
    public boolean enabled(){return !credentialsPath.isBlank();}
    public boolean send(String deviceToken,BookingDispatchEvent event) throws Exception {
        var credentials=mapper.readTree(Files.readString(Path.of(credentialsPath)));
        String project=credentials.path("project_id").asText();
        if(!project.matches("[a-z][a-z0-9-]{4,62}")) throw new IllegalStateException("Invalid push project");
        String token=token(credentials);
        var message=mapper.createObjectNode();message.put("token",deviceToken);
        var data=message.putObject("data");
        data.put("eventId",event.getEventId().toString());data.put("requestId",event.getRequestId().toString());
        data.put("type",event.getEventType());
        if(event.getOfferId()!=null)data.put("offerId",event.getOfferId().toString());
        if(event.getExpiresAt()!=null)data.put("expiresAt",event.getExpiresAt().toString());
        boolean incoming="BOOKING_OFFER".equals(event.getEventType());
        // Offer notifications ring through the app-created channel. Revocations are data updates.
        String[] visible=VISIBLE.get(event.getEventType());
        if(visible!=null) {
            var notification=message.putObject("notification");notification.put("title",visible[0]);notification.put("body",visible[1]);
        }
        long ttl=event.getExpiresAt()==null?300:Math.max(0,Duration.between(Instant.now(),event.getExpiresAt()).toSeconds());
        var android=message.putObject("android");android.put("priority","HIGH");android.put("ttl",ttl+"s");
        android.put("collapse_key","booking-"+event.getRequestId());
        if(message.has("notification")){
            var n=android.putObject("notification");n.put("channel_id",incoming?"booking_calls":"booking_updates");n.put("sound","default");n.put("tag","booking-"+event.getRequestId());
        }
        var apns=message.putObject("apns");var headers=apns.putObject("headers");headers.put("apns-expiration",Long.toString(Instant.now().getEpochSecond()+ttl));
        headers.put("apns-priority",message.has("notification")?"10":"5");
        var aps=apns.putObject("payload").putObject("aps");aps.put("content-available",1);
        if(message.has("notification"))aps.put("sound","default");
        var body=mapper.createObjectNode();body.set("message",message);
        var response=client.send(HttpRequest.newBuilder(URI.create("https://fcm.googleapis.com/v1/projects/"+project+"/messages:send"))
                .timeout(Duration.ofSeconds(5)).header("Authorization","Bearer "+token).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()==200)return true;
        // Do not log tokens, credentials or response bodies.
        if(response.statusCode()==401){accessToken=null;tokenExpiry=Instant.EPOCH;}
        if(response.statusCode()==404 && response.body().contains("UNREGISTERED"))return false;
        throw new IllegalStateException("Push delivery returned HTTP "+response.statusCode());
    }
    private synchronized String token(com.fasterxml.jackson.databind.JsonNode credentials) throws Exception {
        if(accessToken!=null && Instant.now().isBefore(tokenExpiry))return accessToken;
        String pem=credentials.path("private_key").asText().replace("-----BEGIN PRIVATE KEY-----","").replace("-----END PRIVATE KEY-----","").replaceAll("\\s","");
        PrivateKey key=KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Instant now=Instant.now();
        String assertion=Jwts.builder().issuer(credentials.path("client_email").asText())
                .claim("scope","https://www.googleapis.com/auth/firebase.messaging")
                .claim("aud","https://oauth2.googleapis.com/token").issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key,Jwts.SIG.RS256).compact();
        String body="grant_type="+URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer",StandardCharsets.UTF_8)
                +"&assertion="+URLEncoder.encode(assertion,StandardCharsets.UTF_8);
        var response=client.send(HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).timeout(Duration.ofSeconds(5))
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        if(response.statusCode()!=200)throw new IllegalStateException("Push authorization failed");
        var value=mapper.readTree(response.body());accessToken=value.path("access_token").asText();
        if(accessToken.isBlank())throw new IllegalStateException("Push authorization returned no token");
        tokenExpiry=now.plusSeconds(Math.max(0,value.path("expires_in").asLong(3600)-60));return accessToken;
    }
}
