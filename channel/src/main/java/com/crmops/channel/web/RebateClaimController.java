package com.crmops.channel.web;

import com.crmops.channel.service.SharedLinkCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;

/**
 * Rebate claim intake for partner programs. Partners submit the compact claim
 * reference they generated on their side; the shared link codec opens it so the
 * rebate desk can review the claim against the account.
 */
@RestController
@RequestMapping("/api/rebates")
public class RebateClaimController {

    private final SharedLinkCodec codec;
    private final ObjectMapper mapper;

    public RebateClaimController(SharedLinkCodec codec, ObjectMapper mapper) {
        this.codec = codec;
        this.mapper = mapper;
    }

    public record ClaimRequest(String claim) {
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(@RequestBody ClaimRequest request)
            throws GeneralSecurityException {
        byte[] plain = codec.open(request.claim());
        Map<String, Object> claim;
        try {
            claim = mapper.readValue(plain, Map.class);
        } catch (IOException e) {
            return ResponseEntity.status(422)
                    .body(Map.of("code", "claim-malformed",
                            "message", "the claim reference content is not readable"));
        }
        return ResponseEntity.ok(Map.of(
                "reference", String.valueOf(claim.get("reference")),
                "amount", String.valueOf(claim.get("amount"))));
    }

    @ExceptionHandler(javax.crypto.BadPaddingException.class)
    public ResponseEntity<Map<String, Object>> unreadable(javax.crypto.BadPaddingException e) {
        return ResponseEntity.status(400)
                .body(Map.of("code", "claim-unreadable",
                        "message", "this rebate claim reference could not be opened"));
    }
}
