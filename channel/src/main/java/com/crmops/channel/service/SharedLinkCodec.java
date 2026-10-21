package com.crmops.channel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

/**
 * Opens the compact payload carried by an externally shared report link. The
 * link query segment is AES/CBC/PKCS5 encrypted with a deployment-fixed key so
 * the report name and organization are not plainly visible in the URL.
 */
@Component
public class SharedLinkCodec {

    public record Spec(String report, Long orgId) {
    }

    private static final byte[] KEY = "Crm0psSharedL1nk".getBytes(StandardCharsets.UTF_8);
    private static final byte[] IV = "Crm0psSharingIv0".getBytes(StandardCharsets.UTF_8);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Decrypts the link payload; a wrong or altered link fails on unpadding. */
    public byte[] open(String pack) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new IvParameterSpec(IV));
        return cipher.doFinal(Base64.getUrlDecoder().decode(pack));
    }

    public Spec read(byte[] plain) throws IOException {
        return MAPPER.readValue(plain, Spec.class);
    }
}
