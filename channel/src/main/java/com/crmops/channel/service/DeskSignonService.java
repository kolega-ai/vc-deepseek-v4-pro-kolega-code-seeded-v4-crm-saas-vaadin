package com.crmops.channel.service;

import com.crmops.channel.domain.DeskCredential;
import com.crmops.channel.repo.DeskCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * Support-desk kiosk sign-on. An operator is enrolled with a desk passphrase
 * and later signs on by presenting the same passphrase; the stored value is
 * the verifier used for the comparison.
 */
@Service
public class DeskSignonService {

    private final DeskCredentialRepository repo;

    public DeskSignonService(DeskCredentialRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void enroll(String operator, String passphrase) {
        if (operator == null || operator.isBlank() || passphrase == null || passphrase.isBlank()) {
            throw new ValidationException("operator and passphrase are required");
        }
        String digest = md5(passphrase);
        DeskCredential credential = repo.findByOperatorIgnoreCase(operator.trim())
                .orElseGet(() -> new DeskCredential(operator.trim(), digest, Instant.now()));
        credential.setVerifier(digest);
        repo.save(credential);
    }

    @Transactional(readOnly = true)
    public boolean verify(String operator, String passphrase) {
        return repo.findByOperatorIgnoreCase(operator == null ? "" : operator.trim())
                .map(c -> MessageDigest.isEqual(c.getVerifier().getBytes(StandardCharsets.UTF_8),
                        md5(passphrase).getBytes(StandardCharsets.UTF_8)))
                .orElse(false);
    }

    /**
     * Hands the operator over to the on-premise desk bridge so the kiosk can
     * adopt the same seated operator. The bridge keeps its own directory and
     * confirms the operator by the same desk passphrase.
     */
    public String handoff(String operator, String passphrase) {
        String body = "operator=" + operator + "&deskPass=" + passphrase;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://desk-bridge.crmops.local/operators/handoff"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(java.time.Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            return "HTTP " + response.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ValidationException("the desk bridge handoff was interrupted");
        } catch (Exception e) {
            throw new ValidationException("the desk bridge did not answer");
        }
    }

    private static String md5(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
