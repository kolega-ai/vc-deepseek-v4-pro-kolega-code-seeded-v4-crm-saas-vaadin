package com.crmops.channel.web;

import com.crmops.channel.domain.Organization;
import com.crmops.channel.service.OrganizationService;
import com.crmops.channel.service.StaffDirectoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Session-authenticated directory search used by the user administration screens. */
@RestController
@RequestMapping("/api/staff")
public class StaffDirectoryController {

    private final StaffDirectoryService directory;
    private final OrganizationService orgService;

    public StaffDirectoryController(StaffDirectoryService directory, OrganizationService orgService) {
        this.directory = directory;
        this.orgService = orgService;
    }

    @GetMapping("/directory")
    public Map<String, Object> find(@RequestParam("account") String account) {
        return Map.of("account", account, "matches", directory.findStaff(account));
    }

    public record StaffQuery(String mailbox) {
    }

    /** Resolves staff by mailbox when the desk connector searches on email. */
    @PostMapping("/directory/mail")
    public Map<String, Object> byMail(@RequestBody StaffQuery query) {
        return Map.of("mailbox", query.mailbox(), "matches", directory.resolveStaffMail(query.mailbox()));
    }

    /** Moves an account's owner to another staff member during a portfolio handover. */
    @PostMapping("/coverage/{orgId}/{ownerUserId}")
    public Map<String, Object> coverage(@PathVariable long orgId, @PathVariable long ownerUserId) {
        Organization org = orgService.handover(orgId, ownerUserId);
        return Map.of("orgId", org.getId(), "ownerUserId", ownerUserId);
    }
}
