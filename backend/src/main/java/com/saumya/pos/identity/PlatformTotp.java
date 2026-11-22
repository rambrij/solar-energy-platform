package com.saumya.pos.identity;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** RFC 6238 / RFC 4226 TOTP verifier. No raw seed or submitted code is logged. */
@Component
public class PlatformTotp {
    private static final String ALPHABET="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    public String newSecret() {
        byte[] bytes=new byte[20];new java.security.SecureRandom().nextBytes(bytes);
        return base32(bytes);
    }
    public long verifyCounter(String secret,String code,long epochSeconds) {
        if(code==null||!code.matches("[0-9]{6}"))return -1;
        long current=epochSeconds/30;
        for(long counter=Math.max(0,current-1);counter<=current+1;counter++) {
            if(MessageDigest.isEqual(generate(secret,counter).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                code.getBytes(java.nio.charset.StandardCharsets.US_ASCII)))return counter;
        }
        return -1;
    }
    private String generate(String secret,long counter) {
        try {
            byte[] key=decodeBase32(secret);
            Mac mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(key,"HmacSHA1"));
            byte[] digest=mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset=digest[digest.length-1]&0x0f;
            int binary=((digest[offset]&0x7f)<<24)|((digest[offset+1]&0xff)<<16)|((digest[offset+2]&0xff)<<8)|(digest[offset+3]&0xff);
            return "%06d".formatted(binary%1_000_000);
        } catch(Exception e) { throw new IllegalArgumentException("Invalid TOTP seed",e); }
    }
    private static String base32(byte[] input) {
        StringBuilder out=new StringBuilder();int buffer=0,bits=0;
        for(byte b:input){buffer=(buffer<<8)|(b&255);bits+=8;while(bits>=5){out.append(ALPHABET.charAt((buffer>>(bits-5))&31));bits-=5;}}
        if(bits>0)out.append(ALPHABET.charAt((buffer<<(5-bits))&31));
        return out.toString();
    }
    private static byte[] decodeBase32(String input) {
        String value=input.replace("=","").toUpperCase(java.util.Locale.ROOT);
        byte[] out=new byte[value.length()*5/8];int buffer=0,bits=0,index=0;
        for(char c:value.toCharArray()){int n=ALPHABET.indexOf(c);if(n<0)throw new IllegalArgumentException("Invalid base32");buffer=(buffer<<5)|n;bits+=5;if(bits>=8){out[index++]=(byte)(buffer>>(bits-8));bits-=8;}}
        return java.util.Arrays.copyOf(out,index);
    }
}
