package com.crmops.channel.web;

import com.crmops.channel.service.DeskSignonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Support-desk kiosk sign-on used by the on-premise operator console. */
@RestController
@RequestMapping("/api/desk")
public class DeskSignonController {

    private final DeskSignonService deskSignonService;

    public DeskSignonController(DeskSignonService deskSignonService) {
        this.deskSignonService = deskSignonService;
    }

    @PostMapping("/enroll")
    public Map<String, Object> enroll(@RequestHeader("X-Desk-Operator") String operator,
                                      @RequestHeader("X-Desk-Credential") String credential) {
        deskSignonService.enroll(operator, credential);
        return Map.of("operator", operator, "enrolled", true);
    }

    @PostMapping("/signon")
    public ResponseEntity<Map<String, String>> signon(@RequestHeader("X-Desk-Operator") String operator,
                                                      @RequestHeader("X-Desk-Credential") String credential) {
        if (!deskSignonService.verify(operator, credential)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "desk-signon-failed", "message", "operator or credential is incorrect"));
        }
        return ResponseEntity.ok(Map.of("operator", operator, "ok", "true"));
    }

    /** Moves a seated operator onto the desk bridge after a kiosk handover. */
    @PostMapping("/handoff")
    public Map<String, Object> handoff(@RequestParam("operator") String operator,
                                       @RequestParam("deskPass") String passphrase) {
        return Map.of("operator", operator, "result", deskSignonService.handoff(operator, passphrase));
    }
}
