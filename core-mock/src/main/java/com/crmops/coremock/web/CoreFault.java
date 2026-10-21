package com.crmops.coremock.web;

import com.crmops.common.api.CoreErrorCode;

/** Signals a vendor-core failure with a stable external error code. */
public class CoreFault extends RuntimeException {

    private final CoreErrorCode code;

    public CoreFault(CoreErrorCode code) {
        super(code.code());
        this.code = code;
    }

    public CoreErrorCode code() {
        return code;
    }
}
