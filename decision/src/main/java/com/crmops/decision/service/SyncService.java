package com.crmops.decision.service;

import com.crmops.common.api.Dto.SyncRequest;
import com.crmops.common.api.Dto.SyncResponse;
import com.crmops.decision.domain.ApiKeyRecord;
import com.crmops.decision.domain.AuthUser;
import com.crmops.decision.domain.OrgAssignment;
import com.crmops.decision.repo.ApiKeyRecordRepository;
import com.crmops.decision.repo.AuthUserRepository;
import com.crmops.decision.repo.OrgAssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Receives identity, assignment and API-key data from the channel tier (the
 * only writer of that data) and atomically applies it.
 * <ul>
 *   <li>Users are UPSERTED by id. A null password hash keeps the existing hash
 *       (the channel tier never stores hashes and only sends one when a
 *       password is created or changed).</li>
 *   <li>Assignments and API-key records are REPLACED wholesale: channel has the
 *       complete list and this service must never keep stale grants.</li>
 * </ul>
 * This endpoint is service-authenticated; nothing else in this service accepts
 * identity data from callers.
 */
@Service
public class SyncService {

    private final AuthUserRepository userRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final ApiKeyRecordRepository apiKeyRepo;

    public SyncService(AuthUserRepository userRepo, OrgAssignmentRepository assignmentRepo,
                       ApiKeyRecordRepository apiKeyRepo) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.apiKeyRepo = apiKeyRepo;
    }

    @Transactional
    public SyncResponse replace(SyncRequest request) {
        Instant now = Instant.now();
        int users = 0;
        int assignments = 0;
        int apiKeys = 0;
        if (request.users() != null) {
            for (var u : request.users()) {
                AuthUser existing = userRepo.findById(u.id()).orElse(null);
                if (existing == null) {
                    if (u.passwordHash() == null) {
                        throw new IllegalArgumentException("new user " + u.username() + " requires a password hash");
                    }
                    userRepo.save(new AuthUser(u.id(), u.username(), u.displayName(), u.email(), u.role(),
                            u.enabled(), u.customerOrgId(), u.passwordHash(), now));
                } else {
                    existing.applySync(u.displayName(), u.email(), u.role(), u.enabled(), u.customerOrgId(),
                            u.passwordHash() != null ? u.passwordHash() : existing.getPasswordHash(), now);
                }
                users++;
            }
        }
        if (request.assignments() != null) {
            assignmentRepo.deleteAllInBatch();
            for (var a : request.assignments()) {
                assignmentRepo.save(new OrgAssignment(a.userId(), a.orgId(), a.relation()));
                assignments++;
            }
        }
        if (request.apiKeys() != null) {
            apiKeyRepo.deleteAllInBatch();
            for (var k : request.apiKeys()) {
                apiKeyRepo.save(new ApiKeyRecord(k.keyHash(), k.orgId(), k.name(), k.status(), k.expiresAt()));
                apiKeys++;
            }
        }
        return new SyncResponse(users, assignments, apiKeys);
    }
}
