package com.jobhub.service.customer;

import com.jobhub.exception.PaymentUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Authenticated encryption for customer-readable codes; never expose ciphertext in DTOs. */
@Component
public class BookingCodeCipher {
    private final String configuredKey;
    private final SecureRandom random=new SecureRandom();
    public BookingCodeCipher(@Value("${booking.verification-key:}") String configuredKey) { this.configuredKey=configuredKey; }
    public void requireReady() { key(); }
    private SecretKeySpec key() {
        try {
            byte[] value=Base64.getDecoder().decode(configuredKey);
            if(value.length!=32) throw new IllegalArgumentException();
            return new SecretKeySpec(value,"AES");
        } catch(IllegalArgumentException e) { throw new PaymentUnavailableException("A 32-byte base64 booking verification key is required"); }
    }
    public String generate() { return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)); }
    public String encrypt(String code) {
        try {
            byte[] iv=new byte[12]; random.nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key(),new GCMParameterSpec(128,iv));
            byte[] value=cipher.doFinal(code.getBytes(StandardCharsets.UTF_8));
            byte[] result=new byte[iv.length+value.length]; System.arraycopy(iv,0,result,0,iv.length); System.arraycopy(value,0,result,iv.length,value.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(GeneralSecurityException e) { throw new IllegalStateException("Cannot protect booking code",e); }
    }
    public String decrypt(String encoded) {
        try {
            byte[] data=Base64.getDecoder().decode(encoded);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOf(data,12)));
            return new String(cipher.doFinal(Arrays.copyOfRange(data,12,data.length)),StandardCharsets.UTF_8);
        } catch(GeneralSecurityException|IllegalArgumentException e) { throw new IllegalStateException("Cannot read booking code",e); }
    }
    public boolean matches(String stored,String code) {
        return code!=null && MessageDigest.isEqual(decrypt(stored).getBytes(StandardCharsets.UTF_8),code.getBytes(StandardCharsets.UTF_8));
    }
}
