package com.crmops.channel.service;

import com.crmops.common.api.Dto;
import com.crmops.channel.decision.DecisionClient;
import com.crmops.channel.domain.ApiKey;
import com.crmops.channel.domain.OrgAssignment;
import com.crmops.channel.domain.UserAccount;
import com.crmops.channel.repo.ApiKeyRepository;
import com.crmops.channel.repo.OrgAssignmentRepository;
import com.crmops.channel.repo.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pushes the channel tier's identity, assignment and API-key data to the
 * decision tier, which is authoritative for authorization. Mutations call
 * {@link #fullSync()} immediately; a scheduled flusher retries after failures
 * so a brief decision-tier outage self-heals.
 */
@Service
public class DecisionSyncService {

    private static final Logger log = LoggerFactory.getLogger(DecisionSyncService.class);

    private final UserAccountRepository userRepo;
    private final OrgAssignmentRepository assignmentRepo;
    private final ApiKeyRepository apiKeyRepo;
    private final DecisionClient client;
    private final AtomicBoolean dirty = new AtomicBoolean(true);

    public DecisionSyncService(UserAccountRepository userRepo, OrgAssignmentRepository assignmentRepo,
                               ApiKeyRepository apiKeyRepo, DecisionClient client) {
        this.userRepo = userRepo;
        this.assignmentRepo = assignmentRepo;
        this.apiKeyRepo = apiKeyRepo;
        this.client = client;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 20_000)
    public void flushIfDirty() {
        if (dirty.getAndSet(false)) {
            try {
                fullSync();
            } catch (Exception e) {
                log.warn("decision sync failed; will retry: {}", e.getMessage());
                dirty.set(true);
            }
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    @Transactional(readOnly = true)
    public void fullSync() {
        List<Dto.SyncUser> users = userRepo.findAll().stream()
                .map(u -> new Dto.SyncUser(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(),
                        u.getRole().external(), u.isEnabled(), u.getCustomerOrgId(), null))
                .toList();
        List<Dto.SyncAssignment> assignments = assignmentRepo.findAll().stream()
                .map(a -> new Dto.SyncAssignment(a.getUserId(), a.getOrgId(), a.getRelation().name()))
                .toList();
        List<Dto.SyncApiKey> keys = apiKeyRepo.findAll().stream()
                .map(this::toSyncKey)
                .toList();
        Dto.SyncResponse response = client.sync(new Dto.SyncRequest(users, assignments, keys));
        log.info("decision sync complete: {} users, {} assignments, {} api keys",
                response.users(), response.assignments(), response.apiKeys());
    }

    /** Pushes a single user with a (new) password hash; sections left null are skipped. */
    public void syncUserWithPassword(UserAccount user, String bcryptHash) {
        List<Dto.SyncUser> users = List.of(new Dto.SyncUser(user.getId(), user.getUsername(), user.getDisplayName(),
                user.getEmail(), user.getRole().external(), user.isEnabled(), user.getCustomerOrgId(), bcryptHash));
        client.sync(new Dto.SyncRequest(users, null, null));
    }

    private Dto.SyncApiKey toSyncKey(ApiKey key) {
        return new Dto.SyncApiKey(key.getKeyHash(), key.getOrg().getId(), key.getName(),
                key.getStatus().name(), key.getExpiresAt());
    }
}
