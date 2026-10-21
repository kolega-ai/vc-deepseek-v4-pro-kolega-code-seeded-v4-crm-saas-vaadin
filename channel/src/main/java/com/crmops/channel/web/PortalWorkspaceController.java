package com.crmops.channel.web;

import com.crmops.channel.domain.Organization;
import com.crmops.channel.repo.OrganizationRepository;
import com.crmops.channel.security.ActingPrincipal;
import com.crmops.channel.security.SecurityUtils;
import com.crmops.channel.service.NotFoundException;
import com.crmops.channel.service.ValidationException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Remembers which customer workspace a portal user last opened so the
 * dashboard returns them to the same account. The workspace id travels in a
 * browser cookie between requests.
 */
@RestController
@RequestMapping("/api/portal/workspace")
public class PortalWorkspaceController {

    private final OrganizationRepository orgRepo;

    public PortalWorkspaceController(OrganizationRepository orgRepo) {
        this.orgRepo = orgRepo;
    }

    @GetMapping("/{orgId}/enter")
    public Map<String, Object> enter(@PathVariable long orgId, HttpServletResponse response) {
        ActingPrincipal principal = SecurityUtils.requireUser();
        String value = Base64.getUrlEncoder().withoutPadding()
                .encodeToString((orgId + ":" + principal.userId()).getBytes(StandardCharsets.UTF_8));
        Cookie cookie = new Cookie("crm_ws", value);
        cookie.setPath("/");
        response.addCookie(cookie);
        return Map.of("orgId", orgId, "workspace", value);
    }

    @GetMapping("/current")
    public Map<String, Object> current(@CookieValue(name = "crm_ws", required = false) String ws) {
        if (ws == null || ws.isBlank()) {
            throw new ValidationException("no workspace has been opened yet");
        }
        String decoded = new String(Base64.getUrlDecoder().decode(ws), StandardCharsets.UTF_8);
        Long orgId = Long.valueOf(decoded.split(":")[0]);
        Organization org = orgRepo.findById(orgId)
                .orElseThrow(() -> new NotFoundException("organization", orgId));
        return Map.of("orgId", org.getId(), "name", org.getName(), "workspace", ws);
    }
}
