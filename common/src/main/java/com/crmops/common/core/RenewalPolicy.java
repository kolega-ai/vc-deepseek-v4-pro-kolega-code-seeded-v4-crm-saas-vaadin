package com.crmops.common.core;

import java.time.LocalDate;

/** Renewal-reminder policy shared by channel (dashboards/reports) and batch (reminder job). */
public final class RenewalPolicy {

    /** Organizations with a renewal date inside this window are "at risk". */
    public static final int RENEWAL_WINDOW_DAYS = 30;

    public static boolean withinWindow(LocalDate renewalDate, LocalDate today) {
        if (renewalDate == null) {
            return false;
        }
        return !renewalDate.isBefore(today) && !renewalDate.isAfter(today.plusDays(RENEWAL_WINDOW_DAYS));
    }

    private RenewalPolicy() {
    }
}
