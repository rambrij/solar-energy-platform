package com.saumya.pos.identity;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** AES-GCM envelope encryption for platform TOTP seeds. Key material is external configuration only. */
@Component
public class PlatformMfaCrypto {
    private final SecretKeySpec key;
    public PlatformMfaCrypto(@Value("${auth.mfa-encryption-key:}") String configured, Environment env) {
        byte[] material;
        if (configured == null || configured.isBlank()) {
            var profiles=java.util.Set.of(env.getActiveProfiles());
            if (Collections.disjoint(profiles, java.util.Set.of("local","test")) || profiles.contains("prod") || profiles.contains("production"))
                throw new IllegalStateException("AUTH_MFA_ENCRYPTION_KEY is required outside local/test profiles");
            material=sha256("local-test-only-platform-mfa-key-do-not-deploy");
        } else {
            try { material=Base64.getDecoder().decode(configured); }
            catch (IllegalArgumentException e) { throw new IllegalStateException("AUTH_MFA_ENCRYPTION_KEY must be base64 encoded",e); }
            if (material.length!=32) throw new IllegalStateException("AUTH_MFA_ENCRYPTION_KEY must contain exactly 256 bits");
        }
        this.key=new SecretKeySpec(material,"AES");
        Arrays.fill(material,(byte)0);
    }
    public String encrypt(String plaintext) {
        try {
            byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] result=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,result,0,iv.length);System.arraycopy(encrypted,0,result,iv.length,encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch(Exception e) { throw new IllegalStateException("Platform MFA encryption failed",e); }
    }
    public String decrypt(String value) {
        try {
            byte[] packed=Base64.getDecoder().decode(value);
            if(packed.length<29)throw new IllegalArgumentException("Invalid encrypted MFA material");
            byte[] iv=Arrays.copyOfRange(packed,0,12),encrypted=Arrays.copyOfRange(packed,12,packed.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        } catch(Exception e) { throw new IllegalStateException("Platform MFA decryption failed",e); }
    }
    private static byte[] sha256(String value) {
        try{return java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
