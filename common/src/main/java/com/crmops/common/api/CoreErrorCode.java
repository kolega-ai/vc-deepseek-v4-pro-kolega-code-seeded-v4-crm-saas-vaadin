package com.crmops.common.api;

/**
 * Vendor-core error codes. {@code core-mock} behaves like an external system and
 * reports failures exclusively through this enumeration; callers must branch on
 * the code, never on free-form message text.
 */
public enum CoreErrorCode {

    CORE_1001_INVALID_CREDENTIAL("core-1001", "service credential missing or invalid"),
    CORE_1002_INVALID_SCOPE_TOKEN("core-1002", "scope token missing, expired or invalid"),
    CORE_1003_SCOPE_VIOLATION("core-1003", "requested resource is outside the granted scope"),
    CORE_1004_NOT_FOUND("core-1004", "resource not found"),
    CORE_1005_RATE_LIMITED("core-1005", "too many requests to the vendor core"),
    CORE_1006_BAD_REQUEST("core-1006", "malformed request");

    private final String code;
    private final String message;

    CoreErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
