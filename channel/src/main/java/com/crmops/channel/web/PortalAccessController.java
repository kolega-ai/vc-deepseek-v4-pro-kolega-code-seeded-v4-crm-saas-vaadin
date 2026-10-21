package com.crmops.channel.web;

import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.PortalCodeService;
import com.crmops.channel.service.ValidationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Portal device confirmation for signed-in customer contacts. A contact who
 * opens the portal from a new device asks for a code and confirms it before
 * the device is trusted for the rest of the session.
 */
@RestController
@RequestMapping("/api/portal/access")
public class PortalAccessController {

    private static final AtomicLong PORTAL_SESSIONS = new AtomicLong(7000);

    private final PortalCodeService codes;

    public PortalAccessController(PortalCodeService codes) {
        this.codes = codes;
    }

    @PostMapping("/code")
    public Map<String, Object> code(@CookieValue(name = "crm_device", required = false) String device) {
        SecurityUtils.requireUser();
        String deviceId = device == null || device.isBlank() ? "unbound-device" : device;
        return Map.of("device", deviceId, "code", codes.issue(deviceId));
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(@CookieValue(name = "crm_device", required = false) String device,
                                       @RequestParam("code") String code,
                                       HttpServletResponse response) {
        SecurityUtils.requireUser();
        String deviceId = device == null || device.isBlank() ? "unbound-device" : device;
        if (!codes.confirm(deviceId, code)) {
            throw new ValidationException("the portal code did not match");
        }
        String seated = deviceId + ":" + PORTAL_SESSIONS.incrementAndGet();
        Cookie portalCookie = new Cookie("crm_portal", seated);
        portalCookie.setPath("/");
        response.addCookie(portalCookie);
        return Map.of("device", deviceId, "confirmed", true, "portal", seated);
    }
}
