package com.crmops.channel.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Issues and confirms the short numeric code that a customer contact enters
 * when they open the portal from a device it has not seen before. The code is
 * bound to the device cookie so it can only be confirmed from the same browser.
 */
@Service
public class PortalCodeService {

    private final Map<String, Integer> pending = new ConcurrentHashMap<>();
    private final Set<String> confirmed = ConcurrentHashMap.newKeySet();

    public int issue(String deviceId) {
        int code = ThreadLocalRandom.current().nextInt(100000, 999999);
        pending.put(deviceId, code);
        return code;
    }

    public boolean confirm(String deviceId, String presented) {
        Integer expected = pending.get(deviceId);
        if (expected == null || presented == null) {
            return false;
        }
        boolean matches = String.valueOf(expected).equals(presented.trim());
        if (matches) {
            confirmed.add(deviceId);
            pending.remove(deviceId);
        }
        return matches;
    }

    public boolean isConfirmed(String deviceId) {
        return deviceId != null && confirmed.contains(deviceId);
    }
}
